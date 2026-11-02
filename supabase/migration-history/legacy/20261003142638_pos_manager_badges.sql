-- POS manager approval: assigned managers, QR ID badges, badge approvals, audit trail.
--
-- * Managers are whoever already qualifies (admin role, HR grade A1/A2/B1, HR role flagged
--   pos_manager) plus staff an admin explicitly assigns (pos_manager_designations).
-- * A badge is an ID card whose QR carries `GTRMGR1:<badge id>:<secret>`. Only the SHA-256 of the
--   secret is stored; the payload is shown once, when the badge is issued. Badges expire and can be
--   revoked; a badge only works while its holder is still a manager.
-- * pos_badge_approve() runs one governed POS action under a badge: it validates the badge, opens a
--   grant that lives only inside this transaction (is_pos_approver() honours it), runs the action,
--   and writes an append-only audit row whether it succeeded, failed or the badge was rejected.
--   Five rejected scans in 15 minutes lock the operator out of badge approval for 15 minutes.
-- * A signed-in manager needs no approval prompt: is_pos_approver() is already true for them.
--
-- No ZIMRA / payroll content. All tables have RLS; clients reach them only through the RPCs below.

-- ───────────────────────────── tables

CREATE TABLE IF NOT EXISTS public.pos_manager_designations (
  user_id      uuid PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
  granted_by   uuid NOT NULL REFERENCES auth.users(id),
  granted_at   timestamptz NOT NULL DEFAULT now(),
  notes        text,
  revoked_at   timestamptz,
  revoked_by   uuid REFERENCES auth.users(id)
);

