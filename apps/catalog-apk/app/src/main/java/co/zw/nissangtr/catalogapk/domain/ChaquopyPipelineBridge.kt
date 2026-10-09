package co.zw.nissangtr.catalogapk.domain

import android.content.Context
import co.zw.nissangtr.catalogapk.data.model.JobEntity
import co.zw.nissangtr.catalogapk.data.model.SiteProfileEntity
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.coroutineContext

/**
 * Chaquopy-backed bridge — runs vendored ``data_pipeline.*`` orchestrators on-device.
 */
class ChaquopyPipelineBridge(
    private val appContext: Context,
) : PipelineBridge {

    override suspend fun run(
        job: JobEntity,
        profile: SiteProfileEntity,
        argv: List<String>,
        shouldPause: suspend () -> Boolean,
        onHeartbeat: suspend () -> Unit,
    ): PipelineResult = withContext(Dispatchers.IO) {
        ensurePythonStarted()

        val outDir = File(job.outRoot)
        val jobRoot = outDir.parentFile ?: outDir
        outDir.mkdirs()
        jobRoot.mkdirs()
        val pauseFlag = File(jobRoot, "pause.flag")
        val heartbeatFile = File(jobRoot, "heartbeat.json")
        if (pauseFlag.exists()) pauseFlag.delete()

        File(outDir, "profile_snapshot.json").writeText(
            OrchestratorArgBuilder.profileSnapshotJson(profile),
        )
        File(outDir, "argv.txt").writeText(argv.joinToString("\n"))

        val done = AtomicBoolean(false)
        val resultCode = AtomicInteger(-1)
        val errorMessage = AtomicReference<String?>(null)

        val py = Python.getInstance()
        val worker = py.getModule("catalog_worker")
        // Prefer a real Python list. catalog_worker also accepts Java ArrayList via size()/get().
        val pyArgv = py.getBuiltins().callAttr("list")
        for (arg in argv) {
            pyArgv.callAttr("append", arg)
        }

        val runner = Thread {
            try {
                val code = worker.callAttr(
                    "run_job",
                    pyArgv,
                    pauseFlag.absolutePath,
                    heartbeatFile.absolutePath,
                ).toInt()
                resultCode.set(code)
            } catch (t: Throwable) {
                errorMessage.set(t.message ?: t.javaClass.simpleName)
                resultCode.set(1)
            } finally {
                done.set(true)
            }
        }.also {
            it.name = "chaquopy-catalog-${job.id.take(8)}"
            it.isDaemon = true
            it.start()
        }

        while (coroutineContext.isActive && !done.get()) {
            if (shouldPause() && !pauseFlag.exists()) {
                pauseFlag.writeText("pause\n")
            }
            if (heartbeatFile.exists()) {
                onHeartbeat()
            }
            delay(1_000)
        }

        runner.join(5_000)
        when (resultCode.get()) {
            0 -> PipelineResult.COMPLETE
            2 -> PipelineResult.PAUSED
            else -> {
                val fromHeartbeat = runCatching {
                    if (!heartbeatFile.exists()) return@runCatching null
                    val text = heartbeatFile.readText()
                    // Prefer {"phase":"error","error":"..."} from catalog_worker.
                    val marker = "\"error\":"
                    val idx = text.indexOf(marker)
                    if (idx < 0) return@runCatching null
                    val start = text.indexOf('"', idx + marker.length)
                    val end = text.indexOf('"', start + 1)
                    if (start >= 0 && end > start) text.substring(start + 1, end) else null
                }.getOrNull()
                val fromFailReason = runCatching {
                    val f = File(job.outRoot, "fail_reason.txt")
                    if (f.exists()) f.readText().trim().takeIf { it.isNotEmpty() } else null
                }.getOrNull()
                val msg = errorMessage.get() ?: fromHeartbeat ?: fromFailReason ?: "exit ${resultCode.get()}"
                File(job.outRoot, "pipeline_error.txt").writeText(msg.take(4000))
                PipelineResult.FAILED
            }
        }
    }

    private fun ensurePythonStarted() {
        synchronized(lock) {
            if (!Python.isStarted()) {
                Python.start(AndroidPlatform(appContext.applicationContext))
            }
        }
    }

    companion object {
        private val lock = Any()
    }
}
