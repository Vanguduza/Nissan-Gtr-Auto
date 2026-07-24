-- Smoke fixes: recon guard GUC bypass, resolve_item_price currency ambiguity, supplier auth uid.

CREATE OR REPLACE FUNCTION public.guard_stock_reconciliation_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  IF TG_OP = 'UPDATE' THEN
    IF OLD.status IN ('posted', 'cancelled') THEN
      IF public._recon_rpc_active()
        AND OLD.status = 'posted'
        AND NEW.status = 'cancelled'
      THEN
        RETURN NEW;
      END IF;
      RAISE EXCEPTION 'stock_reconciliations: posted/cancelled records are immutable (use cancel_stock_reconciliation RPC)';
    END IF;
  END IF;

  IF public._recon_rpc_active() THEN
    IF TG_OP = 'DELETE' THEN
      RETURN OLD;
    END IF;
    RETURN NEW;
  END IF;

  IF TG_OP = 'INSERT' THEN
    IF NEW.status IS DISTINCT FROM 'draft' THEN
      RAISE EXCEPTION 'stock_reconciliations: new rows must be draft; use create_stock_reconciliation_draft';
    END IF;
    RAISE EXCEPTION 'stock_reconciliations: use create_stock_reconciliation_draft RPC';
  ELSIF TG_OP = 'DELETE' THEN
    RAISE EXCEPTION 'stock_reconciliations: direct delete not allowed';
  ELSIF TG_OP = 'UPDATE' THEN
    RAISE EXCEPTION 'stock_reconciliations: use reconciliation RPCs for updates';
  END IF;

  RETURN NULL;
END;
$$;

CREATE OR REPLACE FUNCTION public.guard_stock_reconciliation_line_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
  v_recon_id UUID;
  v_status public.stock_entry_status;
BEGIN
  v_recon_id := COALESCE(NEW.stock_reconciliation_id, OLD.stock_reconciliation_id);
  SELECT r.status INTO v_status
  FROM public.stock_reconciliations r
  WHERE r.id = v_recon_id;

  IF v_status IS NULL THEN
    RAISE EXCEPTION 'stock_reconciliation_lines: parent reconciliation not found';
  END IF;

  IF v_status <> 'draft' THEN
    IF public._recon_rpc_active() AND TG_OP IN ('INSERT', 'UPDATE', 'DELETE') THEN
      IF TG_OP = 'DELETE' THEN
        RETURN OLD;
      END IF;
      RETURN NEW;
    END IF;
    RAISE EXCEPTION 'stock_reconciliation_lines: parent must be draft (status=%)', v_status;
  END IF;

  IF public._recon_rpc_active() THEN
    IF TG_OP = 'DELETE' THEN
      RETURN OLD;
    END IF;
    RETURN NEW;
  END IF;

  RAISE EXCEPTION 'stock_reconciliation_lines: use reconciliation RPCs';
END;
$$;

CREATE OR REPLACE FUNCTION public.resolve_item_price(
  p_customer_id UUID,
  p_stock_item_id UUID
)
RETURNS TABLE (unit_price NUMERIC, core_charge NUMERIC, currency public.currency_code)
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_list UUID;
  v_currency public.currency_code := 'USD';
  v_unit_price NUMERIC;
  v_core_charge NUMERIC;
  v_row_currency public.currency_code;
BEGIN
  IF p_customer_id IS NOT NULL THEN
    SELECT o.unit_price, COALESCE(o.core_charge, 0), c.currency
    INTO v_unit_price, v_core_charge, v_row_currency
    FROM public.customer_price_overrides o
    JOIN public.customers c ON c.id = o.customer_id
    WHERE o.customer_id = p_customer_id AND o.stock_item_id = p_stock_item_id;
    IF FOUND THEN
      unit_price := v_unit_price;
      core_charge := v_core_charge;
      currency := v_row_currency;
      RETURN NEXT;
      RETURN;
    END IF;

    SELECT price_list_id, customers.currency INTO v_list, v_currency
    FROM public.customers WHERE id = p_customer_id;
  END IF;

  IF v_list IS NULL THEN
    SELECT pl.id, pl.currency INTO v_list, v_currency
    FROM public.price_lists pl WHERE pl.is_default AND pl.is_active LIMIT 1;
  END IF;

  SELECT pli.unit_price, pli.core_charge, pl.currency
  INTO v_unit_price, v_core_charge, v_row_currency
  FROM public.price_list_items pli
  JOIN public.price_lists pl ON pl.id = pli.price_list_id
  WHERE pli.price_list_id = v_list AND pli.stock_item_id = p_stock_item_id;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'no price for stock item % on price list', p_stock_item_id;
  END IF;

  unit_price := v_unit_price;
  core_charge := v_core_charge;
  currency := v_row_currency;
  RETURN NEXT;
END;
$$;

CREATE OR REPLACE FUNCTION public.current_supplier_id()
RETURNS UUID
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT s.id
  FROM public.suppliers s
  WHERE s.profile_id = COALESCE(
    auth.uid(),
    NULLIF(current_setting('request.jwt.claim.sub', true), '')::uuid
  )
    AND s.is_active
  LIMIT 1;
$$;
