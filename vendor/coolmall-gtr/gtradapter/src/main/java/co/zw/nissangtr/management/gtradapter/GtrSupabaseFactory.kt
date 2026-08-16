package co.zw.nissangtr.management.gtradapter

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage

/**
 * Builds a shared supabase-kt client for Live adapters.
 * Patterns transplanted from `apps/android-management/core/rpc` (no legacy UI).
 */
object GtrSupabaseFactory {
    fun create(config: GtrSupabaseConfig): SupabaseClient {
        require(config.useLive()) {
            "GtrSupabaseFactory requires URL + anon key and forceFake=false"
        }
        return createSupabaseClient(
            supabaseUrl = config.supabaseUrl.trim(),
            supabaseKey = config.supabaseAnonKey.trim(),
        ) {
            install(Auth)
            install(Postgrest)
            install(Storage)
        }
    }
}

/** Runtime switch shared by [di.GtrAdapterModule]. */
sealed class GtrSupabaseRuntime {
    data object Fake : GtrSupabaseRuntime()
    data class Live(val client: SupabaseClient) : GtrSupabaseRuntime()

    companion object {
        fun from(config: GtrSupabaseConfig): GtrSupabaseRuntime =
            if (config.useLive()) Live(GtrSupabaseFactory.create(config)) else Fake
    }
}
