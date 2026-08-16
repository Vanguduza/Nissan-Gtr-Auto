-- Advisor hardening (hosted Security + Performance ERROR/WARN)
-- Fixes: security_definer_view, function_search_path_mutable,
-- anon/authenticated_security_definer_function_executable, auth_rls_initplan.
-- Deferred: auth_leaked_password_protection (Auth dashboard),
-- multiple_permissive_policies (intentional overlapping staff policies),
-- remaining authenticated EXECUTE on client SECURITY DEFINER RPCs (authz inside).

BEGIN;

-- 1) ERROR: security_definer_view
ALTER VIEW public.v_master_stock SET (security_invoker = true);
REVOKE ALL ON TABLE public.v_master_stock FROM PUBLIC;
REVOKE ALL ON TABLE public.v_master_stock FROM anon;
REVOKE ALL ON TABLE public.v_master_stock FROM authenticated;
COMMENT ON VIEW public.v_master_stock IS
  'Master stock totals + WH1/WH2. security_invoker; no SELECT for authenticated — use list_master_stock.';

-- 2) WARN: function_search_path_mutable
ALTER FUNCTION public._cart_line_dual_write_minor() SET search_path = public, pg_temp;
ALTER FUNCTION public._chat_staff_roles() SET search_path = public, pg_temp;
ALTER FUNCTION public._customer_compare_max_items() SET search_path = public, pg_temp;
ALTER FUNCTION public._customers_credit_dual_write_minor() SET search_path = public, pg_temp;
ALTER FUNCTION public._delivery_pod_job_id_from_path(p_name text) SET search_path = public, pg_temp;
ALTER FUNCTION public._driver_shift_active(p_starts timestamp with time zone, p_ends timestamp with time zone, p_at timestamp with time zone) SET search_path = public, pg_temp;
ALTER FUNCTION public._finance_period_rpc_active() SET search_path = public, pg_temp;
ALTER FUNCTION public._finance_period_rpc_enter() SET search_path = public, pg_temp;
ALTER FUNCTION public._finance_req_id_from_receipt_path(p_name text) SET search_path = public, pg_temp;
ALTER FUNCTION public._finance_req_required_approvals(p_req_type finance_requisition_type, p_currency currency_code, p_amount numeric) SET search_path = public, pg_temp;
ALTER FUNCTION public._finance_req_rpc_active() SET search_path = public, pg_temp;
ALTER FUNCTION public._finance_req_rpc_enter() SET search_path = public, pg_temp;
ALTER FUNCTION public._fleet_begin_rpc() SET search_path = public, pg_temp;
ALTER FUNCTION public._fleet_rpc_active() SET search_path = public, pg_temp;
ALTER FUNCTION public._haversine_eta_seconds(p_from_lat double precision, p_from_lng double precision, p_to_lat double precision, p_to_lng double precision) SET search_path = public, pg_temp;
ALTER FUNCTION public._haversine_meters(p_lat1 double precision, p_lng1 double precision, p_lat2 double precision, p_lng2 double precision) SET search_path = public, pg_temp;
ALTER FUNCTION public._invoice_line_dual_write_minor() SET search_path = public, pg_temp;
ALTER FUNCTION public._journal_line_dual_write_minor() SET search_path = public, pg_temp;
ALTER FUNCTION public._line_usd_equiv(p_amount numeric, p_currency currency_code, p_rate numeric) SET search_path = public, pg_temp;
ALTER FUNCTION public._logistics_begin_rpc() SET search_path = public, pg_temp;
ALTER FUNCTION public._logistics_rpc_active() SET search_path = public, pg_temp;
ALTER FUNCTION public._loyalty_ledger_dual_write_minor() SET search_path = public, pg_temp;
ALTER FUNCTION public._loyalty_rpc_active() SET search_path = public, pg_temp;
ALTER FUNCTION public._loyalty_rpc_enter() SET search_path = public, pg_temp;
ALTER FUNCTION public._major_to_minor(p_amount numeric) SET search_path = public, pg_temp;
ALTER FUNCTION public._normalize_delivery_pod_object_path(p_path text) SET search_path = public, pg_temp;
ALTER FUNCTION public._normalize_e164(p_phone text) SET search_path = public, pg_temp;
ALTER FUNCTION public._normalize_fleet_plate(p_plate text) SET search_path = public, pg_temp;
ALTER FUNCTION public._normalize_receipt_email(p_email text) SET search_path = public, pg_temp;
ALTER FUNCTION public._online_dispatch_auto_active() SET search_path = public, pg_temp;
ALTER FUNCTION public._online_dispatch_auto_begin() SET search_path = public, pg_temp;
ALTER FUNCTION public._online_dispatch_auto_clear() SET search_path = public, pg_temp;
ALTER FUNCTION public._payment_entry_dual_write_minor() SET search_path = public, pg_temp;
ALTER FUNCTION public._payments_rpc_active() SET search_path = public, pg_temp;
ALTER FUNCTION public._payments_rpc_enter() SET search_path = public, pg_temp;
ALTER FUNCTION public._payroll_begin_rpc() SET search_path = public, pg_temp;
ALTER FUNCTION public._payroll_rpc_active() SET search_path = public, pg_temp;
ALTER FUNCTION public._po_line_dual_write_minor() SET search_path = public, pg_temp;
ALTER FUNCTION public._po_set_progress_on_submit() SET search_path = public, pg_temp;
ALTER FUNCTION public._procurement_begin_rpc() SET search_path = public, pg_temp;
ALTER FUNCTION public._procurement_end_rpc() SET search_path = public, pg_temp;
ALTER FUNCTION public._procurement_rpc_active() SET search_path = public, pg_temp;
ALTER FUNCTION public._recon_begin_rpc() SET search_path = public, pg_temp;
ALTER FUNCTION public._recon_rpc_active() SET search_path = public, pg_temp;
ALTER FUNCTION public._require_dispatcher_staff() SET search_path = public, pg_temp;
ALTER FUNCTION public._require_fleet_staff() SET search_path = public, pg_temp;
ALTER FUNCTION public._require_kit_staff() SET search_path = public, pg_temp;
ALTER FUNCTION public._require_logistics_staff() SET search_path = public, pg_temp;
ALTER FUNCTION public._require_loyalty_staff() SET search_path = public, pg_temp;
ALTER FUNCTION public._require_payments_staff() SET search_path = public, pg_temp;
ALTER FUNCTION public._require_sales_staff() SET search_path = public, pg_temp;
ALTER FUNCTION public._require_warehouse_staff() SET search_path = public, pg_temp;
ALTER FUNCTION public._require_warranty_staff() SET search_path = public, pg_temp;
ALTER FUNCTION public._review_photo_review_id_from_path(p_name text) SET search_path = public, pg_temp;
ALTER FUNCTION public._staff_ops_notify_begin() SET search_path = public, pg_temp;
ALTER FUNCTION public._store_credit_account_dual_write_minor() SET search_path = public, pg_temp;
ALTER FUNCTION public._store_credit_ledger_dual_write_minor() SET search_path = public, pg_temp;
ALTER FUNCTION public._storefront_rpc_active() SET search_path = public, pg_temp;
ALTER FUNCTION public._storefront_rpc_enter() SET search_path = public, pg_temp;
ALTER FUNCTION public._storefront_rpc_exit() SET search_path = public, pg_temp;
ALTER FUNCTION public._warranty_begin_rpc() SET search_path = public, pg_temp;
ALTER FUNCTION public._warranty_claim_touch_updated() SET search_path = public, pg_temp;
ALTER FUNCTION public._warranty_rpc_active() SET search_path = public, pg_temp;
ALTER FUNCTION public.build_qr_payload(p_oem text, p_batch_code text, p_valuation valuation_method) SET search_path = public, pg_temp;
ALTER FUNCTION public.chat_messages_immutable_guard() SET search_path = public, pg_temp;
ALTER FUNCTION public.chat_threads_protect_columns() SET search_path = public, pg_temp;
ALTER FUNCTION public.chat_threads_touch_updated() SET search_path = public, pg_temp;
ALTER FUNCTION public.fleet_vehicles_touch_updated() SET search_path = public, pg_temp;
ALTER FUNCTION public.forbid_account_period_direct_mutation() SET search_path = public, pg_temp;
ALTER FUNCTION public.forbid_domain_event_mutation() SET search_path = public, pg_temp;
ALTER FUNCTION public.forbid_finance_requisition_direct_mutation() SET search_path = public, pg_temp;
ALTER FUNCTION public.forbid_finance_requisition_line_direct_mutation() SET search_path = public, pg_temp;
ALTER FUNCTION public.forbid_journal_entry_delete() SET search_path = public, pg_temp;
ALTER FUNCTION public.forbid_ledger_mutation() SET search_path = public, pg_temp;
ALTER FUNCTION public.forbid_loyalty_ledger_mutation() SET search_path = public, pg_temp;
ALTER FUNCTION public.forbid_posted_journal_line_mutation() SET search_path = public, pg_temp;
ALTER FUNCTION public.forbid_posted_journal_update() SET search_path = public, pg_temp;
ALTER FUNCTION public.forbid_store_credit_ledger_mutation() SET search_path = public, pg_temp;
ALTER FUNCTION public.guard_delivery_job_mutation() SET search_path = public, pg_temp;
ALTER FUNCTION public.guard_delivery_location_mutation() SET search_path = public, pg_temp;
ALTER FUNCTION public.guard_delivery_note_line_mutation() SET search_path = public, pg_temp;
ALTER FUNCTION public.guard_delivery_note_mutation() SET search_path = public, pg_temp;
ALTER FUNCTION public.guard_finance_audit_immutable() SET search_path = public, pg_temp;
ALTER FUNCTION public.guard_finance_req_approvals_immutable() SET search_path = public, pg_temp;
ALTER FUNCTION public.guard_fleet_vehicle_mutation() SET search_path = public, pg_temp;
ALTER FUNCTION public.guard_goods_receipt_line_mutation() SET search_path = public, pg_temp;
ALTER FUNCTION public.guard_landed_cost_child_mutation() SET search_path = public, pg_temp;
ALTER FUNCTION public.guard_loyalty_account_mutation() SET search_path = public, pg_temp;
ALTER FUNCTION public.guard_loyalty_settings_mutation() SET search_path = public, pg_temp;
ALTER FUNCTION public.guard_material_request_line_mutation() SET search_path = public, pg_temp;
ALTER FUNCTION public.guard_payment_allocation_mutation() SET search_path = public, pg_temp;
ALTER FUNCTION public.guard_payment_entry_mutation() SET search_path = public, pg_temp;
ALTER FUNCTION public.guard_payroll_deduction_mutation() SET search_path = public, pg_temp;
ALTER FUNCTION public.guard_payroll_line_mutation() SET search_path = public, pg_temp;
ALTER FUNCTION public.guard_payroll_run_mutation() SET search_path = public, pg_temp;
ALTER FUNCTION public.guard_payslip_mutation() SET search_path = public, pg_temp;
ALTER FUNCTION public.guard_pick_list_line_mutation() SET search_path = public, pg_temp;
ALTER FUNCTION public.guard_pick_list_mutation() SET search_path = public, pg_temp;
ALTER FUNCTION public.guard_procurement_doc_mutation() SET search_path = public, pg_temp;
ALTER FUNCTION public.guard_purchase_order_line_mutation() SET search_path = public, pg_temp;
ALTER FUNCTION public.guard_rfq_line_mutation() SET search_path = public, pg_temp;
ALTER FUNCTION public.guard_rfq_supplier_mutation() SET search_path = public, pg_temp;
ALTER FUNCTION public.guard_staff_ops_notification_insert() SET search_path = public, pg_temp;
ALTER FUNCTION public.guard_stock_reconciliation_line_mutation() SET search_path = public, pg_temp;
ALTER FUNCTION public.guard_stock_reconciliation_mutation() SET search_path = public, pg_temp;
ALTER FUNCTION public.guard_store_credit_account_mutation() SET search_path = public, pg_temp;
ALTER FUNCTION public.guard_supplier_quotation_line_mutation() SET search_path = public, pg_temp;
ALTER FUNCTION public.guard_warranty_claim_mutation() SET search_path = public, pg_temp;
ALTER FUNCTION public.protect_profile_staff_flag() SET search_path = public, pg_temp;
ALTER FUNCTION public.touch_whatsapp_flow_orders_updated_at() SET search_path = public, pg_temp;

