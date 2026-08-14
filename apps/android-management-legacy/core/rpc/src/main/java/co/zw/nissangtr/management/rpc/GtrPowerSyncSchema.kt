package co.zw.nissangtr.management.rpc

import com.powersync.db.schema.Column
import com.powersync.db.schema.Schema
import com.powersync.db.schema.Table

/**
 * Client schema for H7 — mirrors `powersync/schema.json` (+ sync-rule stubs).
 * PowerSync auto-adds `id`; do not declare it. No journal/payment tables.
 */
object GtrPowerSyncSchema {
    val schema: Schema =
        Schema(
            // --- by_user_pos (schema.json + sync-rules stubs) ---
            Table(
                name = "pos_carts",
                columns =
                    listOf(
                        Column.text("document_number"),
                        Column.text("customer_id"),
                        Column.text("warehouse_id"),
                        Column.text("currency"),
                        Column.real("exchange_rate_applied"),
                        Column.text("status"),
                        Column.text("created_by"),
                        Column.text("created_at"),
                        Column.text("updated_at"),
                    ),
            ),
            Table(
                name = "pos_cart_lines",
                columns =
                    listOf(
                        Column.text("cart_id"),
                        Column.text("stock_item_id"),
                        Column.text("parent_line_id"),
                        Column.integer("is_core_charge"),
                        Column.text("uom_id"),
                        Column.real("qty"),
                        Column.real("qty_base"),
                        Column.real("unit_price"),
                        Column.real("line_total"),
                        Column.real("qty_fulfilled"),
                        Column.text("created_at"),
                    ),
            ),
            Table(
                name = "customers",
                columns =
                    listOf(
                        Column.text("display_name"),
                        Column.text("phone_e164"),
                        Column.text("email"),
                        Column.text("price_list_id"),
                        Column.real("credit_limit"),
                        Column.integer("credit_limit_minor"),
                        Column.integer("credit_hold"),
                        Column.text("currency"),
                    ),
            ),
            Table(
                name = "price_lists",
                columns =
                    listOf(
                        Column.text("name"),
                        Column.text("currency"),
                        Column.integer("is_active"),
                    ),
            ),
            Table(
                name = "price_list_items",
                columns =
                    listOf(
                        Column.text("price_list_id"),
                        Column.text("stock_item_id"),
                        Column.real("unit_price"),
                        Column.text("currency"),
                    ),
            ),
            Table(
                name = "customer_price_overrides",
                columns =
                    listOf(
                        Column.text("customer_id"),
                        Column.text("stock_item_id"),
                        Column.real("unit_price"),
                        Column.text("currency"),
                    ),
            ),
            Table(
                name = "stock_items",
                columns =
                    listOf(
                        Column.text("oem_part_number"),
                        Column.text("description"),
                        Column.text("base_uom_id"),
                        Column.integer("requires_serial"),
                    ),
            ),
            Table(
                name = "uoms",
                columns =
                    listOf(
                        Column.text("code"),
                        Column.text("name"),
                    ),
            ),
            Table(
                name = "warehouses",
                columns =
                    listOf(
                        Column.text("code"),
                        Column.text("name"),
                    ),
            ),
            Table(
                name = "stock_levels",
                columns =
                    listOf(
                        Column.text("warehouse_id"),
                        Column.text("stock_item_id"),
                        Column.real("quantity"),
                    ),
            ),
            // --- by_staff_dispatch ---
            Table(
                name = "sales_invoices",
                columns =
                    listOf(
                        Column.text("doc_type"),
                        Column.text("status"),
                        Column.text("document_number"),
                        Column.text("customer_id"),
                        Column.text("warehouse_id"),
                        Column.text("currency"),
                        Column.real("exchange_rate_applied"),
                        Column.text("fulfillment_mode"),
                        Column.real("subtotal"),
                        Column.text("created_at"),
                    ),
            ),
            Table(
                name = "sales_invoice_lines",
                columns =
                    listOf(
                        Column.text("sales_invoice_id"),
                        Column.text("stock_item_id"),
                        Column.text("uom_id"),
                        Column.real("qty"),
                        Column.real("qty_base"),
                        Column.real("unit_price"),
                        Column.real("line_total"),
                    ),
            ),
            Table(
                name = "pick_lists",
                columns =
                    listOf(
                        Column.text("sales_invoice_id"),
                        Column.text("status"),
                        Column.text("warehouse_id"),
                        Column.text("created_at"),
                    ),
            ),
            Table(
                name = "pick_list_lines",
                columns =
                    listOf(
                        Column.text("pick_list_id"),
                        Column.text("sales_invoice_line_id"),
                        Column.text("stock_item_id"),
                        Column.text("uom_id"),
                        Column.real("qty_requested"),
                        Column.real("qty_picked"),
                    ),
            ),
            Table(
                name = "delivery_notes",
                columns =
                    listOf(
                        Column.text("sales_invoice_id"),
                        Column.text("document_number"),
                        Column.text("status"),
                        Column.text("created_at"),
                        Column.text("submitted_at"),
                    ),
            ),
            Table(
                name = "delivery_note_lines",
                columns =
                    listOf(
                        Column.text("delivery_note_id"),
                        Column.text("sales_invoice_line_id"),
                        Column.text("stock_item_id"),
                        Column.text("uom_id"),
                        Column.real("qty"),
                        Column.real("qty_base"),
                    ),
            ),
            Table(
                name = "delivery_jobs",
                columns =
                    listOf(
                        Column.text("delivery_note_id"),
                        Column.text("status"),
                        Column.text("dispatched_at"),
                        Column.text("completed_at"),
                    ),
            ),
            Table(
                name = "delivery_locations",
                columns =
                    listOf(
                        Column.text("delivery_job_id"),
                        Column.real("lat"),
                        Column.real("lng"),
                        Column.text("recorded_at"),
                    ),
            ),
            // --- by_staff_cycle_count ---
            Table(
                name = "stock_reconciliations",
                columns =
                    listOf(
                        Column.text("warehouse_id"),
                        Column.text("scope"),
                        Column.text("status"),
                        Column.text("currency"),
                        Column.real("exchange_rate_applied"),
                        Column.text("notes"),
                        Column.text("created_at"),
                    ),
            ),
            Table(
                name = "stock_reconciliation_lines",
                columns =
                    listOf(
                        Column.text("stock_reconciliation_id"),
                        Column.text("stock_item_id"),
                        Column.real("system_qty"),
                        Column.real("counted_qty"),
                        Column.real("variance_qty"),
                    ),
            ),
        )

    val TABLE_NAMES: Set<String> = schema.tables.map { it.name }.toSet()

    init {
        for (name in TABLE_NAMES) {
            require(name !in PowerSyncOfflineContract.FORBIDDEN_UPLOAD_TABLES) {
                "GtrPowerSyncSchema must not include ledger table: $name"
            }
        }
    }
}
