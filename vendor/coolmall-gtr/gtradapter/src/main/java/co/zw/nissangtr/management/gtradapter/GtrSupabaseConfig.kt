package co.zw.nissangtr.management.gtradapter

/**
 * Supabase inject knobs for CoolMall management.
 * App module supplies values from BuildConfig ← `local.properties`
 * (`SUPABASE_URL`, `SUPABASE_ANON_KEY`, `rpc.forceFake`). Never hardcode secrets.
 */
interface GtrSupabaseConfig {
    val supabaseUrl: String
    val supabaseAnonKey: String
    /** When true, always Fake even if URL+anon are set. */
    val forceFake: Boolean

    fun useLive(): Boolean =
        !forceFake && supabaseUrl.isNotBlank() && supabaseAnonKey.isNotBlank()
}

/** Default for JVM unit tests that do not bind an app BuildConfig. */
object FakeGtrSupabaseConfig : GtrSupabaseConfig {
    override val supabaseUrl: String = ""
    override val supabaseAnonKey: String = ""
    override val forceFake: Boolean = true
}