-- 3) WARN: SD function executable by anon/authenticated
REVOKE EXECUTE ON ALL FUNCTIONS IN SCHEMA public FROM PUBLIC;
REVOKE EXECUTE ON ALL FUNCTIONS IN SCHEMA public FROM anon;
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA public TO authenticated;

-- Internal SD helpers / triggers: not client-callable
REVOKE EXECUTE ON FUNCTION public._adjust_consignment_level(p_kind consignment_kind, p_supplier_id uuid, p_customer_id uuid, p_item uuid, p_warehouse uuid, p_delta numeric, p_unit_cost numeric, p_currency currency_code) FROM PUBLIC, anon, authenticated;
REVOKE EXECUTE ON FUNCTION public._adjust_stock_level(p_item uuid, p_warehouse uuid, p_delta numeric, p_valuation valuation_method, p_unit_cost numeric, p_currency currency_code) FROM PUBLIC, anon, authenticated;
REVOKE EXECUTE ON FUNCTION public._append_loyalty(p_customer_id uuid, p_movement loyalty_movement, p_points numeric, p_currency currency_code, p_exchange_rate numeric, p_money_value numeric, p_sales_invoice_id uuid, p_journal_entry_id uuid, p_reason text, p_reverses_ledger_id uuid) FROM PUBLIC, anon, authenticated;
REVOKE EXECUTE ON FUNCTION public._append_store_credit(p_customer_id uuid, p_movement store_credit_movement, p_amount numeric, p_currency currency_code, p_exchange_rate numeric, p_payment_entry_id uuid, p_journal_entry_id uuid, p_reason text, p_reverses_ledger_id uuid) FROM PUBLIC, anon, authenticated;
REVOKE EXECUTE ON FUNCTION public._assert_ai_analytics_staff() FROM PUBLIC, anon, authenticated;
REVOKE EXECUTE ON FUNCTION public._assert_ai_crm_staff() FROM PUBLIC, anon, authenticated;
REVOKE EXECUTE ON FUNCTION public._assert_ai_finance_staff() FROM PUBLIC, anon, authenticated;
REVOKE EXECUTE ON FUNCTION public._assert_ai_stores_staff() FROM PUBLIC, anon, authenticated;
REVOKE EXECUTE ON FUNCTION public._assert_customer_owns_invoice(p_invoice_id uuid) FROM PUBLIC, anon, authenticated;
REVOKE EXECUTE ON FUNCTION public._assert_customer_owns_open_cart(p_cart_id uuid) FROM PUBLIC, anon, authenticated;
REVOKE EXECUTE ON FUNCTION public._assert_delivery_pod_object_for_job(p_delivery_job_id uuid, p_path text, p_kind text) FROM PUBLIC, anon, authenticated;
REVOKE EXECUTE ON FUNCTION public._assert_fleet_driver_assignee(p_user_id uuid) FROM PUBLIC, anon, authenticated;
REVOKE EXECUTE ON FUNCTION public._auto_ship_online_dispatch_after_pick(p_pick_list_id uuid) FROM PUBLIC, anon, authenticated;
REVOKE EXECUTE ON FUNCTION public._can_access_petty_cash_receipt_object(p_name text) FROM PUBLIC, anon, authenticated;
REVOKE EXECUTE ON FUNCTION public._can_select_chat_thread(p_thread chat_threads) FROM PUBLIC, anon, authenticated;
REVOKE EXECUTE ON FUNCTION public._can_select_delivery_pod_object(p_name text) FROM PUBLIC, anon, authenticated;
REVOKE EXECUTE ON FUNCTION public._can_select_review_photo_object(p_name text) FROM PUBLIC, anon, authenticated;
REVOKE EXECUTE ON FUNCTION public._can_write_delivery_pod_object(p_name text) FROM PUBLIC, anon, authenticated;
REVOKE EXECUTE ON FUNCTION public._can_write_product_image_object(p_name text) FROM PUBLIC, anon, authenticated;
REVOKE EXECUTE ON FUNCTION public._can_write_review_photo_object(p_name text) FROM PUBLIC, anon, authenticated;
REVOKE EXECUTE ON FUNCTION public._consume_fifo_batches(p_item uuid, p_warehouse uuid, p_qty_base numeric) FROM PUBLIC, anon, authenticated;
REVOKE EXECUTE ON FUNCTION public._current_customer_id() FROM PUBLIC, anon, authenticated;
REVOKE EXECUTE ON FUNCTION public._customer_owns_delivery_job(p_job_id uuid) FROM PUBLIC, anon, authenticated;
REVOKE EXECUTE ON FUNCTION public._delivery_job_customer_contact(p_delivery_job_id uuid) FROM PUBLIC, anon, authenticated;
REVOKE EXECUTE ON FUNCTION public._driver_eligible_for_assign(p_user_id uuid, p_at timestamp with time zone) FROM PUBLIC, anon, authenticated;
REVOKE EXECUTE ON FUNCTION public._driver_open_job_count(p_user_id uuid) FROM PUBLIC, anon, authenticated;
REVOKE EXECUTE ON FUNCTION public._enqueue_customer_sms(p_event_code text, p_dedupe_key text, p_profile_id uuid, p_phone_e164 text, p_payload jsonb, p_message_body text, p_actor_user_id uuid) FROM PUBLIC, anon, authenticated;
REVOKE EXECUTE ON FUNCTION public._enqueue_delivery_customer_sms(p_delivery_job_id uuid, p_event_code text, p_dedupe_key text, p_body text) FROM PUBLIC, anon, authenticated;
REVOKE EXECUTE ON FUNCTION public._ensure_loyalty_account(p_customer_id uuid, p_currency currency_code) FROM PUBLIC, anon, authenticated;
REVOKE EXECUTE ON FUNCTION public._ensure_pick_list_for_invoice(p_invoice_id uuid) FROM PUBLIC, anon, authenticated;
REVOKE EXECUTE ON FUNCTION public._ensure_store_credit_account(p_customer_id uuid, p_currency currency_code) FROM PUBLIC, anon, authenticated;
REVOKE EXECUTE ON FUNCTION public._expire_stale_pos_scan_sessions(p_cart_id uuid) FROM PUBLIC, anon, authenticated;
REVOKE EXECUTE ON FUNCTION public._finalize_online_dispatch_order(p_invoice_id uuid) FROM PUBLIC, anon, authenticated;
REVOKE EXECUTE ON FUNCTION public._finance_req_approver_on_reporting_line(p_requester_user_id uuid, p_approver_user_id uuid) FROM PUBLIC, anon, authenticated;
REVOKE EXECUTE ON FUNCTION public._finance_req_can_approve(p_requisition finance_requisitions) FROM PUBLIC, anon, authenticated;
REVOKE EXECUTE ON FUNCTION public._is_chat_staff() FROM PUBLIC, anon, authenticated;
REVOKE EXECUTE ON FUNCTION public._log_pos_action(p_action text, p_entity_type text, p_entity_id uuid, p_before jsonb, p_after jsonb, p_notes text) FROM PUBLIC, anon, authenticated;
REVOKE EXECUTE ON FUNCTION public._loyalty_money_value(p_points numeric) FROM PUBLIC, anon, authenticated;
REVOKE EXECUTE ON FUNCTION public._notify_out_for_delivery(p_delivery_job_id uuid, p_track_token text) FROM PUBLIC, anon, authenticated;
REVOKE EXECUTE ON FUNCTION public._notify_sales_prep(p_invoice_id uuid) FROM PUBLIC, anon, authenticated;
REVOKE EXECUTE ON FUNCTION public._notify_wishlist_back_in_stock(p_stock_item_id uuid) FROM PUBLIC, anon, authenticated;
REVOKE EXECUTE ON FUNCTION public._post_journal_entry_inventory(p_entry_date date, p_description text, p_currency currency_code, p_exchange_rate numeric, p_lines jsonb) FROM PUBLIC, anon, authenticated;
REVOKE EXECUTE ON FUNCTION public._post_journal_entry_payroll(p_entry_date date, p_description text, p_currency currency_code, p_exchange_rate numeric, p_lines jsonb) FROM PUBLIC, anon, authenticated;
REVOKE EXECUTE ON FUNCTION public._post_stock_reconciliation(p_reconciliation_id uuid) FROM PUBLIC, anon, authenticated;
REVOKE EXECUTE ON FUNCTION public._recompute_delivery_eta_haversine(p_job_id uuid, p_lat double precision, p_lng double precision) FROM PUBLIC, anon, authenticated;
REVOKE EXECUTE ON FUNCTION public._recon_dual_auth_threshold(p_currency currency_code) FROM PUBLIC, anon, authenticated;
REVOKE EXECUTE ON FUNCTION public._refresh_payroll_run_totals(p_run_id uuid) FROM PUBLIC, anon, authenticated;
REVOKE EXECUTE ON FUNCTION public._require_cart_mutate(p_cart_id uuid) FROM PUBLIC, anon, authenticated;
REVOKE EXECUTE ON FUNCTION public._require_hr_staff() FROM PUBLIC, anon, authenticated;
REVOKE EXECUTE ON FUNCTION public._require_procurement_finance() FROM PUBLIC, anon, authenticated;
REVOKE EXECUTE ON FUNCTION public._require_procurement_staff() FROM PUBLIC, anon, authenticated;
REVOKE EXECUTE ON FUNCTION public._require_return_post() FROM PUBLIC, anon, authenticated;
REVOKE EXECUTE ON FUNCTION public._resolve_customer_stock_item(p_stock_item_id uuid, p_oem_part_number text) FROM PUBLIC, anon, authenticated;
REVOKE EXECUTE ON FUNCTION public._reverse_journal_inventory(p_entry_id uuid, p_description text) FROM PUBLIC, anon, authenticated;
REVOKE EXECUTE ON FUNCTION public._stock_item_id_from_product_image_path(p_name text) FROM PUBLIC, anon, authenticated;
REVOKE EXECUTE ON FUNCTION public._storefront_customer_provision_denied(p_uid uuid) FROM PUBLIC, anon, authenticated;
REVOKE EXECUTE ON FUNCTION public._try_auto_assign_delivery_job(p_delivery_job_id uuid) FROM PUBLIC, anon, authenticated;
REVOKE EXECUTE ON FUNCTION public.chat_messages_after_insert() FROM PUBLIC, anon, authenticated;
REVOKE EXECUTE ON FUNCTION public.customers_protect_privileged_columns() FROM PUBLIC, anon, authenticated;
REVOKE EXECUTE ON FUNCTION public.handle_new_user() FROM PUBLIC, anon, authenticated;
REVOKE EXECUTE ON FUNCTION public.sync_profile_is_staff() FROM PUBLIC, anon, authenticated;

