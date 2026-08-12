-- E5: dual-write amount_minor / unit_price_minor on PO money paths (never float SoR)
-- Columns added in 20260812020000; this migration keeps them filled on write.

CREATE OR REPLACE FUNCTION public._major_to_minor(p_amount NUMERIC)
RETURNS BIGINT
LANGUAGE sql
IMMUTABLE
AS $$
  SELECT CASE
    WHEN p_amount IS NULL THEN NULL
    ELSE ROUND(p_amount * 100)::BIGINT
  END;
$$;

COMMENT ON FUNCTION public._major_to_minor(NUMERIC) IS
  'Convert NUMERIC major units to BIGINT minor (×100). Dual-write bridge only.';

-- Keep PO line unit_price_minor in sync on insert/update of unit_price
CREATE OR REPLACE FUNCTION public._po_line_dual_write_minor()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  IF NEW.unit_price IS NOT NULL THEN
    NEW.unit_price_minor := public._major_to_minor(NEW.unit_price);
  END IF;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS purchase_order_lines_dual_write_minor ON public.purchase_order_lines;
CREATE TRIGGER purchase_order_lines_dual_write_minor
  BEFORE INSERT OR UPDATE OF unit_price ON public.purchase_order_lines
  FOR EACH ROW
  EXECUTE FUNCTION public._po_line_dual_write_minor();

-- Backfill existing lines
UPDATE public.purchase_order_lines
SET unit_price_minor = public._major_to_minor(unit_price)
WHERE unit_price_minor IS NULL AND unit_price IS NOT NULL;

-- Approve PO: dual-write amount_minor on fund release
CREATE OR REPLACE FUNCTION public.approve_purchase_order(p_purchase_order_id UUID)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_po public.purchase_orders%ROWTYPE;
  v_total NUMERIC;
  v_total_minor BIGINT;
  v_release UUID;
BEGIN
  PERFORM public._procurement_begin_rpc();
  PERFORM public._require_procurement_finance();
  PERFORM public._assert_procurement_period_open();

  SELECT * INTO v_po
  FROM public.purchase_orders
  WHERE id = p_purchase_order_id
  FOR UPDATE;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'purchase order not found: %', p_purchase_order_id;
  END IF;
  IF v_po.status <> 'submitted' THEN
    RAISE EXCEPTION 'only submitted purchase orders can be approved (status=%)', v_po.status;
  END IF;

  UPDATE public.purchase_orders
  SET
    status = 'approved',
    approved_by = auth.uid(),
    approved_at = now(),
    rejected_by = NULL,
    rejected_at = NULL,
    rejection_reason = NULL,
    progress_step = 'approved',
    updated_at = now()
  WHERE id = p_purchase_order_id;

  v_total := public._po_quoted_total(p_purchase_order_id);
  v_total_minor := public._major_to_minor(v_total);

  INSERT INTO public.procurement_fund_releases (
    purchase_order_id,
    requesting_official_id,
    approved_by,
    amount,
    amount_minor,
    currency,
    status,
    notes
  )
  VALUES (
    p_purchase_order_id,
    v_po.created_by,
    auth.uid(),
    v_total,
    v_total_minor,
    v_po.currency,
    'released',
    format('Auto fund release on PO approve for official %s', COALESCE(v_po.created_by::text, 'unknown'))
  )
  ON CONFLICT (purchase_order_id) DO UPDATE
  SET
    amount = EXCLUDED.amount,
    amount_minor = EXCLUDED.amount_minor,
    approved_by = EXCLUDED.approved_by,
    status = 'released',
    notes = EXCLUDED.notes
  RETURNING id INTO v_release;

  UPDATE public.purchase_orders
  SET
    funds_released_at = now(),
    progress_step = 'funds_released',
    updated_at = now()
  WHERE id = p_purchase_order_id;

  PERFORM public.emit_domain_event(
    'po_approved',
    'purchase_order_approved:' || p_purchase_order_id::text,
    jsonb_build_object(
      'purchase_order_id', p_purchase_order_id,
      'document_number', v_po.document_number,
      'currency', v_po.currency,
      'supplier_id', v_po.supplier_id,
      'fund_release_id', v_release,
      'amount', v_total,
      'amount_minor', v_total_minor,
      'requesting_official_id', v_po.created_by
    ),
    auth.uid(),
    format(
      'GTR Auto: PO %s approved — funds released under requesting official',
      COALESCE(v_po.document_number, left(p_purchase_order_id::text, 8))
    )
  );

  PERFORM public.emit_domain_event(
    'procurement_funds_released',
    'procurement_fund_release:' || COALESCE(v_release::text, p_purchase_order_id::text),
    jsonb_build_object(
      'purchase_order_id', p_purchase_order_id,
      'fund_release_id', v_release,
      'requesting_official_id', v_po.created_by,
      'amount', v_total,
      'amount_minor', v_total_minor,
      'currency', v_po.currency
    ),
    auth.uid(),
    format('GTR Auto: procurement funds released for PO %s', COALESCE(v_po.document_number, left(p_purchase_order_id::text, 8)))
  );

  PERFORM public._procurement_end_rpc();
  RETURN p_purchase_order_id;
EXCEPTION
  WHEN OTHERS THEN
    PERFORM public._procurement_end_rpc();
    RAISE;
END;
$$;

REVOKE ALL ON FUNCTION public.approve_purchase_order(UUID) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.approve_purchase_order(UUID) TO authenticated, service_role;

-- Backfill fund releases
UPDATE public.procurement_fund_releases
SET amount_minor = public._major_to_minor(amount)
WHERE amount_minor IS NULL AND amount IS NOT NULL;
