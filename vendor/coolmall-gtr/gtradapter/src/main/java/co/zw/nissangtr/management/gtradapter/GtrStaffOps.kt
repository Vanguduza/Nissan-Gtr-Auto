package co.zw.nissangtr.management.gtradapter

/**
 * Catalog-driven desks for web STAFF_NAV_TREE leaves (D1 remainder → D9).
 * Receive/transfers/master-stock keep dedicated screens; other leaves use this ops adapter.
 */
data class GtrOpsRow(
    val id: String,
    val title: String,
    val subtitle: String,
    val status: String = "",
)

data class GtrOpsField(
    val key: String,
    val label: String,
    val rpcParam: String,
)

data class GtrOpsAction(
    val id: String,
    val label: String,
    val rpc: String?,
    val idParam: String? = null,
    val needsRow: Boolean = false,
    /** Edge function name when [rpc] is null. */
    val edge: String? = null,
)

data class GtrOpsSpec(
    val href: String,
    val table: String? = null,
    val order: String = "created_at",
    val hint: String,
    val fields: List<GtrOpsField> = emptyList(),
    val actions: List<GtrOpsAction> = emptyList(),
    val neverAutoPo: Boolean = false,
)

object GtrOpsCatalog {
    fun spec(href: String): GtrOpsSpec? = SPECS[href]

    fun allHrefs(): Set<String> = SPECS.keys

    private fun f(key: String, label: String, rpc: String = "p_$key") =
        GtrOpsField(key, label, rpc)