-- Intentional anon RPCs
GRANT EXECUTE ON FUNCTION public.list_storefront_home_rails(integer) TO anon;
GRANT EXECUTE ON FUNCTION public.get_zig_exchange_rate(date) TO anon;
GRANT EXECUTE ON FUNCTION public.get_delivery_track_point(uuid, text) TO anon;
GRANT EXECUTE ON FUNCTION public.get_product_review_stats(uuid, text) TO anon;
GRANT EXECUTE ON FUNCTION public.resolve_staff_login_email(text) TO anon;
GRANT EXECUTE ON FUNCTION public.staff_login_is_locked(text) TO anon;
GRANT EXECUTE ON FUNCTION public.record_staff_login_attempt(text, boolean) TO anon;

ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public
  REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC;
ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public
  REVOKE EXECUTE ON FUNCTIONS FROM anon;

-- 4) WARN: auth_rls_initplan
DROP POLICY IF EXISTS "ai_report_subscriptions_admin_finance_insert" ON public."ai_report_subscriptions";
CREATE POLICY "ai_report_subscriptions_admin_finance_insert" ON public."ai_report_subscriptions" AS PERMISSIVE FOR INSERT TO authenticated
  WITH CHECK ((has_staff_role(ARRAY['admin'::staff_role, 'finance'::staff_role]) AND ((created_by IS NULL) OR (created_by = (select auth.uid())))));

