package co.zw.nissangtr.pos.data

import co.zw.nissangtr.management.rpc.CatalogLiveException
import co.zw.nissangtr.management.rpc.FakeRpcClient
import co.zw.nissangtr.management.rpc.RpcClient
import co.zw.nissangtr.pos.domain.error.PosError
import co.zw.nissangtr.pos.domain.model.EpcDiagram
import co.zw.nissangtr.pos.domain.model.EpcMissing
import co.zw.nissangtr.pos.domain.model.EpcSection
import co.zw.nissangtr.pos.domain.model.EpcVariant
import co.zw.nissangtr.pos.domain.model.VehicleModel
import co.zw.nissangtr.pos.domain.result.PosResult
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** EPC on the tablet reads the full catalogue through `catalog-live-r2` and fails closed. */
class LiveEpcGatewayTest {
    private val model = VehicleModel("patrol", "Patrol")
    private val variant = EpcVariant("y62-vk56", "Y62", "VK56", null)
    private val section = EpcSection("engine", "Engine")

    private class Live(val answer: (String, Map<String, String>) -> JsonObject) : RpcClient by FakeRpcClient() {
        val calls = mutableListOf<Pair<String, Map<String, String>>>()
        override suspend fun catalogLive(action: String, params: Map<String, String>): JsonObject {
            calls += action to params
            return answer(action, params)
        }
    }

    private fun json(s: String) = Json.parseToJsonElement(s).jsonObject

    @Test
    fun `diagram list comes from the live catalogue by slugs and carries the diagram id`() = runTest {
        val rpc = Live { _, _ -> json("""{"diagrams":[{"diagram_id":"d-1","title":"Cylinder head"},{"diagram_id":"d-2","name_en":"Oil pump"}]}""") }
        val r = RpcSaleGateways(rpc).epc.diagrams(model, variant, section) as PosResult.Ok
        assertEquals(listOf(EpcDiagram("d-1", "Cylinder head", "d-1"), EpcDiagram("d-2", "Oil pump", "d-2")), r.value)
        val (action, params) = rpc.calls.single()
        assertEquals("staff-diagrams", action)
        assertEquals("patrol", params["family_slug"])
        assertEquals("y62-vk56", params["variant_slug"])
        assertEquals("engine", params["section_slug"])
    }

    @Test
    fun `diagram parts and signed image come from R2, deduplicated by part number`() = runTest {
        val rpc = Live { action, _ ->
            when (action) {
                "diagram-image" -> json("""{"signed_url":"https://r2.example/sig"}""")
                else -> json("""{"parts":[{"display_oem_number":"11044-EN200","name":"Head","pnc_code":"11044"},{"display_oem_number":"11044 EN200"},{"normalized_oem_number":"15010EN200","description":"Oil pump"}]}""")
            }
        }
        val d = (RpcSaleGateways(rpc).epc.diagram(model, variant, section, EpcDiagram("d-1", "Head", "d-1")) as PosResult.Ok).value
        assertEquals("https://r2.example/sig", d.imageUrl)
        assertEquals(listOf("11044-EN200", "15010EN200"), d.parts.map { it.oemPartNumber })
        assertEquals("11044", d.parts.first().pncCode)
        assertTrue(d.hotspots.isEmpty())
        assertNull(d.missing)
    }

    @Test
    fun `an unpublished image still shows the parts, marked as publishing`() = runTest {
        val rpc = Live { action, _ ->
            if (action == "diagram-image") throw CatalogLiveException(409, "CATALOG_REPUBLISH_REQUIRED", "not published")
            json("""{"parts":[{"display_oem_number":"A-1"}]}""")
        }
        val d = (RpcSaleGateways(rpc).epc.diagram(model, variant, section, EpcDiagram("d-1", "x", "d-1")) as PosResult.Ok).value
        assertNull(d.imageUrl)
        assertEquals(1, d.parts.size)
        assertEquals(EpcMissing.Publishing, d.missing)
    }

    @Test
    fun `nothing published or R2 not connected fails closed with the reason, never fixture data`() = runTest {
        val notConnected = Live { _, _ -> throw CatalogLiveException(503, null, "R2 is not configured") }
        val e = RpcSaleGateways(notConnected).epc.diagram(model, variant, section, EpcDiagram("d-1", "x", "d-1")) as PosResult.Err
        assertEquals(PosError.BusinessRule("catalog_not_connected", ""), e.error)
        val publishing = Live { _, _ -> throw CatalogLiveException(409, "CATALOG_REPUBLISH_REQUIRED", "x") }
        val l = RpcSaleGateways(publishing).epc.diagrams(model, variant, section) as PosResult.Err
        assertEquals(PosError.BusinessRule("catalog_publishing", ""), l.error)
    }

    private class LiveVehicle(val shard: () -> JsonObject) : RpcClient by FakeRpcClient() {
        val actions = mutableListOf<Pair<String, Map<String, String>>>()
        override suspend fun resolveVehicleMasterId(chassisCode: String, engineCode: String) =
            if (chassisCode == "Y62" && engineCode == "VK56") "vm-y62" else null
        override suspend fun catalogLive(action: String, params: Map<String, String>): JsonObject {
            actions += action to params
            return shard()
        }
    }

    private val y62 = co.zw.nissangtr.pos.domain.model.VehicleSelection("patrol", "Patrol", "Y62", "Y62", "VK56")

    @Test
    fun `vehicle search reads the vehicle's R2 fitment shard joined to shop stock`() = runTest {
        val rpc = LiveVehicle {
            json("""{"results":[{"stock_item_id":"si-9","name":"Oil filter","internal_catalog_ref":"15208-31U0B","stock":{"state":"in_stock","qty":4},"price":{"amount":14.5,"currency":"USD"}}]}""")
        }
        val parts = (RpcPosGateways(rpc).catalog.search("oil", y62) as PosResult.Ok).value
        assertEquals("customer-search", rpc.actions.single().first)
        assertEquals("vm-y62", rpc.actions.single().second["vehicle_id"])
        val p = parts.single()
        assertEquals("si-9", p.stockItemId)
        assertEquals("15208-31U0B", p.oemPartNumber)
        assertEquals(1450L, p.price?.minor)
        assertEquals(4.0, p.saleableQty!!, 0.0)
        // No query: the vehicle's full stocked fitment list.
        RpcPosGateways(rpc).catalog.search("", y62)
        assertEquals("customer-stock", rpc.actions.last().first)
    }

    @Test
    fun `vehicle search falls back to Supabase fitment while R2 is not serving`() = runTest {
        val rpc = LiveVehicle { throw CatalogLiveException(503, null, "R2 is not configured") }
        val r = RpcPosGateways(rpc).catalog.search("oil", y62)
        assertTrue(r is PosResult.Ok)
        assertEquals(1, rpc.actions.size)
    }
}