CREATE TABLE IF NOT EXISTS public.pos_manager_badges (
  id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id        uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  label          text,
  token_sha256   text NOT NULL UNIQUE,
  issued_by      uuid NOT NULL REFERENCES auth.users(id),
  issued_at      timestamptz NOT NULL DEFAULT now(),
  expires_at     timestamptz NOT NULL,
  revoked_at     timestamptz,
  revoked_by     uuid REFERENCES auth.users(id),
  revoke_reason  text,
  last_used_at   timestamptz,
  use_count      integer NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS pos_manager_badges_user_idx ON public.pos_manager_badges(user_id);

-- Append-only: one row per badge approval attempt (approved, failed or rejected).
CREATE TABLE IF NOT EXISTS public.pos_manager_approvals (
  id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  created_at       timestamptz NOT NULL DEFAULT now(),
  method           text NOT NULL DEFAULT 'badge' CHECK (method IN ('badge')),
  badge_id         uuid REFERENCES public.pos_manager_badges(id),
  manager_user_id  uuid REFERENCES auth.users(id),
  requested_by     uuid NOT NULL REFERENCES auth.users(id),
  action           text NOT NULL,
  args             jsonb NOT NULL DEFAULT '{}'::jsonb,
  device_id        text,
  outcome          text NOT NULL CHECK (outcome IN ('approved', 'failed', 'rejected')),
  result           jsonb,
  error            text
);
CREATE INDEX IF NOT EXISTS pos_manager_approvals_created_idx ON public.pos_manager_approvals(created_at DESC);
CREATE INDEX IF NOT EXISTS pos_manager_approvals_requester_idx ON public.pos_manager_approvals(requested_by, created_at DESC);

-- Admin changes to managers and badges (assign, unassign, issue, revoke). Append-only.
CREATE TABLE IF NOT EXISTS public.pos_manager_admin_events (
  id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  created_at  timestamptz NOT NULL DEFAULT now(),
  actor_id    uuid NOT NULL REFERENCES auth.users(id),
  event       text NOT NULL CHECK (event IN ('assigned', 'unassigned', 'badge_issued', 'badge_revoked')),
  user_id     uuid REFERENCES auth.users(id),
  badge_id    uuid REFERENCES public.pos_manager_badges(id),
  notes       text
);

-- In-transaction grants: written and deleted by pos_badge_approve() inside one transaction.
CREATE TABLE IF NOT EXISTS private.pos_badge_grants (
  id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  txid             bigint NOT NULL,
  requested_by     uuid NOT NULL,
  manager_user_id  uuid NOT NULL,
  badge_id         uuid NOT NULL
);

CREATE OR REPLACE FUNCTION private.forbid_mutation()
RETURNS trigger LANGUAGE plpgsql SET search_path = '' AS $$
BEGIN
  RAISE EXCEPTION '% is append-only', TG_TABLE_NAME;
END $$;

DROP TRIGGER IF EXISTS pos_manager_approvals_append_only ON public.pos_manager_approvals;
CREATE TRIGGER pos_manager_approvals_append_only BEFORE UPDATE OR DELETE ON public.pos_manager_approvals
  FOR EACH ROW EXECUTE FUNCTION private.forbid_mutation();
DROP TRIGGER IF EXISTS pos_manager_admin_events_append_only ON public.pos_manager_admin_events;
CREATE TRIGGER pos_manager_admin_events_append_only BEFORE UPDATE OR DELETE ON public.pos_manager_admin_events
  FOR EACH ROW EXECUTE FUNCTION private.forbid_mutation();

ALTER TABLE public.pos_manager_designations ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.pos_manager_badges ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.pos_manager_approvals ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.pos_manager_admin_events ENABLE ROW LEVEL SECURITY;

-- Reads for admins and finance (audit); every write goes through SECURITY DEFINER RPCs.
DROP POLICY IF EXISTS pos_manager_designations_read ON public.pos_manager_designations;
CREATE POLICY pos_manager_designations_read ON public.pos_manager_designations FOR SELECT TO authenticated
  USING (public.has_staff_role(ARRAY['admin','finance']::public.staff_role[]));
DROP POLICY IF EXISTS pos_manager_approvals_read ON public.pos_manager_approvals;
CREATE POLICY pos_manager_approvals_read ON public.pos_manager_approvals FOR SELECT TO authenticated
  USING (public.has_staff_role(ARRAY['admin','finance']::public.staff_role[]));
DROP POLICY IF EXISTS pos_manager_admin_events_read ON public.pos_manager_admin_events;
CREATE POLICY pos_manager_admin_events_read ON public.pos_manager_admin_events FOR SELECT TO authenticated
  USING (public.has_staff_role(ARRAY['admin','finance']::public.staff_role[]));
-- Badges: no client policy at all (the hash never leaves the server); listed through the RPC.

REVOKE ALL ON public.pos_manager_designations, public.pos_manager_badges, public.pos_manager_approvals, public.pos_manager_admin_events FROM anon;
REVOKE INSERT, UPDATE, DELETE ON public.pos_manager_designations, public.pos_manager_badges, public.pos_manager_approvals, public.pos_manager_admin_events FROM authenticated;
REVOKE ALL ON private.pos_badge_grants FROM PUBLIC;

-- ───────────────────────────── who is a manager

/** Why [p_user] can approve POS actions, or NULL. Same rules for every caller. */
CREATE OR REPLACE FUNCTION private.pos_approver_source(p_user uuid)
RETURNS text LANGUAGE sql STABLE SECURITY DEFINER SET search_path = '' AS $$
  SELECT CASE
    WHEN p_user IS NULL THEN NULL
    WHEN EXISTS (SELECT 1 FROM public.staff_roles sr WHERE sr.user_id = p_user AND sr.role = 'admin') THEN 'admin_role'
    WHEN EXISTS (SELECT 1 FROM public.pos_manager_designations d
                 WHERE d.user_id = p_user AND d.revoked_at IS NULL
                   AND EXISTS (SELECT 1 FROM public.staff_roles sr WHERE sr.user_id = p_user)) THEN 'assigned'
    WHEN EXISTS (
      SELECT 1 FROM public.employees e
      LEFT JOIN public.hr_grades g ON g.id = e.grade_id
      LEFT JOIN public.hr_roles r ON r.id = e.hr_role_id
      WHERE e.user_id = p_user AND e.status = 'active'
        AND (g.code IN ('A1','A2','B1') OR COALESCE(r.approval_flags->>'pos_manager','false') = 'true')
    ) THEN 'hr_grade_or_role'
    ELSE NULL
  END;
$$;

/** The manager behind a badge grant open in this transaction for the calling operator, if any. */
CREATE OR REPLACE FUNCTION private.pos_badge_grant_manager()
RETURNS uuid LANGUAGE sql STABLE SECURITY DEFINER SET search_path = '' AS $$
  SELECT g.manager_user_id FROM private.pos_badge_grants g
  WHERE g.id = NULLIF(current_setting('gtr.pos_badge_grant', true), '')::uuid
    AND g.txid = txid_current()
    AND g.requested_by = auth.uid()
  LIMIT 1;
$$;

CREATE OR REPLACE FUNCTION public.is_pos_approver()
RETURNS boolean LANGUAGE sql STABLE SECURITY DEFINER SET search_path = 'public' AS $$
  SELECT private.pos_approver_source(auth.uid()) IS NOT NULL
      OR private.pos_badge_grant_manager() IS NOT NULL;
$$;

/** The signed-in user's manager status: the POS skips the approval prompt when it is true. */
CREATE OR REPLACE FUNCTION public.get_my_pos_approver_status()
RETURNS jsonb LANGUAGE sql STABLE SECURITY DEFINER SET search_path = '' AS $$
  SELECT jsonb_build_object('is_approver', private.pos_approver_source(auth.uid()) IS NOT NULL,
                            'source', private.pos_approver_source(auth.uid()));
$$;

-- ───────────────────────────── admin: managers and badges

CREATE OR REPLACE FUNCTION private.require_admin()
RETURNS void LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path = '' AS $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM public.staff_roles sr WHERE sr.user_id = auth.uid() AND sr.role = 'admin') THEN
    RAISE EXCEPTION 'admin role required';
  END IF;