DROP POLICY IF EXISTS "attendance_events_insert_hr_or_self" ON public."attendance_events";
CREATE POLICY "attendance_events_insert_hr_or_self" ON public."attendance_events" AS PERMISSIVE FOR INSERT TO authenticated
  WITH CHECK ((has_staff_role(ARRAY['admin'::staff_role, 'hr'::staff_role]) OR (EXISTS ( SELECT 1
   FROM employees e
  WHERE ((e.id = attendance_events.employee_id) AND (e.user_id = (select auth.uid())))))));

DROP POLICY IF EXISTS "attendance_events_select_hr_or_self" ON public."attendance_events";
CREATE POLICY "attendance_events_select_hr_or_self" ON public."attendance_events" AS PERMISSIVE FOR SELECT TO authenticated
  USING ((has_staff_role(ARRAY['admin'::staff_role, 'hr'::staff_role]) OR (EXISTS ( SELECT 1
   FROM employees e
  WHERE ((e.id = attendance_events.employee_id) AND (e.user_id = (select auth.uid())))))));

DROP POLICY IF EXISTS "chat_messages_customer_insert" ON public."chat_messages";
CREATE POLICY "chat_messages_customer_insert" ON public."chat_messages" AS PERMISSIVE FOR INSERT TO authenticated
  WITH CHECK (((sender_user_id = (select auth.uid())) AND (sender_kind = 'customer'::chat_sender_kind) AND (NOT is_staff()) AND (EXISTS ( SELECT 1
   FROM chat_threads t
  WHERE ((t.id = chat_messages.thread_id) AND (t.customer_user_id = (select auth.uid())) AND (t.status = ANY (ARRAY['open'::chat_thread_status, 'assigned'::chat_thread_status])))))));

DROP POLICY IF EXISTS "chat_messages_staff_insert" ON public."chat_messages";
CREATE POLICY "chat_messages_staff_insert" ON public."chat_messages" AS PERMISSIVE FOR INSERT TO authenticated
  WITH CHECK (((sender_user_id = (select auth.uid())) AND (sender_kind = 'staff'::chat_sender_kind) AND has_staff_role(_chat_staff_roles()) AND (EXISTS ( SELECT 1
   FROM chat_threads t
  WHERE ((t.id = chat_messages.thread_id) AND _can_select_chat_thread(t.*) AND (t.status = ANY (ARRAY['open'::chat_thread_status, 'assigned'::chat_thread_status])))))));

DROP POLICY IF EXISTS "chat_participants_insert_own" ON public."chat_participants";
CREATE POLICY "chat_participants_insert_own" ON public."chat_participants" AS PERMISSIVE FOR INSERT TO authenticated
  WITH CHECK (((user_id = (select auth.uid())) AND (((role = 'customer'::chat_participant_role) AND (NOT is_staff()) AND (EXISTS ( SELECT 1
   FROM chat_threads t
  WHERE ((t.id = chat_participants.thread_id) AND (t.customer_user_id = (select auth.uid())))))) OR ((role = 'staff'::chat_participant_role) AND has_staff_role(_chat_staff_roles()) AND (EXISTS ( SELECT 1
   FROM chat_threads t
  WHERE ((t.id = chat_participants.thread_id) AND _can_select_chat_thread(t.*))))))));

