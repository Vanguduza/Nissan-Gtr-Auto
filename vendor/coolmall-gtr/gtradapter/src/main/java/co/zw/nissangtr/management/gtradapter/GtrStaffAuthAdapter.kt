package co.zw.nissangtr.management.gtradapter

/**
 * Contracts for CoolMall → Supabase injection.
 * Behavioral SoT: apps/web/lib/staff-auth.ts (not android-management-legacy UI).
 *
 * Live path (Phase B+): supabase-kt GoTrue + RPC resolve_staff_login_email /
 * my_module_access. Fake path is the default for local smoke.
 */
interface GtrStaffAuthAdapter {
    /** Emp# | email | phone → login email (RPC resolve_staff_login_email). */
    suspend fun resolveLoginEmail(identifier: String): String?

    suspend fun signInWithPassword(email: String, password: String): Result<Unit>

    /**
     * Web [signInWithStaffIdentifier]: resolve → GoTrue password.
     * Non-enumerating errors on failure.
     */
    suspend fun signInWithStaffIdentifier(identifier: String, password: String): Result<Unit>

    suspend fun loadStaffContext(): Result<GtrStaffContext>

    suspend fun signOut()

    /**
     * Idle-lock unlock: re-auth with password for the current session email / Fake gate.
     */
    suspend fun reauthWithPassword(password: String): Result<Unit>

    fun isFakeMode(): Boolean
}

data class GtrStaffContext(
    val userId: String,
    val isStaff: Boolean,
    val roles: List<String>,
    /** Organogram module ids; null/empty → roles-only nav (web parity). */
    val moduleAccess: List<String>?,
    val mustChangePassword: Boolean,
)

/**
 * Hub destinations from web STAFF_NAV_TREE (non-POS this phase).
 */
interface GtrStaffHubAdapter {
    fun filterModules(ctx: GtrStaffContext): List<StaffNavModule>

    fun filterModuleIds(ctx: GtrStaffContext): List<String> =
        filterModules(ctx).map { it.id }

    /**
     * Web staffHomePath — POS deferred this phase, so always hub even for sales-only.
     * When POS ships, restore prefersPosHome → "pos".
     */
    fun homeRoute(ctx: GtrStaffContext): String
}

interface GtrPasswordAdapter {
    /** GoTrue updateUser — web /staff/change-password. */
    suspend fun changePassword(newPassword: String): Result<Unit>
}

data class MasterStockRow(
    val stockItemId: String,
    val oemPartNumber: String,
    val description: String,
    val qtyTotal: Double,
    val qtyWh1: Double,
    val qtyWh2: Double,
)

data class GtrWarehouseOption(
    val id: String,
    val code: String,
    val name: String,
    val roleCode: String? = null,
    val isQuarantine: Boolean = false,
)

data class GtrStockItemOption(
    val id: String,
    val oemPartNumber: String,
    val description: String,
    val baseUomId: String?,
    val requiresSerial: Boolean = false,
)

data class GtrReceiptLine(
    val stockItemId: String,
    val uomId: String,
    val qty: Double,
    val unitCost: Double,
    val currency: String = "USD",
)

data class GtrTransferLine(
    val stockItemId: String,
    val uomId: String,
    val qty: Double,
)

data class GtrPendingTransfer(
    val id: String,
    val documentNumber: String?,
    val fromWarehouseId: String?,
    val toWarehouseId: String?,
    val notes: String?,
    val createdAt: String?,
)

/** Web warehouse desk — master-stock, receive, WH1→WH2 transfers. */
interface GtrWarehouseAdapter {
    suspend fun listMasterStock(
        limit: Int = 200,
        query: String? = null,
        chassisCode: String? = null,
    ): Result<List<MasterStockRow>>

    suspend fun listWarehouses(includeQuarantine: Boolean = false): Result<List<GtrWarehouseOption>>

    suspend fun searchStockItems(query: String, limit: Int = 20): Result<List<GtrStockItemOption>>

    suspend fun postStockReceipt(
        toWarehouseId: String,
        notes: String,
        lines: List<GtrReceiptLine>,
    ): Result<String>

    suspend fun listPendingTransfers(): Result<List<GtrPendingTransfer>>