    private val SPECS: Map<String, GtrOpsSpec> = listOf(
        GtrOpsSpec(
            href = "/staff/warehouse/cycle-count",
            table = "stock_reconciliations",
            hint = "Cycle count — draft → submit → approve. No tax.",
            fields = listOf(
                f("warehouse_id", "Warehouse id", "p_warehouse_id"),
                f("scope", "Scope full|partial", "p_scope"),
                f("currency", "Currency", "p_currency"),
            ),
            actions = listOf(
                GtrOpsAction("draft", "Create draft", "create_stock_reconciliation_draft"),
                GtrOpsAction("submit", "Submit", "submit_stock_reconciliation", "p_reconciliation_id", true),
                GtrOpsAction("approve", "Approve", "approve_stock_reconciliation", "p_reconciliation_id", true),
                GtrOpsAction("cancel", "Cancel", "cancel_stock_reconciliation", "p_reconciliation_id", true),
            ),
        ),
        GtrOpsSpec(
            href = "/staff/warehouse/bins",
            table = "warehouse_bins",
            order = "code",
            hint = "Bins — typed codes (Bridge QR later).",
            fields = listOf(
                f("warehouse_id", "Warehouse id", "p_warehouse_id"),
                f("code", "Bin code", "p_code"),
                f("name", "Name", "p_name"),
            ),
            actions = listOf(
                GtrOpsAction("create", "Create bin", "create_warehouse_bin"),
                GtrOpsAction("deactivate", "Deactivate", "deactivate_warehouse_bin", "p_bin_id", true),
            ),
        ),
        GtrOpsSpec(
            href = "/staff/warehouse/consignment",
            table = "consignment_entries",
            hint = "Consignment draft → submit / cancel.",
            fields = listOf(
                f("warehouse_id", "Warehouse id", "p_warehouse_id"),
                f("kind", "Kind inbound|outbound", "p_kind"),
                f("notes", "Notes", "p_notes"),
            ),
            actions = listOf(
                GtrOpsAction("draft", "Create draft", "create_consignment_entry_draft"),
                GtrOpsAction("submit", "Submit", "submit_consignment_entry", "p_entry_id", true),
                GtrOpsAction("cancel", "Cancel", "cancel_consignment_entry", "p_entry_id", true),
            ),
        ),
        GtrOpsSpec(
            href = "/staff/warehouse/insights",
            hint = "Stores insights — suggestions only; never auto-PO.",
            neverAutoPo = true,
            actions = listOf(
                GtrOpsAction("refresh", "Refresh insights", rpc = null, edge = "stores-insights"),
            ),
        ),
        GtrOpsSpec(
            href = "/staff/finance?tab=accounts",
            table = "chart_of_accounts",
            order = "code",
            hint = "Chart of accounts. Register via report_account_register.",
            fields = listOf(
                f("account_code", "Account code", "p_account_code"),
                f("from", "From (YYYY-MM-DD)", "p_from"),
                f("to", "To", "p_to"),
                f("currency", "Currency", "p_currency"),
            ),
            actions = listOf(
                GtrOpsAction("register", "Account register", "report_account_register"),
            ),
        ),
        GtrOpsSpec(
            href = "/staff/finance?tab=petty-cash",
            table = "finance_requisitions",
            hint = "Petty cash requisitions. Periods: open/close_account_period.",
            fields = listOf(
                f("req_type", "Type petty_cash", "p_req_type"),
                f("amount", "Amount", "p_amount"),
                f("currency", "Currency", "p_currency"),
                f("payee", "Payee", "p_payee"),
                f("memo", "Memo", "p_memo"),
            ),
            actions = listOf(
                GtrOpsAction("create", "Create", "create_finance_requisition"),
                GtrOpsAction("submit", "Submit", "submit_finance_requisition", "p_requisition_id", true),
                GtrOpsAction("approve", "Approve", "approve_finance_requisition", "p_requisition_id", true),
                GtrOpsAction("disburse", "Disburse", "disburse_finance_requisition", "p_requisition_id", true),
            ),
        ),
        GtrOpsSpec(
            href = "/staff/finance?tab=cash-sales",
            hint = "Cash sales register (account 4000 typical).",
            fields = listOf(
                f("account_code", "Account code", "p_account_code"),
                f("from", "From", "p_from"),
                f("to", "To", "p_to"),
                f("currency", "Currency", "p_currency"),
            ),
            actions = listOf(GtrOpsAction("register", "Load register", "report_account_register")),
        ),
        GtrOpsSpec(
            href = "/staff/finance?tab=contipay",
            hint = "ContiPay PSP register — no ZIMRA.",
            fields = listOf(
                f("account_code", "Account code", "p_account_code"),
                f("from", "From", "p_from"),
                f("to", "To", "p_to"),
                f("currency", "Currency", "p_currency"),
            ),
            actions = listOf(GtrOpsAction("register", "Load register", "report_account_register")),
        ),
        GtrOpsSpec(
            href = "/staff/finance?tab=paynow",
            hint = "Paynow register.",
            fields = listOf(
                f("account_code", "Account code", "p_account_code"),
                f("from", "From", "p_from"),
                f("to", "To", "p_to"),
                f("currency", "Currency", "p_currency"),
            ),
            actions = listOf(GtrOpsAction("register", "Load register", "report_account_register")),
        ),
        GtrOpsSpec(
            href = "/staff/finance?tab=ecocash",
            hint = "EcoCash register.",
            fields = listOf(
                f("account_code", "Account code", "p_account_code"),
                f("from", "From", "p_from"),
                f("to", "To", "p_to"),
                f("currency", "Currency", "p_currency"),
            ),
            actions = listOf(GtrOpsAction("register", "Load register", "report_account_register")),
        ),
        GtrOpsSpec(
            href = "/staff/finance?tab=exchange-rate",
            hint = "ZiG exchange rates.",
            fields = listOf(
                f("rate", "USD per ZiG rate", "p_rate"),
                f("effective_on", "Effective date", "p_effective_on"),
            ),
            actions = listOf(
                GtrOpsAction("list", "List rates", "list_zig_exchange_rates"),
                GtrOpsAction("set", "Set rate", "set_zig_exchange_rate"),
            ),
        ),
        GtrOpsSpec(
            href = "/staff/finance?tab=journals",
            table = "journal_entries",
            hint = "Journals — draft / post / reverse (contra). Currency required.",
            fields = listOf(
                f("entry_date", "Entry date", "p_entry_date"),
                f("description", "Description", "p_description"),
                f("currency", "Currency", "p_currency"),
            ),
            actions = listOf(
                GtrOpsAction("post", "Post", "post_journal", "p_entry_id", true),
                GtrOpsAction("reverse", "Reverse", "reverse_journal", "p_entry_id", true),
            ),
        ),
        GtrOpsSpec(
            href = "/staff/finance?tab=requisitions",
            table = "finance_requisitions",
            hint = "Requisitions create → approve → disburse.",
            fields = listOf(
                f("req_type", "Type", "p_req_type"),
                f("amount", "Amount", "p_amount"),
                f("currency", "Currency", "p_currency"),
                f("payee", "Payee", "p_payee"),
                f("memo", "Memo", "p_memo"),
            ),
            actions = listOf(
                GtrOpsAction("create", "Create", "create_finance_requisition"),
                GtrOpsAction("submit", "Submit", "submit_finance_requisition", "p_requisition_id", true),
                GtrOpsAction("approve", "Approve", "approve_finance_requisition", "p_requisition_id", true),
                GtrOpsAction("reject", "Reject", "reject_finance_requisition", "p_requisition_id", true),
                GtrOpsAction("disburse", "Disburse", "disburse_finance_requisition", "p_requisition_id", true),
            ),
        ),
        GtrOpsSpec(
            href = "/staff/finance?tab=payments",
            table = "payment_entries",
            hint = "Payments allocate / post / cancel.",
            fields = listOf(
                f("amount", "Amount", "p_amount"),
                f("currency", "Currency", "p_currency"),
            ),
            actions = listOf(
                GtrOpsAction("post", "Post", "post_payment_entry", "p_payment_id", true),
                GtrOpsAction("cancel", "Cancel", "cancel_payment_entry", "p_payment_id", true),
            ),
        ),
        GtrOpsSpec(
            href = "/staff/finance?tab=reports",
            hint = "P&L / BS / CF / TB / AR aging. No ZIMRA.",
            fields = listOf(
                f("from", "From", "p_from"),
                f("to", "To", "p_to"),
                f("currency", "Currency", "p_currency"),
            ),
            actions = listOf(
                GtrOpsAction("pnl", "P&L", "report_profit_and_loss"),
                GtrOpsAction("bs", "Balance sheet", "report_balance_sheet"),
                GtrOpsAction("cf", "Cash flow", "report_cash_flow"),
                GtrOpsAction("tb", "Trial balance", "report_trial_balance"),
                GtrOpsAction("ar", "AR aging", "kpi_ar_aging_snapshot"),
            ),
        ),
        GtrOpsSpec(
            href = "/staff/finance?tab=bank-recon",
            table = "bank_statements",
            hint = "Bank recon — clear matches.",
            fields = listOf(f("statement_id", "Statement id", "p_statement_id")),
            actions = listOf(
                GtrOpsAction("clear", "Clear matches", "clear_bank_matches"),
            ),
        ),
        GtrOpsSpec(
            href = "/staff/finance?tab=periods",
            table = "accounting_periods",
            order = "period_start",
            hint = "Lock accounting period.",
            fields = listOf(f("period_id", "Period id", "p_period_id")),
            actions = listOf(
                GtrOpsAction("lock", "Lock period", "lock_accounting_period", "p_period_id", true),
            ),
        ),
        GtrOpsSpec(
            href = "/staff/crm/credit",
            table = "customers",
            order = "display_name",
            hint = "Customer credit limit / hold.",
            fields = listOf(
                f("credit_limit", "Credit limit", "p_credit_limit"),
                f("currency", "Currency", "p_currency"),
                f("on_hold", "On hold true|false", "p_on_hold"),
            ),
            actions = listOf(
                GtrOpsAction("set", "Set credit", "set_customer_credit", "p_customer_id", true),
            ),
        ),
        GtrOpsSpec(
            href = "/staff/crm/reviews",
            table = "customer_product_reviews",
            hint = "Moderate product reviews.",
            fields = listOf(f("decision", "approve|reject|hide", "p_decision")),
            actions = listOf(
                GtrOpsAction("moderate", "Moderate", "moderate_customer_product_review", "p_review_id", true),
            ),
        ),
        GtrOpsSpec(
            href = "/staff/crm/product-pages",
            hint = "Product pages + home-rail pins (backup to algorithm).",
            fields = listOf(
                f("query", "OEM / title", "p_query"),
                f("stock_item_id", "Stock item id", "p_stock_item_id"),
                f("unit_price", "Unit price", "p_unit_price"),
                f("pin_featured", "Pin featured true|false", "p_pin_featured"),
                f("pin_movers", "Pin movers", "p_pin_movers"),
                f("pin_newest", "Pin newest", "p_pin_newest"),
                f("pin_sort", "Pin sort", "p_pin_sort"),
            ),
            actions = listOf(
                GtrOpsAction("list", "List pages", "list_staff_product_pages"),
                GtrOpsAction("save", "Save page + pins", "upsert_staff_product_page"),
            ),
        ),
        GtrOpsSpec(
            href = "/staff/crm/kits",
            table = "item_kits",
            order = "oem_part_number",
            hint = "Kits BOM. No catalog title/OEM/diagram edit.",
            fields = listOf(
                f("oem", "Kit OEM", "p_oem"),
                f("title", "Title", "p_title"),
                f("component_ids", "Component ids comma", "p_component_ids"),
            ),
            actions = listOf(
                GtrOpsAction("create", "Create kit", "create_kit_with_components"),
            ),
        ),
        GtrOpsSpec(
            href = "/staff/logistics",
            table = "delivery_jobs",
            hint = "Jobs / pick. GPS ingest stays delivery app.",
            fields = listOf(f("sales_invoice_id", "Invoice id", "p_sales_invoice_id")),
            actions = listOf(
                GtrOpsAction("pick", "Create pick list", "create_pick_list"),
                GtrOpsAction("dn", "Create DN", "create_delivery_note", "p_pick_list_id", true),
                GtrOpsAction("job", "Create job", "create_delivery_job", "p_delivery_note_id", true),
            ),
        ),
        GtrOpsSpec(
            href = "/staff/logistics/prep",
            hint = "Online prep queue.",
            actions = listOf(
                GtrOpsAction("queue", "Load prep queue", "list_online_prep_queue"),
            ),
        ),
        GtrOpsSpec(
            href = "/staff/logistics/tracking",
            hint = "Last-point track (not a GPS producer).",
            fields = listOf(f("delivery_job_id", "Job id", "p_delivery_job_id")),
            actions = listOf(
                GtrOpsAction("point", "Track point", "get_delivery_track_point"),
            ),
        ),
        GtrOpsSpec(
            href = "/staff/logistics/panic",
            table = "panic_events",
            hint = "Panic inbox — acknowledge only.",
            actions = listOf(
                GtrOpsAction("ack", "Acknowledge", "acknowledge_panic_event", "p_panic_event_id", true),
            ),
        ),
        GtrOpsSpec(
            href = "/staff/fleet",
            hint = "Company fleet metadata (no geolocation).",
            fields = listOf(
                f("plate", "Plate", "p_plate"),
                f("label", "Label", "p_label"),
                f("status", "Status", "p_status"),
            ),
            actions = listOf(
                GtrOpsAction("list", "List vehicles", "list_fleet_vehicles"),
                GtrOpsAction("upsert", "Upsert", "upsert_fleet_vehicle"),
                GtrOpsAction("status", "Set status", "set_fleet_vehicle_status", "p_id", true),
            ),
        ),
        GtrOpsSpec(
            href = "/staff/hr",
            table = "employees",
            order = "employee_code",
            hint = "HR desk — clock + hours. Gross pay only; no PAYE/NSSA.",
            fields = listOf(
                f("employee_id", "Employee id", "p_employee_id"),
                f("event_type", "clock_in|clock_out", "p_event_type"),
                f("period_start", "Period start", "p_period_start"),
                f("period_end", "Period end", "p_period_end"),
            ),
            actions = listOf(
                GtrOpsAction("clock", "Clock", "clock_attendance"),
                GtrOpsAction("hours", "Hours in period", "attendance_hours_in_period"),
            ),
        ),
        GtrOpsSpec(
            href = "/staff/hr?tab=payroll",
            table = "payroll_lines",
            hint = "Fund payroll from cash GL (Dr 5200/Cr 2150). No tax / bank payout.",
            fields = listOf(
                f("payroll_line_ids", "Line ids comma", "p_payroll_line_ids"),
                f("cash_account_code", "Cash account", "p_cash_account_code"),
            ),
            actions = listOf(
                GtrOpsAction("fund", "Fund lines", "fund_payroll_lines"),
            ),
        ),
        GtrOpsSpec(
            href = "/staff/hr?tab=organogram",
            table = "hr_roles",
            order = "code",
            hint = "Grades / roles / module_access.",
            fields = listOf(
                f("code", "Role code", "p_code"),
                f("name", "Name", "p_name"),
            ),
            actions = listOf(
                GtrOpsAction("create_role", "Create role", "create_hr_role"),
            ),
        ),
        GtrOpsSpec(
            href = "/staff/hr?tab=onboarding",
            table = "hr_onboarding_drafts",
            hint = "Onboarding drafts. Biometric photo = Bridge later.",
            fields = listOf(
                f("draft_id", "Draft id", "p_draft_id"),
                f("full_name", "Full name", "p_full_name"),
            ),
            actions = listOf(
                GtrOpsAction("complete", "Complete", "complete_hr_onboarding", "p_draft_id", true),
            ),
        ),
        GtrOpsSpec(
            href = "/staff/warranty",
            table = "warranty_claims",
            hint = "Warranty + quarantine return. No ZIMRA.",
            fields = listOf(
                f("notes", "Notes", "p_notes"),
                f("resolution", "Resolution", "p_resolution"),
            ),
            actions = listOf(
                GtrOpsAction("open", "Open claim", "open_warranty_claim"),
                GtrOpsAction("approve", "Approve", "approve_warranty_claim", "p_claim_id", true),
                GtrOpsAction("reject", "Reject", "reject_warranty_claim", "p_claim_id", true),
                GtrOpsAction("close", "Close", "close_warranty_claim", "p_claim_id", true),
                GtrOpsAction("quarantine", "Return to quarantine", "post_return_to_quarantine", "p_claim_id", true),
            ),
        ),
        GtrOpsSpec(
            href = "/staff/chat",
            hint = "Staff chat inbox.",
            fields = listOf(f("body", "Message", "p_body")),
            actions = listOf(
                GtrOpsAction("list", "Open threads", "list_staff_chat_threads"),
                GtrOpsAction("claim", "Claim", "claim_chat_thread", "p_thread_id", true),
                GtrOpsAction("close", "Close", "close_chat_thread", "p_thread_id", true),
                GtrOpsAction("send", "Send", "post_chat_message", "p_thread_id", true),
            ),
        ),
        GtrOpsSpec(
            href = "/staff/analytics",
            hint = "KPIs + optional narrative. Edge analytics-insights.",
            fields = listOf(
                f("from", "From", "from"),
                f("to", "To", "to"),
            ),
            actions = listOf(
                GtrOpsAction("refresh", "Refresh KPIs", rpc = null, edge = "analytics-insights"),
            ),
        ),
        GtrOpsSpec(
            href = "/staff/analytics/subscriptions",
            table = "ai_report_subscriptions",
            hint = "Report subscription CRUD.",
            fields = listOf(
                f("email", "Email", "p_email"),
                f("kpi_set", "KPI set", "p_kpi_set"),
            ),
            actions = listOf(
                GtrOpsAction("deactivate", "Deactivate", "deactivate_ai_report_subscription", "p_id", true),
            ),
        ),
        GtrOpsSpec(
            href = "/procurement",
            table = "purchase_orders",
            hint = "PO progress. Never AI auto-PO.",
            neverAutoPo = true,
        ),
        GtrOpsSpec(
            href = "/procurement/suppliers",
            table = "preferred_suppliers",
            order = "code",
            hint = "Preferred supplier roster (SoR).",
            neverAutoPo = true,
        ),
        GtrOpsSpec(
            href = "/procurement/orders/new",
            hint = "Manual preferred PO. Never AI auto-PO.",
            neverAutoPo = true,
            fields = listOf(
                f("supplier_id", "Supplier id", "p_supplier_id"),
                f("currency", "Currency", "p_currency"),
            ),
            actions = listOf(
                GtrOpsAction("create", "Create PO", "create_purchase_order"),
                GtrOpsAction("submit", "Submit", "submit_purchase_order", "p_purchase_order_id", true),
            ),
        ),
        GtrOpsSpec(
            href = "/procurement/grn",
            table = "goods_receipts",
            hint = "GRN receive. Bridge QR OEM later.",
            neverAutoPo = true,
        ),
        GtrOpsSpec(
            href = "/procurement/approvals",
            table = "purchase_orders",
            hint = "PO approvals.",
            neverAutoPo = true,
            fields = listOf(f("decision", "approve|reject", "p_decision")),
            actions = listOf(
                GtrOpsAction("submit", "Submit PO", "submit_purchase_order", "p_purchase_order_id", true),
            ),
        ),
        GtrOpsSpec(
            href = "/procurement/blankets",
            table = "blanket_purchase_orders",
            hint = "Blankets + call-off.",
            neverAutoPo = true,
            actions = listOf(
                GtrOpsAction("create", "Create blanket", "create_blanket_purchase_order"),
            ),
        ),
        GtrOpsSpec(
            href = "/procurement/rfqs",
            table = "rfqs",
            hint = "Optional spot-buy RFQs — not preferred SoR.",
            neverAutoPo = true,
        ),
        GtrOpsSpec(
            href = "/procurement/rfqs/new",
            hint = "New RFQ (optional).",
            neverAutoPo = true,
            fields = listOf(f("notes", "Notes", "p_notes")),
        ),
    ).associateBy { it.href }
}

