package co.zw.nissangtr.pos

import android.app.Application
import co.zw.nissangtr.pos.api.LivePosClient
import co.zw.nissangtr.pos.api.PosClient
import co.zw.nissangtr.pos.api.PosClientFactory

/**
 * Application-scoped POS runtime so orientation recreate keeps the same
 * [PosClient] / GoTrue session (Compose `remember` alone does not).
 */
class PosApplication : Application() {
    lateinit var posClient: PosClient
        private set
    var liveClient: LivePosClient? = null
        private set
    var forceFake: Boolean = true
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        val force = BuildConfig.RPC_FORCE_FAKE
        val url = BuildConfig.SUPABASE_URL
        val anon = BuildConfig.SUPABASE_ANON_KEY
        val live = PosClientFactory.isLive(url, anon, force)
        forceFake = force || !live
        posClient = PosClientFactory.create(url, anon, force)
        liveClient = posClient as? LivePosClient
    }

    companion object {
        @Volatile
        private var instance: PosApplication? = null

        fun get(): PosApplication =
            instance ?: error("PosApplication not initialized")
    }
}
