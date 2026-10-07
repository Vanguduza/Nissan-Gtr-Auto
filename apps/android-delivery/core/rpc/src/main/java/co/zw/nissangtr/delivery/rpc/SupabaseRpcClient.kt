package co.zw.nissangtr.delivery.rpc

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File

/**
 * Live supabase-kt [RpcClient] for driver delivery RPCs.
 * Uses anon key + Auth session (never hardcode JWTs).
 */
class SupabaseRpcClient(
    val client: SupabaseClient,
) : RpcClient {

    val auth: Auth get() = client.auth
    val sessionStatus: Flow<SessionStatus> get() = auth.sessionStatus

    suspend fun signInWithEmail(email: String, password: String) {
        require(email.isNotBlank()) { "email required" }
        require(password.isNotBlank()) { "password required" }
        auth.signInWith(Email) {
            this.email = email.trim()
            this.password = password
        }
    }

    suspend fun signOut() {
        auth.signOut()
    }

    fun currentUserEmail(): String? =
        auth.currentSessionOrNull()?.user?.email

    override fun currentUserId(): String? =
        auth.currentSessionOrNull()?.user?.id

    fun isSignedIn(): Boolean =
        auth.currentSessionOrNull() != null

    suspend fun importAccessToken(
        accessToken: String,
        refreshToken: String = "",
        expiresIn: Long = 3600,
    ) {
        require(accessToken.isNotBlank()) {
            "accessToken required — do not hardcode JWTs in BuildConfig"
        }
        auth.importSession(
            UserSession(
                accessToken = accessToken,
                refreshToken = refreshToken,
                expiresIn = expiresIn,
                tokenType = "bearer",
                user = null,
            ),
        )
    }

    override suspend fun listMyStaffRoles(): List<String> {
        val uid = currentUserId() ?: return emptyList()
        return client.from("staff_roles")
            .select(Columns.list("role")) {
                filter { eq("user_id", uid) }
            }
            .decodeList<StaffRoleRow>()
            .map { it.role }
    }

    override suspend fun listMyDeliveryJobs(): List<DeliveryJobSummary> {
        val uid = currentUserId() ?: return emptyList()
        return client.from("delivery_jobs")
            .select(JOB_COLUMNS) {
                filter { eq("assignee_user_id", uid) }
                order("route_sequence", Order.ASCENDING)
                order("created_at", Order.DESCENDING)
                limit(100)
            }
            .decodeList<DeliveryJobRow>()
            .map { row ->
                val settlement = runCatching { fetchSettlement(row.id) }.getOrNull()
                val lines = runCatching { fetchLines(row.id) }.getOrElse { emptyList() }
                row.toSummary(settlement, lines)
            }
    }

    override suspend fun getDeliveryJob(jobId: String): DeliveryJobSummary? {
        require(jobId.isNotBlank())
        val row = client.from("delivery_jobs")
            .select(JOB_COLUMNS) {
                filter { eq("id", jobId) }
                limit(1)
            }
            .decodeList<DeliveryJobRow>()
            .firstOrNull()
            ?: return null
        val settlement = runCatching { fetchSettlement(row.id) }.getOrNull()
        val lines = runCatching { fetchLines(row.id) }.getOrElse { emptyList() }
        return row.toSummary(settlement, lines)
    }

    private suspend fun fetchSettlement(jobId: String): DeliveryJobSettlement? {
        val rows = client.postgrest.rpc(
            RpcNames.GET_DELIVERY_JOB_SETTLEMENT,
            buildJsonObject { put("p_delivery_job_id", jobId) },
        ).decodeList<SettlementRow>()
        val row = rows.firstOrNull() ?: return null
        val currency = CurrencyCode.entries.find { it.rpcValue.equals(row.currency, ignoreCase = true) }
            ?: CurrencyCode.USD
        return DeliveryJobSettlement(
            currency = currency,
            invoiceTotal = row.invoiceTotal,
            invoiceTotalMinor = row.invoiceTotalMinor,
            amountPaid = row.amountPaid,
            amountPaidMinor = row.amountPaidMinor,
            amountDue = row.amountDue,
            amountDueMinor = row.amountDueMinor,
        )
    }

    private suspend fun fetchLines(jobId: String): List<DeliveryJobLineItem> {
        val rows = client.postgrest.rpc(
            RpcNames.GET_DELIVERY_JOB_LINES,
            buildJsonObject { put("p_delivery_job_id", jobId) },
        ).decodeList<JobLineRow>()
        return rows.map { row ->
            val currency = CurrencyCode.entries.find {
                it.rpcValue.equals(row.currency, ignoreCase = true)
            } ?: CurrencyCode.USD
            DeliveryJobLineItem(
                lineId = row.lineId ?: "",
                qty = row.qty ?: 0.0,
                oemPartNumber = row.oemPartNumber,
                description = row.description,
                currency = currency,
                unitPrice = row.unitPrice,
                lineTotal = row.lineTotal,
                unitPriceMinor = row.unitPriceMinor,
                lineTotalMinor = row.lineTotalMinor,
                isCoreCharge = row.isCoreCharge ?: false,
            )
        }
    }

    override suspend fun setDriverPresence(
        status: DriverPresenceStatus,
        capacity: Int?,
        shiftStartsAt: String?,
        shiftEndsAt: String?,
        lastLat: Double?,
        lastLng: Double?,
    ): String {
        return client.postgrest.rpc(
            RpcNames.SET_DRIVER_PRESENCE,
            buildJsonObject {
                put("p_status", status.rpcValue)
                if (capacity == null) put("p_capacity", JsonNull) else put("p_capacity", capacity)
                if (shiftStartsAt.isNullOrBlank()) put("p_shift_starts_at", JsonNull)
                else put("p_shift_starts_at", shiftStartsAt)
                if (shiftEndsAt.isNullOrBlank()) put("p_shift_ends_at", JsonNull)
                else put("p_shift_ends_at", shiftEndsAt)
                if (lastLat == null) put("p_last_lat", JsonNull) else put("p_last_lat", lastLat)
                if (lastLng == null) put("p_last_lng", JsonNull) else put("p_last_lng", lastLng)
            },
        ).decodeAs<String>()
    }

    override suspend fun getMyDriverPresence(): DriverPresenceSnapshot? {
        val uid = currentUserId() ?: return null
        return client.from("driver_presence")
            .select(
                Columns.list(
                    "user_id",
                    "status",
                    "capacity",
                    "last_lat",
                    "last_lng",
                ),
            ) {
                filter { eq("user_id", uid) }
                limit(1)
            }
            .decodeList<DriverPresenceRow>()
            .firstOrNull()
            ?.let {
                DriverPresenceSnapshot(
                    userId = it.userId,
                    status = it.status,
                    capacity = it.capacity,
                    lastLat = it.lastLat,
                    lastLng = it.lastLng,
                )
            }
    }

    override suspend fun ingestDeliveryLocation(
        deliveryJobId: String,
        lat: Double,
        lng: Double,
        recordedAt: String?,
        accuracyM: Double?,
    ): String {
        require(deliveryJobId.isNotBlank())
        return client.postgrest.rpc(
            RpcNames.INGEST_DELIVERY_LOCATION,
            buildJsonObject {
                put("p_delivery_job_id", deliveryJobId)
                put("p_lat", lat)
                put("p_lng", lng)
                if (recordedAt.isNullOrBlank()) put("p_recorded_at", JsonNull)
                else put("p_recorded_at", recordedAt)
                if (accuracyM == null) put("p_accuracy_m", JsonNull)
                else put("p_accuracy_m", accuracyM)
            },
        ).decodeAs<String>()
    }

    override suspend fun updateDeliveryJobStatus(
        deliveryJobId: String,
        status: DeliveryJobStatus,
    ): String {
        require(deliveryJobId.isNotBlank())
        return client.postgrest.rpc(
            RpcNames.UPDATE_DELIVERY_JOB_STATUS,
            buildJsonObject {
                put("p_delivery_job_id", deliveryJobId)
                put("p_status", status.rpcValue)
            },
        ).decodeAs<String>()
    }

    override suspend fun uploadPodAsset(
        objectKey: String,
        localFilePath: String,
        mimeType: String,
    ): String {
        require(objectKey.isNotBlank())
        val file = File(localFilePath)
        require(file.exists()) { "POD local file missing: $localFilePath" }
        val bytes = file.readBytes()
        client.storage.from(RpcNames.DELIVERY_PODS_BUCKET).upload(
            path = objectKey,
            data = bytes,
        ) {
            upsert = true
        }
        return objectKey
    }

    override suspend fun generateDeliveryPodOtp(
        deliveryJobId: String,
        ttl: String?,
    ): String {
        require(deliveryJobId.isNotBlank())
        return client.postgrest.rpc(
            RpcNames.GENERATE_DELIVERY_POD_OTP,
            buildJsonObject {
                put("p_delivery_job_id", deliveryJobId)
                if (ttl.isNullOrBlank()) put("p_ttl", JsonNull) else put("p_ttl", ttl)
            },
        ).decodeAs<String>()
    }

    override suspend fun verifyDeliveryPodOtp(
        deliveryJobId: String,
        code: String,
    ): Boolean {
        require(deliveryJobId.isNotBlank() && code.isNotBlank())
        return client.postgrest.rpc(
            RpcNames.VERIFY_DELIVERY_POD_OTP,
            buildJsonObject {
                put("p_delivery_job_id", deliveryJobId)
                put("p_code", code.trim())
            },
        ).decodeAs<Boolean>()
    }

    override suspend fun submitDeliveryPod(
        deliveryJobId: String,
        photoPath: String,
        signaturePath: String,
        otpCode: String,
        notes: String?,
    ): String {
        require(deliveryJobId.isNotBlank())
        require(photoPath.isNotBlank() && signaturePath.isNotBlank())
        require(otpCode.isNotBlank())
        return client.postgrest.rpc(
            RpcNames.SUBMIT_DELIVERY_POD,
            buildJsonObject {
                put("p_delivery_job_id", deliveryJobId)
                put("p_pod_photo_path", photoPath)
                put("p_pod_signature_path", signaturePath)
                put("p_otp_code", otpCode.trim())
                if (notes.isNullOrBlank()) put("p_notes", JsonNull) else put("p_notes", notes)
            },
        ).decodeAs<String>()
    }

    override suspend fun deliveryGeofenceSuggestion(
        deliveryJobId: String,
        lat: Double,
        lng: Double,
        arriveRadiusM: Double?,
        completeRadiusM: Double?,
    ): GeofenceSuggestion {
        require(deliveryJobId.isNotBlank())
        val rows = client.postgrest.rpc(
            RpcNames.DELIVERY_GEOFENCE_SUGGESTION,
            buildJsonObject {
                put("p_delivery_job_id", deliveryJobId)
                put("p_lat", lat)
                put("p_lng", lng)
                if (arriveRadiusM == null) put("p_arrive_radius_m", JsonNull)
                else put("p_arrive_radius_m", arriveRadiusM)
                if (completeRadiusM == null) put("p_complete_radius_m", JsonNull)
                else put("p_complete_radius_m", completeRadiusM)
            },
        ).decodeList<GeofenceRow>()
        val row = rows.firstOrNull()
            ?: return GeofenceSuggestion(null, false, false)
        return GeofenceSuggestion(
            distanceM = row.distanceM,
            suggestArrive = row.suggestArrive,
            suggestComplete = row.suggestComplete,
        )
    }

    override suspend fun failDeliveryJob(
        deliveryJobId: String,
        reason: DeliveryFailureReason,
        notes: String?,
        createReattempt: Boolean,
    ): String {
        require(deliveryJobId.isNotBlank())
        return client.postgrest.rpc(
            RpcNames.FAIL_DELIVERY_JOB,
            buildJsonObject {
                put("p_delivery_job_id", deliveryJobId)
                put("p_reason", reason.rpcValue)
                if (notes.isNullOrBlank()) put("p_notes", JsonNull) else put("p_notes", notes)
                put("p_create_reattempt", createReattempt)
            },
        ).decodeAs<String>()
    }

    override suspend fun raiseDeliveryPanic(
        deliveryJobId: String?,
        lat: Double?,
        lng: Double?,
    ): String {
        return client.postgrest.rpc(
            RpcNames.RAISE_DELIVERY_PANIC,
            buildJsonObject {
                if (deliveryJobId.isNullOrBlank()) put("p_delivery_job_id", JsonNull)
                else put("p_delivery_job_id", deliveryJobId)
                if (lat == null) put("p_lat", JsonNull) else put("p_lat", lat)
                if (lng == null) put("p_lng", JsonNull) else put("p_lng", lng)
            },
        ).decodeAs<String>()
    }

    override suspend fun optimizeDriverStops(driverUserId: String): List<OptimizedStop> {
        require(driverUserId.isNotBlank())
        return client.postgrest.rpc(
            RpcNames.OPTIMIZE_DRIVER_STOPS,
            buildJsonObject { put("p_driver_user_id", driverUserId) },
        ).decodeList<OptimizeStopRow>().map {
            OptimizedStop(
                deliveryJobId = it.deliveryJobId,
                routeSequence = it.routeSequence,
                distanceM = it.distanceM,
            )
        }
    }

    override suspend fun mintDeliveryTrackToken(
        deliveryJobId: String,
        ttl: String?,
    ): String {
        require(deliveryJobId.isNotBlank())
        return client.postgrest.rpc(
            RpcNames.MINT_DELIVERY_TRACK_TOKEN,
            buildJsonObject {
                put("p_delivery_job_id", deliveryJobId)
                if (ttl.isNullOrBlank()) put("p_ttl", JsonNull) else put("p_ttl", ttl)
            },
        ).decodeAs<String>()
    }

    // --- Cash and card on delivery

    private fun kotlinx.serialization.json.JsonObject.str(k: String): String? =
        (this[k] as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it !is JsonNull }?.content
    private fun kotlinx.serialization.json.JsonObject.num(k: String): Double? = str(k)?.toDoubleOrNull()
    private fun kotlinx.serialization.json.JsonObject.bool(k: String): Boolean = str(k) == "true"
    private fun stringMap(e: kotlinx.serialization.json.JsonElement?): Map<String, String?> =
        (e as? kotlinx.serialization.json.JsonObject)?.mapValues { (_, v) -> (v as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it !is JsonNull }?.content } ?: emptyMap()

    private fun attemptFrom(o: kotlinx.serialization.json.JsonObject): DeliveryCardAttempt {
        val t = o["terminal"] as? kotlinx.serialization.json.JsonObject
        return DeliveryCardAttempt(
            attemptId = o.str("attempt_id") ?: error("card attempt missing"),
            status = o.str("status") ?: "initiated",
            amount = o.num("amount") ?: 0.0,
            currency = o.str("currency") ?: "USD",
            externalRef = o.str("external_ref"),
            terminalLabel = t?.str("label"),
            adapterConfig = stringMap(t?.get("adapter_config")),
            transactionId = o.str("terminal_transaction_id"),
            cardLast4 = o.str("card_last4"),
            responseMessage = o.str("response_message"),
            finalizationError = o.str("finalization_error") ?: o.str("error"),
        )
    }

    private suspend fun rpcObject(fn: String, args: kotlinx.serialization.json.JsonObject): kotlinx.serialization.json.JsonObject? =
        client.postgrest.rpc(fn, args).decodeAs<kotlinx.serialization.json.JsonElement>() as? kotlinx.serialization.json.JsonObject

    override suspend fun getDeliveryPaymentContext(deliveryJobId: String): DeliveryPaymentContext? {
        val o = try {
            rpcObject("get_delivery_job_payment_context", buildJsonObject { put("p_delivery_job_id", deliveryJobId) })
        } catch (e: Exception) {
            // A stop without an invoice (e.g. a transfer) has nothing to collect.
            if (e.message?.contains("delivery invoice not found") == true) return null
            throw e
        } ?: return null
        return DeliveryPaymentContext(
            deliveryJobId = o.str("delivery_job_id") ?: deliveryJobId,
            invoiceId = o.str("sales_invoice_id").orEmpty(),
            documentNumber = o.str("document_number"),
            warehouseId = o.str("warehouse_id"),
            currency = o.str("currency") ?: "USD",
            invoiceTotal = o.num("invoice_total") ?: 0.0,
            amountPaid = o.num("amount_paid") ?: 0.0,
            amountDue = o.num("amount_due") ?: 0.0,
            method = o.str("delivery_payment_method") ?: "prepay",
            mayCollectCash = o.bool("may_collect_cash"),
            mayCollectCard = o.bool("may_collect_card"),
        )
    }

    override suspend fun collectDeliveryCash(deliveryJobId: String, amount: Double, requestId: String, notes: String?): DeliveryCashReceipt {
        val o = rpcObject(
            "collect_delivery_cash",
            buildJsonObject {
                put("p_delivery_job_id", deliveryJobId)
                put("p_amount", amount)
                put("p_request_id", requestId)
                if (notes == null) put("p_notes", JsonNull) else put("p_notes", notes)
            },
        ) ?: error("The cash was not recorded.")
        return DeliveryCashReceipt(o.str("collection_id").orEmpty(), o.num("amount") ?: amount, o.str("currency") ?: "USD", o.num("balance_due"))
    }

    override suspend fun listDeliveryCardTerminals(warehouseId: String?, deviceId: String): List<DeliveryCardTerminal> {
        val e = client.postgrest.rpc(
            "list_delivery_card_terminals",
            buildJsonObject {
                if (warehouseId == null) put("p_warehouse_id", JsonNull) else put("p_warehouse_id", warehouseId)
                put("p_device_id", deviceId)
            },
        ).decodeAs<kotlinx.serialization.json.JsonElement>()
        return (e as? kotlinx.serialization.json.JsonArray).orEmpty().mapNotNull { x ->
            val o = x as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
            DeliveryCardTerminal(
                id = o.str("id") ?: return@mapNotNull null,
                label = o.str("label") ?: o.str("code").orEmpty(),
                acquirer = o.str("acquirer_name"),
                adapterKey = o.str("adapter_key"),
                adapterConfig = stringMap(o["adapter_config"]),
                deviceId = o.str("device_id"),
            )
        }
    }

    override suspend fun registerDeliveryCardDeviceKey(terminalId: String, deviceId: String, publicKeySpkiBase64: String, keySha256: String): String =
        client.postgrest.rpc(
            "register_delivery_card_terminal_device_key",
            buildJsonObject {
                put("p_terminal_id", terminalId)
                put("p_device_id", deviceId)
                put("p_public_key_spki_base64", publicKeySpkiBase64)
                put("p_key_sha256", keySha256)
            },
        ).decodeAs<String>()

    override suspend fun beginDeliveryCardPayment(deliveryJobId: String, terminalId: String, deviceId: String, amount: Double, requestId: String) =
        attemptFrom(
            rpcObject(
                "begin_delivery_card_terminal_payment",
                buildJsonObject {
                    put("p_delivery_job_id", deliveryJobId)
                    put("p_terminal_id", terminalId)
                    put("p_device_id", deviceId)
                    put("p_amount", amount)
                    put("p_request_id", requestId)
                },
            ) ?: error("The card payment did not start."),
        )

    override suspend fun getDeliveryCardAttempt(attemptId: String) =
        attemptFrom(rpcObject("get_pos_card_terminal_attempt", buildJsonObject { put("p_attempt_id", attemptId) }) ?: error("card attempt not found"))

    override suspend fun submitCardTerminalEvidence(payloadJson: String, signatureBase64: String): DeliveryCardAttempt {
        val body = buildJsonObject {
            put("payload", kotlinx.serialization.json.Json.parseToJsonElement(payloadJson))
            put("signature_base64", signatureBase64)
        }
        val text = try {
            client.functions.invoke("card-terminal-result") { setBody(body) }.bodyAsText()
        } catch (e: io.github.jan.supabase.exceptions.RestException) {
            val o = runCatching { kotlinx.serialization.json.Json.parseToJsonElement(e.error) as? kotlinx.serialization.json.JsonObject }.getOrNull()
            throw IllegalStateException(o?.str("error") ?: e.error)
        }
        val o = kotlinx.serialization.json.Json.parseToJsonElement(text) as? kotlinx.serialization.json.JsonObject ?: error("The card machine answer was not recorded.")
        return attemptFrom(o["attempt"] as? kotlinx.serialization.json.JsonObject ?: error(o.str("error") ?: "The card machine answer was not recorded."))
    }

    override suspend fun finalizeDeliveryCardPayment(attemptId: String) =
        attemptFrom(rpcObject("finalize_delivery_card_terminal_payment", buildJsonObject { put("p_attempt_id", attemptId) }) ?: error("The card payment was not posted."))

    private fun balanceFrom(o: kotlinx.serialization.json.JsonObject) = DeliveryBalanceApproval(
        id = o.str("id").orEmpty(),
        status = o.str("status") ?: "pending",
        basis = o.str("basis") ?: "back_office",
        amount = o.num("amount") ?: 0.0,
        currency = o.str("currency") ?: "USD",
        reason = o.str("reason").orEmpty(),
        decidedByName = o.str("decided_by_name"),
        decisionNote = o.str("decision_note"),
    )

    override suspend fun requestDeliveryBalanceOnAccount(deliveryJobId: String, reason: String) =
        balanceFrom(
            rpcObject("request_delivery_balance_on_account", buildJsonObject { put("p_delivery_job_id", deliveryJobId); put("p_reason", reason) })
                ?: error("The request was not sent."),
        )

    override suspend fun getDeliveryBalanceApproval(deliveryJobId: String): DeliveryBalanceApproval? =
        rpcObject("get_delivery_balance_approval", buildJsonObject { put("p_delivery_job_id", deliveryJobId) })?.let(::balanceFrom)

    override suspend fun getDeliveryCardRecovery(deliveryJobId: String): DeliveryCardAttempt? =
        rpcObject("get_delivery_card_terminal_recovery", buildJsonObject { put("p_delivery_job_id", deliveryJobId) })?.let(::attemptFrom)

    private fun handinFrom(o: kotlinx.serialization.json.JsonObject) = DriverCashHandin(
        id = o.str("id").orEmpty(),
        documentNumber = o.str("document_number"),
        status = o.str("status") ?: "submitted",
        currency = o.str("currency") ?: "USD",
        expectedAmount = o.num("expected_amount") ?: 0.0,
        declaredAmount = o.num("declared_amount") ?: 0.0,
        receivedAmount = o.num("received_amount"),
        variance = o.num("variance"),
        collectionCount = o.num("collection_count")?.toInt() ?: 0,
        submittedAt = o.str("submitted_at"),
        receivedByName = o.str("received_by_name"),
        reasonCode = o.str("reason_code"),
    )

    override suspend fun getMyDriverCash(): DriverCash {
        val o = rpcObject("get_my_driver_cash", buildJsonObject { }) ?: return DriverCash(emptyList(), emptyList())
        fun list(k: String) = (o[k] as? kotlinx.serialization.json.JsonArray).orEmpty().mapNotNull { it as? kotlinx.serialization.json.JsonObject }
        return DriverCash(
            holding = list("holding").map { h ->
                DriverCashHolding(
                    currency = h.str("currency") ?: "USD",
                    amount = h.num("amount") ?: 0.0,
                    count = h.num("count")?.toInt() ?: 0,
                    oldestAt = h.str("oldest_at"),
                    collections = (h["collections"] as? kotlinx.serialization.json.JsonArray).orEmpty().mapNotNull { c ->
                        val x = c as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
                        DriverCashCollection(x.str("id").orEmpty(), x.num("amount") ?: 0.0, x.str("collected_at"), x.str("job_number"), x.str("invoice_number"))
                    },
                )
            },
            handins = list("handins").map(::handinFrom),
        )
    }

    override suspend fun submitDriverCashHandin(currency: String, declaredAmount: Double, notes: String?) =
        handinFrom(
            rpcObject(
                "submit_driver_cash_handin",
                buildJsonObject {
                    put("p_currency", currency)
                    put("p_declared_amount", declaredAmount)
                    if (notes == null) put("p_notes", JsonNull) else put("p_notes", notes)
                },
            ) ?: error("The hand-in was not recorded."),
        )

    companion object {
        private val JOB_COLUMNS = Columns.list(
            "id",
            "delivery_note_id",
            "document_number",
            "status",
            "dropoff_lat",
            "dropoff_lng",
            "eta_at",
            "eta_seconds",
            "notes",
            "route_sequence",
            "reattempt_of",
            "failure_reason_code",
            "pod_photo_path",
            "pod_signature_path",
            "assignee_user_id",
        )

        fun create(supabaseUrl: String, supabaseAnonKey: String): SupabaseRpcClient {
            val client = createSupabaseClient(
                supabaseUrl = supabaseUrl,
                supabaseKey = supabaseAnonKey,
            ) {
                install(Auth)
                install(Postgrest)
                install(Functions)
                install(Storage)
            }
            return SupabaseRpcClient(client)
        }
    }
}

