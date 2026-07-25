package co.zw.nissangtr.delivery.rpc

/**
 * Builds [RpcClient]: [SupabaseRpcClient] when URL + anon key are set and
 * [forceFake] is false; otherwise [FakeRpcClient].
 */
object RpcClientFactory {
    fun create(
        supabaseUrl: String,
        supabaseAnonKey: String,
        forceFake: Boolean = false,
    ): RpcClient {
        if (forceFake || supabaseUrl.isBlank() || supabaseAnonKey.isBlank()) {
            return FakeRpcClient()
        }
        return SupabaseRpcClient.create(supabaseUrl.trim(), supabaseAnonKey.trim())
    }

    fun isLive(
        supabaseUrl: String,
        supabaseAnonKey: String,
        forceFake: Boolean = false,
    ): Boolean = !forceFake && supabaseUrl.isNotBlank() && supabaseAnonKey.isNotBlank()
}