DROP POLICY IF EXISTS "chat_participants_select" ON public."chat_participants";
CREATE POLICY "chat_participants_select" ON public."chat_participants" AS PERMISSIVE FOR SELECT TO authenticated
  USING (((user_id = (select auth.uid())) OR (EXISTS ( SELECT 1
   FROM chat_threads t
  WHERE ((t.id = chat_participants.thread_id) AND _can_select_chat_thread(t.*))))));

DROP POLICY IF EXISTS "chat_participants_update_own" ON public."chat_participants";
CREATE POLICY "chat_participants_update_own" ON public."chat_participants" AS PERMISSIVE FOR UPDATE TO authenticated
  USING ((user_id = (select auth.uid())))
  WITH CHECK (((user_id = (select auth.uid())) AND (role = ( SELECT p.role
   FROM chat_participants p
  WHERE ((p.thread_id = chat_participants.thread_id) AND (p.user_id = (select auth.uid())))))));

DROP POLICY IF EXISTS "chat_threads_customer_insert" ON public."chat_threads";
CREATE POLICY "chat_threads_customer_insert" ON public."chat_threads" AS PERMISSIVE FOR INSERT TO authenticated
  WITH CHECK (((customer_user_id = (select auth.uid())) AND (NOT is_staff()) AND (status = 'open'::chat_thread_status) AND (assigned_to IS NULL) AND (closed_at IS NULL) AND (closed_by IS NULL) AND ((customer_id IS NULL) OR (customer_id = _current_customer_id()))));

DROP POLICY IF EXISTS "customers_select_own" ON public."customers";
CREATE POLICY "customers_select_own" ON public."customers" AS PERMISSIVE FOR SELECT TO authenticated
  USING ((profile_id = (select auth.uid())));

DROP POLICY IF EXISTS "customers_update_own" ON public."customers";
CREATE POLICY "customers_update_own" ON public."customers" AS PERMISSIVE FOR UPDATE TO authenticated
  USING ((profile_id = (select auth.uid())))
  WITH CHECK ((profile_id = (select auth.uid())));

DROP POLICY IF EXISTS "delivery_jobs_staff_select" ON public."delivery_jobs";
CREATE POLICY "delivery_jobs_staff_select" ON public."delivery_jobs" AS PERMISSIVE FOR SELECT TO authenticated
  USING ((has_staff_role(ARRAY['admin'::staff_role, 'warehouse'::staff_role, 'dispatcher'::staff_role]) OR (assignee_user_id = (select auth.uid()))));

DROP POLICY IF EXISTS "driver_presence_self_insert" ON public."driver_presence";
CREATE POLICY "driver_presence_self_insert" ON public."driver_presence" AS PERMISSIVE FOR INSERT TO authenticated
  WITH CHECK (((user_id = (select auth.uid())) AND has_staff_role(ARRAY['driver'::staff_role])));

DROP POLICY IF EXISTS "driver_presence_self_select" ON public."driver_presence";
CREATE POLICY "driver_presence_self_select" ON public."driver_presence" AS PERMISSIVE FOR SELECT TO authenticated
  USING (((user_id = (select auth.uid())) OR has_staff_role(ARRAY['admin'::staff_role, 'warehouse'::staff_role, 'dispatcher'::staff_role])));

DROP POLICY IF EXISTS "driver_presence_self_update" ON public."driver_presence";
CREATE POLICY "driver_presence_self_update" ON public."driver_presence" AS PERMISSIVE FOR UPDATE TO authenticated
  USING (((user_id = (select auth.uid())) AND has_staff_role(ARRAY['driver'::staff_role])))
  WITH CHECK (((user_id = (select auth.uid())) AND has_staff_role(ARRAY['driver'::staff_role])));

DROP POLICY IF EXISTS "ecocash_intents_staff_select" ON public."ecocash_payment_intents";
CREATE POLICY "ecocash_intents_staff_select" ON public."ecocash_payment_intents" AS PERMISSIVE FOR SELECT TO authenticated
  USING ((EXISTS ( SELECT 1
   FROM staff_roles sr
  WHERE ((sr.user_id = (select auth.uid())) AND (sr.role = ANY (ARRAY['admin'::staff_role, 'finance'::staff_role, 'sales'::staff_role]))))));

DROP POLICY IF EXISTS "employees_select_hr_or_self" ON public."employees";
CREATE POLICY "employees_select_hr_or_self" ON public."employees" AS PERMISSIVE FOR SELECT TO authenticated
  USING ((has_staff_role(ARRAY['admin'::staff_role, 'hr'::staff_role]) OR (user_id = (select auth.uid()))));

DROP POLICY IF EXISTS "finance_req_approvals_insert" ON public."finance_requisition_approvals";
CREATE POLICY "finance_req_approvals_insert" ON public."finance_requisition_approvals" AS PERMISSIVE FOR INSERT TO authenticated
  WITH CHECK ((has_staff_role(ARRAY['admin'::staff_role, 'finance'::staff_role]) AND (approver_user_id = (select auth.uid()))));

DROP POLICY IF EXISTS "finance_requisition_lines_select" ON public."finance_requisition_lines";
CREATE POLICY "finance_requisition_lines_select" ON public."finance_requisition_lines" AS PERMISSIVE FOR SELECT TO authenticated
  USING ((EXISTS ( SELECT 1
   FROM finance_requisitions r
  WHERE ((r.id = finance_requisition_lines.requisition_id) AND ((r.requested_by = (select auth.uid())) OR has_staff_role(ARRAY['admin'::staff_role, 'finance'::staff_role]) OR (is_staff() AND (r.status = ANY (ARRAY['submitted'::finance_requisition_status, 'approved'::finance_requisition_status, 'rejected'::finance_requisition_status, 'disbursed'::finance_requisition_status, 'cancelled'::finance_requisition_status]))))))));

DROP POLICY IF EXISTS "finance_req_receipts_select" ON public."finance_requisition_receipts";
CREATE POLICY "finance_req_receipts_select" ON public."finance_requisition_receipts" AS PERMISSIVE FOR SELECT TO authenticated
  USING ((EXISTS ( SELECT 1
   FROM finance_requisitions r
  WHERE ((r.id = finance_requisition_receipts.requisition_id) AND ((r.requested_by = (select auth.uid())) OR has_staff_role(ARRAY['admin'::staff_role, 'finance'::staff_role]) OR (is_staff() AND (r.status = ANY (ARRAY['submitted'::finance_requisition_status, 'approved'::finance_requisition_status, 'rejected'::finance_requisition_status, 'disbursed'::finance_requisition_status, 'cancelled'::finance_requisition_status]))))))));

DROP POLICY IF EXISTS "finance_requisitions_select" ON public."finance_requisitions";
CREATE POLICY "finance_requisitions_select" ON public."finance_requisitions" AS PERMISSIVE FOR SELECT TO authenticated
  USING (((requested_by = (select auth.uid())) OR has_staff_role(ARRAY['admin'::staff_role, 'finance'::staff_role]) OR (is_staff() AND (status = ANY (ARRAY['submitted'::finance_requisition_status, 'approved'::finance_requisition_status, 'rejected'::finance_requisition_status, 'disbursed'::finance_requisition_status, 'cancelled'::finance_requisition_status])))));

