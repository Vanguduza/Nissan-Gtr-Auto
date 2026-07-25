-- Staff ops notifications + CoA cash sub-accounts + online dispatch auto-assign / sales prep.
-- No ZIMRA / tax accounts. Fail soft on checkout / assign paths.

-- ---------------------------------------------------------------------------
-- CoA: petty cash, cash till, online clearing (assets only)
-- ---------------------------------------------------------------------------
INSERT INTO public.chart_of_accounts (code, name, account_type) VALUES
  ('1110', 'Petty Cash', 'asset'),
  ('1120', 'Cash Sales Till', 'asset'),
  ('1130', 'Online Payment Clearing', 'asset')
ON CONFLICT (code) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Domain event for SMS catalog (opt-in managers; fail-closed without gateway)
-- ---------------------------------------------------------------------------
INSERT INTO public.sms_event_catalog (code, description, category, priority)
VALUES (
  'order_prep_needed',
  'Online dispatch order needs sales prep',
  'sales',
  'normal'
)
ON CONFLICT (code) DO NOTHING;

-- ---------------------------------------------------------------------------
-- staff_ops_notifications
-- ---------------------------------------------------------------------------
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_type WHERE typname = 'staff_ops_notification_kind'
  ) THEN
    CREATE TYPE public.staff_ops_notification_kind AS ENUM (
      'sales_prep',
      'driver_assigned'
    );
  END IF;
END $$;

CREATE TABLE IF NOT EXISTS public.staff_ops_notifications (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  kind public.staff_ops_notification_kind NOT NULL,
  sales_invoice_id UUID REFERENCES public.sales_invoices (id) ON DELETE CASCADE,
  delivery_job_id UUID REFERENCES public.delivery_jobs (id) ON DELETE SET NULL,
  recipient_user_id UUID NOT NULL REFERENCES public.profiles (id) ON DELETE CASCADE,
  title TEXT NOT NULL,
  body TEXT NOT NULL,
  read_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (kind, sales_invoice_id, recipient_user_id)
);

CREATE INDEX IF NOT EXISTS staff_ops_notifications_recipient_unread_idx
  ON public.staff_ops_notifications (recipient_user_id, created_at DESC)
  WHERE read_at IS NULL;

CREATE INDEX IF NOT EXISTS staff_ops_notifications_invoice_idx
  ON public.staff_ops_notifications (sales_invoice_id);

ALTER TABLE public.staff_ops_notifications ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS staff_ops_notifications_select_own ON public.staff_ops_notifications;
CREATE POLICY staff_ops_notifications_select_own
  ON public.staff_ops_notifications FOR SELECT TO authenticated
  USING (
    recipient_user_id = auth.uid()
    AND public.has_staff_role(
      ARRAY['admin', 'sales', 'warehouse', 'dispatcher']::public.staff_role[]
    )
  );

DROP POLICY IF EXISTS staff_ops_notifications_update_own_read ON public.staff_ops_notifications;
CREATE POLICY staff_ops_notifications_update_own_read
  ON public.staff_ops_notifications FOR UPDATE TO authenticated
  USING (recipient_user_id = auth.uid())
  WITH CHECK (recipient_user_id = auth.uid());

REVOKE ALL ON TABLE public.staff_ops_notifications FROM PUBLIC;
GRANT SELECT, UPDATE ON TABLE public.staff_ops_notifications TO authenticated;
GRANT ALL ON TABLE public.staff_ops_notifications TO service_role;

-- Block direct inserts from clients (DEFINER helpers only)
CREATE OR REPLACE FUNCTION public.guard_staff_ops_notification_insert()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  IF COALESCE(current_setting('app.staff_ops_notify', true), '') = '1' THEN
    RETURN NEW;
  END IF;
  RAISE EXCEPTION 'staff_ops_notifications: use notify RPCs';
END;
$$;

DROP TRIGGER IF EXISTS staff_ops_notifications_no_direct_insert
  ON public.staff_ops_notifications;
CREATE TRIGGER staff_ops_notifications_no_direct_insert
  BEFORE INSERT ON public.staff_ops_notifications
  FOR EACH ROW EXECUTE PROCEDURE public.guard_staff_ops_notification_insert();

