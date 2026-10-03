-- Approvers are employees, not POS logins (owner, 2026-10-03): any Nissan GT-R Auto employee who is a
-- manager, or who holds an assigned approval role, approves POS actions with their QR ID badge.
--
-- An active employee approves when ANY of these holds:
--   * senior grade: A1 CEO, A2 Director, B1 Shop & warehouse manager           → 'senior_grade'
--   * their HR role is flagged for approvals (approval_flags.approver,
--     .pos_approver or the earlier .pos_manager = true)                          → 'approval_role'
--   * their HR role heads a team (another active role reports to it)           → 'department_manager'
--   * an admin assigned them as an approver                                     → 'assigned'
-- A signed-in user also approves through the admin staff role ('admin_role').
--
-- Badges belong to the employee; the employee needs no POS login. Replaces the user-keyed tables
-- and RPCs from 20261003142638 (unused: every table was empty when this was written).

-- ───────────────────────────── drop the user-keyed version

DROP FUNCTION IF EXISTS public.list_pos_manager_candidates();
DROP FUNCTION IF EXISTS public.set_pos_manager_assignment(uuid, boolean, text);
DROP FUNCTION IF EXISTS public.issue_pos_manager_badge(uuid, text, integer);
DROP FUNCTION IF EXISTS public.revoke_pos_manager_badge(uuid, text);
DROP FUNCTION IF EXISTS public.list_pos_manager_badges(uuid);
DROP FUNCTION IF EXISTS public.list_pos_manager_approval_trail(integer);
DROP TABLE IF EXISTS public.pos_manager_approvals;
DROP TABLE IF EXISTS public.pos_manager_admin_events;
DROP TABLE IF EXISTS public.pos_manager_badges;
DROP TABLE IF EXISTS public.pos_manager_designations;
DROP TABLE IF EXISTS private.pos_badge_grants;

-- ───────────────────────────── tables

CREATE TABLE public.staff_approver_assignments (
  employee_id  uuid PRIMARY KEY REFERENCES public.employees(id) ON DELETE CASCADE,
  granted_by   uuid NOT NULL REFERENCES auth.users(id),
  granted_at   timestamptz NOT NULL DEFAULT now(),
  notes        text,
  revoked_at   timestamptz,
  revoked_by   uuid REFERENCES auth.users(id)
);

-- One holder: an employee, or (for an admin with no employee record) a staff user.
CREATE TABLE public.staff_approval_badges (
  id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  employee_id    uuid REFERENCES public.employees(id) ON DELETE CASCADE,
  user_id        uuid REFERENCES auth.users(id) ON DELETE CASCADE,
  label          text,
  token_sha256   text NOT NULL UNIQUE,
  issued_by      uuid NOT NULL REFERENCES auth.users(id),
  issued_at      timestamptz NOT NULL DEFAULT now(),
  expires_at     timestamptz NOT NULL,
  revoked_at     timestamptz,
  revoked_by     uuid REFERENCES auth.users(id),
  revoke_reason  text,
  last_used_at   timestamptz,
  use_count      integer NOT NULL DEFAULT 0,
  CONSTRAINT staff_approval_badges_one_holder CHECK ((employee_id IS NULL) <> (user_id IS NULL))
);
CREATE INDEX staff_approval_badges_employee_idx ON public.staff_approval_badges(employee_id);

-- Append-only: one row per badge approval attempt.
CREATE TABLE public.staff_badge_approvals (
  id                    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  created_at            timestamptz NOT NULL DEFAULT now(),
  badge_id              uuid REFERENCES public.staff_approval_badges(id),
  approver_employee_id  uuid REFERENCES public.employees(id),
  approver_user_id      uuid REFERENCES auth.users(id),
  requested_by          uuid NOT NULL REFERENCES auth.users(id),
  action                text NOT NULL,
  args                  jsonb NOT NULL DEFAULT '{}'::jsonb,
  device_id             text,
  outcome               text NOT NULL CHECK (outcome IN ('approved', 'failed', 'rejected')),
  result                jsonb,
  error                 text
);
CREATE INDEX staff_badge_approvals_created_idx ON public.staff_badge_approvals(created_at DESC);
CREATE INDEX staff_badge_approvals_requester_idx ON public.staff_badge_approvals(requested_by, created_at DESC);

