package co.zw.nissangtr.customer.rpc

/** Production customer transport factory. Production runtime is live Supabase only. */
object RpcClientFactory {
    fun create(supabaseUrl: String, supabaseAnonKey: String): RpcClient {
        require(supabaseUrl.isNotBlank()) { "SUPABASE_URL is required" }
        require(supabaseAnonKey.isNotBlank()) { "SUPABASE_ANON_KEY is required" }
        return SupabaseRpcClient.create(supabaseUrl.trim(), supabaseAnonKey.trim())
    }

    fun isLive(supabaseUrl: String, supabaseAnonKey: String): Boolean =
        supabaseUrl.isNotBlank() && supabaseAnonKey.isNotBlank()
}
