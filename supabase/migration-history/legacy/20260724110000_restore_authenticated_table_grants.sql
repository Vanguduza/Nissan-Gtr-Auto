-- Restore table GRANTs that RLS policies assume for authenticated / service_role.
--
-- Root cause: ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public only
-- granted TRUNCATE/REFERENCES/TRIGGER/MAINTAIN (Dxtm) — not SELECT/DML.
-- Tables created by migrations therefore had RLS policies but no SELECT privilege,
-- so SET ROLE / set_config('role','authenticated') failed with:
--   permission denied for table profiles
-- Later domains (procurement, HR, RFQ) granted DML explicitly; early tables did not.
--
-- Writes that mutation guards / SECURITY DEFINER RPCs own stay SELECT-only here.

-- Future tables created by postgres: at least SELECT for API roles
ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public
  GRANT SELECT ON TABLES TO authenticated, service_role;

-- Readable under RLS (all public base tables)
GRANT SELECT ON ALL TABLES IN SCHEMA public TO authenticated, service_role;

-- service_role: full DML for Edge / admin (bypasses RLS but still needs grants)
GRANT INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO service_role;

-- ---------------------------------------------------------------------------
-- profiles / staff_roles (Phase 2 auth)
-- ---------------------------------------------------------------------------
GRANT INSERT ON TABLE public.profiles TO authenticated;
REVOKE UPDATE ON TABLE public.profiles FROM authenticated;
GRANT UPDATE (full_name, updated_at) ON TABLE public.profiles TO authenticated;
-- staff_roles: SELECT only — writes via assign_staff_role / revoke_staff_role RPCs

-- ---------------------------------------------------------------------------
-- Staff-writable reference / master data (RLS policies, no mutation guards)
-- ---------------------------------------------------------------------------
GRANT INSERT, UPDATE, DELETE ON TABLE public.customers TO authenticated;
GRANT INSERT, UPDATE, DELETE ON TABLE public.price_lists TO authenticated;
GRANT INSERT, UPDATE, DELETE ON TABLE public.price_list_items TO authenticated;
GRANT INSERT, UPDATE, DELETE ON TABLE public.customer_price_overrides TO authenticated;

GRANT INSERT, UPDATE, DELETE ON TABLE public.warehouses TO authenticated;
GRANT INSERT, UPDATE, DELETE ON TABLE public.stock_items TO authenticated;
GRANT INSERT, UPDATE, DELETE ON TABLE public.stock_levels TO authenticated;
GRANT INSERT, UPDATE, DELETE ON TABLE public.inventory_qr_codes TO authenticated;

GRANT INSERT, UPDATE, DELETE ON TABLE public.vehicle_master TO authenticated;
GRANT INSERT, UPDATE, DELETE ON TABLE public.pnc_categories TO authenticated;
GRANT INSERT, UPDATE, DELETE ON TABLE public.part_fitment TO authenticated;
GRANT INSERT, UPDATE, DELETE ON TABLE public.oe_cross_refs TO authenticated;

GRANT INSERT, UPDATE, DELETE ON TABLE public.chart_of_accounts TO authenticated;
GRANT INSERT, UPDATE, DELETE ON TABLE public.accounting_periods TO authenticated;
GRANT INSERT, UPDATE, DELETE ON TABLE public.naming_series TO authenticated;
GRANT INSERT, UPDATE, DELETE ON TABLE public.app_settings TO authenticated;

GRANT INSERT, UPDATE, DELETE ON TABLE public.uoms TO authenticated;
GRANT INSERT, UPDATE, DELETE ON TABLE public.item_uom_conversions TO authenticated;

GRANT INSERT, UPDATE, DELETE ON TABLE public.sms_event_catalog TO authenticated;
GRANT INSERT, UPDATE, DELETE ON TABLE public.manager_sms_preferences TO authenticated;

-- ---------------------------------------------------------------------------
-- RPC / guard-owned domains: SELECT only (no new INSERT/UPDATE/DELETE for authenticated)
-- pos_carts, pos_cart_lines, sales_invoices*, journal_*, bank_*, stock_entries*,
-- stock_reconciliations*, warranty_claims, payment_*, store_credit_*, pick_*,
-- delivery_*, domain_events, sms_outbox, contipay_*, paynow_*, receipt_*, forecast_*
-- ---------------------------------------------------------------------------