CREATE OR REPLACE FUNCTION public._staff_ops_notify_begin()
RETURNS void
LANGUAGE sql
AS $$
  SELECT set_config('app.staff_ops_notify', '1', true);
$$;

CREATE OR REPLACE FUNCTION public._online_dispatch_auto_begin()
RETURNS void
LANGUAGE sql
AS $$
  SELECT set_config('app.online_dispatch_auto', '1', true);
$$;

CREATE OR REPLACE FUNCTION public._online_dispatch_auto_active()
RETURNS boolean
LANGUAGE sql
STABLE
AS $$
  SELECT COALESCE(current_setting('app.online_dispatch_auto', true), '') = '1';
$$;

-- Allow system auto-ship to call logistics RPCs without escalating JWT roles.
CREATE OR REPLACE FUNCTION public._require_logistics_staff()
RETURNS void
LANGUAGE plpgsql
STABLE
AS $$
BEGIN
  IF public._online_dispatch_auto_active() THEN
    RETURN;
  END IF;
  IF NOT (
    auth.role() = 'service_role'
    OR public.has_staff_role(
      ARRAY['admin', 'warehouse', 'dispatcher', 'sales']::public.staff_role[]
    )
  ) THEN
    RAISE EXCEPTION 'warehouse, dispatcher, sales, or admin role required';
  END IF;
END;
$$;

CREATE OR REPLACE FUNCTION public._require_dispatcher_staff()
RETURNS void
LANGUAGE plpgsql
STABLE
AS $$
BEGIN
  IF public._online_dispatch_auto_active() THEN
    RETURN;
  END IF;
  IF NOT (
    auth.role() = 'service_role'
    OR public.has_staff_role(
      ARRAY['admin', 'warehouse', 'dispatcher']::public.staff_role[]
    )
  ) THEN
    RAISE EXCEPTION 'dispatcher, warehouse, or admin role required';
  END IF;
END;
$$;

-- ---------------------------------------------------------------------------
-- Helpers
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public._invoice_is_storefront_dispatch(p_invoice_id UUID)
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SET search_path = public
AS $$
  SELECT EXISTS (
    SELECT 1
    FROM public.sales_invoices si
    JOIN public.pos_carts c ON c.id = si.cart_id
    WHERE si.id = p_invoice_id
      AND si.doc_type = 'invoice'
      AND si.status = 'posted'
      AND si.fulfillment_mode = 'dispatch'
      AND c.channel = 'storefront'
  );
$$;

CREATE OR REPLACE FUNCTION public._notify_sales_prep(p_invoice_id UUID)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_doc TEXT;
  v_title TEXT;
  v_body TEXT;
  v_uid UUID;
BEGIN
  IF NOT public._invoice_is_storefront_dispatch(p_invoice_id) THEN
    RETURN;
  END IF;

  SELECT COALESCE(si.document_number, si.id::text)
  INTO v_doc
  FROM public.sales_invoices si
  WHERE si.id = p_invoice_id;

  v_title := 'Prepare online order';
  v_body := format(
    'Online dispatch order %s is ready for pick/pack. Open Sales prep / Logistics.',
    v_doc
  );

  PERFORM public._staff_ops_notify_begin();

  FOR v_uid IN
    SELECT DISTINCT sr.user_id
    FROM public.staff_roles sr
    WHERE sr.role IN ('sales', 'admin')
  LOOP
    INSERT INTO public.staff_ops_notifications (
      kind, sales_invoice_id, recipient_user_id, title, body
    )
    VALUES (
      'sales_prep', p_invoice_id, v_uid, v_title, v_body
    )
    ON CONFLICT (kind, sales_invoice_id, recipient_user_id) DO NOTHING;
  END LOOP;

  BEGIN
    PERFORM public.emit_domain_event(
      'order_prep_needed',
      'invoice:prep:' || p_invoice_id::text,
      jsonb_build_object('invoice_id', p_invoice_id, 'document_number', v_doc),
      NULL,
      v_body
    );
  EXCEPTION
    WHEN OTHERS THEN
      NULL; -- fail closed: never block order path
  END;
