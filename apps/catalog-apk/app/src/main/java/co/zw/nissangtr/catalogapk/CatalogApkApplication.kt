package co.zw.nissangtr.catalogapk

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import co.zw.nissangtr.catalogapk.auth.SupabaseSessionManager
import co.zw.nissangtr.catalogapk.data.JobRepository
import co.zw.nissangtr.catalogapk.data.ProjectRepository
import co.zw.nissangtr.catalogapk.data.db.CatalogDatabase
import co.zw.nissangtr.catalogapk.data.prefs.AppPreferences
import co.zw.nissangtr.catalogapk.data.prefs.SecurePrefs
import co.zw.nissangtr.catalogapk.data.profile.ProfileRepository
import co.zw.nissangtr.catalogapk.data.profile.ProfileSeeder
import co.zw.nissangtr.catalogapk.worker.SupervisorReclaimWorker
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CatalogApkApplication : Application(), Configuration.Provider {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var database: CatalogDatabase
        private set
    lateinit var appPreferences: AppPreferences
        private set
    lateinit var securePrefs: SecurePrefs
        private set
    lateinit var profileRepository: ProfileRepository
        private set
    lateinit var jobRepository: JobRepository
        private set
    lateinit var projectRepository: ProjectRepository
        private set
    lateinit var supabaseSession: SupabaseSessionManager
        private set

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        database = CatalogDatabase.get(this)
        appPreferences = AppPreferences(this)
        securePrefs = SecurePrefs(this)
        profileRepository = ProfileRepository(database.siteProfileDao())
        jobRepository = JobRepository(database.jobDao()) { filesDir }
        projectRepository = ProjectRepository(database.supabaseProjectDao())
        supabaseSession = SupabaseSessionManager(this, securePrefs)

        applicationScope.launch {
            runCatching {
                if (!Python.isStarted()) {
                    Python.start(AndroidPlatform(this@CatalogApkApplication))
                }
            }
        }

        // Supervisor + profile seed only in the default process.
        if (isMainProcess()) {
            applicationScope.launch {
                ProfileSeeder(this@CatalogApkApplication, database.siteProfileDao(), appPreferences)
                    .seedIfNeeded()
            }
            SupervisorReclaimWorker.ensureScheduled(this)
        }
    }

    private fun isMainProcess(): Boolean {
        val pid = android.os.Process.myPid()
        val am = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
        return am.runningAppProcesses.orEmpty().firstOrNull { it.pid == pid }
            ?.processName == packageName
    }
}
