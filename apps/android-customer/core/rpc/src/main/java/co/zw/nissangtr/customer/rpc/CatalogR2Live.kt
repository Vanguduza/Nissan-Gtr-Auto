package co.zw.nissangtr.customer.rpc

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Android customer transport for the hosted Nissan EPC data plane.
 *
 * The app sends only the canonical vehicle-master id / requested scope. The Edge gateway reads
 * the matching compact part shard from Cloudflare R2 and joins it to Supabase commerce data.
 * The 6+ GB catalog bundle is never downloaded by normal customer browsing.
 */
object CatalogR2Live {
    private const val FUNCTION = "catalog-live-r2"
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Compatibility bridge for any pre-hardening caller that still passes chassis/engine only.
     * It resolves against the published master and fails closed if the combination is ambiguous.
     */
    suspend fun resolveVehicleMasterId(
        client: SupabaseClient,
        chassisCode: String,
        engineCode: String?,
    ): String {
        val chassis = chassisCode.trim()
        require(chassis.isNotEmpty()) { "chassis required" }
        val engine = engineCode?.trim()?.takeIf { it.isNotEmpty() }
        val matches = CatalogRpcLive.listVehicleMaster(client).filter { row ->
            row.chassisCode.equals(chassis, ignoreCase = true) &&
                (engine == null || row.engineCode?.equals(engine, ignoreCase = true) == true)
        }
        val ids = matches.mapNotNull { it.id?.trim()?.takeIf(String::isNotEmpty) }.distinct()
        return when (ids.size) {
            1 -> ids.single()
            0 -> error("Vehicle is not present in the published Nissan master. Please reselect it.")
            else -> error("Vehicle identity is ambiguous in the published master. Please reselect the exact model and engine.")
        }
    }

    suspend fun listCatalogForVehicle(
        client: SupabaseClient,
        vehicleMasterId: String,
        category: String? = null,
        limit: Int = 50,
    ): CatalogBrowseResult {
        val vehicleId = vehicleMasterId.trim()
        require(vehicleId.isNotEmpty()) { "canonical vehicle id required" }

        val response = client.functions.invoke(FUNCTION) {
            setBody(
                buildJsonObject {
                    put("action", "customer-stock")
                    put("maker", "nissan")
                    put("vehicle_id", vehicleId)
                    category?.trim()?.takeIf { it.isNotEmpty() }?.let { put("category", it) }
                    put("limit", limit.coerceIn(1, 100))
                },
            )
        }
        val root = json.parseToJsonElement(response.bodyAsText()) as? JsonObject
            ?: error("Invalid live catalog response")
        root["error"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let { message ->
            val status = root["status"]?.jsonPrimitive?.contentOrNull
            if (status == "CATALOG_REPUBLISH_REQUIRED") {
                error("Live EPC fitment index is being published. Fitment was not guessed.")
            }
            error(message)
        }

        val results = root["results"] as? JsonArray ?: JsonArray(emptyList())
        val items = results.mapNotNull { element ->
            val row = element as? JsonObject ?: return@mapNotNull null
            val internalRef = row["internal_catalog_ref"]?.jsonPrimitive?.contentOrNull
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: return@mapNotNull null
            val stock = row["stock"] as? JsonObject
            val state = when (stock?.get("state")?.jsonPrimitive?.contentOrNull?.lowercase()) {
                "in_stock" -> StockState.IN_STOCK
                "low" -> StockState.LOW
                else -> StockState.BACKORDER
            }
            val price = row["price"] as? JsonObject
            val currency = price?.get("currency")?.jsonPrimitive?.contentOrNull?.uppercase()
            val amount = price?.get("amount")?.jsonPrimitive?.doubleOrNull
            CatalogListItem(
                stockItemId = row["stock_item_id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                oem = internalRef,
                name = row["name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty().ifEmpty { "Nissan part" },
                stock = state,
                usd = if (currency == "USD") amount else null,
                category = row["subcategory"]?.jsonPrimitive?.contentOrNull
                    ?: row["category"]?.jsonPrimitive?.contentOrNull,
            )
        }
        return CatalogBrowseResult(
            items = items,
            categories = items.mapNotNull { it.category?.trim()?.takeIf(String::isNotEmpty) }
                .distinct()
                .sorted(),
        )
    }

    suspend fun checkFitment(
        client: SupabaseClient,
        vehicleMasterId: String,
        oem: String,
    ): String {
        val response = client.functions.invoke(FUNCTION) {
            setBody(
                buildJsonObject {
                    put("action", "fitment-check")
                    put("maker", "nissan")
                    put("vehicle_id", vehicleMasterId.trim())
                    put("oem", oem.trim())
                },
            )
        }
        val root = json.parseToJsonElement(response.bodyAsText()) as? JsonObject
            ?: return "FITMENT_UNRESOLVED"
        val fitment = root["fitment"] as? JsonObject ?: return "FITMENT_UNRESOLVED"
        return fitment["status"]?.jsonPrimitive?.contentOrNull ?: "FITMENT_UNRESOLVED"
    }
}