CREATE TABLE public.staff_approver_admin_events (
  id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  created_at   timestamptz NOT NULL DEFAULT now(),
  actor_id     uuid NOT NULL REFERENCES auth.users(id),
  event        text NOT NULL CHECK (event IN ('assigned', 'unassigned', 'badge_issued', 'badge_revoked')),
  employee_id  uuid REFERENCES public.employees(id),
  user_id      uuid REFERENCES auth.users(id),
  badge_id     uuid REFERENCES public.staff_approval_badges(id),
  notes        text
);

CREATE TABLE private.staff_badge_grants (
  id                    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  txid                  bigint NOT NULL,
  requested_by          uuid NOT NULL,
  approver_employee_id  uuid,
  approver_user_id      uuid,
  badge_id              uuid NOT NULL
);

-- Who approved a governed action, when the approver has no login of their own.
ALTER TABLE public.pos_action_audit ADD COLUMN IF NOT EXISTS approved_by_employee_id uuid REFERENCES public.employees(id);

CREATE TRIGGER staff_badge_approvals_append_only BEFORE UPDATE OR DELETE ON public.staff_badge_approvals
  FOR EACH ROW EXECUTE FUNCTION private.forbid_mutation();
CREATE TRIGGER staff_approver_admin_events_append_only BEFORE UPDATE OR DELETE ON public.staff_approver_admin_events
  FOR EACH ROW EXECUTE FUNCTION private.forbid_mutation();

ALTER TABLE public.staff_approver_assignments ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.staff_approval_badges ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.staff_badge_approvals ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.staff_approver_admin_events ENABLE ROW LEVEL SECURITY;
CREATE POLICY staff_approver_assignments_read ON public.staff_approver_assignments FOR SELECT TO authenticated
  USING (public.has_staff_role(ARRAY['admin','finance','hr']::public.staff_role[]));
CREATE POLICY staff_badge_approvals_read ON public.staff_badge_approvals FOR SELECT TO authenticated
  USING (public.has_staff_role(ARRAY['admin','finance']::public.staff_role[]));
CREATE POLICY staff_approver_admin_events_read ON public.staff_approver_admin_events FOR SELECT TO authenticated
  USING (public.has_staff_role(ARRAY['admin','finance','hr']::public.staff_role[]));
-- Badges: no client policy (the hash never leaves the server); listed through the RPC.
REVOKE ALL ON public.staff_approver_assignments, public.staff_approval_badges, public.staff_badge_approvals, public.staff_approver_admin_events FROM anon;
REVOKE INSERT, UPDATE, DELETE ON public.staff_approver_assignments, public.staff_approval_badges, public.staff_badge_approvals, public.staff_approver_admin_events FROM authenticated;
REVOKE ALL ON private.staff_badge_grants FROM PUBLIC;

-- ───────────────────────────── who approves

/** Why an active employee may approve, or NULL. */
CREATE OR REPLACE FUNCTION private.employee_approver_source(p_employee uuid)
RETURNS text LANGUAGE sql STABLE SECURITY DEFINER SET search_path = '' AS $$
  SELECT CASE
    WHEN g.code IN ('A1','A2','B1') THEN 'senior_grade'
    WHEN COALESCE(r.approval_flags->>'approver', r.approval_flags->>'pos_approver', r.approval_flags->>'pos_manager', 'false') = 'true' THEN 'approval_role'
    WHEN r.id IS NOT NULL AND EXISTS (SELECT 1 FROM public.hr_roles c WHERE c.parent_role_id = r.id AND c.is_active) THEN 'department_manager'
    WHEN EXISTS (SELECT 1 FROM public.staff_approver_assignments a WHERE a.employee_id = e.id AND a.revoked_at IS NULL) THEN 'assigned'
    ELSE NULL
  END
  FROM public.employees e
  LEFT JOIN public.hr_grades g ON g.id = e.grade_id
  LEFT JOIN public.hr_roles r ON r.id = e.hr_role_id AND r.is_active
  WHERE e.id = p_employee AND e.status = 'active';
