package co.zw.nissangtr.management.gtradapter

/**
 * Mirrors apps/web/lib/staff-auth.ts STAFF_NAV_TREE module ids + role gates.
 * Leaf routes are documented for later screens; this phase uses module-level hub tiles.
 */
data class StaffNavModule(
    val id: String,
    val label: String,
    /** Web href for the module entry (documentation / deep-link later). */
    val href: String,
    val roles: List<String>,
    val children: List<StaffNavLeaf> = emptyList(),
)

data class StaffNavLeaf(
    val href: String,
    val label: String,
    val roles: List<String>,
)

object StaffNavTree {
    /** POS is in the web tree but excluded from CoolMall destinations this phase. */
    const val POS_MODULE_ID = "pos"

    val MODULES: List<StaffNavModule> = listOf(
        StaffNavModule(
            id = POS_MODULE_ID,
            label = "POS",
            href = "/staff/pos",
            roles = listOf("admin", "warehouse", "sales"),
            children = listOf(
                StaffNavLeaf("/staff/pos?tab=cart", "Cart", listOf("admin", "warehouse", "sales")),
                StaffNavLeaf("/staff/pos?tab=prep", "Online prep", listOf("admin", "warehouse", "sales")),
            ),
        ),
        StaffNavModule(
            id = "warehouse",
            label = "Warehouse",
            href = "/staff/warehouse",
            roles = listOf("admin", "warehouse"),
            children = listOf(
                StaffNavLeaf("/staff/warehouse", "Overview", listOf("admin", "warehouse")),
                StaffNavLeaf("/staff/warehouse/receive", "Receive", listOf("admin", "warehouse")),
                StaffNavLeaf(
                    "/staff/warehouse/master-stock",
                    "Master stock",
                    listOf("admin", "warehouse", "sales", "finance"),
                ),
                StaffNavLeaf("/staff/warehouse/transfers", "Transfers (WH1→WH2)", listOf("admin", "warehouse")),
                StaffNavLeaf("/staff/warehouse/cycle-count", "Cycle count", listOf("admin", "warehouse")),
                StaffNavLeaf("/staff/warehouse/bins", "Bins", listOf("admin", "warehouse")),
                StaffNavLeaf("/staff/warehouse/consignment", "Consignment", listOf("admin", "warehouse")),
                StaffNavLeaf(
                    "/staff/warehouse/insights",
                    "AI insights",
                    listOf("admin", "warehouse", "finance"),
                ),
            ),
        ),
        StaffNavModule(
            id = "finance",
            label = "Finance",
            href = "/staff/finance",
            roles = listOf("admin", "finance"),
            children = listOf(
                StaffNavLeaf("/staff/finance?tab=accounts", "Online sales", listOf("admin", "finance")),
                StaffNavLeaf("/staff/finance?tab=petty-cash", "Petty cash", listOf("admin", "finance")),
                StaffNavLeaf("/staff/finance?tab=cash-sales", "Cash", listOf("admin", "finance")),
                StaffNavLeaf("/staff/finance?tab=contipay", "ContiPay", listOf("admin", "finance")),
                StaffNavLeaf("/staff/finance?tab=paynow", "Paynow", listOf("admin", "finance")),
                StaffNavLeaf("/staff/finance?tab=ecocash", "EcoCash", listOf("admin", "finance")),
                StaffNavLeaf("/staff/finance?tab=exchange-rate", "ZiG rate", listOf("admin", "finance")),
                StaffNavLeaf("/staff/finance?tab=journals", "Journals", listOf("admin", "finance")),
                StaffNavLeaf("/staff/finance?tab=requisitions", "Requisitions", listOf("admin", "finance")),
                StaffNavLeaf("/staff/finance?tab=payments", "Payments", listOf("admin", "finance")),
                StaffNavLeaf("/staff/finance?tab=reports", "Reports", listOf("admin", "finance")),
                StaffNavLeaf("/staff/finance?tab=bank-recon", "Bank recon", listOf("admin", "finance")),
                StaffNavLeaf("/staff/finance?tab=periods", "Periods", listOf("admin", "finance")),
            ),
        ),
        StaffNavModule(
            id = "crm",
            label = "CRM",
            href = "/staff/crm/credit",
            roles = listOf("admin", "sales", "finance"),
            children = listOf(
                StaffNavLeaf("/staff/crm/credit", "Customer credit", listOf("admin", "sales", "finance")),
                StaffNavLeaf("/staff/crm/reviews", "Reviews", listOf("admin", "sales")),
                StaffNavLeaf("/staff/crm/product-pages", "Product pages", listOf("admin", "sales", "warehouse")),
                StaffNavLeaf("/staff/crm/kits", "Kits", listOf("admin", "sales", "warehouse")),
            ),
        ),
        StaffNavModule(
            id = "logistics",
            label = "Logistics",
            href = "/staff/logistics",
            roles = listOf("admin", "warehouse", "sales", "dispatcher"),
            children = listOf(
                StaffNavLeaf("/staff/logistics", "Jobs / pick", listOf("admin", "warehouse", "sales", "dispatcher")),
                StaffNavLeaf("/staff/logistics/prep", "Sales prep", listOf("admin", "warehouse", "sales", "dispatcher")),
                StaffNavLeaf("/staff/logistics/tracking", "Live tracking", listOf("admin", "warehouse", "dispatcher")),
                StaffNavLeaf("/staff/logistics/panic", "Panic inbox", listOf("admin", "warehouse", "dispatcher")),
            ),
        ),
        StaffNavModule(
            id = "fleet",
            label = "Fleet",
            href = "/staff/fleet",
            roles = listOf("admin", "warehouse", "dispatcher"),
            children = listOf(
                StaffNavLeaf("/staff/fleet", "Company fleet", listOf("admin", "warehouse", "dispatcher")),
            ),
        ),
        StaffNavModule(
            id = "hr",
            label = "HR",
            href = "/staff/hr",
            roles = listOf("admin", "hr"),
            children = listOf(
                StaffNavLeaf("/staff/hr", "HR desk", listOf("admin", "hr")),
                StaffNavLeaf("/staff/hr?tab=payroll", "Payroll & payslips", listOf("admin", "hr")),
                StaffNavLeaf("/staff/hr?tab=organogram", "Organogram", listOf("admin", "hr")),
                StaffNavLeaf("/staff/hr?tab=onboarding", "Onboarding", listOf("admin", "hr")),
            ),
        ),
        StaffNavModule(
            id = "warranty",
            label = "Warranty",
            href = "/staff/warranty",
            roles = listOf("admin", "warehouse", "sales"),
            children = listOf(
                StaffNavLeaf("/staff/warranty", "Claims", listOf("admin", "warehouse", "sales")),
            ),
        ),
        StaffNavModule(
            id = "chat",
            label = "Chat",
            href = "/staff/chat",
            roles = listOf("admin", "sales", "warehouse"),
            children = listOf(
                StaffNavLeaf("/staff/chat", "Inbox", listOf("admin", "sales", "warehouse")),
            ),
        ),
        StaffNavModule(
            id = "analytics",
            label = "Analytics",
            href = "/staff/analytics",
            roles = listOf("admin", "finance", "sales"),
            children = listOf(
                StaffNavLeaf("/staff/analytics", "KPIs", listOf("admin", "finance", "sales")),
                StaffNavLeaf("/staff/analytics/subscriptions", "Subscriptions", listOf("admin", "finance", "sales")),
            ),
        ),
        StaffNavModule(
            id = "procurement",
            label = "Procurement",
            href = "/procurement",
            roles = listOf("admin", "warehouse", "finance"),
            children = listOf(
                StaffNavLeaf("/procurement", "Overview", listOf("admin", "warehouse", "finance")),
                StaffNavLeaf("/procurement/suppliers", "Preferred suppliers", listOf("admin", "warehouse", "finance")),
                StaffNavLeaf("/procurement/orders/new", "New PO", listOf("admin", "warehouse", "finance")),
                StaffNavLeaf("/procurement/grn", "GRN", listOf("admin", "warehouse", "finance")),
                StaffNavLeaf("/procurement/approvals", "Approvals", listOf("admin", "warehouse", "finance")),
                StaffNavLeaf("/procurement/blankets", "Blankets", listOf("admin", "warehouse", "finance")),
                StaffNavLeaf("/procurement/rfqs", "RFQs", listOf("admin", "warehouse", "finance")),
                StaffNavLeaf("/procurement/rfqs/new", "New RFQ", listOf("admin", "warehouse", "finance")),
            ),
        ),
    )

