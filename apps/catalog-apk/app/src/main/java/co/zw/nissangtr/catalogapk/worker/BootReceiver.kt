package co.zw.nissangtr.catalogapk.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import co.zw.nissangtr.catalogapk.CatalogApkApplication
import co.zw.nissangtr.catalogapk.data.model.JobDesiredState
import co.zw.nissangtr.catalogapk.data.model.JobStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Re-enqueues RUN jobs left QUEUED/PAUSED/PROCESSING after reboot. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val app = context.applicationContext as? CatalogApkApplication ?: return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val jobs = app.database.jobDao().findByDesiredStateAndStatuses(
                    desiredState = JobDesiredState.RUN.name,
                    statuses = listOf(
                        JobStatus.QUEUED.name,
                        JobStatus.PAUSED.name,
                        JobStatus.PROCESSING.name,
                    ),
                )
                jobs.forEach { SupervisorScheduler.enqueue(context, it.id) }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