$$;

/** Why a signed-in user may approve: the admin staff role, or their employee record. */
CREATE OR REPLACE FUNCTION private.pos_approver_source(p_user uuid)
RETURNS text LANGUAGE sql STABLE SECURITY DEFINER SET search_path = '' AS $$
  SELECT CASE
    WHEN p_user IS NULL THEN NULL
    WHEN EXISTS (SELECT 1 FROM public.staff_roles sr WHERE sr.user_id = p_user AND sr.role = 'admin') THEN 'admin_role'
    ELSE (SELECT private.employee_approver_source(e.id) FROM public.employees e WHERE e.user_id = p_user
          ORDER BY private.employee_approver_source(e.id) NULLS LAST LIMIT 1)
  END;
$$;

/** Is the badge's holder still an approver? */
CREATE OR REPLACE FUNCTION private.badge_holder_source(p_employee uuid, p_user uuid)
RETURNS text LANGUAGE sql STABLE SECURITY DEFINER SET search_path = '' AS $$
  SELECT CASE WHEN p_employee IS NOT NULL THEN private.employee_approver_source(p_employee) ELSE private.pos_approver_source(p_user) END;
$$;

DROP FUNCTION IF EXISTS private.pos_badge_grant_manager();
/** A badge grant open in this transaction for the calling operator. */
CREATE OR REPLACE FUNCTION private.staff_badge_grant_active()
RETURNS boolean LANGUAGE sql STABLE SECURITY DEFINER SET search_path = '' AS $$
  SELECT EXISTS (
    SELECT 1 FROM private.staff_badge_grants g
    WHERE g.id = NULLIF(current_setting('gtr.pos_badge_grant', true), '')::uuid
      AND g.txid = txid_current()
      AND g.requested_by = auth.uid());
$$;

CREATE OR REPLACE FUNCTION public.is_pos_approver()
RETURNS boolean LANGUAGE sql STABLE SECURITY DEFINER SET search_path = 'public' AS $$
  SELECT private.pos_approver_source(auth.uid()) IS NOT NULL OR private.staff_badge_grant_active();
$$;

-- get_my_pos_approver_status() from the previous migration reads pos_approver_source(): unchanged.

CREATE OR REPLACE FUNCTION private.require_approver_admin()
RETURNS void LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path = '' AS $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM public.staff_roles sr WHERE sr.user_id = auth.uid() AND sr.role IN ('admin','hr')) THEN
    RAISE EXCEPTION 'admin or HR role required';
  END IF;
END $$;

-- ───────────────────────────── admin: approvers and badges

/**
 * Everyone who could approve: every active employee (with or without a POS login) and admin staff
 * with no employee record. holder_type is 'employee' or 'user'; badges are issued to that holder.
 */
CREATE OR REPLACE FUNCTION public.list_approver_candidates()
RETURNS TABLE(holder_type text, employee_id uuid, user_id uuid, full_name text, employee_code text, grade text,
              role_title text, department text, has_login boolean, is_approver boolean, source text, assigned boolean, active_badges integer)
LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path = '' AS $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM public.staff_roles sr WHERE sr.user_id = auth.uid() AND sr.role IN ('admin','hr','finance')) THEN
    RAISE EXCEPTION 'admin, HR or finance role required';
  END IF;
  RETURN QUERY
  SELECT 'employee'::text, e.id, e.user_id, e.full_name, e.employee_code::text, g.title, r.title, r.department,
         e.user_id IS NOT NULL,
         private.employee_approver_source(e.id) IS NOT NULL OR (e.user_id IS NOT NULL AND private.pos_approver_source(e.user_id) IS NOT NULL),
         COALESCE(private.employee_approver_source(e.id), private.pos_approver_source(e.user_id)),
         EXISTS (SELECT 1 FROM public.staff_approver_assignments a WHERE a.employee_id = e.id AND a.revoked_at IS NULL),
         (SELECT count(*)::int FROM public.staff_approval_badges b WHERE b.employee_id = e.id AND b.revoked_at IS NULL AND b.expires_at > now())
  FROM public.employees e
  LEFT JOIN public.hr_grades g ON g.id = e.grade_id
  LEFT JOIN public.hr_roles r ON r.id = e.hr_role_id
  WHERE e.status = 'active'
  UNION ALL
  SELECT 'user'::text, NULL::uuid, u.id, u.email::text, NULL::text, NULL::text, 'Administrator'::text, NULL::text, true, true, 'admin_role'::text, false,
         (SELECT count(*)::int FROM public.staff_approval_badges b WHERE b.user_id = u.id AND b.revoked_at IS NULL AND b.expires_at > now())
  FROM auth.users u
  WHERE EXISTS (SELECT 1 FROM public.staff_roles sr WHERE sr.user_id = u.id AND sr.role = 'admin')
    AND NOT EXISTS (SELECT 1 FROM public.employees e WHERE e.user_id = u.id)
  ORDER BY 10 DESC, 4;
END $$;

CREATE OR REPLACE FUNCTION public.set_approver_assignment(p_employee_id uuid, p_assigned boolean, p_notes text DEFAULT NULL)
RETURNS boolean LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $$
BEGIN
  PERFORM private.require_approver_admin();
  IF NOT EXISTS (SELECT 1 FROM public.employees e WHERE e.id = p_employee_id AND e.status = 'active') THEN
    RAISE EXCEPTION 'active employee required';
  END IF;
  IF p_assigned THEN
    INSERT INTO public.staff_approver_assignments(employee_id, granted_by, notes) VALUES (p_employee_id, auth.uid(), p_notes)
    ON CONFLICT (employee_id) DO UPDATE SET granted_by = auth.uid(), granted_at = now(), notes = p_notes, revoked_at = NULL, revoked_by = NULL;
  ELSE
    UPDATE public.staff_approver_assignments SET revoked_at = now(), revoked_by = auth.uid() WHERE employee_id = p_employee_id AND revoked_at IS NULL;
  END IF;
  INSERT INTO public.staff_approver_admin_events(actor_id, event, employee_id, notes)
  VALUES (auth.uid(), CASE WHEN p_assigned THEN 'assigned' ELSE 'unassigned' END, p_employee_id, p_notes);
  RETURN p_assigned;
END $$;