END $$;

/** Staff who could hold POS manager rights, with their current status and live badge count. */
CREATE OR REPLACE FUNCTION public.list_pos_manager_candidates()
RETURNS TABLE(user_id uuid, full_name text, employee_code text, email text, roles text[], is_approver boolean,
              source text, assigned boolean, active_badges integer)
LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path = '' AS $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM public.staff_roles sr WHERE sr.user_id = auth.uid() AND sr.role IN ('admin','finance')) THEN
    RAISE EXCEPTION 'admin or finance role required';
  END IF;
  RETURN QUERY
  SELECT u.id,
         COALESCE(e.full_name, u.email::text),
         e.employee_code::text,
         u.email::text,
         ARRAY(SELECT sr.role::text FROM public.staff_roles sr WHERE sr.user_id = u.id ORDER BY sr.role::text),
         private.pos_approver_source(u.id) IS NOT NULL,
         private.pos_approver_source(u.id),
         EXISTS (SELECT 1 FROM public.pos_manager_designations d WHERE d.user_id = u.id AND d.revoked_at IS NULL),
         (SELECT count(*)::int FROM public.pos_manager_badges b WHERE b.user_id = u.id AND b.revoked_at IS NULL AND b.expires_at > now())
  FROM auth.users u
  LEFT JOIN public.employees e ON e.user_id = u.id
  WHERE EXISTS (SELECT 1 FROM public.staff_roles sr WHERE sr.user_id = u.id AND sr.role IN ('admin','sales','finance','warehouse'))
  ORDER BY 6 DESC, 2;
END $$;