DROP POLICY IF EXISTS "goods_receipt_lines_staff_select" ON public."goods_receipt_lines";
CREATE POLICY "goods_receipt_lines_staff_select" ON public."goods_receipt_lines" AS PERMISSIVE FOR SELECT TO authenticated
  USING ((((select auth.role()) = 'service_role'::text) OR has_staff_role(ARRAY['admin'::staff_role, 'warehouse'::staff_role, 'finance'::staff_role])));

DROP POLICY IF EXISTS "goods_receipts_staff_select" ON public."goods_receipts";
CREATE POLICY "goods_receipts_staff_select" ON public."goods_receipts" AS PERMISSIVE FOR SELECT TO authenticated
  USING ((((select auth.role()) = 'service_role'::text) OR has_staff_role(ARRAY['admin'::staff_role, 'warehouse'::staff_role, 'finance'::staff_role])));

DROP POLICY IF EXISTS "hr_leave_balances_select" ON public."hr_leave_balances";
CREATE POLICY "hr_leave_balances_select" ON public."hr_leave_balances" AS PERMISSIVE FOR SELECT TO authenticated
  USING ((has_staff_role(ARRAY['admin'::staff_role, 'hr'::staff_role]) OR (EXISTS ( SELECT 1
   FROM employees e
  WHERE ((e.id = hr_leave_balances.employee_id) AND (e.user_id = (select auth.uid())))))));

DROP POLICY IF EXISTS "hr_leave_requests_insert" ON public."hr_leave_requests";
CREATE POLICY "hr_leave_requests_insert" ON public."hr_leave_requests" AS PERMISSIVE FOR INSERT TO authenticated
  WITH CHECK ((has_staff_role(ARRAY['admin'::staff_role, 'hr'::staff_role]) OR (EXISTS ( SELECT 1
   FROM employees e
  WHERE ((e.id = hr_leave_requests.employee_id) AND (e.user_id = (select auth.uid())))))));

DROP POLICY IF EXISTS "hr_leave_requests_select" ON public."hr_leave_requests";
CREATE POLICY "hr_leave_requests_select" ON public."hr_leave_requests" AS PERMISSIVE FOR SELECT TO authenticated
  USING ((has_staff_role(ARRAY['admin'::staff_role, 'hr'::staff_role]) OR (EXISTS ( SELECT 1
   FROM employees e
  WHERE ((e.id = hr_leave_requests.employee_id) AND (e.user_id = (select auth.uid())))))));

DROP POLICY IF EXISTS "hr_leave_requests_update" ON public."hr_leave_requests";
CREATE POLICY "hr_leave_requests_update" ON public."hr_leave_requests" AS PERMISSIVE FOR UPDATE TO authenticated
  USING ((has_staff_role(ARRAY['admin'::staff_role, 'hr'::staff_role]) OR ((status = 'draft'::hr_leave_request_status) AND (EXISTS ( SELECT 1
   FROM employees e
  WHERE ((e.id = hr_leave_requests.employee_id) AND (e.user_id = (select auth.uid()))))))));

DROP POLICY IF EXISTS "landed_cost_allocations_staff_select" ON public."landed_cost_allocations";
CREATE POLICY "landed_cost_allocations_staff_select" ON public."landed_cost_allocations" AS PERMISSIVE FOR SELECT TO authenticated
  USING ((((select auth.role()) = 'service_role'::text) OR has_staff_role(ARRAY['admin'::staff_role, 'finance'::staff_role])));

DROP POLICY IF EXISTS "landed_cost_charges_staff_select" ON public."landed_cost_charges";
CREATE POLICY "landed_cost_charges_staff_select" ON public."landed_cost_charges" AS PERMISSIVE FOR SELECT TO authenticated
  USING ((((select auth.role()) = 'service_role'::text) OR has_staff_role(ARRAY['admin'::staff_role, 'finance'::staff_role])));

DROP POLICY IF EXISTS "landed_cost_staff_select" ON public."landed_cost_vouchers";
CREATE POLICY "landed_cost_staff_select" ON public."landed_cost_vouchers" AS PERMISSIVE FOR SELECT TO authenticated
  USING ((((select auth.role()) = 'service_role'::text) OR has_staff_role(ARRAY['admin'::staff_role, 'finance'::staff_role])));

DROP POLICY IF EXISTS "loyalty_accounts_customer_select" ON public."loyalty_accounts";
CREATE POLICY "loyalty_accounts_customer_select" ON public."loyalty_accounts" AS PERMISSIVE FOR SELECT TO authenticated
  USING ((customer_id IN ( SELECT c.id
   FROM customers c
  WHERE (c.profile_id = (select auth.uid())))));

DROP POLICY IF EXISTS "loyalty_ledger_customer_select" ON public."loyalty_ledger";
CREATE POLICY "loyalty_ledger_customer_select" ON public."loyalty_ledger" AS PERMISSIVE FOR SELECT TO authenticated
  USING ((customer_id IN ( SELECT c.id
   FROM customers c
  WHERE (c.profile_id = (select auth.uid())))));

DROP POLICY IF EXISTS "sms_prefs_select_own_or_admin" ON public."manager_sms_preferences";
CREATE POLICY "sms_prefs_select_own_or_admin" ON public."manager_sms_preferences" AS PERMISSIVE FOR SELECT TO authenticated
  USING (((user_id = (select auth.uid())) OR has_staff_role(ARRAY['admin'::staff_role])));

DROP POLICY IF EXISTS "sms_prefs_update_own" ON public."manager_sms_preferences";
CREATE POLICY "sms_prefs_update_own" ON public."manager_sms_preferences" AS PERMISSIVE FOR UPDATE TO authenticated
  USING ((user_id = (select auth.uid())))
  WITH CHECK ((user_id = (select auth.uid())));

DROP POLICY IF EXISTS "sms_prefs_upsert_own" ON public."manager_sms_preferences";
CREATE POLICY "sms_prefs_upsert_own" ON public."manager_sms_preferences" AS PERMISSIVE FOR INSERT TO authenticated
  WITH CHECK (((user_id = (select auth.uid())) AND is_staff()));

DROP POLICY IF EXISTS "material_request_lines_staff_select" ON public."material_request_lines";
CREATE POLICY "material_request_lines_staff_select" ON public."material_request_lines" AS PERMISSIVE FOR SELECT TO authenticated
  USING ((((select auth.role()) = 'service_role'::text) OR has_staff_role(ARRAY['admin'::staff_role, 'warehouse'::staff_role, 'finance'::staff_role])));

DROP POLICY IF EXISTS "material_requests_staff_select" ON public."material_requests";
CREATE POLICY "material_requests_staff_select" ON public."material_requests" AS PERMISSIVE FOR SELECT TO authenticated
  USING ((((select auth.role()) = 'service_role'::text) OR has_staff_role(ARRAY['admin'::staff_role, 'warehouse'::staff_role, 'finance'::staff_role])));

