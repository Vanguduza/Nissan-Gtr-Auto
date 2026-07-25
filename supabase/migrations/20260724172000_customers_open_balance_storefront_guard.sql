-- Allow open_balance adjustments under storefront RPC flag (checkout / customer return).
-- Contact-only self-service still cannot touch credit_limit / credit_hold / etc.

CREATE OR REPLACE FUNCTION public.customers_protect_privileged_columns()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  IF auth.role() = 'service_role' OR public.is_staff() THEN
    RETURN NEW;
  END IF;

  -- SECURITY DEFINER storefront/return wrappers may adjust open_balance only
  IF public._storefront_rpc_active() THEN
    IF NEW.profile_id IS DISTINCT FROM OLD.profile_id
       OR NEW.price_list_id IS DISTINCT FROM OLD.price_list_id
       OR NEW.credit_limit IS DISTINCT FROM OLD.credit_limit
       OR NEW.credit_hold IS DISTINCT FROM OLD.credit_hold
       OR NEW.currency IS DISTINCT FROM OLD.currency
       OR NEW.id IS DISTINCT FROM OLD.id
    THEN
      RAISE EXCEPTION 'storefront path may not change commercial control fields';
    END IF;
    RETURN NEW;
  END IF;

  -- Self-service: contact + receipt prefs only (not open_balance)
  IF NEW.profile_id IS DISTINCT FROM OLD.profile_id
     OR NEW.price_list_id IS DISTINCT FROM OLD.price_list_id
     OR NEW.credit_limit IS DISTINCT FROM OLD.credit_limit
     OR NEW.credit_hold IS DISTINCT FROM OLD.credit_hold
     OR NEW.open_balance IS DISTINCT FROM OLD.open_balance
     OR NEW.currency IS DISTINCT FROM OLD.currency
     OR NEW.id IS DISTINCT FROM OLD.id
  THEN
    RAISE EXCEPTION 'customers may only update contact and receipt preference fields';
  END IF;

  RETURN NEW;
END;
$$;
