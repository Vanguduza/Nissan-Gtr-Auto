package co.zw.nissangtr.delivery.rpc

/**
 * Driver-only RPC boundary for the delivery Compose screens.
 *
 * **Live:** [SupabaseRpcClient] via [RpcClientFactory] when `SUPABASE_URL` +
 * `SUPABASE_ANON_KEY` are set (override with `rpc.forceFake=true`).
 * **Fallback:** [FakeRpcClient].
 *
 * GPS / camera / signature: Bridge-First only (`bridges/android/`) — never HTML5 or WebView.
 */
interface RpcClient {
    fun currentUserId(): String?

    /** Own rows from `staff_roles` (RLS). Gate: driver | admin. */
    suspend fun listMyStaffRoles(): List<String>

    /** Assigned jobs for the signed-in driver (PostgREST + RLS). */
    suspend fun listMyDeliveryJobs(): List<DeliveryJobSummary>

    suspend fun getDeliveryJob(jobId: String): DeliveryJobSummary?

    suspend fun setDriverPresence(
        status: DriverPresenceStatus,
        capacity: Int? = null,
        shiftStartsAt: String? = null,
        shiftEndsAt: String? = null,
        lastLat: Double? = null,
        lastLng: Double? = null,
    ): String

    suspend fun getMyDriverPresence(): DriverPresenceSnapshot?

    /**
     * Bridge-only GPS trail point. Client must throttle ≥~5s.
     * Never from browser / WebView geolocation.
     */
    suspend fun ingestDeliveryLocation(
        deliveryJobId: String,
        lat: Double,
        lng: Double,
        recordedAt: String? = null,
        accuracyM: Double? = null,
    ): String

    suspend fun updateDeliveryJobStatus(
        deliveryJobId: String,
        status: DeliveryJobStatus,
    ): String

    /** Upload local file to `delivery-pods` bucket; returns object key (no bucket prefix). */
    suspend fun uploadPodAsset(
        objectKey: String,
        localFilePath: String,
        mimeType: String,
    ): String

    suspend fun generateDeliveryPodOtp(
        deliveryJobId: String,
        ttl: String? = null,
    ): String

    suspend fun verifyDeliveryPodOtp(
        deliveryJobId: String,
        code: String,
    ): Boolean

    suspend fun submitDeliveryPod(
        deliveryJobId: String,
        photoPath: String,
        signaturePath: String,
        otpCode: String,
        notes: String? = null,
    ): String

    /** Suggest-only — never auto-mutates job status. */
    suspend fun deliveryGeofenceSuggestion(
        deliveryJobId: String,
        lat: Double,
        lng: Double,
        arriveRadiusM: Double? = null,
        completeRadiusM: Double? = null,
    ): GeofenceSuggestion

    suspend fun failDeliveryJob(
        deliveryJobId: String,
        reason: DeliveryFailureReason,
        notes: String? = null,
        createReattempt: Boolean = false,
    ): String

    suspend fun raiseDeliveryPanic(
        deliveryJobId: String? = null,
        lat: Double? = null,
        lng: Double? = null,
    ): String

    suspend fun optimizeDriverStops(driverUserId: String): List<OptimizedStop>

    suspend fun mintDeliveryTrackToken(
        deliveryJobId: String,
        ttl: String? = null,
    ): String
}
