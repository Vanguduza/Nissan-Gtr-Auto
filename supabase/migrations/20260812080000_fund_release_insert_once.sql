-- H8: fund-release insert-once — never rewrite amount / amount_minor on conflict.
-- Restores ON CONFLICT DO NOTHING (as in 20260812010000) while keeping amount_minor dual-write
-- from 20260812030000. Existing release id is resolved for domain-event payloads only.

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
  ON CONFLICT (purchase_order_id) DO NOTHING
  RETURNING id INTO v_release;

  -- Conflict: keep original money columns; only resolve id for event payload / callers.
  IF v_release IS NULL THEN
    SELECT id INTO v_release
    FROM public.procurement_fund_releases
    WHERE purchase_order_id = p_purchase_order_id;
  END IF;

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

COMMENT ON FUNCTION public.approve_purchase_order(UUID) IS
  'Approve submitted PO; insert-once fund release (ON CONFLICT DO NOTHING — never rewrite amount/amount_minor).';

REVOKE ALL ON FUNCTION public.approve_purchase_order(UUID) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.approve_purchase_order(UUID) TO authenticated, service_role;