DROP POLICY IF EXISTS "panic_events_driver_insert" ON public."panic_events";
CREATE POLICY "panic_events_driver_insert" ON public."panic_events" AS PERMISSIVE FOR INSERT TO authenticated
  WITH CHECK (((driver_user_id = (select auth.uid())) AND has_staff_role(ARRAY['driver'::staff_role])));

DROP POLICY IF EXISTS "panic_events_driver_select_own" ON public."panic_events";
CREATE POLICY "panic_events_driver_select_own" ON public."panic_events" AS PERMISSIVE FOR SELECT TO authenticated
  USING (((driver_user_id = (select auth.uid())) OR has_staff_role(ARRAY['admin'::staff_role, 'warehouse'::staff_role, 'dispatcher'::staff_role])));

DROP POLICY IF EXISTS "payment_tender_gl_finance_write" ON public."payment_tender_gl_accounts";
CREATE POLICY "payment_tender_gl_finance_write" ON public."payment_tender_gl_accounts" AS PERMISSIVE FOR ALL TO authenticated
  USING ((EXISTS ( SELECT 1
   FROM staff_roles sr
  WHERE ((sr.user_id = (select auth.uid())) AND (sr.role = ANY (ARRAY['admin'::staff_role, 'finance'::staff_role]))))))
  WITH CHECK ((EXISTS ( SELECT 1
   FROM staff_roles sr
  WHERE ((sr.user_id = (select auth.uid())) AND (sr.role = ANY (ARRAY['admin'::staff_role, 'finance'::staff_role]))))));

DROP POLICY IF EXISTS "payment_tender_gl_select" ON public."payment_tender_gl_accounts";
CREATE POLICY "payment_tender_gl_select" ON public."payment_tender_gl_accounts" AS PERMISSIVE FOR SELECT TO authenticated
  USING ((EXISTS ( SELECT 1
   FROM staff_roles sr
  WHERE ((sr.user_id = (select auth.uid())) AND (sr.role = ANY (ARRAY['admin'::staff_role, 'finance'::staff_role, 'sales'::staff_role]))))));

DROP POLICY IF EXISTS "payroll_deduction_lines_select_hr_or_self" ON public."payroll_deduction_lines";
CREATE POLICY "payroll_deduction_lines_select_hr_or_self" ON public."payroll_deduction_lines" AS PERMISSIVE FOR SELECT TO authenticated
  USING ((has_staff_role(ARRAY['admin'::staff_role, 'hr'::staff_role]) OR (EXISTS ( SELECT 1
   FROM (payroll_lines l
     JOIN employees e ON ((e.id = l.employee_id)))
  WHERE ((l.id = payroll_deduction_lines.payroll_line_id) AND (e.user_id = (select auth.uid())))))));

DROP POLICY IF EXISTS "payroll_lines_select_hr_or_self" ON public."payroll_lines";
CREATE POLICY "payroll_lines_select_hr_or_self" ON public."payroll_lines" AS PERMISSIVE FOR SELECT TO authenticated
  USING ((has_staff_role(ARRAY['admin'::staff_role, 'hr'::staff_role]) OR (EXISTS ( SELECT 1
   FROM employees e
  WHERE ((e.id = payroll_lines.employee_id) AND (e.user_id = (select auth.uid())))))));

DROP POLICY IF EXISTS "payroll_runs_select_hr" ON public."payroll_runs";
CREATE POLICY "payroll_runs_select_hr" ON public."payroll_runs" AS PERMISSIVE FOR SELECT TO authenticated
  USING ((has_staff_role(ARRAY['admin'::staff_role, 'hr'::staff_role]) OR (EXISTS ( SELECT 1
   FROM (payroll_lines l
     JOIN employees e ON ((e.id = l.employee_id)))
  WHERE ((l.payroll_run_id = payroll_runs.id) AND (e.user_id = (select auth.uid())))))));

DROP POLICY IF EXISTS "payslips_select_hr_or_self" ON public."payslips";
CREATE POLICY "payslips_select_hr_or_self" ON public."payslips" AS PERMISSIVE FOR SELECT TO authenticated
  USING ((has_staff_role(ARRAY['admin'::staff_role, 'hr'::staff_role]) OR (EXISTS ( SELECT 1
   FROM employees e
  WHERE ((e.id = payslips.employee_id) AND (e.user_id = (select auth.uid())))))));

DROP POLICY IF EXISTS "pos_action_audit_insert" ON public."pos_action_audit";
CREATE POLICY "pos_action_audit_insert" ON public."pos_action_audit" AS PERMISSIVE FOR INSERT TO authenticated
  WITH CHECK ((has_staff_role(ARRAY['admin'::staff_role, 'finance'::staff_role, 'sales'::staff_role]) AND (actor_user_id = (select auth.uid()))));

DROP POLICY IF EXISTS "pos_offline_sale_receipts_select" ON public."pos_offline_sale_receipts";
CREATE POLICY "pos_offline_sale_receipts_select" ON public."pos_offline_sale_receipts" AS PERMISSIVE FOR SELECT TO authenticated
  USING (((actor_user_id = (select auth.uid())) OR has_staff_role(ARRAY['admin'::staff_role, 'finance'::staff_role])));

DROP POLICY IF EXISTS "pos_scan_sessions_staff_select" ON public."pos_scan_sessions";
CREATE POLICY "pos_scan_sessions_staff_select" ON public."pos_scan_sessions" AS PERMISSIVE FOR SELECT TO authenticated
  USING ((has_staff_role(ARRAY['admin'::staff_role, 'sales'::staff_role, 'warehouse'::staff_role]) AND ((owner_user_id = (select auth.uid())) OR (scanner_user_id = (select auth.uid())) OR has_staff_role(ARRAY['admin'::staff_role]))));

DROP POLICY IF EXISTS "profiles_insert_own" ON public."profiles";
CREATE POLICY "profiles_insert_own" ON public."profiles" AS PERMISSIVE FOR INSERT TO authenticated
  WITH CHECK (((id = (select auth.uid())) AND (is_staff = false)));

DROP POLICY IF EXISTS "profiles_select_own_or_admin" ON public."profiles";
CREATE POLICY "profiles_select_own_or_admin" ON public."profiles" AS PERMISSIVE FOR SELECT TO authenticated
  USING (((id = (select auth.uid())) OR has_staff_role(ARRAY['admin'::staff_role])));

DROP POLICY IF EXISTS "profiles_update_own" ON public."profiles";
CREATE POLICY "profiles_update_own" ON public."profiles" AS PERMISSIVE FOR UPDATE TO authenticated
  USING ((id = (select auth.uid())))
  WITH CHECK (((id = (select auth.uid())) AND (is_staff = is_staff())));

DROP POLICY IF EXISTS "purchase_order_lines_staff_select" ON public."purchase_order_lines";
CREATE POLICY "purchase_order_lines_staff_select" ON public."purchase_order_lines" AS PERMISSIVE FOR SELECT TO authenticated
  USING ((((select auth.role()) = 'service_role'::text) OR has_staff_role(ARRAY['admin'::staff_role, 'warehouse'::staff_role, 'finance'::staff_role])));

