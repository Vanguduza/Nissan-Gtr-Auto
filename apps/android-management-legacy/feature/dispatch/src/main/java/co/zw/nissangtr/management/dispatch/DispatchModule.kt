package co.zw.nissangtr.management.dispatch

/**
 * Dispatch / logistics feature module.
 * Pick + DN + assignment / route / panic via [co.zw.nissangtr.management.rpc.RpcClient].
 *
 * Driver GPS producer gated: sole FGS → ingest_delivery_location producer is
 * `apps/android-delivery`. Management staff VIEW live last-point / ETA only.
 */
object DispatchModule {
    const val id: String = "dispatch"
}

