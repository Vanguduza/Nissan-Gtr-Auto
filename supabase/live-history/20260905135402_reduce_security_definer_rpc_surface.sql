-- Exported from the hosted project's supabase_migrations.schema_migrations (20260905135402 reduce_security_definer_rpc_surface).
-- Source of record for what production ran; see supabase/live-history/README.md.

-- Trigger functions are database internals, not RPC endpoints.
REVOKE ALL ON FUNCTION public.chat_messages_after_insert() FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.trg_delivery_job_online_auto_assign() FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.trg_pick_list_online_auto_ship() FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.trg_sales_invoice_online_dispatch_finalize() FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.customers_protect_privileged_columns() FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.handle_new_user() FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.sync_profile_is_staff() FROM PUBLIC, anon, authenticated;

-- Worker-only SECURITY DEFINER functions must be service-role-only RPC surfaces.
REVOKE ALL ON FUNCTION public.claim_hr_auth_provision(uuid,text,integer) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.complete_hr_credential_outbox(uuid,boolean,text,text) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.enqueue_hr_credential_outbox(uuid,uuid,public.hr_credential_channel,text,text) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.finalize_ai_promo_run(uuid,public.ai_promo_run_status,integer,integer,boolean,text) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.finalize_ai_report_run(uuid,public.ai_report_run_status,jsonb,text,text,boolean) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.insert_ai_promo_delivery(uuid,uuid,public.ai_delivery_channel,text,public.ai_delivery_status,text,text[],text,text,text,boolean) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.insert_ai_promo_run() FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.insert_ai_report_delivery(uuid,public.ai_delivery_channel,text,public.ai_delivery_status,text,text) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.insert_ai_report_run(uuid,public.ai_report_cadence,timestamptz,timestamptz) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.link_employee_auth_user(uuid,uuid,text,text) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.list_due_ai_report_subscriptions(public.ai_report_cadence,timestamptz,boolean,uuid) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.release_hr_auth_provision(uuid,text) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.scrub_hr_credential_outbox_bodies(integer) FROM PUBLIC, anon, authenticated;

GRANT EXECUTE ON FUNCTION public.claim_hr_auth_provision(uuid,text,integer) TO service_role;
GRANT EXECUTE ON FUNCTION public.complete_hr_credential_outbox(uuid,boolean,text,text) TO service_role;
GRANT EXECUTE ON FUNCTION public.enqueue_hr_credential_outbox(uuid,uuid,public.hr_credential_channel,text,text) TO service_role;
GRANT EXECUTE ON FUNCTION public.finalize_ai_promo_run(uuid,public.ai_promo_run_status,integer,integer,boolean,text) TO service_role;
GRANT EXECUTE ON FUNCTION public.finalize_ai_report_run(uuid,public.ai_report_run_status,jsonb,text,text,boolean) TO service_role;
GRANT EXECUTE ON FUNCTION public.insert_ai_promo_delivery(uuid,uuid,public.ai_delivery_channel,text,public.ai_delivery_status,text,text[],text,text,text,boolean) TO service_role;
GRANT EXECUTE ON FUNCTION public.insert_ai_promo_run() TO service_role;
GRANT EXECUTE ON FUNCTION public.insert_ai_report_delivery(uuid,public.ai_delivery_channel,text,public.ai_delivery_status,text,text) TO service_role;
GRANT EXECUTE ON FUNCTION public.insert_ai_report_run(uuid,public.ai_report_cadence,timestamptz,timestamptz) TO service_role;
GRANT EXECUTE ON FUNCTION public.link_employee_auth_user(uuid,uuid,text,text) TO service_role;
GRANT EXECUTE ON FUNCTION public.list_due_ai_report_subscriptions(public.ai_report_cadence,timestamptz,boolean,uuid) TO service_role;
GRANT EXECUTE ON FUNCTION public.release_hr_auth_provision(uuid,text) TO service_role;
GRANT EXECUTE ON FUNCTION public.scrub_hr_credential_outbox_bodies(integer) TO service_role;

-- Forecast conversion is staff procurement functionality, never anonymous.
CREATE OR REPLACE FUNCTION public.create_mr_from_forecast(
  p_suggestion_ids uuid[],
  p_needed_by date DEFAULT NULL::date,
  p_notes text DEFAULT NULL::text
)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $function$
DECLARE
  v_wh uuid;
  v_lines jsonb := '[]'::jsonb;
  v_sug record;
  v_uom uuid;
  v_mr uuid;
  v_ids uuid[];
BEGIN
  PERFORM public._require_procurement_staff();
  PERFORM public._procurement_begin_rpc();

  IF p_suggestion_ids IS NULL OR cardinality(p_suggestion_ids)=0 THEN
    RAISE EXCEPTION 'suggestion ids required';
  END IF;
  v_ids:=p_suggestion_ids;

  SELECT DISTINCT warehouse_id INTO v_wh
  FROM public.forecast_suggestions
  WHERE id=ANY(v_ids) AND status='open';
  IF v_wh IS NULL THEN RAISE EXCEPTION 'no open forecast suggestions for given ids'; END IF;

  IF (
    SELECT count(DISTINCT warehouse_id)
    FROM public.forecast_suggestions
    WHERE id=ANY(v_ids) AND status='open'
  ) > 1 THEN
    RAISE EXCEPTION 'all suggestions must share one warehouse';
  END IF;

  FOR v_sug IN
    SELECT * FROM public.forecast_suggestions
    WHERE id=ANY(v_ids) AND status='open'
    ORDER BY id
  LOOP
    SELECT base_uom_id INTO v_uom FROM public.stock_items WHERE id=v_sug.stock_item_id;
    IF v_uom IS NULL THEN RAISE EXCEPTION 'stock item missing base_uom_id: %',v_sug.stock_item_id; END IF;
    v_lines:=v_lines||jsonb_build_array(jsonb_build_object(
      'stock_item_id',v_sug.stock_item_id,
      'uom_id',v_uom,
      'qty',v_sug.suggested_qty
    ));
  END LOOP;

  v_mr:=public.create_material_request(v_wh,p_needed_by,v_lines,COALESCE(p_notes,'From demand forecast suggestions'));

  UPDATE public.forecast_suggestions
  SET status='converted',material_request_id=v_mr,converted_at=now()
  WHERE id=ANY(v_ids) AND status='open';

  RETURN v_mr;
END;
$function$;

REVOKE ALL ON FUNCTION public.create_mr_from_forecast(uuid[],date,text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.create_mr_from_forecast(uuid[],date,text) TO authenticated, service_role;
