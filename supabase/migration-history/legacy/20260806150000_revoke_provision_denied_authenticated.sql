-- Security follow-up (39f6e642): _storefront_customer_provision_denied must not be
-- callable by API roles. It is only invoked from SECURITY DEFINER RPCs / triggers
-- (owner rights) and may be granted to service_role for admin tooling.
-- Leaves 20260806140000 unchanged for already-applied local/remote DBs.

REVOKE ALL ON FUNCTION public._storefront_customer_provision_denied(UUID)
  FROM PUBLIC;
REVOKE ALL ON FUNCTION public._storefront_customer_provision_denied(UUID)
  FROM anon, authenticated;

GRANT EXECUTE ON FUNCTION public._storefront_customer_provision_denied(UUID)
  TO service_role;

COMMENT ON FUNCTION public._storefront_customer_provision_denied(UUID) IS
  'True when uid must not receive a retail customers row (staff_roles, employees, '
  'profiles.is_staff, or HR onboarding Admin create). '
  'Internal helper: EXECUTE for service_role only; DEFINER callers use owner rights.';
