package co.zw.nissangtr.catalogapk.ui.navigation

import android.net.Uri

object CatalogRoutes {
    const val TARGETS = "targets"
    const val NEW_SESSION = "new_session"
    const val JOBS = "jobs"
    const val JOB_DETAIL = "job_detail/{jobId}"
    const val BUNDLE_PICKER = "bundle_picker/{jobId}"
    const val BUNDLE_REVIEW = "bundle_review/{jobId}"
    const val BUNDLE_SECTION = "bundle_review/{jobId}/section/{sectionKey}"
    const val BUNDLE_DIAGRAM = "bundle_review/{jobId}/diagram/{diagramKey}"
    const val PROJECTS = "projects"

    fun jobDetail(jobId: String) = "job_detail/$jobId"
    fun bundlePicker(jobId: String) = "bundle_picker/$jobId"
    fun bundleReview(jobId: String) = "bundle_review/$jobId"
    fun bundleSection(jobId: String, sectionKey: String) =
        "bundle_review/$jobId/section/${Uri.encode(sectionKey)}"
    fun bundleDiagram(jobId: String, diagramKey: String) =
        "bundle_review/$jobId/diagram/${Uri.encode(diagramKey)}"
}
