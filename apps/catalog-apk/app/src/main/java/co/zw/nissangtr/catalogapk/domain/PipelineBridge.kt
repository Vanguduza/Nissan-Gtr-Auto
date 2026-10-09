package co.zw.nissangtr.catalogapk.domain

import co.zw.nissangtr.catalogapk.data.model.JobEntity
import co.zw.nissangtr.catalogapk.data.model.SiteProfileEntity

enum class PipelineResult {
    COMPLETE,
    PAUSED,
    FAILED,
}

interface PipelineBridge {
    suspend fun run(
        job: JobEntity,
        profile: SiteProfileEntity,
        argv: List<String>,
        shouldPause: suspend () -> Boolean,
        onHeartbeat: suspend () -> Unit,
    ): PipelineResult
}
