-- Exported from the hosted project's supabase_migrations.schema_migrations (20260908129000 pos_legacy_fk_indexes).
-- Source of record for what production ran; see supabase/live-history/README.md.

-- Close remaining legacy POS foreign-key index gaps found by the full POS audit.
CREATE INDEX IF NOT EXISTS pos_cart_lines_parent_line_idx
  ON public.pos_cart_lines (parent_line_id);

CREATE INDEX IF NOT EXISTS pos_cart_lines_stock_item_idx
  ON public.pos_cart_lines (stock_item_id);

CREATE INDEX IF NOT EXISTS pos_cart_lines_uom_idx
  ON public.pos_cart_lines (uom_id);

CREATE INDEX IF NOT EXISTS pos_offline_receipts_warehouse_idx
  ON public.pos_offline_sale_receipts (warehouse_id);

CREATE INDEX IF NOT EXISTS pos_quotation_lines_parent_line_idx
  ON public.pos_quotation_lines (parent_line_id);

CREATE INDEX IF NOT EXISTS pos_quotation_lines_stock_item_idx
  ON public.pos_quotation_lines (stock_item_id);

CREATE INDEX IF NOT EXISTS pos_quotation_lines_uom_idx
  ON public.pos_quotation_lines (uom_id);

CREATE INDEX IF NOT EXISTS pos_quotations_converted_cart_idx
  ON public.pos_quotations (converted_cart_id);

CREATE INDEX IF NOT EXISTS pos_quotations_converted_invoice_idx
  ON public.pos_quotations (converted_invoice_id);

CREATE INDEX IF NOT EXISTS pos_quotations_source_cart_idx
  ON public.pos_quotations (source_cart_id);

CREATE INDEX IF NOT EXISTS pos_quotations_warehouse_idx
  ON public.pos_quotations (warehouse_id);

CREATE INDEX IF NOT EXISTS pos_scan_sessions_scanner_user_idx
  ON public.pos_scan_sessions (scanner_user_id);