END;
$$;

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
    RAISE EXCEPTION 'posted sales invoice required';
  END IF;
  IF v_inv.fulfillment_mode <> 'dispatch' THEN
    RAISE EXCEPTION 'pick lists only for dispatch fulfillment invoices';
  END IF;

  SELECT pl.id INTO v_pick
  FROM public.pick_lists pl
  WHERE pl.sales_invoice_id = p_invoice_id
    AND pl.status IN ('draft', 'done')
  ORDER BY pl.created_at DESC
  LIMIT 1;

  IF v_pick IS NOT NULL THEN
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
    RETURN NULL;
  END IF;

  RETURN v_pick;
END;
$$;

CREATE OR REPLACE FUNCTION public._try_auto_assign_delivery_job(p_delivery_job_id UUID)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_job public.delivery_jobs%ROWTYPE;
  v_driver UUID;
  v_pickup_lat DOUBLE PRECISION;
  v_pickup_lng DOUBLE PRECISION;
BEGIN
  SELECT * INTO v_job
  FROM public.delivery_jobs
  WHERE id = p_delivery_job_id
  FOR UPDATE;
  IF NOT FOUND THEN
    RETURN NULL;
  END IF;
  IF v_job.assignee_user_id IS NOT NULL THEN
    RETURN v_job.assignee_user_id;
  END IF;
  IF v_job.status IN ('completed', 'failed') THEN
    RETURN NULL;
  END IF;

  v_pickup_lat := v_job.pickup_lat;
  v_pickup_lng := v_job.pickup_lng;

  SELECT dp.user_id INTO v_driver
  FROM public.driver_presence dp
  JOIN public.staff_roles sr
    ON sr.user_id = dp.user_id AND sr.role = 'driver'
  WHERE dp.status IN ('available', 'on_duty')
    AND public._driver_shift_active(dp.shift_starts_at, dp.shift_ends_at, now())
    AND public._driver_open_job_count(dp.user_id) < dp.capacity
  ORDER BY
    (public._haversine_meters(
      v_pickup_lat, v_pickup_lng, dp.last_lat, dp.last_lng
    ) IS NULL) ASC,
    public._haversine_meters(
      v_pickup_lat, v_pickup_lng, dp.last_lat, dp.last_lng
    ) ASC NULLS LAST,
    dp.last_seen_at DESC NULLS LAST
  LIMIT 1;

  IF v_driver IS NULL THEN
    RETURN NULL;
  END IF;

  UPDATE public.delivery_jobs
  SET assignee_user_id = v_driver, updated_at = now()
  WHERE id = p_delivery_job_id;

  RETURN v_driver;
EXCEPTION
  WHEN OTHERS THEN
    RETURN NULL; -- fail soft
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

  -- Job already exists?
  SELECT dj.id INTO v_existing
  FROM public.delivery_jobs dj
  JOIN public.delivery_notes dn ON dn.id = dj.delivery_note_id
  WHERE dn.sales_invoice_id = v_inv_id
  ORDER BY dj.created_at DESC
  LIMIT 1;

  IF v_existing IS NOT NULL THEN
    PERFORM public._try_auto_assign_delivery_job(v_existing);
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

  -- Staff already authenticated via confirm_pick_lines
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

  RETURN v_job;
EXCEPTION
  WHEN OTHERS THEN
    RETURN NULL; -- fail soft — prep still succeeded
END;
$$;

CREATE OR REPLACE FUNCTION public._finalize_online_dispatch_order(p_invoice_id UUID)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_pick UUID;
BEGIN
  IF NOT public._invoice_is_storefront_dispatch(p_invoice_id) THEN
    RETURN NULL;
  END IF;

  BEGIN
    v_pick := public._ensure_pick_list_for_invoice(p_invoice_id);
  EXCEPTION
    WHEN OTHERS THEN
      v_pick := NULL;
  END;

  BEGIN
    PERFORM public._notify_sales_prep(p_invoice_id);
  EXCEPTION
    WHEN OTHERS THEN
      NULL;
  END;

  RETURN v_pick;
END;
$$;

