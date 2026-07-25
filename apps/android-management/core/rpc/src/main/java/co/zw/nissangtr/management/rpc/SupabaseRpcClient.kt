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
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Live supabase-kt [RpcClient] for HR, POS, warehouse, and pick/DN logistics RPCs.
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

    override fun currentUserId(): String? =
        auth.currentSessionOrNull()?.user?.id

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

    override suspend fun createPosCart(
        warehouseId: String,
        currency: CurrencyCode,
        fulfillmentMode: FulfillmentMode,
        customerId: String?,
    ): String {
        require(warehouseId.isNotBlank())
        return client.postgrest.rpc(
            RpcNames.CREATE_POS_CART,
            buildJsonObject {
                put("p_warehouse_id", warehouseId)
                if (customerId.isNullOrBlank()) put("p_customer_id", JsonNull)
                else put("p_customer_id", customerId)
                put("p_currency", currency.rpcValue)
                put("p_fulfillment_mode", fulfillmentMode.rpcValue)
            },
        ).decodeAs<String>()
    }

    override suspend fun addCartLine(
        cartId: String,
        stockItemId: String,
        uomId: String,
        qty: Double,
    ): String {
        require(cartId.isNotBlank() && stockItemId.isNotBlank() && uomId.isNotBlank())
        require(qty > 0)
        return client.postgrest.rpc(
            RpcNames.ADD_CART_LINE,
            buildJsonObject {
                put("p_cart_id", cartId)
                put("p_stock_item_id", stockItemId)
                put("p_uom_id", uomId)
                put("p_qty", qty)
            },
        ).decodeAs<String>()
    }

    override suspend fun addCartLineFromQr(
        cartId: String,
        qrPayload: String,
        qty: Double,
    ): String {
        require(cartId.isNotBlank() && qrPayload.isNotBlank())
        require(qty > 0)
        return client.postgrest.rpc(
            RpcNames.ADD_CART_LINE_FROM_QR,
            buildJsonObject {
                put("p_cart_id", cartId)
                put("p_qr_payload", qrPayload.trim())
                put("p_qty", qty)
            },
        ).decodeAs<String>()
    }

    override suspend fun checkoutPosCart(cartId: String): String {
        require(cartId.isNotBlank())
        return client.postgrest.rpc(
            RpcNames.CHECKOUT_POS_CART,
            buildJsonObject { put("p_cart_id", cartId) },
        ).decodeAs<String>()
    }

    override suspend fun lookupStockItemByOem(oemPartNumber: String): StockItemRef {
        val oem = oemPartNumber.trim()
        require(oem.isNotBlank())
        val row = client.from("stock_items")
            .select(Columns.list("id", "base_uom_id", "oem_part_number")) {
                filter {
                    eq("oem_part_number", oem)
                }
                limit(1)
            }
            .decodeList<StockItemRow>()
            .firstOrNull()
            ?: throw IllegalStateException("unknown part $oem")
        return StockItemRef(
            stockItemId = row.id,
            uomId = row.baseUomId,
            oemPartNumber = row.oemPartNumber,
        )
    }

    override suspend fun postStockReceipt(
        toWarehouseId: String,
        notes: String?,
        lines: List<ReceiptLineInput>,
    ): String {
        require(toWarehouseId.isNotBlank())
        require(lines.isNotEmpty())
        return client.postgrest.rpc(
            RpcNames.POST_STOCK_RECEIPT,
            buildJsonObject {
                put("p_to_warehouse_id", toWarehouseId)
                if (notes.isNullOrBlank()) put("p_notes", JsonNull)
                else put("p_notes", notes)
                put("p_lines", lines.toReceiptJsonArray())
            },
        ).decodeAs<String>()
    }

    override suspend fun createStockTransfer(
        fromWarehouseId: String,
        toWarehouseId: String,
        notes: String?,
        lines: List<TransferLineInput>,
    ): String {
        require(fromWarehouseId.isNotBlank() && toWarehouseId.isNotBlank())
        require(fromWarehouseId != toWarehouseId)
        require(lines.isNotEmpty())
        return client.postgrest.rpc(
            RpcNames.CREATE_STOCK_TRANSFER,
            buildJsonObject {
                put("p_from_warehouse_id", fromWarehouseId)
                put("p_to_warehouse_id", toWarehouseId)
                if (notes.isNullOrBlank()) put("p_notes", JsonNull)
                else put("p_notes", notes)
                put("p_lines", lines.toTransferJsonArray())
            },
        ).decodeAs<String>()
    }

    override suspend fun approveStockTransfer(entryId: String): String {
        require(entryId.isNotBlank())
        return client.postgrest.rpc(
            RpcNames.APPROVE_STOCK_TRANSFER,
            buildJsonObject { put("p_entry_id", entryId) },
        ).decodeAs<String>()
    }

    override suspend fun rejectStockTransfer(entryId: String): String {
        require(entryId.isNotBlank())
        return client.postgrest.rpc(
            RpcNames.REJECT_STOCK_TRANSFER,
            buildJsonObject { put("p_entry_id", entryId) },
        ).decodeAs<String>()
    }

    override suspend fun createStockReconciliationDraft(
        warehouseId: String,
        scope: ReconciliationScope,
        currency: CurrencyCode,
        itemIds: List<String>?,
        notes: String?,
        exchangeRate: Double?,
    ): String {
        require(warehouseId.isNotBlank())
        if (scope == ReconciliationScope.PARTIAL) {
            require(!itemIds.isNullOrEmpty())
        }
        val rate = when {
            exchangeRate != null -> exchangeRate
            currency == CurrencyCode.USD -> 1.0
            else -> null
        }
        return client.postgrest.rpc(
            RpcNames.CREATE_STOCK_RECONCILIATION_DRAFT,
            buildJsonObject {
                put("p_warehouse_id", warehouseId)
                put("p_scope", scope.rpcValue)
                if (itemIds.isNullOrEmpty()) put("p_item_ids", JsonNull)
                else put("p_item_ids", buildJsonArray { itemIds.forEach { add(JsonPrimitive(it)) } })
                if (notes.isNullOrBlank()) put("p_notes", JsonNull)
                else put("p_notes", notes)
                put("p_currency", currency.rpcValue)
                if (rate == null) put("p_exchange_rate", JsonNull)
                else put("p_exchange_rate", rate)
            },
        ).decodeAs<String>()
    }

    override suspend fun upsertStockReconciliationLines(
        reconciliationId: String,
        lines: List<ReconciliationLineInput>,
    ): Int {
        require(reconciliationId.isNotBlank())
        require(lines.isNotEmpty())
        return client.postgrest.rpc(
            RpcNames.UPSERT_STOCK_RECONCILIATION_LINES,
            buildJsonObject {
                put("p_reconciliation_id", reconciliationId)
                put(
                    "p_lines",
                    buildJsonArray {
                        lines.forEach { line ->
                            add(
                                buildJsonObject {
                                    put("stock_item_id", line.stockItemId)
                                    put("counted_qty", line.countedQty)
                                },
                            )
                        }
                    },
                )
            },
        ).decodeAs<Int>()
    }

    override suspend fun submitStockReconciliation(reconciliationId: String): String {
        require(reconciliationId.isNotBlank())
        return client.postgrest.rpc(
            RpcNames.SUBMIT_STOCK_RECONCILIATION,
            buildJsonObject { put("p_reconciliation_id", reconciliationId) },
        ).decodeAs<String>()
    }

    override suspend fun approveStockReconciliation(reconciliationId: String): String {
        require(reconciliationId.isNotBlank())
        return client.postgrest.rpc(
            RpcNames.APPROVE_STOCK_RECONCILIATION,
            buildJsonObject { put("p_reconciliation_id", reconciliationId) },
        ).decodeAs<String>()
    }

    override suspend fun cancelStockReconciliation(
        reconciliationId: String,
        notes: String?,
    ): String {
        require(reconciliationId.isNotBlank())
        return client.postgrest.rpc(
            RpcNames.CANCEL_STOCK_RECONCILIATION,
            buildJsonObject {
                put("p_reconciliation_id", reconciliationId)
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

    override suspend fun listMyStaffRoles(): List<String> {
        val uid = currentUserId() ?: return emptyList()
        return client.from("staff_roles")
            .select(Columns.list("role")) {
                filter { eq("user_id", uid) }
            }
            .decodeList<StaffRoleRow>()
            .map { it.role }
    }

    override suspend fun listStaffChatThreads(filter: StaffChatFilter): List<ChatThreadSummary> {
        val uid = currentUserId()
        return client.from("chat_threads")
            .select(
                Columns.list(
                    "id",
                    "kind",
                    "status",
                    "subject",
                    "assigned_to",
                    "last_message_at",
                    "created_at",
                ),
            ) {
                filter {
                    when (filter) {
                        StaffChatFilter.OPEN -> eq("status", "open")
                        StaffChatFilter.MINE -> {
                            require(!uid.isNullOrBlank()) { "signed-in user required for mine filter" }
                            eq("assigned_to", uid)
                            neq("status", "closed")
                        }
                        StaffChatFilter.CLOSED -> eq("status", "closed")
                    }
                }
                order("last_message_at", Order.DESCENDING, nullsFirst = false)
                limit(80)
            }
            .decodeList<ChatThreadRow>()
            .map { it.toSummary() }
    }

    override suspend fun listChatMessages(threadId: String): List<ChatMessageSummary> {
        require(threadId.isNotBlank())
        return client.from("chat_messages")
            .select(
                Columns.list(
                    "id",
                    "thread_id",
                    "sender_user_id",
                    "sender_kind",
                    "body",
                    "created_at",
                ),
            ) {
                filter { eq("thread_id", threadId) }
                order("created_at", Order.ASCENDING)
                limit(200)
            }
            .decodeList<ChatMessageRow>()
            .map { it.toSummary() }
    }

    override suspend fun claimChatThread(threadId: String) {
        require(threadId.isNotBlank())
        client.postgrest.rpc(
            RpcNames.CLAIM_CHAT_THREAD,
            buildJsonObject { put("p_thread_id", threadId) },
        )
    }

    override suspend fun closeChatThread(threadId: String) {
        require(threadId.isNotBlank())
        client.postgrest.rpc(
            RpcNames.CLOSE_CHAT_THREAD,
            buildJsonObject { put("p_thread_id", threadId) },
        )
    }

    override suspend fun markChatThreadRead(threadId: String) {
        require(threadId.isNotBlank())
        client.postgrest.rpc(
            RpcNames.MARK_CHAT_THREAD_READ,
            buildJsonObject { put("p_thread_id", threadId) },
        )
    }

    override suspend fun postChatMessage(threadId: String, body: String): String {
        require(threadId.isNotBlank())
        val trimmed = body.trim()
        require(trimmed.isNotEmpty()) { "message body required" }
        return client.postgrest.rpc(
            RpcNames.POST_CHAT_MESSAGE,
            buildJsonObject {
                put("p_thread_id", threadId)
                put("p_body", trimmed)
            },
        ).decodeAs<String>()
    }

    override suspend fun chatUnreadCount(threadId: String?): Int {
        return client.postgrest.rpc(
            RpcNames.CHAT_UNREAD_COUNT,
            buildJsonObject {
                if (threadId.isNullOrBlank()) put("p_thread_id", JsonNull)
                else put("p_thread_id", threadId)
            },
        ).decodeAs<Int>()
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

        private fun List<ReceiptLineInput>.toReceiptJsonArray(): JsonArray = buildJsonArray {
            forEach { line ->
                add(
                    buildJsonObject {
                        put("stock_item_id", line.stockItemId)
                        put("uom_id", line.uomId)
                        put("qty", line.qty)
                        put("unit_cost", line.unitCost)
                        put("currency", line.currency.rpcValue)
                        put("valuation_method", line.valuationMethod.rpcValue)
                        val serials = line.serials
                        if (!serials.isNullOrEmpty()) {
                            put("serials", buildJsonArray { serials.forEach { add(JsonPrimitive(it)) } })
                        }
                    },
                )
            }
        }

        private fun List<TransferLineInput>.toTransferJsonArray(): JsonArray = buildJsonArray {
            forEach { line ->
                add(
                    buildJsonObject {
                        put("stock_item_id", line.stockItemId)
                        put("uom_id", line.uomId)
                        put("qty", line.qty)
                        put("valuation_method", line.valuationMethod.rpcValue)
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

@Serializable
private data class StockItemRow(
    val id: String,
    @SerialName("base_uom_id") val baseUomId: String,
    @SerialName("oem_part_number") val oemPartNumber: String,
)

@Serializable
private data class StaffRoleRow(
    val role: String,
)

@Serializable
private data class ChatThreadRow(
    val id: String,
    val kind: String,
    val status: String,
    val subject: String? = null,
    @SerialName("assigned_to") val assignedTo: String? = null,
    @SerialName("last_message_at") val lastMessageAt: String? = null,
    @SerialName("created_at") val createdAt: String,
) {
    fun toSummary() = ChatThreadSummary(
        id = id,
        kind = kind,
        status = status,
        subject = subject,
        assignedTo = assignedTo,
        lastMessageAt = lastMessageAt,
        createdAt = createdAt,
    )
}

@Serializable
private data class ChatMessageRow(
    val id: String,
    @SerialName("thread_id") val threadId: String,
    @SerialName("sender_user_id") val senderUserId: String,
    @SerialName("sender_kind") val senderKind: String,
    val body: String,
    @SerialName("created_at") val createdAt: String,
) {
    fun toSummary() = ChatMessageSummary(
        id = id,
        threadId = threadId,
        senderUserId = senderUserId,
        senderKind = senderKind,
        body = body,
        createdAt = createdAt,
    )
}