CREATE OR REPLACE FUNCTION public.set_pos_manager_assignment(p_user_id uuid, p_assigned boolean, p_notes text DEFAULT NULL)
RETURNS boolean LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $$
BEGIN
  PERFORM private.require_admin();
  IF NOT EXISTS (SELECT 1 FROM public.staff_roles sr WHERE sr.user_id = p_user_id) THEN
    RAISE EXCEPTION 'staff account required';
  END IF;
  IF p_assigned THEN
    INSERT INTO public.pos_manager_designations(user_id, granted_by, notes)
    VALUES (p_user_id, auth.uid(), p_notes)
    ON CONFLICT (user_id) DO UPDATE SET granted_by = auth.uid(), granted_at = now(), notes = p_notes, revoked_at = NULL, revoked_by = NULL;
  ELSE
    UPDATE public.pos_manager_designations SET revoked_at = now(), revoked_by = auth.uid() WHERE user_id = p_user_id AND revoked_at IS NULL;
  END IF;
  INSERT INTO public.pos_manager_admin_events(actor_id, event, user_id, notes)
  VALUES (auth.uid(), CASE WHEN p_assigned THEN 'assigned' ELSE 'unassigned' END, p_user_id, p_notes);
  RETURN p_assigned;
END $$;

/**
 * Issue an ID badge to a manager. Returns the QR payload once; only its hash is kept. A lost card
 * is revoked and a new one issued.
 */
CREATE OR REPLACE FUNCTION public.issue_pos_manager_badge(p_user_id uuid, p_label text DEFAULT NULL, p_valid_days integer DEFAULT 365)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $$
DECLARE v_secret text; v_id uuid := gen_random_uuid(); v_expires timestamptz; v_name text; v_code text; v_email text;
BEGIN
  PERFORM private.require_admin();
  IF private.pos_approver_source(p_user_id) IS NULL THEN RAISE EXCEPTION 'only a POS manager can hold a manager badge'; END IF;
  IF COALESCE(p_valid_days, 0) < 1 OR p_valid_days > 1095 THEN RAISE EXCEPTION 'badge validity must be 1 to 1095 days'; END IF;
  v_secret := translate(encode(extensions.gen_random_bytes(32), 'base64'), '+/=', '-_');
  v_expires := now() + make_interval(days => p_valid_days);
  INSERT INTO public.pos_manager_badges(id, user_id, label, token_sha256, issued_by, expires_at)
  VALUES (v_id, p_user_id, NULLIF(trim(COALESCE(p_label, '')), ''), encode(extensions.digest(v_secret, 'sha256'), 'hex'), auth.uid(), v_expires);
  INSERT INTO public.pos_manager_admin_events(actor_id, event, user_id, badge_id, notes)
  VALUES (auth.uid(), 'badge_issued', p_user_id, v_id, p_label);
  SELECT COALESCE(e.full_name, u.email::text), e.employee_code::text, u.email::text INTO v_name, v_code, v_email
  FROM auth.users u LEFT JOIN public.employees e ON e.user_id = u.id WHERE u.id = p_user_id;
  RETURN jsonb_build_object('badge_id', v_id, 'payload', 'GTRMGR1:' || v_id::text || ':' || v_secret,
    'expires_at', v_expires, 'full_name', v_name, 'employee_code', v_code, 'email', v_email);
END $$;

CREATE OR REPLACE FUNCTION public.revoke_pos_manager_badge(p_badge_id uuid, p_reason text)
RETURNS uuid LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $$
DECLARE v_user uuid;
BEGIN
  PERFORM private.require_admin();
  IF trim(COALESCE(p_reason, '')) = '' THEN RAISE EXCEPTION 'revocation reason required'; END IF;
  UPDATE public.pos_manager_badges SET revoked_at = now(), revoked_by = auth.uid(), revoke_reason = trim(p_reason)
  WHERE id = p_badge_id AND revoked_at IS NULL RETURNING user_id INTO v_user;
  IF v_user IS NULL THEN RAISE EXCEPTION 'active badge not found'; END IF;
  INSERT INTO public.pos_manager_admin_events(actor_id, event, user_id, badge_id, notes)
  VALUES (auth.uid(), 'badge_revoked', v_user, p_badge_id, trim(p_reason));
  RETURN p_badge_id;
END $$;

