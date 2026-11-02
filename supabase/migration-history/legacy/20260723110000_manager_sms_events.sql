-- Manager SMS: event catalog, preferences, domain_events, sms_outbox
-- Emit early; gateway send comes later (Phase 13). No ZIMRA/fiscal messages.

CREATE TYPE public.sms_event_priority AS ENUM ('low', 'normal', 'high');
CREATE TYPE public.sms_outbox_status AS ENUM ('pending', 'sending', 'sent', 'failed', 'cancelled');

CREATE TABLE public.sms_event_catalog (
  code TEXT PRIMARY KEY,
  description TEXT NOT NULL,
  category TEXT NOT NULL,
  priority public.sms_event_priority NOT NULL DEFAULT 'normal',
  is_active BOOLEAN NOT NULL DEFAULT true,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE public.manager_sms_preferences (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES public.profiles (id) ON DELETE CASCADE,
  event_code TEXT NOT NULL REFERENCES public.sms_event_catalog (code),
  phone_e164 TEXT NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT false,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (user_id, event_code)
);

CREATE INDEX manager_sms_prefs_event_idx
  ON public.manager_sms_preferences (event_code)
  WHERE enabled = true;

CREATE TABLE public.domain_events (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  event_code TEXT NOT NULL REFERENCES public.sms_event_catalog (code),
  dedupe_key TEXT NOT NULL,
  payload JSONB NOT NULL DEFAULT '{}'::jsonb,
  occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  actor_user_id UUID REFERENCES auth.users (id),
  UNIQUE (event_code, dedupe_key)
);

CREATE INDEX domain_events_occurred_idx ON public.domain_events (occurred_at DESC);
CREATE INDEX domain_events_code_idx ON public.domain_events (event_code);

CREATE TABLE public.sms_outbox (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  domain_event_id UUID NOT NULL REFERENCES public.domain_events (id) ON DELETE CASCADE,
  event_code TEXT NOT NULL REFERENCES public.sms_event_catalog (code),
  recipient_user_id UUID NOT NULL REFERENCES public.profiles (id),
  phone_e164 TEXT NOT NULL,
  body TEXT NOT NULL,
  status public.sms_outbox_status NOT NULL DEFAULT 'pending',
  attempt_count INT NOT NULL DEFAULT 0,
  last_error TEXT,
  provider_message_id TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  sent_at TIMESTAMPTZ,
  UNIQUE (domain_event_id, recipient_user_id)
);

CREATE INDEX sms_outbox_pending_idx
  ON public.sms_outbox (created_at)
  WHERE status = 'pending';

-- Append-only domain_events
CREATE OR REPLACE FUNCTION public.forbid_domain_event_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  RAISE EXCEPTION 'domain_events is append-only';
END;
$$;

CREATE TRIGGER domain_events_no_update
  BEFORE UPDATE ON public.domain_events
  FOR EACH ROW EXECUTE PROCEDURE public.forbid_domain_event_mutation();

CREATE TRIGGER domain_events_no_delete
  BEFORE DELETE ON public.domain_events
  FOR EACH ROW EXECUTE PROCEDURE public.forbid_domain_event_mutation();

-- Record a domain event and enqueue SMS for opted-in managers.
-- Safe before SMS provider exists (rows stay pending).
CREATE OR REPLACE FUNCTION public.emit_domain_event(
  p_event_code TEXT,
  p_dedupe_key TEXT,
  p_payload JSONB DEFAULT '{}'::jsonb,
  p_actor_user_id UUID DEFAULT auth.uid(),
  p_message_body TEXT DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_event_id UUID;
  v_body TEXT;
  v_desc TEXT;
BEGIN
  IF auth.role() = 'authenticated' AND NOT public.is_staff() THEN
    RAISE EXCEPTION 'Only staff or service role may emit domain events';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM public.sms_event_catalog c
    WHERE c.code = p_event_code AND c.is_active
  ) THEN
    RAISE EXCEPTION 'Unknown or inactive event_code: %', p_event_code;
  END IF;

  INSERT INTO public.domain_events (event_code, dedupe_key, payload, actor_user_id)
  VALUES (p_event_code, p_dedupe_key, COALESCE(p_payload, '{}'::jsonb), p_actor_user_id)
  ON CONFLICT (event_code, dedupe_key) DO NOTHING
  RETURNING id INTO v_event_id;

  IF v_event_id IS NULL THEN
    SELECT id INTO v_event_id
    FROM public.domain_events
    WHERE event_code = p_event_code AND dedupe_key = p_dedupe_key;
  END IF;

  SELECT description INTO v_desc FROM public.sms_event_catalog WHERE code = p_event_code;
  v_body := COALESCE(
    p_message_body,
    format('GTR Auto: %s (%s)', v_desc, p_event_code)
  );

  INSERT INTO public.sms_outbox (
    domain_event_id, event_code, recipient_user_id, phone_e164, body
  )
  SELECT
    v_event_id,
    p_event_code,
    p.user_id,
    p.phone_e164,
    v_body
  FROM public.manager_sms_preferences p
  WHERE p.event_code = p_event_code
    AND p.enabled = true
  ON CONFLICT (domain_event_id, recipient_user_id) DO NOTHING;

  RETURN v_event_id;
END;
$$;

REVOKE ALL ON FUNCTION public.emit_domain_event(TEXT, TEXT, JSONB, UUID, TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.emit_domain_event(TEXT, TEXT, JSONB, UUID, TEXT) TO authenticated, service_role;

-- RLS
ALTER TABLE public.sms_event_catalog ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.manager_sms_preferences ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.domain_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.sms_outbox ENABLE ROW LEVEL SECURITY;

CREATE POLICY sms_catalog_select_staff
  ON public.sms_event_catalog FOR SELECT TO authenticated
  USING (public.is_staff());

CREATE POLICY sms_catalog_admin_write
  ON public.sms_event_catalog FOR ALL TO authenticated
  USING (public.has_staff_role(ARRAY['admin']::public.staff_role[]))
  WITH CHECK (public.has_staff_role(ARRAY['admin']::public.staff_role[]));

CREATE POLICY sms_prefs_select_own_or_admin
  ON public.manager_sms_preferences FOR SELECT TO authenticated
  USING (
    user_id = auth.uid()
    OR public.has_staff_role(ARRAY['admin']::public.staff_role[])
  );

CREATE POLICY sms_prefs_upsert_own
  ON public.manager_sms_preferences FOR INSERT TO authenticated
  WITH CHECK (
    user_id = auth.uid()
    AND public.is_staff()
  );

CREATE POLICY sms_prefs_update_own
  ON public.manager_sms_preferences FOR UPDATE TO authenticated
  USING (user_id = auth.uid())
  WITH CHECK (user_id = auth.uid());

CREATE POLICY sms_prefs_admin_all
  ON public.manager_sms_preferences FOR ALL TO authenticated
  USING (public.has_staff_role(ARRAY['admin']::public.staff_role[]))
  WITH CHECK (public.has_staff_role(ARRAY['admin']::public.staff_role[]));

CREATE POLICY domain_events_select_admin_finance
  ON public.domain_events FOR SELECT TO authenticated
  USING (public.has_staff_role(ARRAY['admin', 'finance', 'sales', 'warehouse', 'dispatcher']::public.staff_role[]));

CREATE POLICY domain_events_insert_staff
  ON public.domain_events FOR INSERT TO authenticated
  WITH CHECK (public.is_staff());

CREATE POLICY sms_outbox_select_own_or_admin
  ON public.sms_outbox FOR SELECT TO authenticated
  USING (
    recipient_user_id = auth.uid()
    OR public.has_staff_role(ARRAY['admin']::public.staff_role[])
  );

-- Seed catalog
INSERT INTO public.sms_event_catalog (code, description, category, priority) VALUES
  ('order_received', 'New sales order / POS checkout received', 'sales', 'high'),
  ('order_completed', 'Order completed / fulfilled', 'sales', 'high'),
  ('order_cancelled', 'Order cancelled after acceptance', 'sales', 'high'),
  ('order_on_hold', 'Order placed on credit/hold', 'sales', 'normal'),
  ('large_order', 'Order total exceeded large-order threshold', 'sales', 'high'),
  ('return_initiated', 'Return / credit note opened', 'sales', 'high'),
  ('return_completed', 'Return completed; stock to Quarantine', 'sales', 'high'),
  ('payment_received', 'Payment captured', 'payments', 'high'),
  ('payment_failed', 'Payment attempt failed', 'payments', 'high'),
  ('payment_partial', 'Partial payment applied', 'payments', 'normal'),
  ('refund_issued', 'Refund or store credit issued', 'payments', 'high'),
  ('ar_overdue', 'Invoice past due', 'payments', 'normal'),
  ('stock_received', 'Goods receipt posted', 'inventory', 'normal'),
  ('low_stock', 'Stock at or below reorder point', 'inventory', 'high'),
  ('stockout', 'Active SKU reached zero quantity', 'inventory', 'high'),
  ('transfer_pending_approval', 'Stock transfer awaiting dual authorization', 'inventory', 'high'),
  ('transfer_completed', 'Stock transfer completed', 'inventory', 'normal'),
  ('transfer_rejected', 'Stock transfer rejected', 'inventory', 'normal'),
  ('quarantine_received', 'Item moved into Quarantine warehouse', 'inventory', 'high'),
  ('serial_moved', 'Serialized / high-value assembly moved', 'inventory', 'normal'),
  ('po_created', 'Purchase order created', 'procurement', 'normal'),
  ('po_approved', 'Purchase order approved', 'procurement', 'normal'),
  ('po_received', 'PO goods receipt posted', 'procurement', 'high'),
  ('po_overdue', 'PO expected date passed', 'procurement', 'normal'),
  ('supplier_mismatch', 'Receipt vs PO variance over threshold', 'procurement', 'high'),
  ('delivery_dispatched', 'Delivery dispatched from warehouse', 'logistics', 'normal'),
  ('delivery_completed', 'Delivery completed', 'logistics', 'high'),
  ('delivery_failed', 'Delivery failed / returned to depot', 'logistics', 'high'),
  ('delivery_delayed', 'Delivery ETA slipped past threshold', 'logistics', 'normal'),
  ('journal_post_rejected', 'Journal post rejected', 'finance', 'high'),
  ('day_close_completed', 'Day close completed', 'finance', 'normal'),
  ('cash_drawer_variance', 'POS cash drawer variance over threshold', 'finance', 'high'),
  ('staff_no_show', 'Rostered staff missed clock-in window', 'hr', 'normal'),
  ('payroll_run_ready', 'Gross payroll run ready for review', 'hr', 'normal')
ON CONFLICT (code) DO NOTHING;
