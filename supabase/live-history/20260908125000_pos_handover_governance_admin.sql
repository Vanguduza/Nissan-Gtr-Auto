-- Exported from the hosted project's supabase_migrations.schema_migrations (20260908125000 pos_handover_governance_admin).
-- Source of record for what production ran; see supabase/live-history/README.md.

-- POS handover operator discovery + governance administration read model.
CREATE OR REPLACE FUNCTION public.list_pos_handover_operators()
RETURNS TABLE(user_id UUID,employee_code TEXT,full_name TEXT,roles TEXT[])
LANGUAGE sql STABLE SECURITY DEFINER SET search_path='' AS $$
 SELECT e.user_id,e.employee_code::text,e.full_name,
  ARRAY(SELECT sr.role::text FROM public.staff_roles sr WHERE sr.user_id=e.user_id ORDER BY sr.role::text)
 FROM public.employees e
 WHERE e.status='active' AND e.user_id IS NOT NULL
  AND EXISTS(SELECT 1 FROM public.staff_roles sr WHERE sr.user_id=e.user_id AND sr.role IN('sales','admin'))
 ORDER BY e.full_name,e.employee_code;
$$;

CREATE OR REPLACE FUNCTION public.list_pos_approval_policies()
RETURNS TABLE(action TEXT,threshold_value NUMERIC,always_require_manager BOOLEAN,reason_required BOOLEAN,updated_at TIMESTAMPTZ)
LANGUAGE sql STABLE SECURITY DEFINER SET search_path='' AS $$
 SELECT p.action,p.threshold_value,p.always_require_manager,p.reason_required,p.updated_at
 FROM public.pos_approval_policies p ORDER BY p.action;
$$;

CREATE OR REPLACE FUNCTION public.handover_pos_till_session(p_session_id UUID,p_new_operator_user_id UUID,p_notes TEXT DEFAULT NULL)
RETURNS UUID LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE s public.pos_till_sessions%ROWTYPE;
BEGIN
 IF NOT public.is_pos_approver() THEN RAISE EXCEPTION 'POS manager approval required'; END IF;
 SELECT * INTO s FROM public.pos_till_sessions WHERE id=p_session_id FOR UPDATE;
 IF NOT FOUND OR s.status<>'open' THEN RAISE EXCEPTION 'open till session required'; END IF;
 IF p_new_operator_user_id IS NULL OR p_new_operator_user_id=s.operator_user_id THEN RAISE EXCEPTION 'different operator required'; END IF;
 IF NOT EXISTS(
  SELECT 1 FROM public.employees e JOIN public.staff_roles sr ON sr.user_id=e.user_id
  WHERE e.user_id=p_new_operator_user_id AND e.status='active' AND sr.role IN('sales','admin')
 ) THEN RAISE EXCEPTION 'active sales/admin operator required'; END IF;
 IF EXISTS(SELECT 1 FROM public.pos_till_sessions WHERE operator_user_id=p_new_operator_user_id AND status IN('open','variance_pending') AND id<>p_session_id) THEN
  RAISE EXCEPTION 'new operator already has an open till';
 END IF;
 UPDATE public.pos_till_sessions SET operator_user_id=p_new_operator_user_id,updated_at=now() WHERE id=p_session_id;
 INSERT INTO public.pos_till_session_events(session_id,event_type,actor_user_id,detail)
 VALUES(p_session_id,'handover',auth.uid(),jsonb_build_object('from_operator_user_id',s.operator_user_id,'to_operator_user_id',p_new_operator_user_id,'notes',p_notes));
 RETURN p_session_id;
END $$;

REVOKE ALL ON FUNCTION public.list_pos_handover_operators() FROM PUBLIC,anon;

REVOKE ALL ON FUNCTION public.list_pos_approval_policies() FROM PUBLIC,anon;

REVOKE ALL ON FUNCTION public.handover_pos_till_session(UUID,UUID,TEXT) FROM PUBLIC,anon;

GRANT EXECUTE ON FUNCTION public.list_pos_handover_operators(),
 public.list_pos_approval_policies(),public.handover_pos_till_session(UUID,UUID,TEXT)
 TO authenticated,service_role;
