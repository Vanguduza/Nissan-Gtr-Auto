package co.zw.nissangtr.catalogapk.ui.navigation

object CatalogRoutes {
    const val TARGETS = "targets"
    const val NEW_SESSION = "new_session"
    const val JOBS = "jobs"
    const val JOB_DETAIL = "job_detail/{jobId}"
    const val BUNDLE_PICKER = "bundle_picker/{jobId}"
    const val PROJECTS = "projects"

    fun jobDetail(jobId: String) = "job_detail/$jobId"
    fun bundlePicker(jobId: String) = "bundle_picker/$jobId"
}