CREATE OR REPLACE FUNCTION public.list_pos_manager_badges(p_user_id uuid DEFAULT NULL)
RETURNS TABLE(badge_id uuid, user_id uuid, full_name text, label text, issued_at timestamptz, expires_at timestamptz,
              revoked_at timestamptz, revoke_reason text, last_used_at timestamptz, use_count integer, status text)
LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path = '' AS $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM public.staff_roles sr WHERE sr.user_id = auth.uid() AND sr.role IN ('admin','finance')) THEN
    RAISE EXCEPTION 'admin or finance role required';
  END IF;
  RETURN QUERY
  SELECT b.id, b.user_id, COALESCE(e.full_name, u.email::text), b.label, b.issued_at, b.expires_at, b.revoked_at, b.revoke_reason,
         b.last_used_at, b.use_count,
         CASE WHEN b.revoked_at IS NOT NULL THEN 'revoked' WHEN b.expires_at <= now() THEN 'expired' ELSE 'active' END
  FROM public.pos_manager_badges b
  JOIN auth.users u ON u.id = b.user_id
  LEFT JOIN public.employees e ON e.user_id = b.user_id
  WHERE p_user_id IS NULL OR b.user_id = p_user_id
  ORDER BY b.issued_at DESC;
END $$;

-- ───────────────────────────── badge approval

/**
 * Run one governed POS action approved by a scanned manager badge. Returns
 * {ok, approval_id, manager_name, result | error}; never raises for a business refusal, so the
 * audit row survives. Actions and their args:
 *   discount        {cart_id, percent, reason_code, notes}
 *   price_override  {line_id, unit_price, reason_code, notes}
 *   void_sale       {cart_id, reason_code, notes}
 *   refund          {invoice_id, reason_code, notes}
 *   cash_out        {session_id, kind, amount, reason_code, notes}
 *   till_variance   {session_id, reason_code, notes}
 *   till_handover   {session_id, new_operator_user_id, notes}
 *   repair_paid_order {order_id, notes}
 */
CREATE OR REPLACE FUNCTION public.pos_badge_approve(p_badge text, p_action text, p_args jsonb DEFAULT '{}'::jsonb, p_device_id text DEFAULT NULL)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $$
DECLARE
  b public.pos_manager_badges%ROWTYPE;
  v_parts text[]; v_badge_id uuid; v_secret text; v_manager uuid; v_grant uuid; v_result jsonb; v_name text;
  v_err text; v_reject text; a jsonb := COALESCE(p_args, '{}'::jsonb);