interface GtrStaffOpsAdapter {
    suspend fun listLeaf(href: String): Result<List<GtrOpsRow>>

    suspend fun runAction(
        href: String,
        actionId: String,
        rowId: String?,
        fields: Map<String, String>,
    ): Result<String>
}

class FakeGtrStaffOpsAdapter : GtrStaffOpsAdapter {
    private val extra = mutableMapOf<String, MutableList<GtrOpsRow>>()

    override suspend fun listLeaf(href: String): Result<List<GtrOpsRow>> {
        val spec = GtrOpsCatalog.spec(href)
            ?: return Result.failure(IllegalArgumentException("Unknown desk $href"))
        val seeded = extra[href].orEmpty() + listOf(
            GtrOpsRow(
                id = "fake-${href.hashCode().toUInt()}",
                title = spec.href.substringAfterLast('/').ifBlank { spec.href },
                subtitle = spec.hint.take(80),
                status = if (spec.neverAutoPo) "no-auto-po" else "ok",
            ),
        )
        return Result.success(seeded)
    }

    override suspend fun runAction(
        href: String,
        actionId: String,
        rowId: String?,
        fields: Map<String, String>,
    ): Result<String> {
        val spec = GtrOpsCatalog.spec(href)
            ?: return Result.failure(IllegalArgumentException("Unknown desk $href"))
        val action = spec.actions.find { it.id == actionId }
            ?: return Result.failure(IllegalArgumentException("Unknown action $actionId"))
        if (action.needsRow && rowId.isNullOrBlank()) {
            return Result.failure(IllegalArgumentException("Select a row"))
        }
        if (spec.neverAutoPo && actionId.contains("auto", ignoreCase = true)) {
            return Result.failure(IllegalStateException("AI never auto-creates POs"))
        }
        val id = "ok-$actionId-${rowId ?: "new"}"
        extra.getOrPut(href) { mutableListOf() }.add(
            0,
            GtrOpsRow(id, action.label, fields.entries.joinToString { "${it.key}=${it.value}" }, "done"),
        )
        return Result.success(id)
    }
}
