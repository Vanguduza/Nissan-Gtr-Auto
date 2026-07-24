package co.zw.nissangtr.management.dispatch

/**
 * Dispatch / logistics feature module.
 * Pick + DN RPCs via [co.zw.nissangtr.management.rpc.RpcClient].
 * GPS trail: Bridge-First via `:location-tracker` → `ingest_delivery_location`.
 */
object DispatchModule {
    const val id: String = "dispatch"
}