    suspend fun createStockTransfer(
        fromWarehouseId: String,
        toWarehouseId: String,
        notes: String,
        lines: List<GtrTransferLine>,
    ): Result<String>

    suspend fun approveStockTransfer(entryId: String): Result<String>

    suspend fun rejectStockTransfer(entryId: String): Result<String>
}

/** Holds signed-in staff context for hub / gates (in-memory; Fake or post-login Live). */
class GtrStaffSession {
    @Volatile
    var context: GtrStaffContext? = null
        private set

    fun update(ctx: GtrStaffContext?) {
        context = ctx
    }

    fun clear() {
        context = null
    }
}

/** Reference Fake for offline UI smoke — replace with Live when Supabase inject lands. */
class FakeGtrStaffAuthAdapter(
    private val session: GtrStaffSession,
) : GtrStaffAuthAdapter {
    override suspend fun resolveLoginEmail(identifier: String): String? =
        identifier.trim().ifEmpty { null }?.let { "fake@local.test" }

    override suspend fun signInWithPassword(email: String, password: String): Result<Unit> {
        if (email.isBlank() || password.isBlank()) {
            return Result.failure(IllegalArgumentException("Sign-in failed"))
        }
        return Result.success(Unit)
    }

    override suspend fun signInWithStaffIdentifier(
        identifier: String,
        password: String,
    ): Result<Unit> {
        val email = resolveLoginEmail(identifier)
            ?: return Result.failure(IllegalArgumentException("Sign-in failed"))
        val signed = signInWithPassword(email, password)
        if (signed.isFailure) return signed
        val ctx = loadStaffContext().getOrElse { return Result.failure(it) }
        session.update(ctx)
        return Result.success(Unit)
    }

    override suspend fun loadStaffContext(): Result<GtrStaffContext> {
        val existing = session.context
        if (existing != null) return Result.success(existing)
        val ctx = GtrStaffContext(
            userId = "fake-staff",
            isStaff = true,
            roles = listOf("admin"),
            moduleAccess = null,
            mustChangePassword = false,
        )
        session.update(ctx)
        return Result.success(ctx)
    }

    override suspend fun signOut() {
        session.clear()
    }

    override suspend fun reauthWithPassword(password: String): Result<Unit> {
        if (password.length < 6) {
            return Result.failure(IllegalArgumentException("Unlock failed"))
        }
        if (session.context == null) {
            return Result.failure(IllegalStateException("Not signed in"))
        }
        return Result.success(Unit)
    }

    override fun isFakeMode(): Boolean = true
}

class FakeGtrStaffHubAdapter : GtrStaffHubAdapter {
    override fun filterModules(ctx: GtrStaffContext): List<StaffNavModule> =
        StaffNavTree.filterModules(ctx.roles, ctx.moduleAccess, excludePos = true)

    override fun homeRoute(ctx: GtrStaffContext): String = "hub"
}

class FakeGtrPasswordAdapter(
    private val session: GtrStaffSession,
) : GtrPasswordAdapter {
    override suspend fun changePassword(newPassword: String): Result<Unit> {
        if (newPassword.length < 8) {
            return Result.failure(IllegalArgumentException("Password too short"))
        }
        val ctx = session.context
            ?: return Result.failure(IllegalStateException("Not signed in"))
        session.update(ctx.copy(mustChangePassword = false))
        return Result.success(Unit)
    }
}

class FakeGtrWarehouseAdapter : GtrWarehouseAdapter {
    private val warehouses = listOf(
        GtrWarehouseOption("wh-1", "WH1", "Receiving", roleCode = "WH1"),
        GtrWarehouseOption("wh-2", "WH2", "Storefloor", roleCode = "WH2"),
        GtrWarehouseOption("wh-q", "QUAR", "Quarantine", isQuarantine = true),
    )
    private val pending = mutableListOf<GtrPendingTransfer>()
    private val seed = listOf(
        MasterStockRow(
            stockItemId = "si-1001",
            oemPartNumber = "16546-6N200",
            description = "Oil filter — VR38",
            qtyTotal = 42.0,
            qtyWh1 = 30.0,
            qtyWh2 = 12.0,
        ),
        MasterStockRow(
            stockItemId = "si-1002",
            oemPartNumber = "15208-65F0A",
            description = "Oil filter element",
            qtyTotal = 18.0,
            qtyWh1 = 10.0,
            qtyWh2 = 8.0,
        ),
        MasterStockRow(
            stockItemId = "si-1003",
            oemPartNumber = "11910-AA350",
            description = "Alternator belt",
            qtyTotal = 7.0,
            qtyWh1 = 5.0,
            qtyWh2 = 2.0,
        ),
        MasterStockRow(
            stockItemId = "si-1004",
            oemPartNumber = "B010A-1EA0A",
            description = "Brake pad set front",
            qtyTotal = 0.0,
            qtyWh1 = 0.0,
            qtyWh2 = 0.0,
        ),
    )

