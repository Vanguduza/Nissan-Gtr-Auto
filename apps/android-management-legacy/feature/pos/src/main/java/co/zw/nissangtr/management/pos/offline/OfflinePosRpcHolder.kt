package co.zw.nissangtr.management.pos.offline

import co.zw.nissangtr.management.rpc.RpcClient
import java.util.concurrent.atomic.AtomicReference

/**
 * Process-scoped live [RpcClient] for WorkManager drain.
 * Set from the signed-in management session; never holds manager approval tokens.
 */
object OfflinePosRpcHolder {
    private val ref = AtomicReference<RpcClient?>(null)

    fun set(client: RpcClient?) {
        ref.set(client)
    }

    fun get(): RpcClient? = ref.get()
}
