package co.zw.nissangtr.delivery.rpc

import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/**
 * In-memory stub so driver screens compile and exercise flows without Supabase.
 */
class FakeRpcClient : RpcClient {
    private val jobs = mutableListOf(
        DeliveryJobSummary(
            id = JOB_1,
            deliveryNoteId = "00000000-0000-4000-8000-0000000000d1",
            documentNumber = "DJ-SEED-001",
            status = "dispatched",
            dropoffLat = -17.8292,
            dropoffLng = 31.0522,
            etaAt = "2026-07-25T12:00:00Z",
            etaSeconds = 1800,
            notes = "Gate code 1234",
            routeSequence = 1,
            reattemptOf = null,
            failureReasonCode = null,
            podPhotoPath = null,
            podSignaturePath = null,
            assigneeUserId = FAKE_DRIVER_USER_ID,
            // H4 dual-read seed: divergent majors — display prefers *_minor (COD $45.50).
            settlement = DeliveryJobSettlement(
                currency = CurrencyCode.USD,
                invoiceTotal = 1.0,
                invoiceTotalMinor = 4550L,
                amountPaid = 99.0,
                amountPaidMinor = 0L,
                amountDue = 1.0,
                amountDueMinor = 4550L,
            ),
        ),
        DeliveryJobSummary(
            id = JOB_2,
            deliveryNoteId = "00000000-0000-4000-8000-0000000000d2",
            documentNumber = "DJ-SEED-002",
            status = "pending",
            dropoffLat = -17.8350,
            dropoffLng = 31.0600,
            etaAt = null,
            etaSeconds = null,
            notes = null,
            routeSequence = 2,
            reattemptOf = null,
            failureReasonCode = null,
            podPhotoPath = null,
            podSignaturePath = null,
            assigneeUserId = FAKE_DRIVER_USER_ID,
            settlement = null,
        ),
    )

    private var presence = DriverPresenceSnapshot(
        userId = FAKE_DRIVER_USER_ID,
        status = DriverPresenceStatus.AVAILABLE.rpcValue,
        capacity = 5,
        lastLat = null,
        lastLng = null,
    )

    val ingestedLocationCount: AtomicInteger = AtomicInteger(0)
    val panicCount: AtomicInteger = AtomicInteger(0)
    private val otps = mutableMapOf<String, String>()

    override fun currentUserId(): String? = FAKE_DRIVER_USER_ID

    override suspend fun listMyStaffRoles(): List<String> = listOf("driver")

    override suspend fun listMyDeliveryJobs(): List<DeliveryJobSummary> =
        jobs.sortedWith(
            compareBy<DeliveryJobSummary> { it.routeSequence ?: Int.MAX_VALUE }
                .thenBy { it.documentNumber ?: it.id },
        )

    override suspend fun getDeliveryJob(jobId: String): DeliveryJobSummary? =
        jobs.find { it.id == jobId }

    override suspend fun setDriverPresence(
        status: DriverPresenceStatus,
        capacity: Int?,
        shiftStartsAt: String?,
        shiftEndsAt: String?,
        lastLat: Double?,
        lastLng: Double?,
    ): String {
        presence = presence.copy(
            status = status.rpcValue,
            capacity = capacity ?: presence.capacity,
            lastLat = lastLat ?: presence.lastLat,
            lastLng = lastLng ?: presence.lastLng,
        )
        return FAKE_DRIVER_USER_ID
    }

    override suspend fun getMyDriverPresence(): DriverPresenceSnapshot? = presence

    override suspend fun ingestDeliveryLocation(
        deliveryJobId: String,
        lat: Double,
        lng: Double,
        recordedAt: String?,
        accuracyM: Double?,
    ): String {
        require(deliveryJobId.isNotBlank())
        require(lat in -90.0..90.0) { "lat out of range" }
        require(lng in -180.0..180.0) { "lng out of range" }
        val job = jobs.find { it.id == deliveryJobId }
        if (job != null) {
            require(job.status !in listOf("completed", "failed")) {
                "cannot ingest locations for terminal job"
            }
        }
        ingestedLocationCount.incrementAndGet()
        return UUID.randomUUID().toString()
    }

    override suspend fun updateDeliveryJobStatus(
        deliveryJobId: String,
        status: DeliveryJobStatus,
    ): String {
        val idx = jobs.indexOfFirst { it.id == deliveryJobId }
        require(idx >= 0) { "job not found" }
        val current = jobs[idx]
        require(current.status !in listOf("completed", "failed")) {
            "terminal delivery job cannot change status"
        }
        jobs[idx] = current.copy(status = status.rpcValue)
        return deliveryJobId
    }

    override suspend fun uploadPodAsset(
        objectKey: String,
        localFilePath: String,
        mimeType: String,
    ): String {
        require(objectKey.isNotBlank())
        require(File(localFilePath).exists() || localFilePath.startsWith("fake://")) {
            "POD local file missing: $localFilePath"
        }
        require(mimeType.isNotBlank())
        return objectKey
    }

    override suspend fun generateDeliveryPodOtp(
        deliveryJobId: String,
        ttl: String?,
    ): String {
        require(jobs.any { it.id == deliveryJobId }) { "job not found" }
        val code = "123456"
        otps[deliveryJobId] = code
        return "otp-generated"
    }

    override suspend fun verifyDeliveryPodOtp(
        deliveryJobId: String,
        code: String,
    ): Boolean {
        val expected = otps[deliveryJobId] ?: return false
        return expected == code.trim()
    }