@Serializable
private data class StaffRoleRow(val role: String)

@Serializable
private data class DriverPresenceRow(
    @SerialName("user_id") val userId: String,
    val status: String,
    val capacity: Int = 1,
    @SerialName("last_lat") val lastLat: Double? = null,
    @SerialName("last_lng") val lastLng: Double? = null,
)

@Serializable
private data class DeliveryJobRow(
    val id: String,
    @SerialName("delivery_note_id") val deliveryNoteId: String,
    @SerialName("document_number") val documentNumber: String? = null,
    val status: String,
    @SerialName("dropoff_lat") val dropoffLat: Double? = null,
    @SerialName("dropoff_lng") val dropoffLng: Double? = null,
    @SerialName("eta_at") val etaAt: String? = null,
    @SerialName("eta_seconds") val etaSeconds: Int? = null,
    val notes: String? = null,
    @SerialName("route_sequence") val routeSequence: Int? = null,
    @SerialName("reattempt_of") val reattemptOf: String? = null,
    @SerialName("failure_reason_code") val failureReasonCode: String? = null,
    @SerialName("pod_photo_path") val podPhotoPath: String? = null,
    @SerialName("pod_signature_path") val podSignaturePath: String? = null,
    @SerialName("assignee_user_id") val assigneeUserId: String? = null,
) {
    fun toSummary(
        settlement: DeliveryJobSettlement? = null,
        lineItems: List<DeliveryJobLineItem> = emptyList(),
    ) = DeliveryJobSummary(
        id = id,
        deliveryNoteId = deliveryNoteId,
        documentNumber = documentNumber,
        status = status,
        dropoffLat = dropoffLat,
        dropoffLng = dropoffLng,
        etaAt = etaAt,
        etaSeconds = etaSeconds,
        notes = notes,
        routeSequence = routeSequence,
        reattemptOf = reattemptOf,
        failureReasonCode = failureReasonCode,
        podPhotoPath = podPhotoPath,
        podSignaturePath = podSignaturePath,
        assigneeUserId = assigneeUserId,
        settlement = settlement,
        lineItems = lineItems,
    )
}

