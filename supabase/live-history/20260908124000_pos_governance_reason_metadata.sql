-- Exported from the hosted project's supabase_migrations.schema_migrations (20260908124000 pos_governance_reason_metadata).
-- Source of record for what production ran; see supabase/live-history/README.md.

-- Complete POS governance metadata and close the governed void/refund gaps.
ALTER TABLE public.pos_approval_reason_codes
  ADD COLUMN IF NOT EXISTS requires_notes BOOLEAN NOT NULL DEFAULT false;

INSERT INTO public.pos_approval_policies(action,threshold_value,always_require_manager,reason_required) VALUES
 ('void_cart',0,true,true),
 ('refund_full_invoice',0,true,true)
ON CONFLICT(action) DO NOTHING;

INSERT INTO public.pos_approval_reason_codes(action,code,label,requires_notes,sort_order) VALUES
 ('void_cart','customer_cancelled','Customer cancelled',false,10),
 ('void_cart','duplicate_cart','Duplicate cart',false,20),
 ('void_cart','pricing_error','Pricing / setup error',true,30),
 ('void_cart','operator_error','Operator correction',true,40),
 ('refund_full_invoice','wrong_part','Wrong part supplied',false,10),
 ('refund_full_invoice','customer_changed_mind','Customer changed mind',false,20),
 ('refund_full_invoice','defective','Defective part',false,30),
 ('refund_full_invoice','manager_exception','Manager exception',true,40)
ON CONFLICT(action,code) DO UPDATE SET
 label=EXCLUDED.label,requires_notes=EXCLUDED.requires_notes,sort_order=EXCLUDED.sort_order;

DROP FUNCTION IF EXISTS public.list_pos_approval_reasons(TEXT);

CREATE FUNCTION public.list_pos_approval_reasons(p_action TEXT)
RETURNS TABLE(code TEXT,label TEXT,requires_notes BOOLEAN,sort_order INTEGER)
LANGUAGE sql STABLE SECURITY DEFINER SET search_path='' AS $$
 SELECT r.code,r.label,r.requires_notes,r.sort_order
 FROM public.pos_approval_reason_codes r
 WHERE r.action=p_action AND r.is_active
 ORDER BY r.sort_order,r.label;
$$;

CREATE OR REPLACE FUNCTION public.post_pos_refund_governed(
 p_invoice_id UUID,p_reason_code TEXT,p_notes TEXT DEFAULT NULL)
RETURNS UUID LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE v UUID;
BEGIN
 PERFORM private.require_pos_reason('refund_full_invoice',p_reason_code);
 IF public.pos_action_requires_manager('refund_full_invoice',0)
    AND NOT public.is_pos_approver() THEN
  RAISE EXCEPTION 'POS manager approval required';
 END IF;
 v:=public.post_pos_refund(p_invoice_id,concat_ws(' · ',p_reason_code,p_notes));
 UPDATE public.pos_action_audit
 SET reason_code=p_reason_code,
     approved_by_user_id=CASE WHEN public.is_pos_approver() THEN auth.uid() ELSE NULL END,
     approval_policy_action='refund_full_invoice'
 WHERE id=(SELECT id FROM public.pos_action_audit
   WHERE action='refund_posted' AND entity_id=v ORDER BY created_at DESC LIMIT 1);
 RETURN v;
END $$;

REVOKE ALL ON FUNCTION public.list_pos_approval_reasons(TEXT) FROM PUBLIC,anon;

REVOKE ALL ON FUNCTION public.post_pos_refund_governed(UUID,TEXT,TEXT) FROM PUBLIC,anon;

GRANT EXECUTE ON FUNCTION public.list_pos_approval_reasons(TEXT),
 public.post_pos_refund_governed(UUID,TEXT,TEXT) TO authenticated,service_role;