    override suspend fun listMasterStock(
        limit: Int,
        query: String?,
        chassisCode: String?,
    ): Result<List<MasterStockRow>> {
        val q = query?.trim()?.lowercase().orEmpty()
        // Fake seed has no chassis column — chassis filter is a no-op smoke stub.
        @Suppress("UNUSED_VARIABLE")
        val chassis = chassisCode?.trim().orEmpty()
        val filtered = if (q.isEmpty()) {
            seed
        } else {
            seed.filter {
                it.oemPartNumber.lowercase().contains(q) ||
                    it.description.lowercase().contains(q)
            }
        }
        return Result.success(filtered.take(limit.coerceAtLeast(1)))
    }

    override suspend fun listWarehouses(includeQuarantine: Boolean): Result<List<GtrWarehouseOption>> =
        Result.success(
            if (includeQuarantine) warehouses else warehouses.filter { !it.isQuarantine },
        )

    override suspend fun searchStockItems(
        query: String,
        limit: Int,
    ): Result<List<GtrStockItemOption>> {
        val q = query.trim()
        if (q.length < 2) return Result.success(emptyList())
        val qLower = q.lowercase()
        return Result.success(
            seed.filter {
                it.stockItemId.equals(q, ignoreCase = true) ||
                    it.oemPartNumber.lowercase().contains(qLower) ||
                    it.description.lowercase().contains(qLower)
            }.take(limit.coerceIn(1, 50)).map {
                GtrStockItemOption(
                    id = it.stockItemId,
                    oemPartNumber = it.oemPartNumber,
                    description = it.description,
                    baseUomId = "uom-ea",
                )
            },
        )
    }

    override suspend fun postStockReceipt(
        toWarehouseId: String,
        notes: String,
        lines: List<GtrReceiptLine>,
    ): Result<String> = runCatching {
        require(toWarehouseId.isNotBlank())
        require(lines.isNotEmpty()) { "Receipt needs at least one line" }
        lines.forEach { line ->
            require(line.stockItemId.isNotBlank() && line.uomId.isNotBlank())
            require(line.qty > 0)
            require(line.currency == "USD" || line.currency == "ZIG")
        }
        "recv-${java.util.UUID.randomUUID()}"
    }

    override suspend fun listPendingTransfers(): Result<List<GtrPendingTransfer>> =
        Result.success(pending.toList())

    override suspend fun createStockTransfer(
        fromWarehouseId: String,
        toWarehouseId: String,
        notes: String,
        lines: List<GtrTransferLine>,
    ): Result<String> = runCatching {
        require(fromWarehouseId.isNotBlank() && toWarehouseId.isNotBlank())
        require(fromWarehouseId != toWarehouseId) { "From and to warehouses must differ" }
        require(lines.isNotEmpty()) { "Transfer needs at least one line" }
        val id = "xfer-${java.util.UUID.randomUUID()}"
        pending.add(
            0,
            GtrPendingTransfer(
                id = id,
                documentNumber = "TR-FAKE",
                fromWarehouseId = fromWarehouseId,
                toWarehouseId = toWarehouseId,
                notes = notes.trim().ifEmpty { null },
                createdAt = "now",
            ),
        )
        id
    }

    override suspend fun approveStockTransfer(entryId: String): Result<String> = runCatching {
        require(entryId.isNotBlank())
        pending.removeAll { it.id == entryId }
        entryId
    }

    override suspend fun rejectStockTransfer(entryId: String): Result<String> = runCatching {
        require(entryId.isNotBlank())
        pending.removeAll { it.id == entryId }
        entryId
    }
}
