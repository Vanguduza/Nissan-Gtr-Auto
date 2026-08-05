-- HR onboarding: link auth user + multi-channel credential outbox.
-- Reuses SMS/email/WA gateway workers — no new senders. No ZIMRA / payroll tax.

INSERT INTO public.sms_event_catalog (code, description, category, priority)
VALUES (
  'hr_staff_credentials',
  'New staff login credentials (temp password)',
  'hr',
  'high'
)
ON CONFLICT (code) DO NOTHING;

CREATE TYPE public.hr_credential_channel AS ENUM ('email', 'sms', 'whatsapp');
CREATE TYPE public.hr_credential_outbox_status AS ENUM (
  'pending', 'sending', 'sent', 'failed', 'skipped'
);

CREATE TABLE public.hr_credential_outbox (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  employee_id UUID NOT NULL REFERENCES public.employees (id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES public.profiles (id) ON DELETE CASCADE,
  channel public.hr_credential_channel NOT NULL,
  recipient TEXT NOT NULL,
  body TEXT NOT NULL,
  status public.hr_credential_outbox_status NOT NULL DEFAULT 'pending',
  attempt_count INT NOT NULL DEFAULT 0,
  last_error TEXT,
  provider_message_id TEXT,
  created_by UUID REFERENCES auth.users (id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  sent_at TIMESTAMPTZ,
  claimed_at TIMESTAMPTZ,
  UNIQUE (employee_id, channel, user_id)
);

CREATE INDEX hr_credential_outbox_pending_idx
  ON public.hr_credential_outbox (created_at)
  WHERE status = 'pending';

COMMENT ON TABLE public.hr_credential_outbox IS
  'Staff onboarding credential delivery (email/SMS/WA). Body may include temp password; service_role + HR only.';

ALTER TABLE public.hr_credential_outbox ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS hr_credential_outbox_select ON public.hr_credential_outbox;
CREATE POLICY hr_credential_outbox_select ON public.hr_credential_outbox
  FOR SELECT TO authenticated
  USING (public.has_staff_role(ARRAY['admin', 'hr']::public.staff_role[]));

-- Writes via SECURITY DEFINER RPCs / service_role only (no authenticated INSERT/UPDATE/DELETE).

GRANT SELECT ON TABLE public.hr_credential_outbox TO authenticated;
GRANT ALL ON TABLE public.hr_credential_outbox TO service_role;

-- Link employee ↔ auth profile after Admin createUser; set must_change_password.
-- service_role only (Edge). Never callable from authenticated clients.
CREATE OR REPLACE FUNCTION public.link_employee_auth_user(
  p_employee_id UUID,
  p_user_id UUID,
  p_phone_e164 TEXT DEFAULT NULL,
  p_full_name TEXT DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_emp public.employees%ROWTYPE;
BEGIN
  IF auth.role() IS DISTINCT FROM 'service_role' THEN
    RAISE EXCEPTION 'service_role required';
  END IF;

  IF p_employee_id IS NULL OR p_user_id IS NULL THEN
    RAISE EXCEPTION 'employee_id and user_id required';
  END IF;

  SELECT * INTO v_emp FROM public.employees WHERE id = p_employee_id FOR UPDATE;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'employee not found: %', p_employee_id;
  END IF;

  IF v_emp.user_id IS NOT NULL AND v_emp.user_id IS DISTINCT FROM p_user_id THEN
    RAISE EXCEPTION 'employee already linked to a different user';
  END IF;

  IF EXISTS (
    SELECT 1 FROM public.employees e
    WHERE e.user_id = p_user_id AND e.id IS DISTINCT FROM p_employee_id
  ) THEN
    RAISE EXCEPTION 'user already linked to another employee';
  END IF;

  UPDATE public.employees
  SET
    user_id = p_user_id,
    phone_e164 = COALESCE(NULLIF(trim(COALESCE(p_phone_e164, '')), ''), phone_e164),
    full_name = COALESCE(NULLIF(trim(COALESCE(p_full_name, '')), ''), full_name),
    updated_at = now()
  WHERE id = p_employee_id;

  UPDATE public.profiles
  SET
    must_change_password = true,
    phone_e164 = COALESCE(
      NULLIF(trim(COALESCE(p_phone_e164, '')), ''),
      phone_e164
    ),
    full_name = COALESCE(
      NULLIF(trim(COALESCE(p_full_name, '')), ''),
      full_name
    ),
    updated_at = now()
  WHERE id = p_user_id;

  RETURN p_employee_id;
END;
$$;

-- Enqueue one credential channel row (Edge fills body with temp password).
-- service_role only — clients must not plant plaintext credential bodies.
CREATE OR REPLACE FUNCTION public.enqueue_hr_credential_outbox(
  p_employee_id UUID,
  p_user_id UUID,
  p_channel public.hr_credential_channel,
  p_recipient TEXT,
  p_body TEXT
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_id UUID;
  v_recipient TEXT := NULLIF(trim(COALESCE(p_recipient, '')), '');
  v_body TEXT := NULLIF(trim(COALESCE(p_body, '')), '');
BEGIN
  IF auth.role() IS DISTINCT FROM 'service_role' THEN
    RAISE EXCEPTION 'service_role required';
  END IF;

  IF p_employee_id IS NULL OR p_user_id IS NULL THEN
    RAISE EXCEPTION 'employee_id and user_id required';
  END IF;
  IF v_recipient IS NULL OR v_body IS NULL THEN
    RAISE EXCEPTION 'recipient and body required';
  END IF;

  INSERT INTO public.hr_credential_outbox (
    employee_id, user_id, channel, recipient, body, created_by
  ) VALUES (
    p_employee_id, p_user_id, p_channel, v_recipient, v_body, auth.uid()
  )
  ON CONFLICT (employee_id, channel, user_id) DO UPDATE
  SET
    recipient = EXCLUDED.recipient,
    body = EXCLUDED.body,
    status = 'pending',
    attempt_count = 0,
    last_error = NULL,
    provider_message_id = NULL,
    sent_at = NULL,
    claimed_at = NULL,
    created_by = COALESCE(EXCLUDED.created_by, public.hr_credential_outbox.created_by)
  RETURNING id INTO v_id;

  RETURN v_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.complete_hr_credential_outbox(
  p_id UUID,
  p_success BOOLEAN,
  p_error TEXT DEFAULT NULL,
  p_provider_message_id TEXT DEFAULT NULL
)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  IF auth.role() IS DISTINCT FROM 'service_role' THEN
    RAISE EXCEPTION 'service_role required';
  END IF;

  -- Redact plaintext temp password from body after delivery attempt.
  UPDATE public.hr_credential_outbox
  SET
    status = CASE
      WHEN COALESCE(p_success, false) THEN 'sent'::public.hr_credential_outbox_status
      ELSE 'failed'::public.hr_credential_outbox_status
    END,
    attempt_count = attempt_count + 1,
    last_error = CASE WHEN COALESCE(p_success, false) THEN NULL ELSE left(COALESCE(p_error, 'send failed'), 500) END,
    provider_message_id = COALESCE(p_provider_message_id, provider_message_id),
    sent_at = CASE WHEN COALESCE(p_success, false) THEN now() ELSE sent_at END,
    claimed_at = NULL,
    body = '[redacted after delivery attempt]'
  WHERE id = p_id;
END;
$$;

REVOKE ALL ON FUNCTION public.link_employee_auth_user(UUID, UUID, TEXT, TEXT) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.enqueue_hr_credential_outbox(
  UUID, UUID, public.hr_credential_channel, TEXT, TEXT
) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.complete_hr_credential_outbox(UUID, BOOLEAN, TEXT, TEXT)
  FROM PUBLIC;

-- Link may be called by Edge (service_role) only — avoids client linking arbitrary users.
GRANT EXECUTE ON FUNCTION public.link_employee_auth_user(UUID, UUID, TEXT, TEXT)
  TO service_role;
GRANT EXECUTE ON FUNCTION public.enqueue_hr_credential_outbox(
  UUID, UUID, public.hr_credential_channel, TEXT, TEXT
) TO service_role;
GRANT EXECUTE ON FUNCTION public.complete_hr_credential_outbox(UUID, BOOLEAN, TEXT, TEXT)
  TO service_role;
