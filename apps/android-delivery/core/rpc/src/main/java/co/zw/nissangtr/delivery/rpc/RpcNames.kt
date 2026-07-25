package co.zw.nissangtr.delivery.rpc

/**
 * Canonical Postgres RPC names for the driver delivery app.
 * Aligns with packages/supabase-client delivery.ts DELIVERY_RPC.
 */
object RpcNames {
    const val SET_DRIVER_PRESENCE = "set_driver_presence"
    const val INGEST_DELIVERY_LOCATION = "ingest_delivery_location"
    const val UPDATE_DELIVERY_JOB_STATUS = "update_delivery_job_status"
    const val SUBMIT_DELIVERY_POD = "submit_delivery_pod"
    const val GENERATE_DELIVERY_POD_OTP = "generate_delivery_pod_otp"
    const val VERIFY_DELIVERY_POD_OTP = "verify_delivery_pod_otp"
    const val DELIVERY_GEOFENCE_SUGGESTION = "delivery_geofence_suggestion"
    const val FAIL_DELIVERY_JOB = "fail_delivery_job"
    const val RAISE_DELIVERY_PANIC = "raise_delivery_panic"
    const val OPTIMIZE_DRIVER_STOPS = "optimize_driver_stops"
    const val MINT_DELIVERY_TRACK_TOKEN = "mint_delivery_track_token"
    const val GET_DELIVERY_TRACK_POINT = "get_delivery_track_point"

    /** Storage bucket for POD photo / signature object keys. */
    const val DELIVERY_PODS_BUCKET = "delivery-pods"
}
