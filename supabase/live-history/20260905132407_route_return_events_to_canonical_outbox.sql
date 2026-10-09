-- Exported from the hosted project's supabase_migrations.schema_migrations (20260905132407 route_return_events_to_canonical_outbox).
-- Source of record for what production ran; see supabase/live-history/README.md.

CREATE OR REPLACE FUNCTION private.enqueue_commerce_event(
  p_event_key text,
  p_event_type text,
  p_aggregate_id uuid,
  p_payload jsonb DEFAULT '{}'::jsonb
)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $$
DECLARE v_id uuid; v_aggregate_type text;
BEGIN
  v_aggregate_type:=CASE
    WHEN p_event_type LIKE 'return.%' THEN 'return_request'
    WHEN p_event_type LIKE 'refund.%' THEN 'return_refund'
    ELSE 'commerce_order'
  END;
  INSERT INTO public.commerce_outbox(event_key,event_type,aggregate_type,aggregate_id,payload)
  VALUES(p_event_key,p_event_type,v_aggregate_type,p_aggregate_id,COALESCE(p_payload,'{}'::jsonb))
  ON CONFLICT(event_key) DO NOTHING
  RETURNING id INTO v_id;
  IF v_id IS NULL THEN SELECT id INTO v_id FROM public.commerce_outbox WHERE event_key=p_event_key; END IF;
  RETURN v_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.emit_domain_event(
  p_event_code text,
  p_dedupe_key text,
  p_payload jsonb DEFAULT '{}'::jsonb,
  p_actor_user_id uuid DEFAULT auth.uid(),
  p_message_body text DEFAULT NULL::text
)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $$
DECLARE
  v_event_id uuid;
  v_body text;
  v_desc text;
  v_aggregate_id uuid;
  v_event_type text;
BEGIN
  IF auth.role()='authenticated'
     AND NOT public.is_staff()
     AND NOT public._storefront_rpc_active() THEN
    RAISE EXCEPTION 'Only staff or service role may emit domain events';
  END IF;

  IF NOT EXISTS(
    SELECT 1 FROM public.sms_event_catalog c
    WHERE c.code=p_event_code AND c.is_active
  ) THEN
    IF p_event_code IN ('return_approved','return_rejected','return_received','refund_pending','refund_completed') THEN
      v_aggregate_id:=COALESCE(
        NULLIF(p_payload->>'return_request_id','')::uuid,
        NULLIF(p_payload->>'refund_request_id','')::uuid
      );
      IF v_aggregate_id IS NULL THEN
        RAISE EXCEPTION 'return/refund outbox event requires aggregate id';
      END IF;
      v_event_type:=CASE p_event_code
        WHEN 'return_approved' THEN 'return.approved'
        WHEN 'return_rejected' THEN 'return.rejected'
        WHEN 'return_received' THEN 'return.received'
        WHEN 'refund_pending' THEN 'refund.pending'
        WHEN 'refund_completed' THEN 'refund.completed'
      END;
      RETURN private.enqueue_commerce_event(p_dedupe_key,v_event_type,v_aggregate_id,COALESCE(p_payload,'{}'::jsonb));
    END IF;
    RAISE EXCEPTION 'Unknown or inactive event_code: %',p_event_code;
  END IF;

  INSERT INTO public.domain_events(event_code,dedupe_key,payload,actor_user_id)
  VALUES(p_event_code,p_dedupe_key,COALESCE(p_payload,'{}'::jsonb),p_actor_user_id)
  ON CONFLICT(event_code,dedupe_key) DO NOTHING
  RETURNING id INTO v_event_id;

  IF v_event_id IS NULL THEN
    SELECT id INTO v_event_id FROM public.domain_events
    WHERE event_code=p_event_code AND dedupe_key=p_dedupe_key;
  END IF;

  SELECT description INTO v_desc FROM public.sms_event_catalog WHERE code=p_event_code;
  v_body:=COALESCE(p_message_body,format('GTR Auto: %s (%s)',v_desc,p_event_code));
  INSERT INTO public.sms_outbox(domain_event_id,event_code,recipient_user_id,phone_e164,body)
  SELECT v_event_id,p_event_code,p.user_id,p.phone_e164,v_body
  FROM public.manager_sms_preferences p
  WHERE p.event_code=p_event_code AND p.enabled=true
  ON CONFLICT(domain_event_id,recipient_user_id) DO NOTHING;
  RETURN v_event_id;
END;
$$;
