package co.zw.nissangtr.management.rpc

/**
 * Builds [RpcClient]: [SupabaseRpcClient] when URL + anon key are set and
 * [forceFake] is false; otherwise [FakeRpcClient].
 *
 * Auth: live client uses the anon key; JWT comes from GoTrue ([SupabaseRpcClient.auth]).
 * No hardcoded JWTs — call [SupabaseRpcClient.importAccessToken] or sign-in once login UI exists.
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