BEGIN
  IF auth.uid() IS NULL THEN RAISE EXCEPTION 'sign in required'; END IF;
  IF p_action NOT IN ('discount','price_override','void_sale','refund','cash_out','till_variance','till_handover','repair_paid_order') THEN
    RAISE EXCEPTION 'unknown action %', p_action;
  END IF;
  -- Lockout after repeated bad scans (per operator).
  IF (SELECT count(*) FROM public.pos_manager_approvals x
      WHERE x.requested_by = auth.uid() AND x.outcome = 'rejected' AND x.created_at > now() - interval '15 minutes') >= 5 THEN
    RAISE EXCEPTION 'too many rejected badge scans; try again in 15 minutes or ask the manager to sign in';
  END IF;

  v_parts := string_to_array(trim(COALESCE(p_badge, '')), ':');
  IF array_length(v_parts, 1) = 3 AND v_parts[1] = 'GTRMGR1' AND v_parts[2] ~ '^[0-9a-f-]{36}$' THEN
    v_badge_id := v_parts[2]::uuid; v_secret := v_parts[3];
    SELECT * INTO b FROM public.pos_manager_badges WHERE id = v_badge_id FOR UPDATE;
  END IF;
  v_reject := CASE
    WHEN b.id IS NULL THEN 'not a manager badge'
    WHEN b.token_sha256 <> encode(extensions.digest(v_secret, 'sha256'), 'hex') THEN 'badge not recognised'
    WHEN b.revoked_at IS NOT NULL THEN 'badge revoked'
    WHEN b.expires_at <= now() THEN 'badge expired'
    WHEN private.pos_approver_source(b.user_id) IS NULL THEN 'badge holder is no longer a POS manager'
    ELSE NULL END;
  IF v_reject IS NOT NULL THEN
    INSERT INTO public.pos_manager_approvals(badge_id, manager_user_id, requested_by, action, args, device_id, outcome, error)
    VALUES (b.id, b.user_id, auth.uid(), p_action, a, p_device_id, 'rejected', v_reject);
    RETURN jsonb_build_object('ok', false, 'error', v_reject);
  END IF;
  v_manager := b.user_id;
  SELECT COALESCE(e.full_name, u.email::text) INTO v_name FROM auth.users u LEFT JOIN public.employees e ON e.user_id = u.id WHERE u.id = v_manager;

  BEGIN
    INSERT INTO private.pos_badge_grants(txid, requested_by, manager_user_id, badge_id)
    VALUES (txid_current(), auth.uid(), v_manager, b.id) RETURNING id INTO v_grant;
    PERFORM set_config('gtr.pos_badge_grant', v_grant::text, true);

    v_result := CASE p_action
      WHEN 'discount' THEN to_jsonb(public.apply_pos_cart_discount_governed((a->>'cart_id')::uuid, (a->>'percent')::numeric, a->>'reason_code', a->>'notes'))
      WHEN 'price_override' THEN to_jsonb(public.apply_pos_line_price_override_governed((a->>'line_id')::uuid, (a->>'unit_price')::numeric, a->>'reason_code', a->>'notes'))
      WHEN 'void_sale' THEN to_jsonb(public.void_pos_cart_governed((a->>'cart_id')::uuid, a->>'reason_code', a->>'notes'))
      WHEN 'refund' THEN to_jsonb(public.post_pos_refund_governed((a->>'invoice_id')::uuid, a->>'reason_code', a->>'notes'))
      WHEN 'cash_out' THEN to_jsonb(public.record_pos_till_cash_movement((a->>'session_id')::uuid, (a->>'kind')::public.pos_till_cash_movement_kind,
                                     (a->>'amount')::numeric, a->>'reason_code', a->>'notes'))
      WHEN 'till_variance' THEN to_jsonb(public.approve_pos_till_variance((a->>'session_id')::uuid, a->>'reason_code', a->>'notes'))
      WHEN 'till_handover' THEN to_jsonb(public.handover_pos_till_session((a->>'session_id')::uuid, (a->>'new_operator_user_id')::uuid, a->>'notes'))
      WHEN 'repair_paid_order' THEN to_jsonb(public.repair_pos_paid_order((a->>'order_id')::uuid, a->>'notes'))
    END;

    -- The governed functions stamp the approver as auth.uid(); under a badge it is the manager.
    UPDATE public.pos_action_audit SET approved_by_user_id = v_manager
    WHERE created_at = now() AND actor_user_id = auth.uid() AND approved_by_user_id = auth.uid();
    DELETE FROM private.pos_badge_grants WHERE id = v_grant;
    PERFORM set_config('gtr.pos_badge_grant', '', true);
  EXCEPTION WHEN OTHERS THEN
    -- The action and its grant roll back together; only the audit row below remains.
    v_err := SQLERRM;
  END;
  PERFORM set_config('gtr.pos_badge_grant', '', true);

  IF v_err IS NOT NULL THEN
    INSERT INTO public.pos_manager_approvals(badge_id, manager_user_id, requested_by, action, args, device_id, outcome, error)
    VALUES (b.id, v_manager, auth.uid(), p_action, a, p_device_id, 'failed', v_err);
    RETURN jsonb_build_object('ok', false, 'error', v_err, 'manager_name', v_name);
  END IF;
  UPDATE public.pos_manager_badges SET last_used_at = now(), use_count = use_count + 1 WHERE id = b.id;
  INSERT INTO public.pos_manager_approvals(id, badge_id, manager_user_id, requested_by, action, args, device_id, outcome, result)
  VALUES (gen_random_uuid(), b.id, v_manager, auth.uid(), p_action, a, p_device_id, 'approved', jsonb_build_object('value', v_result))
  RETURNING jsonb_build_object('ok', true, 'approval_id', id, 'manager_name', v_name, 'result', v_result) INTO v_result;
  RETURN v_result;
