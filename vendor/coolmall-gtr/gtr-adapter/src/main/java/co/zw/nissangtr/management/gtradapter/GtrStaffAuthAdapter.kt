package co.zw.nissangtr.management.gtradapter

/**
 * Stub contracts for CoolMall → Supabase injection.
 * Behavioral SoT: apps/web/lib/staff-auth.ts (not android-management-legacy UI).
 *
 * Phase B: implement with supabase-kt / RpcClient Fake|Live and register in CoolMall DI.
 */
interface GtrStaffAuthAdapter {
    /** Emp# | email | phone → login email (RPC resolve_staff_login_email). */
    suspend fun resolveLoginEmail(identifier: String): String?

    suspend fun signInWithPassword(email: String, password: String): Result<Unit>

    suspend fun loadStaffContext(): Result<GtrStaffContext>

    /** Local smoke without secrets — mirrors web Fake paths on Android. */
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
 * Hub destinations must mirror web STAFF_NAV_TREE ids:
 * pos, warehouse, finance, crm, logistics, fleet, hr, warranty, chat, analytics, procurement.
 */
interface GtrStaffHubAdapter {
    fun filterModuleIds(ctx: GtrStaffContext): List<String>

    fun homeRoute(ctx: GtrStaffContext): String
}

/** Reference Fake for offline UI smoke — replace with Live in Phase B. */
object FakeGtrStaffAuthAdapter : GtrStaffAuthAdapter {
    override suspend fun resolveLoginEmail(identifier: String): String? =
        identifier.trim().ifEmpty { null }?.let { "fake@local.test" }

    override suspend fun signInWithPassword(email: String, password: String): Result<Unit> =
        Result.success(Unit)

    override suspend fun loadStaffContext(): Result<GtrStaffContext> =
        Result.success(
            GtrStaffContext(
                userId = "fake-staff",
                isStaff = true,
                roles = listOf("admin"),
                moduleAccess = null,
                mustChangePassword = false,
            ),
        )

    override fun isFakeMode(): Boolean = true
}

object FakeGtrStaffHubAdapter : GtrStaffHubAdapter {
    private val all =
        listOf(
            "pos", "warehouse", "finance", "crm", "logistics", "fleet",
            "hr", "warranty", "chat", "analytics", "procurement",
        )

    override fun filterModuleIds(ctx: GtrStaffContext): List<String> {
        if (ctx.roles.contains("admin")) return all
        val allowed = ctx.moduleAccess?.map { it.lowercase() }.orEmpty()
        if (allowed.isEmpty()) return all
        return all.filter { allowed.contains(it) }
    }

    /** Sales-only → POS; else hub (web staffHomePath). */
    override fun homeRoute(ctx: GtrStaffContext): String {
        val salesOnly =
            ctx.roles.contains("sales") &&
                !ctx.roles.contains("admin") &&
                !ctx.roles.contains("warehouse")
        return if (salesOnly) "pos" else "hub"
    }
}