/** Issue an ID badge to an approver (an employee, or an admin user with no employee record). The payload is returned once. */
CREATE OR REPLACE FUNCTION public.issue_approval_badge(p_employee_id uuid DEFAULT NULL, p_user_id uuid DEFAULT NULL, p_label text DEFAULT NULL, p_valid_days integer DEFAULT 365)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $$
DECLARE v_secret text; v_id uuid := gen_random_uuid(); v_expires timestamptz; v_name text; v_code text; v_grade text; v_role text;
BEGIN
  PERFORM private.require_approver_admin();
  IF (p_employee_id IS NULL) = (p_user_id IS NULL) THEN RAISE EXCEPTION 'one badge holder required (employee or user)'; END IF;
  IF private.badge_holder_source(p_employee_id, p_user_id) IS NULL THEN RAISE EXCEPTION 'only a manager or an assigned approver can hold an approval badge'; END IF;
  IF COALESCE(p_valid_days, 0) < 1 OR p_valid_days > 1095 THEN RAISE EXCEPTION 'badge validity must be 1 to 1095 days'; END IF;
  v_secret := translate(encode(extensions.gen_random_bytes(32), 'base64'), '+/=', '-_');
  v_expires := now() + make_interval(days => p_valid_days);
  INSERT INTO public.staff_approval_badges(id, employee_id, user_id, label, token_sha256, issued_by, expires_at)
  VALUES (v_id, p_employee_id, p_user_id, NULLIF(trim(COALESCE(p_label, '')), ''), encode(extensions.digest(v_secret, 'sha256'), 'hex'), auth.uid(), v_expires);
  INSERT INTO public.staff_approver_admin_events(actor_id, event, employee_id, user_id, badge_id, notes)
  VALUES (auth.uid(), 'badge_issued', p_employee_id, p_user_id, v_id, p_label);
  IF p_employee_id IS NOT NULL THEN
    SELECT e.full_name, e.employee_code::text, g.title, r.title INTO v_name, v_code, v_grade, v_role
    FROM public.employees e LEFT JOIN public.hr_grades g ON g.id = e.grade_id LEFT JOIN public.hr_roles r ON r.id = e.hr_role_id
    WHERE e.id = p_employee_id;
  ELSE
    SELECT u.email::text, 'Administrator' INTO v_name, v_role FROM auth.users u WHERE u.id = p_user_id;
  END IF;
  RETURN jsonb_build_object('badge_id', v_id, 'payload', 'GTRMGR1:' || v_id::text || ':' || v_secret, 'expires_at', v_expires,
    'full_name', v_name, 'employee_code', v_code, 'title', COALESCE(v_role, v_grade));
END $$;

CREATE OR REPLACE FUNCTION public.revoke_approval_badge(p_badge_id uuid, p_reason text)
RETURNS uuid LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $$
DECLARE b public.staff_approval_badges%ROWTYPE;
BEGIN
  PERFORM private.require_approver_admin();
  IF trim(COALESCE(p_reason, '')) = '' THEN RAISE EXCEPTION 'revocation reason required'; END IF;
  UPDATE public.staff_approval_badges SET revoked_at = now(), revoked_by = auth.uid(), revoke_reason = trim(p_reason)
  WHERE id = p_badge_id AND revoked_at IS NULL RETURNING * INTO b;
  IF b.id IS NULL THEN RAISE EXCEPTION 'active badge not found'; END IF;
  INSERT INTO public.staff_approver_admin_events(actor_id, event, employee_id, user_id, badge_id, notes)
  VALUES (auth.uid(), 'badge_revoked', b.employee_id, b.user_id, p_badge_id, trim(p_reason));
  RETURN p_badge_id;
END $$;

CREATE OR REPLACE FUNCTION public.list_approval_badges()
RETURNS TABLE(badge_id uuid, employee_id uuid, user_id uuid, full_name text, label text, issued_at timestamptz, expires_at timestamptz,
              revoked_at timestamptz, revoke_reason text, last_used_at timestamptz, use_count integer, status text)
LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path = '' AS $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM public.staff_roles sr WHERE sr.user_id = auth.uid() AND sr.role IN ('admin','hr','finance')) THEN
    RAISE EXCEPTION 'admin, HR or finance role required';
  END IF;
  RETURN QUERY
  SELECT b.id, b.employee_id, b.user_id, COALESCE(e.full_name, u.email::text), b.label, b.issued_at, b.expires_at, b.revoked_at, b.revoke_reason,
         b.last_used_at, b.use_count,
         CASE WHEN b.revoked_at IS NOT NULL THEN 'revoked' WHEN b.expires_at <= now() THEN 'expired' ELSE 'active' END
  FROM public.staff_approval_badges b
  LEFT JOIN public.employees e ON e.id = b.employee_id
  LEFT JOIN auth.users u ON u.id = b.user_id
  ORDER BY b.issued_at DESC;
END $$;

-- ───────────────────────────── badge approval (same contract as before; holder is employee or user)

CREATE OR REPLACE FUNCTION public.pos_badge_approve(p_badge text, p_action text, p_args jsonb DEFAULT '{}'::jsonb, p_device_id text DEFAULT NULL)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $$
DECLARE
  b public.staff_approval_badges%ROWTYPE;
  v_parts text[]; v_secret text; v_grant uuid; v_result jsonb; v_name text; v_holder_user uuid;
  v_err text; v_reject text; a jsonb := COALESCE(p_args, '{}'::jsonb);
