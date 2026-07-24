package co.zw.nissangtr.management.rpc

/**
 * Canonical Postgres RPC names for management surfaces.
 * Live binding: `supabase.postgrest.rpc(RpcNames.X, params)`.
 * Supabase Kotlin SDK is not wired yet — see [FakeRpcClient].
 */
object RpcNames {
    // Phase 9 HR (gross payroll only — no PAYE/NSSA UI)
    const val CLOCK_ATTENDANCE = "clock_attendance"

    // Phase 10 logistics / pick-pack / DN
    const val CREATE_PICK_LIST = "create_pick_list"
    const val CONFIRM_PICK_LINES = "confirm_pick_lines"
    const val CREATE_DELIVERY_NOTE = "create_delivery_note"
    const val SUBMIT_DELIVERY_NOTE = "submit_delivery_note"
    const val CANCEL_DELIVERY_NOTE = "cancel_delivery_note"
    const val CREATE_DELIVERY_JOB = "create_delivery_job"
    const val UPDATE_DELIVERY_JOB_STATUS = "update_delivery_job_status"
    /** Bridge-only ingest (~5s). Do not call from Compose with browser geolocation. */
    const val INGEST_DELIVERY_LOCATION = "ingest_delivery_location"
}
