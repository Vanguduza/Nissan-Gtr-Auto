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

    // --- Cash and card on delivery. Only the assigned driver of a dispatched job; idempotent on request ids.

    suspend fun getDeliveryPaymentContext(deliveryJobId: String): DeliveryPaymentContext? = null

    suspend fun collectDeliveryCash(deliveryJobId: String, amount: Double, requestId: String, notes: String?): DeliveryCashReceipt =
        throw UnsupportedOperationException("cash on delivery needs the live backend")

    suspend fun listDeliveryCardTerminals(warehouseId: String?, deviceId: String): List<DeliveryCardTerminal> = emptyList()

    /** Pairs this phone with a machine an admin assigned to [deviceId] (evidence public key). */
    suspend fun registerDeliveryCardDeviceKey(terminalId: String, deviceId: String, publicKeySpkiBase64: String, keySha256: String): String =
        throw UnsupportedOperationException("card on delivery needs the live backend")

    suspend fun beginDeliveryCardPayment(deliveryJobId: String, terminalId: String, deviceId: String, amount: Double, requestId: String): DeliveryCardAttempt =
        throw UnsupportedOperationException("card on delivery needs the live backend")

    suspend fun getDeliveryCardAttempt(attemptId: String): DeliveryCardAttempt =
        throw UnsupportedOperationException("card on delivery needs the live backend")

    /** Signed machine answer (`card-terminal-result`); [payloadJson] is the canonical JSON that was signed. */
    suspend fun submitCardTerminalEvidence(payloadJson: String, signatureBase64: String): DeliveryCardAttempt =
        throw UnsupportedOperationException("card on delivery needs the live backend")

    /** Approved on the machine → posts the payment against the delivery invoice. */
    suspend fun finalizeDeliveryCardPayment(attemptId: String): DeliveryCardAttempt =
        throw UnsupportedOperationException("card on delivery needs the live backend")

    /** The customer cannot pay the rest: leave it on account (credit check, else dispatch decides). */
    suspend fun requestDeliveryBalanceOnAccount(deliveryJobId: String, reason: String): DeliveryBalanceApproval =
        throw UnsupportedOperationException("needs the live backend")

    suspend fun getDeliveryBalanceApproval(deliveryJobId: String): DeliveryBalanceApproval? = null

    /** The unresolved card attempt of this delivery, if any (no answer, or charged but not posted). */
    suspend fun getDeliveryCardRecovery(deliveryJobId: String): DeliveryCardAttempt? = null
}