    fun rolesAllow(userRoles: List<String>, required: List<String>): Boolean {
        if (required.isEmpty()) return true
        if (userRoles.contains("admin")) return true
        return required.any { userRoles.contains(it) }
    }

    /**
     * Web [filterNavTreeForModuleAccess]: role filter, then organogram module_access
     * (admins bypass module_access; empty/null → roles-only).
     */
    fun filterModules(
        roles: List<String>,
        moduleAccess: List<String>?,
        excludePos: Boolean = true,
    ): List<StaffNavModule> {
        val roleFiltered = MODULES.filter { rolesAllow(roles, it.roles) }
        val afterAccess = if (roles.contains("admin")) {
            roleFiltered
        } else {
            val allowed = (moduleAccess ?: emptyList())
                .map { it.trim().lowercase() }
                .filter { it.isNotEmpty() }
            if (allowed.isEmpty()) {
                roleFiltered
            } else {
                roleFiltered.filter { allowed.contains(it.id.lowercase()) }
            }
        }
        return if (excludePos) {
            afterAccess.filter { it.id != POS_MODULE_ID }
        } else {
            afterAccess
        }
    }

    fun filterLeaves(module: StaffNavModule, roles: List<String>): List<StaffNavLeaf> {
        val leaves = module.children.filter { rolesAllow(roles, it.roles) }
        return leaves.ifEmpty {
            listOf(StaffNavLeaf(module.href, module.label, module.roles))
        }
    }

    /** Web prefersPosHome — sales-only → POS; admin/warehouse keep hub. */
    fun prefersPosHome(roles: List<String>): Boolean {
        if (roles.any { it == "admin" || it == "warehouse" }) return false
        return roles.contains("sales")
    }
}
