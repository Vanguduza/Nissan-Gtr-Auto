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
            dropoffAddressText = "12 Samora Machel Ave, Harare",
            lineItems = listOf(
                DeliveryJobLineItem(
                    lineId = "line-seed-1a",
                    qty = 2.0,
                    oemPartNumber = "40206-EG000",
                    description = "Front brake pad set",
                    currency = CurrencyCode.USD,
                    unitPriceMinor = 1500L,
                    lineTotalMinor = 3000L,
                ),
                DeliveryJobLineItem(
                    lineId = "line-seed-1b",
                    qty = 1.0,
                    oemPartNumber = "15208-65F0A",
                    description = "Oil filter",
                    currency = CurrencyCode.USD,
                    unitPriceMinor = 1550L,
                    lineTotalMinor = 1550L,
                ),
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
            dropoffAddressText = "45 Borrowdale Rd, Harare",
            lineItems = listOf(
                DeliveryJobLineItem(
                    lineId = "line-seed-2a",
                    qty = 1.0,
                    oemPartNumber = "16546-EA000",
                    description = "Air filter element",
                    currency = CurrencyCode.USD,
                ),
            ),
        ),
        DeliveryJobSummary(
            id = JOB_DONE,
            deliveryNoteId = "00000000-0000-4000-8000-0000000000d3",
            documentNumber = "DJ-SEED-DONE",
            status = "completed",
            dropoffLat = -17.8200,
            dropoffLng = 31.0400,
            etaAt = null,
            etaSeconds = null,
            notes = "Left with reception",
            routeSequence = 3,
            reattemptOf = null,
            failureReasonCode = null,
            podPhotoPath = "$JOB_DONE/photo.jpg",
            podSignaturePath = "$JOB_DONE/signature.png",
            assigneeUserId = FAKE_DRIVER_USER_ID,
            settlement = DeliveryJobSettlement(
                currency = CurrencyCode.USD,
                invoiceTotalMinor = 1200L,
                amountPaidMinor = 1200L,
                amountDueMinor = 0L,
            ),
            dropoffAddressText = "8 Leopold Takawira St, Harare",
            lineItems = listOf(
                DeliveryJobLineItem(
                    lineId = "line-seed-done",
                    qty = 4.0,
                    oemPartNumber = "B4551-JD00A",
                    description = "Wiper blade",
                    currency = CurrencyCode.USD,
                    lineTotalMinor = 1200L,
                ),
            ),
        ),
        DeliveryJobSummary(
            id = JOB_FAILED,
            deliveryNoteId = "00000000-0000-4000-8000-0000000000d4",
            documentNumber = "DJ-SEED-FAIL",
            status = "failed",
            dropoffLat = -17.8400,
            dropoffLng = 31.0700,
            etaAt = null,
            etaSeconds = null,
            notes = "Customer absent",
            routeSequence = 4,
            reattemptOf = null,
            failureReasonCode = DeliveryFailureReason.CUSTOMER_ABSENT.rpcValue,
            podPhotoPath = null,
            podSignaturePath = null,
            assigneeUserId = FAKE_DRIVER_USER_ID,
            settlement = null,
            dropoffAddressText = "22 Enterprise Rd, Harare",
            lineItems = emptyList(),
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
        if (status == DeliveryJobStatus.COMPLETED) {
            require(!current.podSignaturePath.isNullOrBlank()) {
                "POD photo and signature required; use submit_delivery_pod"
            }
            require(!current.podPhotoPath.isNullOrBlank()) {
                "POD photo and signature required; use submit_delivery_pod"
            }
        }
        if (status == DeliveryJobStatus.FAILED) {
            error("use fail_delivery_job for failed status (reason + optional reattempt)")
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
        const val JOB_DONE = "00000000-0000-4000-8000-0000000000j3"
        const val JOB_FAILED = "00000000-0000-4000-8000-0000000000j4"

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

    // --- Cash and card on delivery (in memory, same rules as the server)

    private val fakePaid = mutableMapOf<String, Double>()
    private val fakeCashKeys = mutableMapOf<String, DeliveryCashReceipt>()
    private val fakeCardAttempts = linkedMapOf<String, DeliveryCardAttempt>()
    private val fakeCardKeys = mutableMapOf<String, String>()
    private val fakeCardJob = mutableMapOf<String, String>()
    private val fakeTerminal = DeliveryCardTerminal(
        "term-fake-1", "Demo swipe machine", "Demo bank", "android_intent_v1",
        mapOf("package_name" to "zw.demo.pos", "purchase_action" to "zw.demo.pos.PURCHASE", "status_action" to "zw.demo.pos.STATUS"),
        null,
    )

    override suspend fun getDeliveryPaymentContext(deliveryJobId: String): DeliveryPaymentContext? {
        val job = jobs.firstOrNull { it.id == deliveryJobId } ?: return null
        val s = job.settlement
        val total = (s?.invoiceTotalMinor ?: 0L) / 100.0
        val paid = (s?.amountPaidMinor ?: 0L) / 100.0 + (fakePaid[deliveryJobId] ?: 0.0)
        val method = if (s != null && total > 0.0) "cash_or_card_on_delivery" else "prepay"
        return DeliveryPaymentContext(
            deliveryJobId, "inv-${job.deliveryNoteId.takeLast(4)}", job.documentNumber, "wh-main", s?.currency?.rpcValue ?: "USD",
            total, paid, maxOf(0.0, Math.round((total - paid) * 100) / 100.0), method,
            mayCollectCash = method != "prepay", mayCollectCard = method != "prepay",
        )
    }

    override suspend fun collectDeliveryCash(deliveryJobId: String, amount: Double, requestId: String, notes: String?): DeliveryCashReceipt {
        fakeCashKeys[requestId]?.let { return it }
        val ctx = getDeliveryPaymentContext(deliveryJobId) ?: error("assigned dispatched driver job required")
        check(ctx.mayCollectCash) { "this delivery is not authorized for cash collection" }
        check(ctx.amountDue > 0) { "invoice has no balance due" }
        check(amount > 0 && amount <= ctx.amountDue + 0.01) { "cash amount must be >0 and <= delivery balance ${ctx.amountDue}" }
        fakePaid[deliveryJobId] = (fakePaid[deliveryJobId] ?: 0.0) + amount
        val r = DeliveryCashReceipt("cash-${requestId.take(8)}", amount, ctx.currency, maxOf(0.0, ctx.amountDue - amount))
        fakeCashKeys[requestId] = r
        return r
    }

    override suspend fun listDeliveryCardTerminals(warehouseId: String?, deviceId: String) = listOf(fakeTerminal.copy(deviceId = deviceId))

    override suspend fun registerDeliveryCardDeviceKey(terminalId: String, deviceId: String, publicKeySpkiBase64: String, keySha256: String) = "key-${keySha256.take(8)}"

    override suspend fun beginDeliveryCardPayment(deliveryJobId: String, terminalId: String, deviceId: String, amount: Double, requestId: String): DeliveryCardAttempt {
        fakeCardKeys[requestId]?.let { return fakeCardAttempts.getValue(it) }
        val ctx = getDeliveryPaymentContext(deliveryJobId) ?: error("assigned dispatched driver job required")
        check(ctx.mayCollectCard) { "this delivery is not authorized for card collection" }
        check(amount > 0 && amount <= ctx.amountDue + 0.01) { "card amount must be >0 and <= delivery balance ${ctx.amountDue}" }
        check(fakeCardAttempts.values.none { fakeCardJob[it.attemptId] == deliveryJobId && it.status in setOf("initiated", "approved", "unknown") }) {
            "reconcile the unresolved delivery card payment before charging again"
        }
        val id = "datt-${requestId.take(8)}"
        val a = DeliveryCardAttempt(id, "initiated", amount, ctx.currency, "GTR-DCT-${requestId.take(12)}", fakeTerminal.label, fakeTerminal.adapterConfig, null, null, null, null)
        fakeCardAttempts[id] = a; fakeCardKeys[requestId] = id; fakeCardJob[id] = deliveryJobId
        return a
    }

    override suspend fun getDeliveryCardAttempt(attemptId: String) = fakeCardAttempts[attemptId] ?: error("card terminal attempt not found")

    /** The fake reads the outcome from the payload the bridge produced (simulated machine). */
    override suspend fun submitCardTerminalEvidence(payloadJson: String, signatureBase64: String): DeliveryCardAttempt {
        val id = Regex("\"attempt_id\":\"([^\"]+)\"").find(payloadJson)?.groupValues?.get(1) ?: error("attempt id missing")
        val outcome = Regex("\"outcome\":\"([^\"]+)\"").find(payloadJson)?.groupValues?.get(1) ?: "unknown"
        val txn = Regex("\"terminal_transaction_id\":\"([^\"]+)\"").find(payloadJson)?.groupValues?.get(1)
        val last4 = Regex("\"card_last4\":\"([^\"]+)\"").find(payloadJson)?.groupValues?.get(1)
        val a = getDeliveryCardAttempt(id).copy(status = outcome, transactionId = txn, cardLast4 = last4)
        fakeCardAttempts[id] = a
        return a
    }

    override suspend fun finalizeDeliveryCardPayment(attemptId: String): DeliveryCardAttempt {
        val a = getDeliveryCardAttempt(attemptId)
        if (a.status == "settled") return a
        check(a.status == "approved") { "approved delivery card purchase required" }
        val job = fakeCardJob.getValue(attemptId)
        fakePaid[job] = (fakePaid[job] ?: 0.0) + a.amount
        return a.copy(status = "settled").also { fakeCardAttempts[attemptId] = it }
    }

    private val fakeBalance = mutableMapOf<String, DeliveryBalanceApproval>()

    /** Demo rule: up to USD 30 left on account is covered by the customer's credit; more waits for dispatch. */
    override suspend fun requestDeliveryBalanceOnAccount(deliveryJobId: String, reason: String): DeliveryBalanceApproval {
        check(reason.isNotBlank()) { "say why the customer cannot pay the rest" }
        val ctx = getDeliveryPaymentContext(deliveryJobId) ?: error("assigned dispatched driver job required")
        check(ctx.amountDue > 0.004) { "nothing is owed on this delivery" }
        fakeBalance[deliveryJobId]?.takeIf { it.approved && ctx.amountDue <= it.amount + 0.01 }?.let { return it }
        val auto = ctx.amountDue <= 30.0
        return DeliveryBalanceApproval(
            "bal-${deliveryJobId.takeLast(4)}", if (auto) "auto_approved" else "pending", if (auto) "credit_limit" else "back_office",
            ctx.amountDue, ctx.currency, reason, null, null,
        ).also { fakeBalance[deliveryJobId] = it }
    }

    /** A pending request is approved by "dispatch" the next time the driver checks. */
    override suspend fun getDeliveryBalanceApproval(deliveryJobId: String): DeliveryBalanceApproval? {
        val b = fakeBalance[deliveryJobId] ?: return null
        return if (b.status == "pending") b.copy(status = "approved", decidedByName = "Dispatch (demo)").also { fakeBalance[deliveryJobId] = it } else b
    }

    override suspend fun getDeliveryCardRecovery(deliveryJobId: String): DeliveryCardAttempt? =
        fakeCardAttempts.values.lastOrNull { fakeCardJob[it.attemptId] == deliveryJobId && (it.status in setOf("initiated", "approved", "unknown") || it.finalizationError != null) }
}
