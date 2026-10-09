package co.zw.nissangtr.management.pos.offline.bundle

import co.zw.nissangtr.management.rpc.ManagerApproval
import co.zw.nissangtr.management.rpc.RpcClient
import co.zw.nissangtr.management.rpc.VehicleMasterEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject

/**
 * The tablet's RPC client with the downloaded catalogue behind its catalogue calls: the vehicle
 * master and the `catalog-live-r2` actions the POS uses (sections, diagrams, diagram parts and
 * images, vehicle stock search). With no connection they are answered from [bundle]; online they go
 * to the server and fall back to the bundle if the server cannot be reached. Every other call
 * (carts, payments, customers) is the server's alone.
 */
class OfflineCatalogRpcClient(
    private val delegate: RpcClient,
    private val bundle: OfflineCatalogBundle,
    private val isOnline: () -> Boolean,
    /** The shop's stocked parts by normalised part number (the offline stock snapshot). */
    private val stock: () -> Map<String, OfflineStockLine>,
) : RpcClient by delegate, ManagerApproval {

    /** Manager approvals sign in on the real client; without one there is no manager session to use. */
    override suspend fun <T> withManagerApproval(managerIdentifier: String, managerPassword: String, block: suspend () -> T): T =
        (delegate as? ManagerApproval)?.withManagerApproval(managerIdentifier, managerPassword, block) ?: block()

    override suspend fun listVehicleMaster(): List<VehicleMasterEntry> =
        pick({ delegate.listVehicleMaster() }) { bundle.vehicleMaster() }

    // The interface default reads listVehicleMaster(); delegation would bypass the override above.
    override suspend fun resolveVehicleMasterId(chassisCode: String, engineCode: String): String? =
        listVehicleMaster()
            .filter {
                it.chassisCode.equals(chassisCode.trim(), ignoreCase = true) &&
                    it.engineCode.orEmpty().equals(engineCode.trim(), ignoreCase = true)
            }
            .map { it.id }
            .minOrNull()

    override suspend fun catalogLive(action: String, params: Map<String, String>): JsonObject {
        val local: (() -> JsonObject)? = when (action) {
            "staff-sections" -> params["variant_id"]?.let { v -> { bundle.sections(v) } }
            "staff-diagrams" -> params["variant_id"]?.let { v ->
                {
                    bundle.diagrams(
                        vehicleId = v,
                        sectionId = params["section_id"].orEmpty(),
                        limit = params["limit"]?.toIntOrNull()?.coerceIn(1, 500) ?: 200,
                        offset = params["offset"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0,
                    )
                }
            }
            "staff-diagram-parts" -> params["diagram_id"]?.let { d -> { bundle.diagramParts(d) } }
            "diagram-image" -> params["diagram_id"]?.let { d -> { bundle.diagramImage(d) } }
            "customer-stock", "customer-search" -> params["vehicle_id"]?.let { v ->
                {
                    bundle.vehicleStock(
                        vehicleId = v,
                        query = if (action == "customer-search") params["q"].orEmpty() else "",
                        stock = stock(),
                        limit = params["limit"]?.toIntOrNull()?.coerceIn(1, 100) ?: 100,
                    )
                }
            }
            else -> null
        }
        if (local == null) return delegate.catalogLive(action, params)
        return pick({ delegate.catalogLive(action, params) }, local)
    }

    private suspend fun <T> pick(live: suspend () -> T, local: () -> T): T {
        if (bundle.ready && !isOnline()) return withContext(Dispatchers.IO) { local() }
        return try {
            live()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!bundle.ready) throw e
            try {
                withContext(Dispatchers.IO) { local() }
            } catch (inner: CancellationException) {
                throw inner
            } catch (_: Exception) {
                throw e
            }
        }
    }
}