-- ---------------------------------------------------------------------------
-- Staff-facing RPCs
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.list_staff_ops_notifications(p_limit INTEGER DEFAULT 40)
RETURNS SETOF public.staff_ops_notifications
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_lim INTEGER := greatest(1, least(COALESCE(p_limit, 40), 100));
BEGIN
  IF auth.uid() IS NULL THEN
    RAISE EXCEPTION 'authentication required';
  END IF;
  IF NOT public.has_staff_role(
    ARRAY['admin', 'sales', 'warehouse', 'dispatcher']::public.staff_role[]
  ) THEN
    RAISE EXCEPTION 'staff role required';
  END IF;

  RETURN QUERY
  SELECT n.*
  FROM public.staff_ops_notifications n
  WHERE n.recipient_user_id = auth.uid()
  ORDER BY n.created_at DESC
  LIMIT v_lim;
END;
$$;

CREATE OR REPLACE FUNCTION public.mark_staff_ops_notification_read(p_id UUID)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  IF auth.uid() IS NULL THEN
    RAISE EXCEPTION 'authentication required';
  END IF;

  UPDATE public.staff_ops_notifications
  SET read_at = COALESCE(read_at, now())
  WHERE id = p_id AND recipient_user_id = auth.uid();

  IF NOT FOUND THEN
    RAISE EXCEPTION 'notification not found';
  END IF;
  RETURN p_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.list_online_prep_queue(p_limit INTEGER DEFAULT 50)
RETURNS TABLE (
  invoice_id UUID,
  document_number TEXT,
  posted_at TIMESTAMPTZ,
  currency public.currency_code,
  total NUMERIC,
  pick_list_id UUID,
  pick_status public.pick_list_status,
  delivery_job_id UUID,
  delivery_job_status public.delivery_job_status,
  assignee_user_id UUID
)
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_lim INTEGER := greatest(1, least(COALESCE(p_limit, 50), 100));
BEGIN
  IF NOT public.has_staff_role(
    ARRAY['admin', 'sales', 'warehouse', 'dispatcher']::public.staff_role[]
  ) THEN
    RAISE EXCEPTION 'staff role required';
  END IF;

  RETURN QUERY
  SELECT
    si.id,
    si.document_number,
    si.posted_at,
    si.currency,
    si.total,
    pl.id,
    pl.status,
    dj.id,
    dj.status,
    dj.assignee_user_id
  FROM public.sales_invoices si
  JOIN public.pos_carts c ON c.id = si.cart_id AND c.channel = 'storefront'
  LEFT JOIN LATERAL (
    SELECT p.*
    FROM public.pick_lists p
    WHERE p.sales_invoice_id = si.id
      AND p.status <> 'cancelled'
    ORDER BY p.created_at DESC
    LIMIT 1
  ) pl ON true
  LEFT JOIN LATERAL (
    SELECT j.*
    FROM public.delivery_jobs j
    JOIN public.delivery_notes dn ON dn.id = j.delivery_note_id
    WHERE dn.sales_invoice_id = si.id
    ORDER BY j.created_at DESC
    LIMIT 1
  ) dj ON true
  WHERE si.doc_type = 'invoice'
    AND si.status = 'posted'
    AND si.fulfillment_mode = 'dispatch'
    AND (
      pl.id IS NULL
      OR pl.status = 'draft'
      OR dj.id IS NULL
      OR dj.assignee_user_id IS NULL
    )
  ORDER BY si.posted_at DESC NULLS LAST
  LIMIT v_lim;
END;
$$;

REVOKE ALL ON FUNCTION public._invoice_is_storefront_dispatch(UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION public._notify_sales_prep(UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION public._ensure_pick_list_for_invoice(UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION public._try_auto_assign_delivery_job(UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION public._auto_ship_online_dispatch_after_pick(UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION public._finalize_online_dispatch_order(UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION public._staff_ops_notify_begin() FROM PUBLIC;
REVOKE ALL ON FUNCTION public.list_staff_ops_notifications(INTEGER) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.mark_staff_ops_notification_read(UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.list_online_prep_queue(INTEGER) FROM PUBLIC;

GRANT EXECUTE ON FUNCTION public.list_staff_ops_notifications(INTEGER)
  TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.mark_staff_ops_notification_read(UUID)
  TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.list_online_prep_queue(INTEGER)
  TO authenticated, service_role;