END $$;

-- ───────────────────────────── audit trail

/**
 * Every manager approval, newest first: badge approvals (approved, failed, rejected) and governed
 * actions approved by a manager's own session (signed in, or password sign-in for one action).
 */
CREATE OR REPLACE FUNCTION public.list_pos_manager_approval_trail(p_limit integer DEFAULT 200)
RETURNS TABLE(at timestamptz, method text, outcome text, action text, manager_name text, requested_by_name text,
              reason_code text, detail text, device_id text)
LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path = '' AS $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM public.staff_roles sr WHERE sr.user_id = auth.uid() AND sr.role IN ('admin','finance')) THEN
    RAISE EXCEPTION 'admin or finance role required';
  END IF;
  RETURN QUERY
  WITH names AS (
    SELECT u.id, COALESCE(e.full_name, u.email::text) AS name FROM auth.users u LEFT JOIN public.employees e ON e.user_id = u.id
  ), trail(t_at, t_method, t_outcome, t_action, t_manager, t_requested_by, t_reason, t_detail, t_device) AS (
    SELECT x.created_at, 'badge'::text, x.outcome, x.action, m.name, r.name, x.args->>'reason_code',
           COALESCE(x.error, x.args->>'notes'), x.device_id
    FROM public.pos_manager_approvals x
    LEFT JOIN names m ON m.id = x.manager_user_id
    LEFT JOIN names r ON r.id = x.requested_by
    UNION ALL
    SELECT p.created_at, 'manager_session'::text, 'approved'::text, p.action, m.name, r.name, p.reason_code, p.notes, NULL::text
    FROM public.pos_action_audit p
    LEFT JOIN names m ON m.id = p.approved_by_user_id
    LEFT JOIN names r ON r.id = p.actor_user_id
    WHERE p.approved_by_user_id IS NOT NULL AND p.approved_by_user_id = p.actor_user_id
    UNION ALL
    SELECT ev.created_at, 'admin'::text, ev.event, ev.event, a.name, t.name, NULL::text, ev.notes, NULL::text
    FROM public.pos_manager_admin_events ev
    LEFT JOIN names a ON a.id = ev.actor_id
    LEFT JOIN names t ON t.id = ev.user_id
  )
  SELECT * FROM trail ORDER BY 1 DESC LIMIT LEAST(GREATEST(COALESCE(p_limit, 200), 1), 1000);
END $$;

REVOKE ALL ON FUNCTION private.pos_approver_source(uuid), private.pos_badge_grant_manager(), private.require_admin() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.get_my_pos_approver_status(), public.list_pos_manager_candidates(),
  public.set_pos_manager_assignment(uuid, boolean, text), public.issue_pos_manager_badge(uuid, text, integer),
  public.revoke_pos_manager_badge(uuid, text), public.list_pos_manager_badges(uuid),
  public.pos_badge_approve(text, text, jsonb, text), public.list_pos_manager_approval_trail(integer) TO authenticated;
REVOKE EXECUTE ON FUNCTION public.get_my_pos_approver_status(), public.list_pos_manager_candidates(),
  public.set_pos_manager_assignment(uuid, boolean, text), public.issue_pos_manager_badge(uuid, text, integer),
  public.revoke_pos_manager_badge(uuid, text), public.list_pos_manager_badges(uuid),
  public.pos_badge_approve(text, text, jsonb, text), public.list_pos_manager_approval_trail(integer) FROM anon, PUBLIC;
