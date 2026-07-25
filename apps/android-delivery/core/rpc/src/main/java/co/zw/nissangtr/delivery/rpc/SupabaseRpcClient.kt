package co.zw.nissangtr.delivery.rpc

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
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
            .map { it.toSummary() }
    }

    override suspend fun getDeliveryJob(jobId: String): DeliveryJobSummary? {
        require(jobId.isNotBlank())
        return client.from("delivery_jobs")
            .select(JOB_COLUMNS) {
                filter { eq("id", jobId) }
                limit(1)
            }
            .decodeList<DeliveryJobRow>()
            .firstOrNull()
            ?.toSummary()
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
    fun toSummary() = DeliveryJobSummary(
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
    )
}

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
