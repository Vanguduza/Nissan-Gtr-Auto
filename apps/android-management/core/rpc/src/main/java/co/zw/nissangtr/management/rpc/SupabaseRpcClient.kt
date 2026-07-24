package co.zw.nissangtr.management.rpc

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
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Live supabase-kt [RpcClient] for HR clock + pick/DN logistics RPCs.
 *
 * Uses anon key + Auth session (never hardcode JWTs). List reads via PostgREST + RLS.
 * Session persistence: auth-kt default Android session manager (Settings / SharedPreferences).
 */
class SupabaseRpcClient(
    val client: SupabaseClient,
) : RpcClient {

    /** GoTrue Auth plugin — [signInWithEmail] (preferred) or [importAccessToken] fallback. */
    val auth: Auth get() = client.auth

    /** Hot flow of GoTrue session state (persisted across process restarts). */
    val sessionStatus: Flow<SessionStatus> get() = auth.sessionStatus

    /**
     * Email/password sign-in via GoTrue. Session is stored by the SDK session manager —
     * never put passwords or JWTs in BuildConfig.
     */
    suspend fun signInWithEmail(email: String, password: String) {
        require(email.isNotBlank()) { "email required" }
        require(password.isNotBlank()) { "password required" }
        auth.signInWith(Email) {
            this.email = email.trim()
            this.password = password
        }
    }

    /** Clears the persisted GoTrue session. */
    suspend fun signOut() {
        auth.signOut()
    }

    fun currentUserEmail(): String? =
        auth.currentSessionOrNull()?.user?.email

    fun isSignedIn(): Boolean =
        auth.currentSessionOrNull() != null

    /**
     * Fallback: import an existing access token when a custom auth path cannot use
     * [signInWithEmail]. Prefer real sign-in — do not embed JWTs in source.
     */
    suspend fun importAccessToken(
        accessToken: String,
        refreshToken: String = "",
        expiresIn: Long = 3600,
    ) {
        require(accessToken.isNotBlank()) { "accessToken required — do not hardcode JWTs in BuildConfig" }
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

    override suspend fun clockAttendance(
        employeeId: String,
        eventType: AttendanceEventType,
        notes: String?,
    ): String {
        require(employeeId.isNotBlank())
        return client.postgrest.rpc(
            RpcNames.CLOCK_ATTENDANCE,
            buildJsonObject {
                put("p_employee_id", employeeId)
                put("p_event_type", eventType.rpcValue)
                put("p_occurred_at", JsonNull)
                if (notes.isNullOrBlank()) put("p_notes", JsonNull)
                else put("p_notes", notes)
            },
        ).decodeAs<String>()
    }

    override suspend fun listDeliveryNotes(): List<DeliveryNoteSummary> =
        client.from("delivery_notes")
            .select(
                Columns.list(
                    "id",
                    "document_number",
                    "sales_invoice_id",
                    "status",
                ),
            ) {
                order("created_at", Order.DESCENDING)
                limit(40)
            }
            .decodeList<DeliveryNoteRow>()
            .map {
                DeliveryNoteSummary(
                    id = it.id,
                    documentNumber = it.documentNumber,
                    salesInvoiceId = it.salesInvoiceId,
                    status = it.status,
                )
            }

    override suspend fun listPickLists(): List<PickListSummary> =
        client.from("pick_lists")
            .select(
                Columns.list(
                    "id",
                    "document_number",
                    "sales_invoice_id",
                    "status",
                ),
            ) {
                order("created_at", Order.DESCENDING)
                limit(40)
            }
            .decodeList<PickListRow>()
            .map {
                PickListSummary(
                    id = it.id,
                    documentNumber = it.documentNumber,
                    salesInvoiceId = it.salesInvoiceId,
                    status = it.status,
                )
            }

    override suspend fun createPickList(salesInvoiceId: String, linesJson: String?): String {
        require(salesInvoiceId.isNotBlank())
        val linesElement = when {
            linesJson.isNullOrBlank() -> JsonNull
            else -> runCatching {
                kotlinx.serialization.json.Json.parseToJsonElement(linesJson)
            }.getOrElse { JsonNull }
        }
        return client.postgrest.rpc(
            RpcNames.CREATE_PICK_LIST,
            buildJsonObject {
                put("p_sales_invoice_id", salesInvoiceId)
                put("p_lines", linesElement)
            },
        ).decodeAs<String>()
    }

    override suspend fun confirmPickLines(
        pickListId: String,
        lines: List<ConfirmPickLineInput>,
    ): String {
        require(lines.isNotEmpty())
        return client.postgrest.rpc(
            RpcNames.CONFIRM_PICK_LINES,
            buildJsonObject {
                put("p_pick_list_id", pickListId)
                put("p_lines", lines.toJsonArray())
            },
        ).decodeAs<String>()
    }

    override suspend fun createDeliveryNote(
        salesInvoiceId: String,
        lines: List<DnLineInput>,
        pickListId: String?,
    ): String {
        require(salesInvoiceId.isNotBlank())
        require(lines.isNotEmpty())
        return client.postgrest.rpc(
            RpcNames.CREATE_DELIVERY_NOTE,
            buildJsonObject {
                put("p_sales_invoice_id", salesInvoiceId)
                put(
                    "p_lines",
                    buildJsonArray {
                        lines.forEach { line ->
                            add(
                                buildJsonObject {
                                    put("sales_invoice_line_id", line.salesInvoiceLineId)
                                    put("qty", line.qty)
                                },
                            )
                        }
                    },
                )
                if (pickListId.isNullOrBlank()) put("p_pick_list_id", JsonNull)
                else put("p_pick_list_id", pickListId)
            },
        ).decodeAs<String>()
    }

    override suspend fun submitDeliveryNote(deliveryNoteId: String): String =
        client.postgrest.rpc(
            RpcNames.SUBMIT_DELIVERY_NOTE,
            buildJsonObject { put("p_delivery_note_id", deliveryNoteId) },
        ).decodeAs<String>()

    override suspend fun cancelDeliveryNote(deliveryNoteId: String): String =
        client.postgrest.rpc(
            RpcNames.CANCEL_DELIVERY_NOTE,
            buildJsonObject { put("p_delivery_note_id", deliveryNoteId) },
        ).decodeAs<String>()

    override suspend fun createDeliveryJob(
        deliveryNoteId: String,
        assigneeUserId: String?,
        etaAt: String?,
        notes: String?,
    ): String {
        require(deliveryNoteId.isNotBlank())
        return client.postgrest.rpc(
            RpcNames.CREATE_DELIVERY_JOB,
            buildJsonObject {
                put("p_delivery_note_id", deliveryNoteId)
                if (assigneeUserId.isNullOrBlank()) put("p_assignee_user_id", JsonNull)
                else put("p_assignee_user_id", assigneeUserId)
                if (etaAt.isNullOrBlank()) put("p_eta_at", JsonNull)
                else put("p_eta_at", etaAt)
                if (notes.isNullOrBlank()) put("p_notes", JsonNull)
                else put("p_notes", notes)
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

    companion object {
        fun create(supabaseUrl: String, supabaseAnonKey: String): SupabaseRpcClient {
            val client = createSupabaseClient(
                supabaseUrl = supabaseUrl,
                supabaseKey = supabaseAnonKey,
            ) {
                install(Auth)
                install(Postgrest)
            }
            return SupabaseRpcClient(client)
        }

        private fun List<ConfirmPickLineInput>.toJsonArray(): JsonArray = buildJsonArray {
            forEach { line ->
                add(
                    buildJsonObject {
                        if (!line.pickListLineId.isNullOrBlank()) {
                            put("pick_list_line_id", line.pickListLineId)
                        }
                        if (!line.salesInvoiceLineId.isNullOrBlank()) {
                            put("sales_invoice_line_id", line.salesInvoiceLineId)
                        }
                        put("qty_picked", line.qtyPicked)
                    },
                )
            }
        }
    }
}

@Serializable
private data class DeliveryNoteRow(
    val id: String,
    @SerialName("document_number") val documentNumber: String,
    @SerialName("sales_invoice_id") val salesInvoiceId: String,
    val status: String,
)

@Serializable
private data class PickListRow(
    val id: String,
    @SerialName("document_number") val documentNumber: String,
    @SerialName("sales_invoice_id") val salesInvoiceId: String,
    val status: String,
)
