-- Exported from the hosted project's supabase_migrations.schema_migrations (20260905114149 commerce_table_acl_hardening_v1).
-- Source of record for what production ran; see supabase/live-history/README.md.

REVOKE ALL ON TABLE
  public.commerce_orders,
  public.inventory_reservations,
  public.commerce_outbox,
  public.commerce_payment_exceptions,
  public.commerce_manual_payment_requests
FROM anon, authenticated;

GRANT SELECT ON TABLE
  public.commerce_orders,
  public.inventory_reservations,
  public.commerce_outbox,
  public.commerce_payment_exceptions,
  public.commerce_manual_payment_requests
TO authenticated;

GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE
  public.commerce_orders,
  public.inventory_reservations,
  public.commerce_outbox,
  public.commerce_payment_exceptions,
  public.commerce_manual_payment_requests
TO service_role;

COMMENT ON TABLE public.commerce_orders IS
  'Canonical customer commerce order. Authenticated access is SELECT-only at table ACL level and row-scoped by RLS; all mutations are RPC/service controlled.';
