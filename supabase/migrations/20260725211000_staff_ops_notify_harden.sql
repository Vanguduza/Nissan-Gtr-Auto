-- Harden staff_ops_notifications UPDATE + clear online_dispatch auto GUCs after use.
-- Follow-up to 20260725210000 (security-reviewer warnings).

-- Recipients may only flip read_at (not rewrite title/body/kind/FKs).
REVOKE UPDATE ON TABLE public.staff_ops_notifications FROM authenticated;
GRANT UPDATE (read_at) ON TABLE public.staff_ops_notifications TO authenticated;

DROP POLICY IF EXISTS staff_ops_notifications_update_own_read ON public.staff_ops_notifications;
CREATE POLICY staff_ops_notifications_update_own_read
  ON public.staff_ops_notifications FOR UPDATE TO authenticated
  USING (
    recipient_user_id = auth.uid()
    AND public.has_staff_role(
      ARRAY['admin', 'sales', 'warehouse', 'dispatcher']::public.staff_role[]
    )
  )
  WITH CHECK (
    recipient_user_id = auth.uid()
    AND public.has_staff_role(
      ARRAY['admin', 'sales', 'warehouse', 'dispatcher']::public.staff_role[]
    )
  );

-- Explicit deny of GUC setters / internals to API roles (defense in depth).
REVOKE ALL ON FUNCTION public._online_dispatch_auto_begin() FROM PUBLIC;
REVOKE ALL ON FUNCTION public._online_dispatch_auto_begin() FROM anon, authenticated;
REVOKE ALL ON FUNCTION public._online_dispatch_auto_active() FROM PUBLIC;
REVOKE ALL ON FUNCTION public._online_dispatch_auto_active() FROM anon, authenticated;
REVOKE ALL ON FUNCTION public._staff_ops_notify_begin() FROM PUBLIC;
REVOKE ALL ON FUNCTION public._staff_ops_notify_begin() FROM anon, authenticated;

CREATE OR REPLACE FUNCTION public._online_dispatch_auto_clear()
RETURNS void
LANGUAGE sql
AS $$
  SELECT set_config('app.online_dispatch_auto', '', true);
$$;

REVOKE ALL ON FUNCTION public._online_dispatch_auto_clear() FROM PUBLIC;
REVOKE ALL ON FUNCTION public._online_dispatch_auto_clear() FROM anon, authenticated;

CREATE OR REPLACE FUNCTION public._ensure_pick_list_for_invoice(p_invoice_id UUID)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_inv public.sales_invoices%ROWTYPE;
  v_pick UUID;
  v_line RECORD;
  v_bin UUID;
  v_open NUMERIC;
  v_any BOOLEAN := false;
BEGIN
  PERFORM public._online_dispatch_auto_begin();
  PERFORM public._logistics_begin_rpc();

  SELECT * INTO v_inv
  FROM public.sales_invoices
  WHERE id = p_invoice_id AND doc_type = 'invoice' AND status = 'posted'
  FOR UPDATE;
  IF NOT FOUND THEN
    PERFORM public._online_dispatch_auto_clear();
    RAISE EXCEPTION 'posted sales invoice required';
  END IF;
  IF v_inv.fulfillment_mode <> 'dispatch' THEN
    PERFORM public._online_dispatch_auto_clear();
    RAISE EXCEPTION 'pick lists only for dispatch fulfillment invoices';
  END IF;

  SELECT pl.id INTO v_pick
  FROM public.pick_lists pl
  WHERE pl.sales_invoice_id = p_invoice_id
    AND pl.status IN ('draft', 'done')
  ORDER BY pl.created_at DESC
  LIMIT 1;

  IF v_pick IS NOT NULL THEN
    PERFORM public._online_dispatch_auto_clear();
    RETURN v_pick;
  END IF;

  INSERT INTO public.pick_lists (
    document_number, sales_invoice_id, warehouse_id, status, created_by
  )
  VALUES (
    public.next_series_value('PL-'),
    p_invoice_id,
    v_inv.warehouse_id,
    'draft',
    auth.uid()
  )
  RETURNING id INTO v_pick;

  FOR v_line IN
    SELECT *
    FROM public.sales_invoice_lines
    WHERE invoice_id = p_invoice_id
      AND NOT is_core_charge
      AND qty_base > qty_fulfilled
    ORDER BY created_at
  LOOP
    v_open := public._invoice_line_open_qty_base(v_line.id);
    IF v_open <= 0 THEN
      CONTINUE;
    END IF;
    v_any := true;

    SELECT sl.bin_id INTO v_bin
    FROM public.stock_levels sl
    WHERE sl.stock_item_id = v_line.stock_item_id
      AND sl.warehouse_id = v_inv.warehouse_id;

    INSERT INTO public.pick_list_lines (
      pick_list_id, sales_invoice_line_id, stock_item_id, uom_id,
      qty_requested, qty_base_requested, suggested_bin_id
    )
    VALUES (
      v_pick, v_line.id, v_line.stock_item_id, v_line.uom_id,
      v_open, v_open, v_bin
    );
  END LOOP;

  IF NOT v_any THEN
    UPDATE public.pick_lists SET status = 'cancelled', updated_at = now()
    WHERE id = v_pick;
    PERFORM public._online_dispatch_auto_clear();
    RETURN NULL;
  END IF;

  PERFORM public._online_dispatch_auto_clear();
  RETURN v_pick;