BEGIN
  IF auth.uid() IS NULL THEN RAISE EXCEPTION 'sign in required'; END IF;
  IF p_action NOT IN ('discount','price_override','void_sale','refund','cash_out','till_variance','till_handover','repair_paid_order') THEN
    RAISE EXCEPTION 'unknown action %', p_action;
  END IF;
  IF (SELECT count(*) FROM public.staff_badge_approvals x
      WHERE x.requested_by = auth.uid() AND x.outcome = 'rejected' AND x.created_at > now() - interval '15 minutes') >= 5 THEN
    RAISE EXCEPTION 'too many rejected badge scans; try again in 15 minutes or ask the approver to sign in';
  END IF;

  v_parts := string_to_array(trim(COALESCE(p_badge, '')), ':');
  IF array_length(v_parts, 1) = 3 AND v_parts[1] = 'GTRMGR1' AND v_parts[2] ~ '^[0-9a-f-]{36}$' THEN
    v_secret := v_parts[3];
    SELECT * INTO b FROM public.staff_approval_badges WHERE id = v_parts[2]::uuid FOR UPDATE;
  END IF;
  v_reject := CASE
    WHEN b.id IS NULL THEN 'not an approval badge'
    WHEN b.token_sha256 <> encode(extensions.digest(v_secret, 'sha256'), 'hex') THEN 'badge not recognised'
    WHEN b.revoked_at IS NOT NULL THEN 'badge revoked'
    WHEN b.expires_at <= now() THEN 'badge expired'
    WHEN private.badge_holder_source(b.employee_id, b.user_id) IS NULL THEN 'badge holder is no longer an approver'
    ELSE NULL END;
  IF v_reject IS NOT NULL THEN
    INSERT INTO public.staff_badge_approvals(badge_id, approver_employee_id, approver_user_id, requested_by, action, args, device_id, outcome, error)
    VALUES (b.id, b.employee_id, b.user_id, auth.uid(), p_action, a, p_device_id, 'rejected', v_reject);
    RETURN jsonb_build_object('ok', false, 'error', v_reject);
  END IF;
  v_holder_user := COALESCE(b.user_id, (SELECT e.user_id FROM public.employees e WHERE e.id = b.employee_id));
  SELECT COALESCE(e.full_name, u.email::text) INTO v_name
  FROM (SELECT 1) one LEFT JOIN public.employees e ON e.id = b.employee_id LEFT JOIN auth.users u ON u.id = b.user_id;

  BEGIN
    INSERT INTO private.staff_badge_grants(txid, requested_by, approver_employee_id, approver_user_id, badge_id)
    VALUES (txid_current(), auth.uid(), b.employee_id, v_holder_user, b.id) RETURNING id INTO v_grant;
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

    -- The governed functions stamp auth.uid() as approver; under a badge the approver is its holder.
    UPDATE public.pos_action_audit SET approved_by_user_id = v_holder_user, approved_by_employee_id = b.employee_id
    WHERE created_at = now() AND actor_user_id = auth.uid() AND approved_by_user_id = auth.uid();
    DELETE FROM private.staff_badge_grants WHERE id = v_grant;
  EXCEPTION WHEN OTHERS THEN
    v_err := SQLERRM;
  END;
  PERFORM set_config('gtr.pos_badge_grant', '', true);

  IF v_err IS NOT NULL THEN
    INSERT INTO public.staff_badge_approvals(badge_id, approver_employee_id, approver_user_id, requested_by, action, args, device_id, outcome, error)
    VALUES (b.id, b.employee_id, b.user_id, auth.uid(), p_action, a, p_device_id, 'failed', v_err);
    RETURN jsonb_build_object('ok', false, 'error', v_err, 'manager_name', v_name);
  END IF;
  UPDATE public.staff_approval_badges SET last_used_at = now(), use_count = use_count + 1 WHERE id = b.id;
  INSERT INTO public.staff_badge_approvals(badge_id, approver_employee_id, approver_user_id, requested_by, action, args, device_id, outcome, result)
  VALUES (b.id, b.employee_id, b.user_id, auth.uid(), p_action, a, p_device_id, 'approved', jsonb_build_object('value', v_result));
  RETURN jsonb_build_object('ok', true, 'manager_name', v_name, 'result', v_result);