    override suspend fun submitDeliveryPod(
        deliveryJobId: String,
        photoPath: String,
        signaturePath: String,
        otpCode: String,
        notes: String?,
    ): String {
        require(photoPath.isNotBlank() && signaturePath.isNotBlank())
        require(verifyDeliveryPodOtp(deliveryJobId, otpCode)) { "OTP invalid" }
        val idx = jobs.indexOfFirst { it.id == deliveryJobId }
        require(idx >= 0) { "job not found" }
        jobs[idx] = jobs[idx].copy(
            status = DeliveryJobStatus.COMPLETED.rpcValue,
            podPhotoPath = photoPath,
            podSignaturePath = signaturePath,
        )
        return deliveryJobId
    }

    override suspend fun deliveryGeofenceSuggestion(
        deliveryJobId: String,
        lat: Double,
        lng: Double,
        arriveRadiusM: Double?,
        completeRadiusM: Double?,
    ): GeofenceSuggestion {
        val job = jobs.find { it.id == deliveryJobId }
            ?: return GeofenceSuggestion(null, false, false)
        val dLat = job.dropoffLat ?: return GeofenceSuggestion(null, false, false)
        val dLng = job.dropoffLng ?: return GeofenceSuggestion(null, false, false)
        val dist = haversineM(lat, lng, dLat, dLng)
        val arriveR = arriveRadiusM ?: 150.0
        val completeR = completeRadiusM ?: 50.0
        return GeofenceSuggestion(
            distanceM = dist,
            suggestArrive = dist <= arriveR && job.status == "dispatched",
            suggestComplete = dist <= completeR && job.status == "dispatched",
        )
    }

    override suspend fun failDeliveryJob(
        deliveryJobId: String,
        reason: DeliveryFailureReason,
        notes: String?,
        createReattempt: Boolean,
    ): String {
        val idx = jobs.indexOfFirst { it.id == deliveryJobId }
        require(idx >= 0) { "job not found" }
        jobs[idx] = jobs[idx].copy(
            status = DeliveryJobStatus.FAILED.rpcValue,
            failureReasonCode = reason.rpcValue,
        )
        if (createReattempt) {
            val parent = jobs[idx]
            jobs.add(
                parent.copy(
                    id = UUID.randomUUID().toString(),
                    status = DeliveryJobStatus.PENDING.rpcValue,
                    reattemptOf = deliveryJobId,
                    failureReasonCode = null,
                    podPhotoPath = null,
                    podSignaturePath = null,
                    documentNumber = "${parent.documentNumber}-R",
                    routeSequence = (parent.routeSequence ?: 0) + 10,
                ),
            )
        }
        return deliveryJobId
    }

    override suspend fun raiseDeliveryPanic(
        deliveryJobId: String?,
        lat: Double?,
        lng: Double?,
    ): String {
        panicCount.incrementAndGet()
        return UUID.randomUUID().toString()
    }

    override suspend fun optimizeDriverStops(driverUserId: String): List<OptimizedStop> {
        require(driverUserId.isNotBlank())
        val active = jobs.filter {
            it.assigneeUserId == driverUserId &&
                it.status in listOf("pending", "dispatched")
        }
        // Nearest-neighbor from Harare CBD seed for Fake demos.
        var lat = -17.8250
        var lng = 31.0500
        val remaining = active.toMutableList()
        val ordered = mutableListOf<OptimizedStop>()
        var seq = 1
        while (remaining.isNotEmpty()) {
            val next = remaining.minByOrNull { j ->
                val jl = j.dropoffLat ?: lat
                val jg = j.dropoffLng ?: lng
                haversineM(lat, lng, jl, jg)
            }!!
            remaining.remove(next)
            val dist = if (next.dropoffLat != null && next.dropoffLng != null) {
                haversineM(lat, lng, next.dropoffLat, next.dropoffLng)
            } else null
            ordered.add(
                OptimizedStop(
                    deliveryJobId = next.id,
                    routeSequence = seq++,
                    distanceM = dist,
                ),
            )
            if (next.dropoffLat != null && next.dropoffLng != null) {
                lat = next.dropoffLat
                lng = next.dropoffLng
            }
            val idx = jobs.indexOfFirst { it.id == next.id }
            if (idx >= 0) {
                jobs[idx] = jobs[idx].copy(routeSequence = ordered.last().routeSequence)
            }
        }
        return ordered
    }

    override suspend fun mintDeliveryTrackToken(
        deliveryJobId: String,
        ttl: String?,
    ): String {
        require(jobs.any { it.id == deliveryJobId }) { "job not found" }
        return "fake-track-token-${deliveryJobId.take(8)}"
    }

    companion object {
        const val FAKE_DRIVER_USER_ID = "00000000-0000-4000-8000-0000000000d0"
        const val JOB_1 = "00000000-0000-4000-8000-0000000000j1"
        const val JOB_2 = "00000000-0000-4000-8000-0000000000j2"

        /** Last generated OTP in Fake (always 123456). */
        const val FAKE_OTP = "123456"

        private fun haversineM(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
            val r = 6_371_000.0
            val p1 = Math.toRadians(lat1)
            val p2 = Math.toRadians(lat2)
            val dp = Math.toRadians(lat2 - lat1)
            val dl = Math.toRadians(lng2 - lng1)
            val a = kotlin.math.sin(dp / 2) * kotlin.math.sin(dp / 2) +
                kotlin.math.cos(p1) * kotlin.math.cos(p2) *
                kotlin.math.sin(dl / 2) * kotlin.math.sin(dl / 2)
            return 2 * r * kotlin.math.asin(kotlin.math.sqrt(a))
        }
    }
}
