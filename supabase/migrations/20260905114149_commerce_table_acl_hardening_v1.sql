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