END $$;

-- ───────────────────────────── audit trail

CREATE OR REPLACE FUNCTION public.list_approval_trail(p_limit integer DEFAULT 200)
RETURNS TABLE(at timestamptz, method text, outcome text, action text, manager_name text, requested_by_name text,
              reason_code text, detail text, device_id text)
LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path = '' AS $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM public.staff_roles sr WHERE sr.user_id = auth.uid() AND sr.role IN ('admin','finance','hr')) THEN
    RAISE EXCEPTION 'admin, finance or HR role required';
  END IF;
  RETURN QUERY
  WITH unames AS (
    SELECT u.id, COALESCE((SELECT e.full_name FROM public.employees e WHERE e.user_id = u.id LIMIT 1), u.email::text) AS name FROM auth.users u
  ), trail(t_at, t_method, t_outcome, t_action, t_manager, t_requested_by, t_reason, t_detail, t_device) AS (
    SELECT x.created_at, 'badge'::text, x.outcome, x.action, COALESCE(e.full_name, mu.name), r.name, x.args->>'reason_code',
           COALESCE(x.error, x.args->>'notes'), x.device_id
    FROM public.staff_badge_approvals x
    LEFT JOIN public.employees e ON e.id = x.approver_employee_id
    LEFT JOIN unames mu ON mu.id = x.approver_user_id
    LEFT JOIN unames r ON r.id = x.requested_by
    UNION ALL
    SELECT p.created_at, 'approver_session'::text, 'approved'::text, p.action, m.name, r.name, p.reason_code, p.notes, NULL::text
    FROM public.pos_action_audit p
    LEFT JOIN unames m ON m.id = p.approved_by_user_id
    LEFT JOIN unames r ON r.id = p.actor_user_id
    WHERE p.approved_by_user_id IS NOT NULL AND p.approved_by_user_id = p.actor_user_id
    UNION ALL
    SELECT ev.created_at, 'admin'::text, ev.event, ev.event, a.name, COALESCE(te.full_name, tu.name), NULL::text, ev.notes, NULL::text
    FROM public.staff_approver_admin_events ev
    LEFT JOIN unames a ON a.id = ev.actor_id
    LEFT JOIN public.employees te ON te.id = ev.employee_id
    LEFT JOIN unames tu ON tu.id = ev.user_id
  )
  SELECT * FROM trail ORDER BY 1 DESC LIMIT LEAST(GREATEST(COALESCE(p_limit, 200), 1), 1000);
END $$;

REVOKE ALL ON FUNCTION private.employee_approver_source(uuid), private.pos_approver_source(uuid), private.badge_holder_source(uuid, uuid),
  private.staff_badge_grant_active(), private.require_approver_admin() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.list_approver_candidates(), public.set_approver_assignment(uuid, boolean, text),
  public.issue_approval_badge(uuid, uuid, text, integer), public.revoke_approval_badge(uuid, text), public.list_approval_badges(),
  public.pos_badge_approve(text, text, jsonb, text), public.list_approval_trail(integer) TO authenticated;
REVOKE EXECUTE ON FUNCTION public.list_approver_candidates(), public.set_approver_assignment(uuid, boolean, text),
  public.issue_approval_badge(uuid, uuid, text, integer), public.revoke_approval_badge(uuid, text), public.list_approval_badges(),
  public.pos_badge_approve(text, text, jsonb, text), public.list_approval_trail(integer) FROM anon, PUBLIC;