@Serializable
private data class SettlementRow(
    @SerialName("delivery_job_id") val deliveryJobId: String? = null,
    val currency: String = "USD",
    @SerialName("invoice_total") val invoiceTotal: Double? = null,
    @SerialName("amount_paid") val amountPaid: Double? = null,
    @SerialName("amount_due") val amountDue: Double? = null,
    @SerialName("invoice_total_minor") val invoiceTotalMinor: Long? = null,
    @SerialName("amount_paid_minor") val amountPaidMinor: Long? = null,
    @SerialName("amount_due_minor") val amountDueMinor: Long? = null,
)

@Serializable
private data class JobLineRow(
    @SerialName("delivery_job_id") val deliveryJobId: String? = null,
    @SerialName("line_id") val lineId: String? = null,
    val qty: Double? = null,
    @SerialName("oem_part_number") val oemPartNumber: String? = null,
    val description: String? = null,
    val currency: String = "USD",
    @SerialName("unit_price") val unitPrice: Double? = null,
    @SerialName("line_total") val lineTotal: Double? = null,
    @SerialName("unit_price_minor") val unitPriceMinor: Long? = null,
    @SerialName("line_total_minor") val lineTotalMinor: Long? = null,
    @SerialName("is_core_charge") val isCoreCharge: Boolean? = null,
)

@Serializable
private data class GeofenceRow(
    @SerialName("distance_m") val distanceM: Double? = null,
    @SerialName("suggest_arrive") val suggestArrive: Boolean = false,
    @SerialName("suggest_complete") val suggestComplete: Boolean = false,
)

@Serializable
private data class OptimizeStopRow(
    @SerialName("delivery_job_id") val deliveryJobId: String,
    @SerialName("route_sequence") val routeSequence: Int,
    @SerialName("distance_m") val distanceM: Double? = null,
)
