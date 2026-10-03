-- POS split payments (phase 4): refunds of captured split legs are manager/finance steps
-- (approve_pos_split_refund, complete_pos_split_refund, fail_pos_split_refund check is_pos_approver()).
-- Let an approver's ID badge authorise them at the counter, the same way as the other governed actions.
-- Same contract as 20261003144849; only the action list grows.

CREATE OR REPLACE FUNCTION public.pos_badge_approve(p_badge text, p_action text, p_args jsonb DEFAULT '{}'::jsonb, p_device_id text DEFAULT NULL)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $$
DECLARE
  b public.staff_approval_badges%ROWTYPE;
  v_parts text[]; v_secret text; v_grant uuid; v_result jsonb; v_name text; v_holder_user uuid;
  v_err text; v_reject text; a jsonb := COALESCE(p_args, '{}'::jsonb);
BEGIN
  IF auth.uid() IS NULL THEN RAISE EXCEPTION 'sign in required'; END IF;
  IF p_action NOT IN ('discount','price_override','void_sale','refund','cash_out','till_variance','till_handover','repair_paid_order',
                      'split_refund_approve','split_refund_complete','split_refund_fail') THEN
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
      WHEN 'split_refund_approve' THEN public.approve_pos_split_refund((a->>'refund_id')::uuid, COALESCE(a->>'fee_policy', 'business_absorbs'),
                                     (a->>'estimated_provider_fee')::numeric, (a->>'estimated_transfer_fee')::numeric,
                                     COALESCE((a->>'customer_fee')::numeric, 0), COALESCE((a->>'expected_days')::integer, 3), a->>'notes')
      WHEN 'split_refund_complete' THEN public.complete_pos_split_refund((a->>'refund_id')::uuid, a->>'provider_ref',
                                     (a->>'actual_provider_fee')::numeric, (a->>'actual_transfer_fee')::numeric,
                                     (a->>'actual_customer_fee')::numeric, a->>'notes')
      WHEN 'split_refund_fail' THEN public.fail_pos_split_refund((a->>'refund_id')::uuid, a->>'reason')
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
