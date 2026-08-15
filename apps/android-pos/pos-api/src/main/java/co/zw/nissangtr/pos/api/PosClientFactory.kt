package co.zw.nissangtr.pos.api

/**
 * Builds [LivePosClient] when URL + anon key are set and [forceFake] is false;
 * otherwise [FakePosClient]. Mirrors management/delivery [RpcClientFactory].
 */
object PosClientFactory {
    fun create(
        supabaseUrl: String,
        supabaseAnonKey: String,
        forceFake: Boolean = false,
    ): PosClient {
        if (forceFake || supabaseUrl.isBlank() || supabaseAnonKey.isBlank()) {
            return FakePosClient()
        }
        return LivePosClient(supabaseUrl = supabaseUrl, supabaseAnonKey = supabaseAnonKey)
    }

    fun isLive(
        supabaseUrl: String,
        supabaseAnonKey: String,
        forceFake: Boolean = false,
    ): Boolean = !forceFake && supabaseUrl.isNotBlank() && supabaseAnonKey.isNotBlank()
}
