-- Phase 13: payment mutation guards (AuthZ / over-allocate / immutable posted)

CREATE OR REPLACE FUNCTION public.guard_payment_entry_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  IF public._payments_rpc_active() THEN
    IF TG_OP = 'DELETE' THEN
      RETURN OLD;
    END IF;
    RETURN NEW;
  END IF;

  IF TG_OP = 'INSERT' THEN
    RAISE EXCEPTION 'payment_entries: use create_payment_entry RPC';
  ELSIF TG_OP = 'DELETE' THEN
    RAISE EXCEPTION 'payment_entries: direct delete not allowed';
  ELSIF TG_OP = 'UPDATE' THEN
    IF OLD.status IN ('posted', 'cancelled') THEN
      RAISE EXCEPTION 'payment_entries: posted/cancelled payments are immutable (use cancel_payment_entry)';
    END IF;
    RAISE EXCEPTION 'payment_entries: use payment RPCs for updates';
  END IF;

  RETURN NULL;
END;
$$;

CREATE OR REPLACE FUNCTION public.guard_payment_allocation_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
  v_status public.payment_entry_status;
BEGIN
  IF public._payments_rpc_active() THEN
    IF TG_OP = 'DELETE' THEN
      RETURN OLD;
    END IF;
    RETURN NEW;
  END IF;

  SELECT pe.status INTO v_status
  FROM public.payment_entries pe
  WHERE pe.id = COALESCE(NEW.payment_entry_id, OLD.payment_entry_id);

  IF v_status IS DISTINCT FROM 'draft' THEN
    RAISE EXCEPTION 'payment_allocations: parent must be draft (status=%)', v_status;
  END IF;

  RAISE EXCEPTION 'payment_allocations: use allocate_payment RPC';
END;
$$;

CREATE OR REPLACE FUNCTION public.guard_store_credit_account_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  IF public._payments_rpc_active() THEN
    IF TG_OP = 'DELETE' THEN
      RETURN OLD;
    END IF;
    RETURN NEW;
  END IF;

  RAISE EXCEPTION 'store_credit_accounts: use store credit RPCs only';
END;
$$;

DROP TRIGGER IF EXISTS payment_entries_mutation_guard ON public.payment_entries;
CREATE TRIGGER payment_entries_mutation_guard
  BEFORE INSERT OR UPDATE OR DELETE ON public.payment_entries
  FOR EACH ROW
  EXECUTE PROCEDURE public.guard_payment_entry_mutation();

DROP TRIGGER IF EXISTS payment_allocations_mutation_guard ON public.payment_allocations;
CREATE TRIGGER payment_allocations_mutation_guard
  BEFORE INSERT OR UPDATE OR DELETE ON public.payment_allocations
  FOR EACH ROW
  EXECUTE PROCEDURE public.guard_payment_allocation_mutation();

DROP TRIGGER IF EXISTS store_credit_accounts_mutation_guard ON public.store_credit_accounts;
CREATE TRIGGER store_credit_accounts_mutation_guard
  BEFORE INSERT OR UPDATE OR DELETE ON public.store_credit_accounts
  FOR EACH ROW
  EXECUTE PROCEDURE public.guard_store_credit_account_mutation();

REVOKE ALL ON FUNCTION public.guard_payment_entry_mutation() FROM PUBLIC;
REVOKE ALL ON FUNCTION public.guard_payment_allocation_mutation() FROM PUBLIC;
REVOKE ALL ON FUNCTION public.guard_store_credit_account_mutation() FROM PUBLIC;
