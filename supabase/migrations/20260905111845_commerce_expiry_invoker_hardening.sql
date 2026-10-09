CREATE OR REPLACE FUNCTION public.expire_commerce_checkouts()
RETURNS INTEGER
LANGUAGE sql
SECURITY INVOKER
SET search_path = ''
AS $$
  SELECT private.expire_commerce_checkouts_internal();
$$;

REVOKE ALL ON FUNCTION public.expire_commerce_checkouts() FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.expire_commerce_checkouts() TO service_role;

COMMENT ON FUNCTION public.expire_commerce_checkouts() IS
  'Service-role-only invoker wrapper for checkout reservation expiry. Cron invokes the private implementation directly.';