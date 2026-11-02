-- Customer suspensions: who can read them, and looking never changes anything.
-- 1. The suspension list (reasons, findings, debts) was readable by every staff member (drivers,
--    warehouse, HR). It is now for managers (POS approvers), finance and admin. The counter still
--    learns whether the customer in front of it is suspended (get_customer_suspension, any staff).
-- 2. get_customer_suspension and get_my_account_suspension suspended the customer as a side effect of
--    being looked up. They now only report: an active suspension, or "due" (the rules already apply,
--    computed without writing). The suspension is recorded where it is enforced: checkout / on-account
--    (assert_customer_not_suspended), refused deliveries, and the nightly sweep.

DROP POLICY IF EXISTS customer_suspensions_staff_read ON public.customer_suspensions;
CREATE POLICY customer_suspensions_manager_read ON public.customer_suspensions FOR SELECT TO authenticated
  USING (public.has_staff_role(ARRAY['admin','finance']::public.staff_role[]) OR public.is_pos_approver());

CREATE OR REPLACE FUNCTION public.list_customer_suspensions(p_status text DEFAULT 'active'::text, p_limit integer DEFAULT 100)
 RETURNS jsonb LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path TO '' AS $f$
BEGIN
 IF NOT (public.has_staff_role(ARRAY['admin','finance']::public.staff_role[]) OR public.is_pos_approver()) THEN
  RAISE EXCEPTION 'manager, finance or admin required';
 END IF;
 RETURN COALESCE((SELECT jsonb_agg(private._customer_suspension_json(s) ORDER BY (s.status='active') DESC, s.suspended_at DESC)
  FROM (SELECT * FROM public.customer_suspensions WHERE p_status IS NULL OR status=p_status ORDER BY suspended_at DESC
        LIMIT LEAST(GREATEST(COALESCE(p_limit,100),1),500)) s),'[]'::jsonb);
END $f$;

-- The reason text the automatic suspension uses, from its findings.
CREATE OR REPLACE FUNCTION private.suspension_reason(p_findings jsonb)
 RETURNS text LANGUAGE sql IMMUTABLE SET search_path TO '' AS $f$
 SELECT 'Failed to settle: ' || string_agg(DISTINCT CASE f->>'rule'
   WHEN 'on_account_overdue' THEN 'balance left on account unpaid after 7 days'
   WHEN 'invoice_overdue' THEN 'invoice unpaid after 30 days'
   WHEN 'refused_cod' THEN 'refused pay-on-delivery deliveries' END, '; ')
 FROM jsonb_array_elements(p_findings) f
$f$;

-- Read-only: the active suspension, else the one the rules would record now (status 'due'), else NULL.
CREATE OR REPLACE FUNCTION private.customer_suspension_preview(p_customer_id uuid)
 RETURNS jsonb LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path TO '' AS $f$
DECLARE s public.customer_suspensions%ROWTYPE; v_findings jsonb; e jsonb;
BEGIN
 IF p_customer_id IS NULL THEN RETURN NULL; END IF;
 SELECT * INTO s FROM public.customer_suspensions WHERE customer_id=p_customer_id AND status='active';
 IF FOUND THEN RETURN private._customer_suspension_json(s); END IF;
 v_findings := private.customer_suspension_findings(p_customer_id);
 IF jsonb_array_length(v_findings)=0 THEN RETURN NULL; END IF;
 e := private.customer_exposure(p_customer_id);
 RETURN jsonb_build_object('id',NULL,'customer_id',p_customer_id,
  'customer_name',(SELECT COALESCE(c.business_name,c.display_name) FROM public.customers c WHERE c.id=p_customer_id),
  'status','due','source','automatic','reason',private.suspension_reason(v_findings),'findings',v_findings,
  'suspended_by_name',NULL,'suspended_at',NULL,'lifted_by_name',NULL,'lifted_at',NULL,'lift_reason',NULL,
  'owing',e->'total','owing_currency',e->'currency','owing_by_currency',e->'by_currency');
END $f$;

CREATE OR REPLACE FUNCTION private.suspend_customer_if_due(p_customer_id uuid)
 RETURNS uuid LANGUAGE plpgsql SECURITY DEFINER SET search_path TO '' AS $f$
DECLARE v_id uuid; v_findings jsonb;
BEGIN
 IF p_customer_id IS NULL THEN RETURN NULL; END IF;
 SELECT id INTO v_id FROM public.customer_suspensions WHERE customer_id=p_customer_id AND status='active';
 IF FOUND THEN RETURN v_id; END IF;
 v_findings := private.customer_suspension_findings(p_customer_id);
 IF jsonb_array_length(v_findings)=0 THEN RETURN NULL; END IF;
 INSERT INTO public.customer_suspensions(customer_id,status,source,reason,findings)
 VALUES(p_customer_id,'active','automatic',private.suspension_reason(v_findings),v_findings)
 ON CONFLICT DO NOTHING RETURNING id INTO v_id;
 RETURN v_id;
END $f$;

CREATE OR REPLACE FUNCTION public.get_customer_suspension(p_customer_id uuid)
 RETURNS jsonb LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path TO '' AS $f$
BEGIN
 IF NOT public.is_staff() THEN RAISE EXCEPTION 'staff required'; END IF;
 RETURN private.customer_suspension_preview(p_customer_id);
END $f$;

CREATE OR REPLACE FUNCTION public.get_my_account_suspension()
 RETURNS jsonb LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path TO '' AS $f$
DECLARE v_cust uuid := public._current_customer_id(); p jsonb;
BEGIN
 IF v_cust IS NULL THEN RETURN NULL; END IF;
 p := private.customer_suspension_preview(v_cust);
 IF p IS NULL THEN RETURN NULL; END IF;
 RETURN jsonb_build_object('suspended',true,'reason',p->>'reason','since',p->'suspended_at',
  'owing',p->'owing','owing_currency',p->'owing_currency','owing_by_currency',p->'owing_by_currency');
END $f$;

REVOKE ALL ON FUNCTION private.suspension_reason(jsonb) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION private.customer_suspension_preview(uuid) FROM PUBLIC, anon, authenticated;
