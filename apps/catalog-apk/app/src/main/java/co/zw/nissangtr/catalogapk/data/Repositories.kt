package co.zw.nissangtr.catalogapk.data

import co.zw.nissangtr.catalogapk.data.db.JobDao
import co.zw.nissangtr.catalogapk.data.db.SupabaseProjectDao
import co.zw.nissangtr.catalogapk.data.model.JobDesiredState
import co.zw.nissangtr.catalogapk.data.model.JobEntity
import co.zw.nissangtr.catalogapk.data.model.JobStatus
import co.zw.nissangtr.catalogapk.data.model.SupabaseProjectEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class JobRepository(
    private val jobDao: JobDao,
    private val filesRootProvider: () -> java.io.File,
) {
    fun observeJobs(): Flow<List<JobEntity>> = jobDao.observeAll()

    fun observeJob(id: String): Flow<JobEntity?> = jobDao.observeById(id)

    suspend fun getJob(id: String): JobEntity? = jobDao.getById(id)

    suspend fun createJobs(
        profileId: String,
        maker: String,
        modelSlug: String,
        modelDisplayName: String,
        chassisCodes: List<String>,
    ): List<JobEntity> {
        val now = System.currentTimeMillis()
        return chassisCodes.map { chassis ->
            val id = UUID.randomUUID().toString()
            val jobDir = filesRootProvider().resolve("catalog-jobs/$id")
            val outDir = jobDir.resolve("out")
            jobDir.mkdirs()
            outDir.mkdirs()
            val outRoot = outDir.absolutePath
            JobEntity(
                id = id,
                profileId = profileId,
                maker = maker,
                modelSlug = modelSlug,
                modelDisplayName = modelDisplayName,
                chassisCodesCsv = chassis,
                desiredState = JobDesiredState.RUN.name,
                status = JobStatus.QUEUED.name,
                outRoot = outRoot,
                htmlDropped = 0,
                htmlRetained = 0,
                lastHeartbeat = now,
                createdAt = now,
                updatedAt = now,
                errorMessage = null,
            ).also { jobDao.upsert(it) }
        }
    }

    suspend fun setDesiredState(id: String, desiredState: JobDesiredState) {
        jobDao.updateDesiredState(id, desiredState.name, System.currentTimeMillis())
    }

    suspend fun updateJob(job: JobEntity) {
        jobDao.update(job.copy(updatedAt = System.currentTimeMillis()))
    }

    /**
     * Request cooperative pause if the worker is mid-crawl (pause.flag + desired PAUSE).
     * Caller should cancel WorkManager unique work before [deleteJobRecordAndFiles].
     */
    suspend fun requestCancelBeforeDelete(id: String): JobEntity? {
        val job = jobDao.getById(id) ?: return null
        if (job.status == JobStatus.PROCESSING.name) {
            runCatching {
                val jobRoot = java.io.File(job.outRoot).parentFile ?: java.io.File(job.outRoot)
                jobRoot.mkdirs()
                java.io.File(jobRoot, "pause.flag").writeText("pause\n")
            }
            setDesiredState(id, JobDesiredState.PAUSE)
        }
        return jobDao.getById(id) ?: job
    }

    /** Remove Room row and on-disk `catalog-jobs/{id}/` tree (parent of outRoot). */
    suspend fun deleteJobRecordAndFiles(job: JobEntity) {
        val jobRoot = java.io.File(job.outRoot).parentFile ?: java.io.File(job.outRoot)
        runCatching {
            if (jobRoot.exists()) {
                jobRoot.deleteRecursively()
            }
        }
        jobDao.deleteById(job.id)
    }
}

class ProjectRepository(private val dao: SupabaseProjectDao) {
    fun observeProjects(): Flow<List<SupabaseProjectEntity>> = dao.observeAll()

    suspend fun upsert(project: SupabaseProjectEntity) {
        dao.upsert(project)
    }

    suspend fun delete(id: String) {
        dao.delete(id)
    }
}