DROP POLICY IF EXISTS "purchase_orders_staff_select" ON public."purchase_orders";
CREATE POLICY "purchase_orders_staff_select" ON public."purchase_orders" AS PERMISSIVE FOR SELECT TO authenticated
  USING ((((select auth.role()) = 'service_role'::text) OR has_staff_role(ARRAY['admin'::staff_role, 'warehouse'::staff_role, 'finance'::staff_role])));

DROP POLICY IF EXISTS "rfq_lines_staff_select" ON public."rfq_lines";
CREATE POLICY "rfq_lines_staff_select" ON public."rfq_lines" AS PERMISSIVE FOR SELECT TO authenticated
  USING ((((select auth.role()) = 'service_role'::text) OR has_staff_role(ARRAY['admin'::staff_role, 'warehouse'::staff_role, 'finance'::staff_role])));

DROP POLICY IF EXISTS "rfq_suppliers_staff_select" ON public."rfq_suppliers";
CREATE POLICY "rfq_suppliers_staff_select" ON public."rfq_suppliers" AS PERMISSIVE FOR SELECT TO authenticated
  USING ((((select auth.role()) = 'service_role'::text) OR has_staff_role(ARRAY['admin'::staff_role, 'warehouse'::staff_role, 'finance'::staff_role])));

DROP POLICY IF EXISTS "rfqs_staff_select" ON public."rfqs";
CREATE POLICY "rfqs_staff_select" ON public."rfqs" AS PERMISSIVE FOR SELECT TO authenticated
  USING ((((select auth.role()) = 'service_role'::text) OR has_staff_role(ARRAY['admin'::staff_role, 'warehouse'::staff_role, 'finance'::staff_role])));

DROP POLICY IF EXISTS "salary_structures_select_hr_or_self" ON public."salary_structures";
CREATE POLICY "salary_structures_select_hr_or_self" ON public."salary_structures" AS PERMISSIVE FOR SELECT TO authenticated
  USING ((has_staff_role(ARRAY['admin'::staff_role, 'hr'::staff_role]) OR (EXISTS ( SELECT 1
   FROM employees e
  WHERE ((e.id = salary_structures.employee_id) AND (e.user_id = (select auth.uid())))))));

DROP POLICY IF EXISTS "sms_outbox_select_own_or_admin" ON public."sms_outbox";
CREATE POLICY "sms_outbox_select_own_or_admin" ON public."sms_outbox" AS PERMISSIVE FOR SELECT TO authenticated
  USING (((recipient_user_id = (select auth.uid())) OR has_staff_role(ARRAY['admin'::staff_role])));

DROP POLICY IF EXISTS "staff_ops_notifications_select_own" ON public."staff_ops_notifications";
CREATE POLICY "staff_ops_notifications_select_own" ON public."staff_ops_notifications" AS PERMISSIVE FOR SELECT TO authenticated
  USING (((recipient_user_id = (select auth.uid())) AND has_staff_role(ARRAY['admin'::staff_role, 'sales'::staff_role, 'warehouse'::staff_role, 'dispatcher'::staff_role])));

DROP POLICY IF EXISTS "staff_ops_notifications_update_own_read" ON public."staff_ops_notifications";
CREATE POLICY "staff_ops_notifications_update_own_read" ON public."staff_ops_notifications" AS PERMISSIVE FOR UPDATE TO authenticated
  USING (((recipient_user_id = (select auth.uid())) AND has_staff_role(ARRAY['admin'::staff_role, 'sales'::staff_role, 'warehouse'::staff_role, 'dispatcher'::staff_role])))
  WITH CHECK (((recipient_user_id = (select auth.uid())) AND has_staff_role(ARRAY['admin'::staff_role, 'sales'::staff_role, 'warehouse'::staff_role, 'dispatcher'::staff_role])));

DROP POLICY IF EXISTS "staff_roles_select_own_or_admin" ON public."staff_roles";
CREATE POLICY "staff_roles_select_own_or_admin" ON public."staff_roles" AS PERMISSIVE FOR SELECT TO authenticated
  USING (((user_id = (select auth.uid())) OR has_staff_role(ARRAY['admin'::staff_role])));

DROP POLICY IF EXISTS "store_credit_accounts_customer_select" ON public."store_credit_accounts";
CREATE POLICY "store_credit_accounts_customer_select" ON public."store_credit_accounts" AS PERMISSIVE FOR SELECT TO authenticated
  USING ((customer_id IN ( SELECT c.id
   FROM customers c
  WHERE (c.profile_id = (select auth.uid())))));

DROP POLICY IF EXISTS "supplier_quotation_lines_staff_select" ON public."supplier_quotation_lines";
CREATE POLICY "supplier_quotation_lines_staff_select" ON public."supplier_quotation_lines" AS PERMISSIVE FOR SELECT TO authenticated
  USING ((((select auth.role()) = 'service_role'::text) OR has_staff_role(ARRAY['admin'::staff_role, 'warehouse'::staff_role, 'finance'::staff_role])));

DROP POLICY IF EXISTS "supplier_quotations_staff_select" ON public."supplier_quotations";
CREATE POLICY "supplier_quotations_staff_select" ON public."supplier_quotations" AS PERMISSIVE FOR SELECT TO authenticated
  USING ((((select auth.role()) = 'service_role'::text) OR has_staff_role(ARRAY['admin'::staff_role, 'warehouse'::staff_role, 'finance'::staff_role])));

DROP POLICY IF EXISTS "suppliers_self_select" ON public."suppliers";
CREATE POLICY "suppliers_self_select" ON public."suppliers" AS PERMISSIVE FOR SELECT TO authenticated
  USING ((profile_id = (select auth.uid())));

DROP POLICY IF EXISTS "suppliers_staff_all" ON public."suppliers";
CREATE POLICY "suppliers_staff_all" ON public."suppliers" AS PERMISSIVE FOR ALL TO authenticated
  USING ((((select auth.role()) = 'service_role'::text) OR has_staff_role(ARRAY['admin'::staff_role, 'warehouse'::staff_role, 'finance'::staff_role])))
  WITH CHECK ((((select auth.role()) = 'service_role'::text) OR has_staff_role(ARRAY['admin'::staff_role, 'warehouse'::staff_role, 'finance'::staff_role])));

DROP POLICY IF EXISTS "whatsapp_flow_orders_staff_select" ON public."whatsapp_flow_orders";
CREATE POLICY "whatsapp_flow_orders_staff_select" ON public."whatsapp_flow_orders" AS PERMISSIVE FOR SELECT TO authenticated
  USING ((EXISTS ( SELECT 1
   FROM staff_roles sr
  WHERE ((sr.user_id = (select auth.uid())) AND (sr.role = ANY (ARRAY['admin'::staff_role, 'finance'::staff_role, 'sales'::staff_role, 'warehouse'::staff_role, 'dispatcher'::staff_role]))))));

COMMIT;

