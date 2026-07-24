package co.zw.nissangtr.management.dispatch

/**
 * Dispatch / logistics feature module.
 * Pick + DN RPCs via [co.zw.nissangtr.management.rpc.RpcClient].
 * GPS trail: Bridge-First (`ingest_delivery_location`) — not wired in this scaffold.
 */
object DispatchModule {
    const val id: String = "dispatch"
}