EXCEPTION
  WHEN OTHERS THEN
    PERFORM public._online_dispatch_auto_clear();
    RAISE;
END;
$$;

CREATE OR REPLACE FUNCTION public._auto_ship_online_dispatch_after_pick(p_pick_list_id UUID)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_pick public.pick_lists%ROWTYPE;
  v_lines JSONB := '[]'::jsonb;
  v_pl RECORD;
  v_dn UUID;
  v_job UUID;
  v_existing UUID;
  v_driver UUID;
  v_inv_id UUID;
BEGIN
  SELECT * INTO v_pick FROM public.pick_lists WHERE id = p_pick_list_id;
  IF NOT FOUND OR v_pick.status <> 'done' THEN
    RETURN NULL;
  END IF;

  v_inv_id := v_pick.sales_invoice_id;
  IF NOT public._invoice_is_storefront_dispatch(v_inv_id) THEN
    RETURN NULL;
  END IF;

  SELECT dj.id INTO v_existing
  FROM public.delivery_jobs dj
  JOIN public.delivery_notes dn ON dn.id = dj.delivery_note_id
  WHERE dn.sales_invoice_id = v_inv_id
  ORDER BY dj.created_at DESC
  LIMIT 1;

  IF v_existing IS NOT NULL THEN
    v_driver := public._try_auto_assign_delivery_job(v_existing);
    RETURN v_existing;
  END IF;

  FOR v_pl IN
    SELECT *
    FROM public.pick_list_lines
    WHERE pick_list_id = p_pick_list_id
      AND qty_base_picked > 0
  LOOP
    v_lines := v_lines || jsonb_build_array(
      jsonb_build_object(
        'sales_invoice_line_id', v_pl.sales_invoice_line_id,
        'qty', v_pl.qty_picked
      )
    );
  END LOOP;

  IF jsonb_array_length(v_lines) = 0 THEN
    RETURN NULL;
  END IF;

  PERFORM public._online_dispatch_auto_begin();
  PERFORM public._logistics_begin_rpc();

  BEGIN
    v_dn := public.create_delivery_note(v_inv_id, v_lines, p_pick_list_id);
    PERFORM public.submit_delivery_note(v_dn);
    v_job := public.create_delivery_job(v_dn, NULL, NULL, 'Auto after online prep');
    v_driver := public._try_auto_assign_delivery_job(v_job);

    IF v_driver IS NOT NULL THEN
      PERFORM public._staff_ops_notify_begin();
      INSERT INTO public.staff_ops_notifications (
        kind, sales_invoice_id, delivery_job_id, recipient_user_id, title, body
      )
      SELECT
        'driver_assigned',
        v_inv_id,
        v_job,
        sr.user_id,
        'Driver assigned',
        format('Driver assigned for online order job %s', v_job::text)
      FROM public.staff_roles sr
      WHERE sr.role IN ('sales', 'admin', 'dispatcher')
      ON CONFLICT (kind, sales_invoice_id, recipient_user_id) DO NOTHING;
    END IF;

    PERFORM public._online_dispatch_auto_clear();
    RETURN v_job;
  EXCEPTION
    WHEN OTHERS THEN
      PERFORM public._online_dispatch_auto_clear();
      RETURN NULL;
  END;
END;
$$;
