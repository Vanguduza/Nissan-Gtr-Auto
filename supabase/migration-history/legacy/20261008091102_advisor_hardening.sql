-- Supabase advisor findings (security and performance), 2026-10-08.
-- Not changed, on purpose: SECURITY DEFINER functions signed-in users can call (the RPC API; each
-- checks the caller itself), tables with RLS and no policy (server-only by design), several
-- permissive policies on one table (merging them could change who sees what), and indexes not yet
-- used (statistics are young). Leaked-password protection is an Auth setting, not SQL.
-- 1. The one ERROR: a view that ran with its owner's rights. Callers now need their own access.
ALTER VIEW public.v_master_stock SET (security_invoker = true);
REVOKE ALL ON public.v_master_stock FROM anon;

-- 2a. Catalogue storage signing and build-token functions: signed R2 URLs for the private catalogue bucket
--     (list and download) were open to anyone, signed in or not. Server-side only now (service role).
REVOKE EXECUTE ON FUNCTION public.catalog_r2_presign_batch(p_build_token text, p_object_kind text, p_offset integer, p_limit integer, p_expires integer) FROM PUBLIC, anon, authenticated;
REVOKE EXECUTE ON FUNCTION public.catalog_r2_presign_get(p_object_key text, p_expires integer) FROM PUBLIC, anon, authenticated;
REVOKE EXECUTE ON FUNCTION public.catalog_r2_presign_list(p_prefix text, p_expires integer) FROM PUBLIC, anon, authenticated;
REVOKE EXECUTE ON FUNCTION public.catalog_r2_presign_list_after(p_prefix text, p_start_after text, p_max_keys integer, p_expires integer) FROM PUBLIC, anon, authenticated;
REVOKE EXECUTE ON FUNCTION public.catalog_r2_presign_list_page(p_prefix text, p_continuation_token text, p_max_keys integer, p_expires integer) FROM PUBLIC, anon, authenticated;
REVOKE EXECUTE ON FUNCTION public.catalog_r2_presign_prefixes(p_prefix text, p_expires integer) FROM PUBLIC, anon, authenticated;
REVOKE EXECUTE ON FUNCTION public.catalog_seed_vehicle_master(p_build_token text) FROM PUBLIC, anon, authenticated;

-- 2b. Staff / customer functions that already refuse signed-out callers (or are internal helpers):
--     signed-out visitors cannot call them at all. Kept for anon: helpers used inside RLS / storage
--     policies (they run as the visitor), the storefront catalogue and price lookups, public delivery
--     tracking by token, and the two staff-login pre-checks:
--     _can_access_petty_cash_receipt_object, _can_select_chat_thread, _can_select_delivery_pod_object, _can_select_review_photo_object, _can_write_delivery_pod_object, _can_write_product_image_object, _can_write_review_photo_object, _current_customer_id, catalog_commerce_stock_for_oems, convert_to_base_uom, current_supplier_id, get_delivery_track_point, get_product_review_stats, get_zig_exchange_rate, has_staff_role, is_pos_approver, is_staff, list_customer_vehicle_master, list_storefront_home_rails, resolve_item_price, resolve_staff_login_email, staff_login_is_locked
REVOKE EXECUTE ON FUNCTION public._assert_ai_analytics_staff() FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public._assert_ai_crm_staff() FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public._assert_ai_finance_staff() FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public._assert_ai_stores_staff() FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public._assert_customer_owns_invoice(p_invoice_id uuid) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public._assert_delivery_pod_object_for_job(p_delivery_job_id uuid, p_path text, p_kind text) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public._assert_fleet_driver_assignee(p_user_id uuid) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public._customer_owns_delivery_job(p_job_id uuid) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public._driver_eligible_for_assign(p_user_id uuid, p_at timestamp with time zone) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public._finance_req_can_approve(p_requisition finance_requisitions) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public._is_chat_staff() FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public._recon_dual_auth_threshold(p_currency currency_code) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public._resolve_customer_stock_item(p_stock_item_id uuid, p_oem_part_number text) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public._snapshot_sales_invoice_customer() FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public._stock_item_id_from_product_image_path(p_name text) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public._sync_converted_cart_vehicle_from_quote() FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.attach_finance_requisition_receipt(p_requisition_id uuid, p_storage_path text, p_content_type text) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.attach_goods_receipt_invoice(p_goods_receipt_id uuid, p_storage_path text) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.chat_unread_count(p_thread_id uuid) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.compute_petty_cash_replenish_amount(p_currency currency_code, p_as_of date) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.create_kit_with_components(p_oem text, p_title text, p_components jsonb, p_chassis_code text, p_sell_mode kit_sell_mode) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.create_petty_cash_expense_request(p_amount numeric, p_currency currency_code, p_description text, p_entry_date date, p_expense_account_code character varying, p_exchange_rate numeric) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.create_petty_cash_float_request(p_amount numeric, p_currency currency_code, p_description text, p_entry_date date, p_exchange_rate numeric) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.create_pos_customer(p_customer_kind text, p_display_name text, p_business_name text, p_email text, p_phone_e164 text, p_whatsapp_e164 text) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.current_employee_id() FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.deactivate_preferred_supplier(p_supplier_id uuid) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.delete_pos_popular_pin(p_item_type text, p_item_key text) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.delete_stock_item_image(p_image_id uuid) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.get_delivery_job_lines(p_delivery_job_id uuid) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.get_delivery_job_settlement(p_delivery_job_id uuid) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.get_loyalty_balance(p_customer_id uuid) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.get_pick_path_hints(p_warehouse_id uuid, p_stock_item_ids uuid[]) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.kpi_ar_aging_snapshot() FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.kpi_credit_holds_summary() FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.kpi_finance_performance_v1(p_from timestamp with time zone, p_to timestamp with time zone, p_top_expenses integer) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.kpi_inventory_summary() FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.kpi_open_deliveries_summary() FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.kpi_ops_sales_v1(p_from timestamp with time zone, p_to timestamp with time zone, p_top_limit integer) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.kpi_returns_summary(p_from timestamp with time zone, p_to timestamp with time zone) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.kpi_sales_summary(p_from timestamp with time zone, p_to timestamp with time zone) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.kpi_top_skus(p_from timestamp with time zone, p_to timestamp with time zone, p_limit integer) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.list_customer_compare_items() FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.list_fleet_vehicles(p_status fleet_vehicle_status) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.list_master_stock(p_limit integer, p_query text) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.list_online_prep_queue(p_limit integer) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.list_pos_customer_garage(p_customer_id uuid) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.list_pos_customers(p_query text, p_limit integer) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.list_pos_popular_pins() FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.list_pos_quotations(p_status pos_quotation_status, p_limit integer) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.list_pos_till_items(p_warehouse_id uuid, p_source text, p_in_stock_only boolean, p_chassis_code text, p_engine_code text, p_category text, p_oems text[], p_section_key text, p_limit integer, p_offset integer) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.list_staff_ops_notifications(p_limit integer) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.list_staff_product_pages(p_query text, p_limit integer) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.list_zig_exchange_rates(p_limit integer) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.my_default_landing() FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.my_module_access() FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.petty_cash_funding_account_code() FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.register_stock_item_image(p_stock_item_id uuid, p_storage_path text, p_is_primary boolean, p_sort_order smallint) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.report_account_register(p_account_code character varying, p_from date, p_to date, p_currency currency_code) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.report_balance_sheet(p_as_of date, p_currency currency_code) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.report_cash_flow(p_from date, p_to date, p_currency currency_code) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.report_profit_and_loss(p_from date, p_to date, p_currency currency_code) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.report_trial_balance(p_as_of date, p_currency currency_code) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.set_customer_credit(p_customer_id uuid, p_credit_limit numeric, p_credit_hold boolean) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.set_pos_cart_customer(p_cart_id uuid, p_customer_id uuid) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.set_pos_cart_vehicle(p_cart_id uuid, p_model_slug text, p_model_name text, p_generation text, p_chassis_code text, p_engine_code text) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.set_stock_item_primary_image(p_image_id uuid) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.update_item_kit(p_kit_id uuid, p_sell_mode kit_sell_mode, p_is_active boolean, p_title text) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.update_pos_customer(p_customer_id uuid, p_customer_kind text, p_display_name text, p_business_name text, p_email text, p_phone_e164 text, p_whatsapp_e164 text) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.upsert_pos_customer_garage_vehicle(p_customer_id uuid, p_vehicle_id uuid, p_model_slug text, p_make text, p_model text, p_generation text, p_chassis_code text, p_engine text, p_vin text, p_is_primary boolean) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.upsert_pos_popular_pin(p_item_type text, p_item_key text, p_label text, p_subtitle text, p_search_query text, p_maker_slug text, p_model_slug text, p_category_name text, p_subcategory_name text, p_oem_part_number text, p_image_url text) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.upsert_preferred_supplier(p_code text, p_name text, p_email text, p_phone_e164 text, p_currency currency_code, p_notes text, p_address text, p_tax_id text, p_payment_terms text, p_categories text[]) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.upsert_staff_product_page(p_stock_item_id uuid, p_unit_price numeric, p_discount_kind text, p_discount_value numeric, p_discount_description text) FROM PUBLIC, anon;

-- 3. 97 functions had no fixed search_path: pinned to the default one (public, extensions).
ALTER FUNCTION _chat_staff_roles() SET search_path = public, extensions;
ALTER FUNCTION _customer_compare_max_items() SET search_path = public, extensions;
ALTER FUNCTION _customers_credit_dual_write_minor() SET search_path = public, extensions;
ALTER FUNCTION _delivery_pod_job_id_from_path(text) SET search_path = public, extensions;
ALTER FUNCTION _driver_shift_active(timestamp with time zone,timestamp with time zone,timestamp with time zone) SET search_path = public, extensions;
ALTER FUNCTION _finance_period_rpc_active() SET search_path = public, extensions;
ALTER FUNCTION _finance_period_rpc_enter() SET search_path = public, extensions;
ALTER FUNCTION _finance_req_id_from_receipt_path(text) SET search_path = public, extensions;
ALTER FUNCTION _finance_req_required_approvals(finance_requisition_type,currency_code,numeric) SET search_path = public, extensions;
ALTER FUNCTION _finance_req_rpc_active() SET search_path = public, extensions;
ALTER FUNCTION _finance_req_rpc_enter() SET search_path = public, extensions;
ALTER FUNCTION _fleet_begin_rpc() SET search_path = public, extensions;
ALTER FUNCTION _fleet_rpc_active() SET search_path = public, extensions;
ALTER FUNCTION _haversine_eta_seconds(double precision,double precision,double precision,double precision) SET search_path = public, extensions;
ALTER FUNCTION _haversine_meters(double precision,double precision,double precision,double precision) SET search_path = public, extensions;
ALTER FUNCTION _line_usd_equiv(numeric,currency_code,numeric) SET search_path = public, extensions;
ALTER FUNCTION _loyalty_ledger_dual_write_minor() SET search_path = public, extensions;
ALTER FUNCTION _loyalty_rpc_active() SET search_path = public, extensions;
ALTER FUNCTION _loyalty_rpc_enter() SET search_path = public, extensions;
ALTER FUNCTION _major_to_minor(numeric) SET search_path = public, extensions;
ALTER FUNCTION _normalize_delivery_pod_object_path(text) SET search_path = public, extensions;
ALTER FUNCTION _normalize_e164(text) SET search_path = public, extensions;
ALTER FUNCTION _normalize_fleet_plate(text) SET search_path = public, extensions;
ALTER FUNCTION _normalize_receipt_email(text) SET search_path = public, extensions;
ALTER FUNCTION _online_dispatch_auto_active() SET search_path = public, extensions;
ALTER FUNCTION _online_dispatch_auto_begin() SET search_path = public, extensions;
ALTER FUNCTION _online_dispatch_auto_clear() SET search_path = public, extensions;
ALTER FUNCTION _payroll_begin_rpc() SET search_path = public, extensions;
ALTER FUNCTION _payroll_rpc_active() SET search_path = public, extensions;
ALTER FUNCTION _po_line_dual_write_minor() SET search_path = public, extensions;
ALTER FUNCTION _po_set_progress_on_submit() SET search_path = public, extensions;
ALTER FUNCTION _procurement_begin_rpc() SET search_path = public, extensions;
ALTER FUNCTION _procurement_rpc_active() SET search_path = public, extensions;
ALTER FUNCTION _recon_begin_rpc() SET search_path = public, extensions;
ALTER FUNCTION _recon_rpc_active() SET search_path = public, extensions;
ALTER FUNCTION _require_dispatcher_staff() SET search_path = public, extensions;
ALTER FUNCTION _require_fleet_staff() SET search_path = public, extensions;
ALTER FUNCTION _require_kit_staff() SET search_path = public, extensions;
ALTER FUNCTION _require_logistics_staff() SET search_path = public, extensions;
ALTER FUNCTION _require_loyalty_staff() SET search_path = public, extensions;
ALTER FUNCTION _require_sales_staff() SET search_path = public, extensions;
ALTER FUNCTION _require_warehouse_staff() SET search_path = public, extensions;
ALTER FUNCTION _require_warranty_staff() SET search_path = public, extensions;
ALTER FUNCTION _review_photo_review_id_from_path(text) SET search_path = public, extensions;
ALTER FUNCTION _staff_ops_notify_begin() SET search_path = public, extensions;
ALTER FUNCTION _store_credit_account_dual_write_minor() SET search_path = public, extensions;
ALTER FUNCTION _store_credit_ledger_dual_write_minor() SET search_path = public, extensions;
ALTER FUNCTION _warranty_begin_rpc() SET search_path = public, extensions;
ALTER FUNCTION _warranty_claim_touch_updated() SET search_path = public, extensions;
ALTER FUNCTION _warranty_rpc_active() SET search_path = public, extensions;
ALTER FUNCTION build_qr_payload(text,text,valuation_method) SET search_path = public, extensions;
ALTER FUNCTION chat_messages_immutable_guard() SET search_path = public, extensions;
ALTER FUNCTION chat_threads_protect_columns() SET search_path = public, extensions;
ALTER FUNCTION chat_threads_touch_updated() SET search_path = public, extensions;
ALTER FUNCTION fleet_vehicles_touch_updated() SET search_path = public, extensions;
ALTER FUNCTION forbid_account_period_direct_mutation() SET search_path = public, extensions;
ALTER FUNCTION forbid_domain_event_mutation() SET search_path = public, extensions;
ALTER FUNCTION forbid_finance_requisition_direct_mutation() SET search_path = public, extensions;
ALTER FUNCTION forbid_finance_requisition_line_direct_mutation() SET search_path = public, extensions;
ALTER FUNCTION forbid_journal_entry_delete() SET search_path = public, extensions;
ALTER FUNCTION forbid_ledger_mutation() SET search_path = public, extensions;
ALTER FUNCTION forbid_loyalty_ledger_mutation() SET search_path = public, extensions;
ALTER FUNCTION forbid_posted_journal_line_mutation() SET search_path = public, extensions;
ALTER FUNCTION forbid_posted_journal_update() SET search_path = public, extensions;
ALTER FUNCTION forbid_store_credit_ledger_mutation() SET search_path = public, extensions;
ALTER FUNCTION guard_delivery_job_mutation() SET search_path = public, extensions;
ALTER FUNCTION guard_delivery_location_mutation() SET search_path = public, extensions;
ALTER FUNCTION guard_delivery_note_line_mutation() SET search_path = public, extensions;
ALTER FUNCTION guard_delivery_note_mutation() SET search_path = public, extensions;
ALTER FUNCTION guard_finance_audit_immutable() SET search_path = public, extensions;
ALTER FUNCTION guard_finance_req_approvals_immutable() SET search_path = public, extensions;
ALTER FUNCTION guard_fleet_vehicle_mutation() SET search_path = public, extensions;
ALTER FUNCTION guard_goods_receipt_line_mutation() SET search_path = public, extensions;
ALTER FUNCTION guard_landed_cost_child_mutation() SET search_path = public, extensions;
ALTER FUNCTION guard_loyalty_account_mutation() SET search_path = public, extensions;
ALTER FUNCTION guard_loyalty_settings_mutation() SET search_path = public, extensions;
ALTER FUNCTION guard_material_request_line_mutation() SET search_path = public, extensions;
ALTER FUNCTION guard_payment_allocation_mutation() SET search_path = public, extensions;
ALTER FUNCTION guard_payment_entry_mutation() SET search_path = public, extensions;
ALTER FUNCTION guard_payroll_deduction_mutation() SET search_path = public, extensions;
ALTER FUNCTION guard_payroll_line_mutation() SET search_path = public, extensions;
ALTER FUNCTION guard_payroll_run_mutation() SET search_path = public, extensions;
ALTER FUNCTION guard_payslip_mutation() SET search_path = public, extensions;
ALTER FUNCTION guard_pick_list_line_mutation() SET search_path = public, extensions;
ALTER FUNCTION guard_pick_list_mutation() SET search_path = public, extensions;
ALTER FUNCTION guard_procurement_doc_mutation() SET search_path = public, extensions;
ALTER FUNCTION guard_purchase_order_line_mutation() SET search_path = public, extensions;
ALTER FUNCTION guard_rfq_line_mutation() SET search_path = public, extensions;
ALTER FUNCTION guard_rfq_supplier_mutation() SET search_path = public, extensions;
ALTER FUNCTION guard_staff_ops_notification_insert() SET search_path = public, extensions;
ALTER FUNCTION guard_stock_reconciliation_line_mutation() SET search_path = public, extensions;
ALTER FUNCTION guard_stock_reconciliation_mutation() SET search_path = public, extensions;
ALTER FUNCTION guard_store_credit_account_mutation() SET search_path = public, extensions;
ALTER FUNCTION guard_supplier_quotation_line_mutation() SET search_path = public, extensions;
ALTER FUNCTION guard_warranty_claim_mutation() SET search_path = public, extensions;
ALTER FUNCTION protect_profile_staff_flag() SET search_path = public, extensions;
ALTER FUNCTION touch_whatsapp_flow_orders_updated_at() SET search_path = public, extensions;

-- 4. Row-level policies called auth.uid() / auth.role() / auth.jwt() once per row: wrapped in a
--    sub-select so Postgres evaluates it once per query (same result, much faster on big tables).
ALTER POLICY "ai_report_subscriptions_admin_finance_insert" ON public.ai_report_subscriptions WITH CHECK ((has_staff_role(ARRAY['admin'::staff_role, 'finance'::staff_role]) AND ((created_by IS NULL) OR (created_by = (SELECT auth.uid())))));
ALTER POLICY "attendance_events_insert_hr_or_self" ON public.attendance_events WITH CHECK ((has_staff_role(ARRAY['admin'::staff_role, 'hr'::staff_role]) OR (EXISTS ( SELECT 1
   FROM employees e
  WHERE ((e.id = attendance_events.employee_id) AND (e.user_id = (SELECT auth.uid())))))));
ALTER POLICY "attendance_events_select_hr_or_self" ON public.attendance_events USING ((has_staff_role(ARRAY['admin'::staff_role, 'hr'::staff_role]) OR (EXISTS ( SELECT 1
   FROM employees e
  WHERE ((e.id = attendance_events.employee_id) AND (e.user_id = (SELECT auth.uid())))))));
ALTER POLICY "chat_messages_customer_insert" ON public.chat_messages WITH CHECK (((sender_user_id = (SELECT auth.uid())) AND (sender_kind = 'customer'::chat_sender_kind) AND (NOT is_staff()) AND (EXISTS ( SELECT 1
   FROM chat_threads t
  WHERE ((t.id = chat_messages.thread_id) AND (t.customer_user_id = (SELECT auth.uid())) AND (t.status = ANY (ARRAY['open'::chat_thread_status, 'assigned'::chat_thread_status])))))));
ALTER POLICY "chat_messages_staff_insert" ON public.chat_messages WITH CHECK (((sender_user_id = (SELECT auth.uid())) AND (sender_kind = 'staff'::chat_sender_kind) AND has_staff_role(_chat_staff_roles()) AND (EXISTS ( SELECT 1
   FROM chat_threads t
  WHERE ((t.id = chat_messages.thread_id) AND _can_select_chat_thread(t.*) AND (t.status = ANY (ARRAY['open'::chat_thread_status, 'assigned'::chat_thread_status])))))));
ALTER POLICY "chat_participants_insert_own" ON public.chat_participants WITH CHECK (((user_id = (SELECT auth.uid())) AND (((role = 'customer'::chat_participant_role) AND (NOT is_staff()) AND (EXISTS ( SELECT 1
   FROM chat_threads t
  WHERE ((t.id = chat_participants.thread_id) AND (t.customer_user_id = (SELECT auth.uid())))))) OR ((role = 'staff'::chat_participant_role) AND has_staff_role(_chat_staff_roles()) AND (EXISTS ( SELECT 1
   FROM chat_threads t
  WHERE ((t.id = chat_participants.thread_id) AND _can_select_chat_thread(t.*))))))));
ALTER POLICY "chat_participants_select" ON public.chat_participants USING (((user_id = (SELECT auth.uid())) OR (EXISTS ( SELECT 1
   FROM chat_threads t
  WHERE ((t.id = chat_participants.thread_id) AND _can_select_chat_thread(t.*))))));
ALTER POLICY "chat_participants_update_own" ON public.chat_participants USING ((user_id = (SELECT auth.uid()))) WITH CHECK (((user_id = (SELECT auth.uid())) AND (role = ( SELECT p.role
   FROM chat_participants p
  WHERE ((p.thread_id = chat_participants.thread_id) AND (p.user_id = (SELECT auth.uid())))))));
ALTER POLICY "chat_threads_customer_insert" ON public.chat_threads WITH CHECK (((customer_user_id = (SELECT auth.uid())) AND (NOT is_staff()) AND (status = 'open'::chat_thread_status) AND (assigned_to IS NULL) AND (closed_at IS NULL) AND (closed_by IS NULL) AND ((customer_id IS NULL) OR (customer_id = _current_customer_id()))));
ALTER POLICY "customers_select_own" ON public.customers USING ((profile_id = (SELECT auth.uid())));
ALTER POLICY "customers_update_own" ON public.customers USING ((profile_id = (SELECT auth.uid()))) WITH CHECK ((profile_id = (SELECT auth.uid())));
ALTER POLICY "delivery_balance_approvals_read" ON public.delivery_balance_approvals USING (((requested_by = (SELECT auth.uid())) OR has_staff_role(ARRAY['admin'::staff_role, 'dispatcher'::staff_role, 'finance'::staff_role]) OR is_pos_approver()));
ALTER POLICY "delivery_jobs_staff_select" ON public.delivery_jobs USING ((has_staff_role(ARRAY['admin'::staff_role, 'warehouse'::staff_role, 'dispatcher'::staff_role]) OR (assignee_user_id = (SELECT auth.uid()))));
ALTER POLICY "driver_cash_handins_read" ON public.driver_cash_handins USING (((driver_user_id = (SELECT auth.uid())) OR has_staff_role(ARRAY['admin'::staff_role, 'finance'::staff_role, 'sales'::staff_role, 'dispatcher'::staff_role]) OR is_pos_approver()));
ALTER POLICY "driver_cash_recoveries_read" ON public.driver_cash_recoveries USING ((EXISTS ( SELECT 1
   FROM driver_cash_handins h
  WHERE ((h.id = driver_cash_recoveries.handin_id) AND ((h.driver_user_id = (SELECT auth.uid())) OR has_staff_role(ARRAY['admin'::staff_role, 'finance'::staff_role, 'sales'::staff_role, 'dispatcher'::staff_role]) OR is_pos_approver())))));
ALTER POLICY "driver_presence_self_insert" ON public.driver_presence WITH CHECK (((user_id = (SELECT auth.uid())) AND has_staff_role(ARRAY['driver'::staff_role])));
ALTER POLICY "driver_presence_self_select" ON public.driver_presence USING (((user_id = (SELECT auth.uid())) OR has_staff_role(ARRAY['admin'::staff_role, 'warehouse'::staff_role, 'dispatcher'::staff_role])));
ALTER POLICY "driver_presence_self_update" ON public.driver_presence USING (((user_id = (SELECT auth.uid())) AND has_staff_role(ARRAY['driver'::staff_role]))) WITH CHECK (((user_id = (SELECT auth.uid())) AND has_staff_role(ARRAY['driver'::staff_role])));
ALTER POLICY "ecocash_intents_staff_select" ON public.ecocash_payment_intents USING ((EXISTS ( SELECT 1
   FROM staff_roles sr
  WHERE ((sr.user_id = (SELECT auth.uid())) AND (sr.role = ANY (ARRAY['admin'::staff_role, 'finance'::staff_role, 'sales'::staff_role]))))));
ALTER POLICY "employees_select_hr_or_self" ON public.employees USING ((has_staff_role(ARRAY['admin'::staff_role, 'hr'::staff_role]) OR (user_id = (SELECT auth.uid()))));
ALTER POLICY "finance_req_approvals_insert" ON public.finance_requisition_approvals WITH CHECK ((has_staff_role(ARRAY['admin'::staff_role, 'finance'::staff_role]) AND (approver_user_id = (SELECT auth.uid()))));
ALTER POLICY "finance_requisition_lines_select" ON public.finance_requisition_lines USING ((EXISTS ( SELECT 1
   FROM finance_requisitions r
  WHERE ((r.id = finance_requisition_lines.requisition_id) AND ((r.requested_by = (SELECT auth.uid())) OR has_staff_role(ARRAY['admin'::staff_role, 'finance'::staff_role]) OR (is_staff() AND (r.status = ANY (ARRAY['submitted'::finance_requisition_status, 'approved'::finance_requisition_status, 'rejected'::finance_requisition_status, 'disbursed'::finance_requisition_status, 'cancelled'::finance_requisition_status]))))))));
ALTER POLICY "finance_req_receipts_select" ON public.finance_requisition_receipts USING ((EXISTS ( SELECT 1
   FROM finance_requisitions r
  WHERE ((r.id = finance_requisition_receipts.requisition_id) AND ((r.requested_by = (SELECT auth.uid())) OR has_staff_role(ARRAY['admin'::staff_role, 'finance'::staff_role]) OR (is_staff() AND (r.status = ANY (ARRAY['submitted'::finance_requisition_status, 'approved'::finance_requisition_status, 'rejected'::finance_requisition_status, 'disbursed'::finance_requisition_status, 'cancelled'::finance_requisition_status]))))))));
ALTER POLICY "finance_requisitions_select" ON public.finance_requisitions USING (((requested_by = (SELECT auth.uid())) OR has_staff_role(ARRAY['admin'::staff_role, 'finance'::staff_role]) OR (is_staff() AND (status = ANY (ARRAY['submitted'::finance_requisition_status, 'approved'::finance_requisition_status, 'rejected'::finance_requisition_status, 'disbursed'::finance_requisition_status, 'cancelled'::finance_requisition_status])))));
ALTER POLICY "goods_receipt_lines_staff_select" ON public.goods_receipt_lines USING ((((SELECT auth.role()) = 'service_role'::text) OR has_staff_role(ARRAY['admin'::staff_role, 'warehouse'::staff_role, 'finance'::staff_role])));
ALTER POLICY "goods_receipts_staff_select" ON public.goods_receipts USING ((((SELECT auth.role()) = 'service_role'::text) OR has_staff_role(ARRAY['admin'::staff_role, 'warehouse'::staff_role, 'finance'::staff_role])));
ALTER POLICY "hr_leave_balances_select" ON public.hr_leave_balances USING ((has_staff_role(ARRAY['admin'::staff_role, 'hr'::staff_role]) OR (EXISTS ( SELECT 1
   FROM employees e
  WHERE ((e.id = hr_leave_balances.employee_id) AND (e.user_id = (SELECT auth.uid())))))));
ALTER POLICY "hr_leave_requests_insert" ON public.hr_leave_requests WITH CHECK ((has_staff_role(ARRAY['admin'::staff_role, 'hr'::staff_role]) OR (EXISTS ( SELECT 1
   FROM employees e
  WHERE ((e.id = hr_leave_requests.employee_id) AND (e.user_id = (SELECT auth.uid())))))));
ALTER POLICY "hr_leave_requests_select" ON public.hr_leave_requests USING ((has_staff_role(ARRAY['admin'::staff_role, 'hr'::staff_role]) OR (EXISTS ( SELECT 1
   FROM employees e
  WHERE ((e.id = hr_leave_requests.employee_id) AND (e.user_id = (SELECT auth.uid())))))));
ALTER POLICY "hr_leave_requests_update" ON public.hr_leave_requests USING ((has_staff_role(ARRAY['admin'::staff_role, 'hr'::staff_role]) OR ((status = 'draft'::hr_leave_request_status) AND (EXISTS ( SELECT 1
   FROM employees e
  WHERE ((e.id = hr_leave_requests.employee_id) AND (e.user_id = (SELECT auth.uid()))))))));
ALTER POLICY "landed_cost_allocations_staff_select" ON public.landed_cost_allocations USING ((((SELECT auth.role()) = 'service_role'::text) OR has_staff_role(ARRAY['admin'::staff_role, 'finance'::staff_role])));
ALTER POLICY "landed_cost_charges_staff_select" ON public.landed_cost_charges USING ((((SELECT auth.role()) = 'service_role'::text) OR has_staff_role(ARRAY['admin'::staff_role, 'finance'::staff_role])));
ALTER POLICY "landed_cost_staff_select" ON public.landed_cost_vouchers USING ((((SELECT auth.role()) = 'service_role'::text) OR has_staff_role(ARRAY['admin'::staff_role, 'finance'::staff_role])));
ALTER POLICY "loyalty_accounts_customer_select" ON public.loyalty_accounts USING ((customer_id IN ( SELECT c.id
   FROM customers c
  WHERE (c.profile_id = (SELECT auth.uid())))));
ALTER POLICY "loyalty_ledger_customer_select" ON public.loyalty_ledger USING ((customer_id IN ( SELECT c.id
   FROM customers c
  WHERE (c.profile_id = (SELECT auth.uid())))));
ALTER POLICY "sms_prefs_select_own_or_admin" ON public.manager_sms_preferences USING (((user_id = (SELECT auth.uid())) OR has_staff_role(ARRAY['admin'::staff_role])));
ALTER POLICY "sms_prefs_update_own" ON public.manager_sms_preferences USING ((user_id = (SELECT auth.uid()))) WITH CHECK ((user_id = (SELECT auth.uid())));
ALTER POLICY "sms_prefs_upsert_own" ON public.manager_sms_preferences WITH CHECK (((user_id = (SELECT auth.uid())) AND is_staff()));
ALTER POLICY "material_request_lines_staff_select" ON public.material_request_lines USING ((((SELECT auth.role()) = 'service_role'::text) OR has_staff_role(ARRAY['admin'::staff_role, 'warehouse'::staff_role, 'finance'::staff_role])));
ALTER POLICY "material_requests_staff_select" ON public.material_requests USING ((((SELECT auth.role()) = 'service_role'::text) OR has_staff_role(ARRAY['admin'::staff_role, 'warehouse'::staff_role, 'finance'::staff_role])));
ALTER POLICY "panic_events_driver_insert" ON public.panic_events WITH CHECK (((driver_user_id = (SELECT auth.uid())) AND has_staff_role(ARRAY['driver'::staff_role])));
ALTER POLICY "panic_events_driver_select_own" ON public.panic_events USING (((driver_user_id = (SELECT auth.uid())) OR has_staff_role(ARRAY['admin'::staff_role, 'warehouse'::staff_role, 'dispatcher'::staff_role])));
ALTER POLICY "payment_tender_gl_finance_write" ON public.payment_tender_gl_accounts USING ((EXISTS ( SELECT 1
   FROM staff_roles sr
  WHERE ((sr.user_id = (SELECT auth.uid())) AND (sr.role = ANY (ARRAY['admin'::staff_role, 'finance'::staff_role])))))) WITH CHECK ((EXISTS ( SELECT 1
   FROM staff_roles sr
  WHERE ((sr.user_id = (SELECT auth.uid())) AND (sr.role = ANY (ARRAY['admin'::staff_role, 'finance'::staff_role]))))));
ALTER POLICY "payment_tender_gl_select" ON public.payment_tender_gl_accounts USING ((EXISTS ( SELECT 1
   FROM staff_roles sr
  WHERE ((sr.user_id = (SELECT auth.uid())) AND (sr.role = ANY (ARRAY['admin'::staff_role, 'finance'::staff_role, 'sales'::staff_role]))))));
ALTER POLICY "payroll_deduction_lines_select_hr_or_self" ON public.payroll_deduction_lines USING ((has_staff_role(ARRAY['admin'::staff_role, 'hr'::staff_role]) OR (EXISTS ( SELECT 1
   FROM (payroll_lines l
     JOIN employees e ON ((e.id = l.employee_id)))
  WHERE ((l.id = payroll_deduction_lines.payroll_line_id) AND (e.user_id = (SELECT auth.uid())))))));
ALTER POLICY "payroll_lines_select_hr_or_self" ON public.payroll_lines USING ((has_staff_role(ARRAY['admin'::staff_role, 'hr'::staff_role]) OR (EXISTS ( SELECT 1
   FROM employees e
  WHERE ((e.id = payroll_lines.employee_id) AND (e.user_id = (SELECT auth.uid())))))));
ALTER POLICY "payroll_runs_select_hr" ON public.payroll_runs USING ((has_staff_role(ARRAY['admin'::staff_role, 'hr'::staff_role]) OR (EXISTS ( SELECT 1
   FROM (payroll_lines l
     JOIN employees e ON ((e.id = l.employee_id)))
  WHERE ((l.payroll_run_id = payroll_runs.id) AND (e.user_id = (SELECT auth.uid())))))));
ALTER POLICY "payslips_select_hr_or_self" ON public.payslips USING ((has_staff_role(ARRAY['admin'::staff_role, 'hr'::staff_role]) OR (EXISTS ( SELECT 1
   FROM employees e
  WHERE ((e.id = payslips.employee_id) AND (e.user_id = (SELECT auth.uid())))))));
ALTER POLICY "pos_offline_sale_receipts_select" ON public.pos_offline_sale_receipts USING (((actor_user_id = (SELECT auth.uid())) OR has_staff_role(ARRAY['admin'::staff_role, 'finance'::staff_role])));
ALTER POLICY "pos_operator_hidden_bestsellers_own_read" ON public.pos_operator_hidden_bestsellers USING ((user_id = (SELECT auth.uid())));
ALTER POLICY "pos_operator_hidden_bestsellers_own_write" ON public.pos_operator_hidden_bestsellers USING ((user_id = (SELECT auth.uid()))) WITH CHECK ((user_id = (SELECT auth.uid())));
ALTER POLICY "pos_operator_popular_pins_own_read" ON public.pos_operator_popular_pins USING ((user_id = (SELECT auth.uid())));
ALTER POLICY "pos_operator_popular_pins_own_write" ON public.pos_operator_popular_pins USING ((user_id = (SELECT auth.uid()))) WITH CHECK ((user_id = (SELECT auth.uid())));
ALTER POLICY "pos_scan_sessions_staff_select" ON public.pos_scan_sessions USING ((has_staff_role(ARRAY['admin'::staff_role, 'sales'::staff_role, 'warehouse'::staff_role]) AND ((owner_user_id = (SELECT auth.uid())) OR (scanner_user_id = (SELECT auth.uid())) OR has_staff_role(ARRAY['admin'::staff_role]))));
ALTER POLICY "profiles_insert_own" ON public.profiles WITH CHECK (((id = (SELECT auth.uid())) AND (is_staff = false)));
ALTER POLICY "profiles_select_own_or_admin" ON public.profiles USING (((id = (SELECT auth.uid())) OR has_staff_role(ARRAY['admin'::staff_role])));
ALTER POLICY "profiles_update_own" ON public.profiles USING ((id = (SELECT auth.uid()))) WITH CHECK (((id = (SELECT auth.uid())) AND (is_staff = is_staff())));
ALTER POLICY "purchase_order_lines_staff_select" ON public.purchase_order_lines USING ((((SELECT auth.role()) = 'service_role'::text) OR has_staff_role(ARRAY['admin'::staff_role, 'warehouse'::staff_role, 'finance'::staff_role])));
ALTER POLICY "purchase_orders_staff_select" ON public.purchase_orders USING ((((SELECT auth.role()) = 'service_role'::text) OR has_staff_role(ARRAY['admin'::staff_role, 'warehouse'::staff_role, 'finance'::staff_role])));
ALTER POLICY "rfq_lines_staff_select" ON public.rfq_lines USING ((((SELECT auth.role()) = 'service_role'::text) OR has_staff_role(ARRAY['admin'::staff_role, 'warehouse'::staff_role, 'finance'::staff_role])));
ALTER POLICY "rfq_suppliers_staff_select" ON public.rfq_suppliers USING ((((SELECT auth.role()) = 'service_role'::text) OR has_staff_role(ARRAY['admin'::staff_role, 'warehouse'::staff_role, 'finance'::staff_role])));
ALTER POLICY "rfqs_staff_select" ON public.rfqs USING ((((SELECT auth.role()) = 'service_role'::text) OR has_staff_role(ARRAY['admin'::staff_role, 'warehouse'::staff_role, 'finance'::staff_role])));
ALTER POLICY "salary_structures_select_hr_or_self" ON public.salary_structures USING ((has_staff_role(ARRAY['admin'::staff_role, 'hr'::staff_role]) OR (EXISTS ( SELECT 1
   FROM employees e
  WHERE ((e.id = salary_structures.employee_id) AND (e.user_id = (SELECT auth.uid())))))));
ALTER POLICY "sms_outbox_select_own_or_admin" ON public.sms_outbox USING (((recipient_user_id = (SELECT auth.uid())) OR has_staff_role(ARRAY['admin'::staff_role])));
ALTER POLICY "staff_alert_settings_own_read" ON public.staff_alert_settings USING ((user_id = (SELECT auth.uid())));
ALTER POLICY "staff_ops_notifications_select_own" ON public.staff_ops_notifications USING (((recipient_user_id = (SELECT auth.uid())) AND has_staff_role(ARRAY['admin'::staff_role, 'sales'::staff_role, 'warehouse'::staff_role, 'dispatcher'::staff_role])));
ALTER POLICY "staff_ops_notifications_update_own_read" ON public.staff_ops_notifications USING (((recipient_user_id = (SELECT auth.uid())) AND has_staff_role(ARRAY['admin'::staff_role, 'sales'::staff_role, 'warehouse'::staff_role, 'dispatcher'::staff_role]))) WITH CHECK (((recipient_user_id = (SELECT auth.uid())) AND has_staff_role(ARRAY['admin'::staff_role, 'sales'::staff_role, 'warehouse'::staff_role, 'dispatcher'::staff_role])));
ALTER POLICY "staff_roles_select_own_or_admin" ON public.staff_roles USING (((user_id = (SELECT auth.uid())) OR has_staff_role(ARRAY['admin'::staff_role])));
ALTER POLICY "store_credit_accounts_customer_select" ON public.store_credit_accounts USING ((customer_id IN ( SELECT c.id
   FROM customers c
  WHERE (c.profile_id = (SELECT auth.uid())))));
ALTER POLICY "supplier_quotation_lines_staff_select" ON public.supplier_quotation_lines USING ((((SELECT auth.role()) = 'service_role'::text) OR has_staff_role(ARRAY['admin'::staff_role, 'warehouse'::staff_role, 'finance'::staff_role])));
ALTER POLICY "supplier_quotations_staff_select" ON public.supplier_quotations USING ((((SELECT auth.role()) = 'service_role'::text) OR has_staff_role(ARRAY['admin'::staff_role, 'warehouse'::staff_role, 'finance'::staff_role])));
ALTER POLICY "suppliers_self_select" ON public.suppliers USING ((profile_id = (SELECT auth.uid())));
ALTER POLICY "suppliers_staff_all" ON public.suppliers USING ((((SELECT auth.role()) = 'service_role'::text) OR has_staff_role(ARRAY['admin'::staff_role, 'warehouse'::staff_role, 'finance'::staff_role]))) WITH CHECK ((((SELECT auth.role()) = 'service_role'::text) OR has_staff_role(ARRAY['admin'::staff_role, 'warehouse'::staff_role, 'finance'::staff_role])));
ALTER POLICY "whatsapp_flow_orders_staff_select" ON public.whatsapp_flow_orders USING ((EXISTS ( SELECT 1
   FROM staff_roles sr
  WHERE ((sr.user_id = (SELECT auth.uid())) AND (sr.role = ANY (ARRAY['admin'::staff_role, 'finance'::staff_role, 'sales'::staff_role, 'warehouse'::staff_role, 'dispatcher'::staff_role]))))));

-- 5. Foreign keys without an index (slow joins and slow deletes on the parent): indexed.
CREATE INDEX IF NOT EXISTS journal_entries_posted_by_fkx ON public.journal_entries (posted_by);
CREATE INDEX IF NOT EXISTS journal_entries_reverses_entry_id_fkx ON public.journal_entries (reverses_entry_id);
CREATE INDEX IF NOT EXISTS inventory_qr_codes_stock_item_id_fkx ON public.inventory_qr_codes (stock_item_id);
CREATE INDEX IF NOT EXISTS domain_events_actor_user_id_fkx ON public.domain_events (actor_user_id);
CREATE INDEX IF NOT EXISTS sms_outbox_event_code_fkx ON public.sms_outbox (event_code);
CREATE INDEX IF NOT EXISTS sms_outbox_recipient_user_id_fkx ON public.sms_outbox (recipient_user_id);
CREATE INDEX IF NOT EXISTS accounting_periods_locked_by_fkx ON public.accounting_periods (locked_by);
CREATE INDEX IF NOT EXISTS bank_statements_account_code_fkx ON public.bank_statements (account_code);
CREATE INDEX IF NOT EXISTS bank_statements_created_by_fkx ON public.bank_statements (created_by);
CREATE INDEX IF NOT EXISTS bank_statement_lines_statement_id_fkx ON public.bank_statement_lines (statement_id);
CREATE INDEX IF NOT EXISTS bank_recon_matches_journal_entry_line_id_fkx ON public.bank_recon_matches (journal_entry_line_id);
CREATE INDEX IF NOT EXISTS bank_recon_matches_matched_by_fkx ON public.bank_recon_matches (matched_by);
CREATE INDEX IF NOT EXISTS stock_items_base_uom_id_fkx ON public.stock_items (base_uom_id);
CREATE INDEX IF NOT EXISTS inventory_qr_codes_stock_batch_id_fkx ON public.inventory_qr_codes (stock_batch_id);
CREATE INDEX IF NOT EXISTS item_uom_conversions_from_uom_id_fkx ON public.item_uom_conversions (from_uom_id);
CREATE INDEX IF NOT EXISTS item_uom_conversions_to_uom_id_fkx ON public.item_uom_conversions (to_uom_id);
CREATE INDEX IF NOT EXISTS stock_batches_warehouse_id_fkx ON public.stock_batches (warehouse_id);
CREATE INDEX IF NOT EXISTS inventory_qr_codes_stock_entry_line_id_fkx ON public.inventory_qr_codes (stock_entry_line_id);
CREATE INDEX IF NOT EXISTS stock_entries_from_warehouse_id_fkx ON public.stock_entries (from_warehouse_id);
CREATE INDEX IF NOT EXISTS stock_entries_to_warehouse_id_fkx ON public.stock_entries (to_warehouse_id);
CREATE INDEX IF NOT EXISTS stock_entries_created_by_fkx ON public.stock_entries (created_by);
CREATE INDEX IF NOT EXISTS stock_entries_first_approver_id_fkx ON public.stock_entries (first_approver_id);
CREATE INDEX IF NOT EXISTS stock_entries_second_approver_id_fkx ON public.stock_entries (second_approver_id);
CREATE INDEX IF NOT EXISTS stock_entry_lines_stock_item_id_fkx ON public.stock_entry_lines (stock_item_id);
CREATE INDEX IF NOT EXISTS stock_entry_lines_uom_id_fkx ON public.stock_entry_lines (uom_id);
CREATE INDEX IF NOT EXISTS stock_entry_lines_stock_batch_id_fkx ON public.stock_entry_lines (stock_batch_id);
CREATE INDEX IF NOT EXISTS stock_serials_stock_batch_id_fkx ON public.stock_serials (stock_batch_id);
CREATE INDEX IF NOT EXISTS stock_serials_warehouse_id_fkx ON public.stock_serials (warehouse_id);
CREATE INDEX IF NOT EXISTS stock_serials_stock_entry_line_id_fkx ON public.stock_serials (stock_entry_line_id);
CREATE INDEX IF NOT EXISTS price_list_items_stock_item_id_fkx ON public.price_list_items (stock_item_id);
CREATE INDEX IF NOT EXISTS customer_price_overrides_stock_item_id_fkx ON public.customer_price_overrides (stock_item_id);
CREATE INDEX IF NOT EXISTS customers_price_list_id_fkx ON public.customers (price_list_id);
CREATE INDEX IF NOT EXISTS stock_reconciliations_created_by_fkx ON public.stock_reconciliations (created_by);
CREATE INDEX IF NOT EXISTS sales_invoices_journal_entry_id_fkx ON public.sales_invoices (journal_entry_id);
CREATE INDEX IF NOT EXISTS sales_invoices_posted_by_fkx ON public.sales_invoices (posted_by);
CREATE INDEX IF NOT EXISTS sales_invoice_lines_stock_item_id_fkx ON public.sales_invoice_lines (stock_item_id);
CREATE INDEX IF NOT EXISTS sales_invoice_lines_parent_line_id_fkx ON public.sales_invoice_lines (parent_line_id);
CREATE INDEX IF NOT EXISTS sales_invoice_lines_uom_id_fkx ON public.sales_invoice_lines (uom_id);
CREATE INDEX IF NOT EXISTS sales_invoice_lines_stock_batch_id_fkx ON public.sales_invoice_lines (stock_batch_id);
CREATE INDEX IF NOT EXISTS stock_reconciliations_first_approver_id_fkx ON public.stock_reconciliations (first_approver_id);
CREATE INDEX IF NOT EXISTS stock_reconciliations_second_approver_id_fkx ON public.stock_reconciliations (second_approver_id);
CREATE INDEX IF NOT EXISTS stock_reconciliations_journal_entry_id_fkx ON public.stock_reconciliations (journal_entry_id);
CREATE INDEX IF NOT EXISTS stock_reconciliations_reversal_journal_entry_id_fkx ON public.stock_reconciliations (reversal_journal_entry_id);
CREATE INDEX IF NOT EXISTS stock_reconciliation_lines_stock_item_id_fkx ON public.stock_reconciliation_lines (stock_item_id);
CREATE INDEX IF NOT EXISTS stock_reconciliation_lines_stock_batch_id_fkx ON public.stock_reconciliation_lines (stock_batch_id);
CREATE INDEX IF NOT EXISTS material_requests_warehouse_id_fkx ON public.material_requests (warehouse_id);
CREATE INDEX IF NOT EXISTS material_requests_created_by_fkx ON public.material_requests (created_by);
CREATE INDEX IF NOT EXISTS material_request_lines_stock_item_id_fkx ON public.material_request_lines (stock_item_id);
CREATE INDEX IF NOT EXISTS material_request_lines_uom_id_fkx ON public.material_request_lines (uom_id);
CREATE INDEX IF NOT EXISTS purchase_orders_supplier_id_fkx ON public.purchase_orders (supplier_id);
CREATE INDEX IF NOT EXISTS purchase_orders_warehouse_id_fkx ON public.purchase_orders (warehouse_id);
CREATE INDEX IF NOT EXISTS purchase_orders_material_request_id_fkx ON public.purchase_orders (material_request_id);
CREATE INDEX IF NOT EXISTS purchase_orders_created_by_fkx ON public.purchase_orders (created_by);
CREATE INDEX IF NOT EXISTS landed_cost_vouchers_reversal_journal_entry_id_fkx ON public.landed_cost_vouchers (reversal_journal_entry_id);
CREATE INDEX IF NOT EXISTS purchase_order_lines_stock_item_id_fkx ON public.purchase_order_lines (stock_item_id);
CREATE INDEX IF NOT EXISTS purchase_order_lines_uom_id_fkx ON public.purchase_order_lines (uom_id);
CREATE INDEX IF NOT EXISTS purchase_order_lines_material_request_line_id_fkx ON public.purchase_order_lines (material_request_line_id);
CREATE INDEX IF NOT EXISTS material_request_lines_purchase_order_line_id_fkx ON public.material_request_lines (purchase_order_line_id);
CREATE INDEX IF NOT EXISTS goods_receipts_purchase_order_id_fkx ON public.goods_receipts (purchase_order_id);
CREATE INDEX IF NOT EXISTS goods_receipts_supplier_id_fkx ON public.goods_receipts (supplier_id);
CREATE INDEX IF NOT EXISTS goods_receipts_warehouse_id_fkx ON public.goods_receipts (warehouse_id);
CREATE INDEX IF NOT EXISTS goods_receipts_stock_entry_id_fkx ON public.goods_receipts (stock_entry_id);
CREATE INDEX IF NOT EXISTS goods_receipts_created_by_fkx ON public.goods_receipts (created_by);
CREATE INDEX IF NOT EXISTS goods_receipt_lines_purchase_order_line_id_fkx ON public.goods_receipt_lines (purchase_order_line_id);
CREATE INDEX IF NOT EXISTS goods_receipt_lines_stock_item_id_fkx ON public.goods_receipt_lines (stock_item_id);
CREATE INDEX IF NOT EXISTS goods_receipt_lines_uom_id_fkx ON public.goods_receipt_lines (uom_id);
CREATE INDEX IF NOT EXISTS goods_receipt_lines_stock_entry_line_id_fkx ON public.goods_receipt_lines (stock_entry_line_id);
CREATE INDEX IF NOT EXISTS landed_cost_vouchers_goods_receipt_id_fkx ON public.landed_cost_vouchers (goods_receipt_id);
CREATE INDEX IF NOT EXISTS landed_cost_vouchers_journal_entry_id_fkx ON public.landed_cost_vouchers (journal_entry_id);
CREATE INDEX IF NOT EXISTS landed_cost_vouchers_created_by_fkx ON public.landed_cost_vouchers (created_by);
CREATE INDEX IF NOT EXISTS landed_cost_charges_landed_cost_voucher_id_fkx ON public.landed_cost_charges (landed_cost_voucher_id);
CREATE INDEX IF NOT EXISTS landed_cost_allocations_stock_batch_id_fkx ON public.landed_cost_allocations (stock_batch_id);
CREATE INDEX IF NOT EXISTS rfqs_warehouse_id_fkx ON public.rfqs (warehouse_id);
CREATE INDEX IF NOT EXISTS rfqs_created_by_fkx ON public.rfqs (created_by);
CREATE INDEX IF NOT EXISTS rfq_lines_stock_item_id_fkx ON public.rfq_lines (stock_item_id);
CREATE INDEX IF NOT EXISTS rfq_lines_uom_id_fkx ON public.rfq_lines (uom_id);
CREATE INDEX IF NOT EXISTS supplier_quotations_supplier_id_fkx ON public.supplier_quotations (supplier_id);
CREATE INDEX IF NOT EXISTS supplier_quotation_lines_rfq_line_id_fkx ON public.supplier_quotation_lines (rfq_line_id);
CREATE INDEX IF NOT EXISTS supplier_quotation_lines_stock_item_id_fkx ON public.supplier_quotation_lines (stock_item_id);
CREATE INDEX IF NOT EXISTS supplier_quotation_lines_uom_id_fkx ON public.supplier_quotation_lines (uom_id);
CREATE INDEX IF NOT EXISTS rfqs_awarded_quotation_id_fkx ON public.rfqs (awarded_quotation_id);
CREATE INDEX IF NOT EXISTS purchase_orders_rfq_id_fkx ON public.purchase_orders (rfq_id);
CREATE INDEX IF NOT EXISTS purchase_orders_awarded_quotation_id_fkx ON public.purchase_orders (awarded_quotation_id);
CREATE INDEX IF NOT EXISTS purchase_order_lines_blanket_parent_line_id_fkx ON public.purchase_order_lines (blanket_parent_line_id);
CREATE INDEX IF NOT EXISTS attendance_events_recorded_by_fkx ON public.attendance_events (recorded_by);
CREATE INDEX IF NOT EXISTS payroll_runs_created_by_fkx ON public.payroll_runs (created_by);
CREATE INDEX IF NOT EXISTS payroll_lines_salary_structure_id_fkx ON public.payroll_lines (salary_structure_id);
CREATE INDEX IF NOT EXISTS payslips_generated_by_fkx ON public.payslips (generated_by);
CREATE INDEX IF NOT EXISTS pick_lists_created_by_fkx ON public.pick_lists (created_by);
CREATE INDEX IF NOT EXISTS pick_list_lines_stock_item_id_fkx ON public.pick_list_lines (stock_item_id);
CREATE INDEX IF NOT EXISTS pick_list_lines_uom_id_fkx ON public.pick_list_lines (uom_id);
CREATE INDEX IF NOT EXISTS delivery_notes_stock_entry_id_fkx ON public.delivery_notes (stock_entry_id);
CREATE INDEX IF NOT EXISTS delivery_notes_cogs_journal_entry_id_fkx ON public.delivery_notes (cogs_journal_entry_id);
CREATE INDEX IF NOT EXISTS delivery_notes_reverse_journal_entry_id_fkx ON public.delivery_notes (reverse_journal_entry_id);
CREATE INDEX IF NOT EXISTS delivery_notes_created_by_fkx ON public.delivery_notes (created_by);
CREATE INDEX IF NOT EXISTS delivery_notes_submitted_by_fkx ON public.delivery_notes (submitted_by);
CREATE INDEX IF NOT EXISTS delivery_note_lines_stock_item_id_fkx ON public.delivery_note_lines (stock_item_id);
CREATE INDEX IF NOT EXISTS delivery_note_lines_uom_id_fkx ON public.delivery_note_lines (uom_id);
CREATE INDEX IF NOT EXISTS delivery_jobs_created_by_fkx ON public.delivery_jobs (created_by);
CREATE INDEX IF NOT EXISTS payment_entries_journal_entry_id_fkx ON public.payment_entries (journal_entry_id);
CREATE INDEX IF NOT EXISTS payment_entries_reversal_journal_entry_id_fkx ON public.payment_entries (reversal_journal_entry_id);
CREATE INDEX IF NOT EXISTS payment_entries_posted_by_fkx ON public.payment_entries (posted_by);
CREATE INDEX IF NOT EXISTS payment_entries_created_by_fkx ON public.payment_entries (created_by);
CREATE INDEX IF NOT EXISTS store_credit_ledger_customer_id_fkx ON public.store_credit_ledger (customer_id);
CREATE INDEX IF NOT EXISTS store_credit_ledger_payment_entry_id_fkx ON public.store_credit_ledger (payment_entry_id);
CREATE INDEX IF NOT EXISTS store_credit_ledger_journal_entry_id_fkx ON public.store_credit_ledger (journal_entry_id);
CREATE INDEX IF NOT EXISTS store_credit_ledger_reverses_ledger_id_fkx ON public.store_credit_ledger (reverses_ledger_id);
CREATE INDEX IF NOT EXISTS store_credit_ledger_created_by_fkx ON public.store_credit_ledger (created_by);
CREATE INDEX IF NOT EXISTS contipay_payment_intents_created_by_fkx ON public.contipay_payment_intents (created_by);
CREATE INDEX IF NOT EXISTS receipt_pdf_artifacts_generated_by_fkx ON public.receipt_pdf_artifacts (generated_by);
CREATE INDEX IF NOT EXISTS forecast_suggestions_stock_item_id_fkx ON public.forecast_suggestions (stock_item_id);
CREATE INDEX IF NOT EXISTS forecast_suggestions_material_request_id_fkx ON public.forecast_suggestions (material_request_id);
CREATE INDEX IF NOT EXISTS paynow_payment_intents_created_by_fkx ON public.paynow_payment_intents (created_by);
CREATE INDEX IF NOT EXISTS warehouse_bins_created_by_fkx ON public.warehouse_bins (created_by);
CREATE INDEX IF NOT EXISTS item_kits_created_by_fkx ON public.item_kits (created_by);
CREATE INDEX IF NOT EXISTS item_kit_components_uom_id_fkx ON public.item_kit_components (uom_id);
CREATE INDEX IF NOT EXISTS consignment_stock_levels_warehouse_id_fkx ON public.consignment_stock_levels (warehouse_id);
CREATE INDEX IF NOT EXISTS consignment_entries_supplier_id_fkx ON public.consignment_entries (supplier_id);
CREATE INDEX IF NOT EXISTS consignment_entries_customer_id_fkx ON public.consignment_entries (customer_id);
CREATE INDEX IF NOT EXISTS consignment_entries_warehouse_id_fkx ON public.consignment_entries (warehouse_id);
CREATE INDEX IF NOT EXISTS consignment_entries_journal_entry_id_fkx ON public.consignment_entries (journal_entry_id);
CREATE INDEX IF NOT EXISTS consignment_entries_reversal_journal_entry_id_fkx ON public.consignment_entries (reversal_journal_entry_id);
CREATE INDEX IF NOT EXISTS consignment_entries_created_by_fkx ON public.consignment_entries (created_by);
CREATE INDEX IF NOT EXISTS consignment_entry_lines_stock_item_id_fkx ON public.consignment_entry_lines (stock_item_id);
CREATE INDEX IF NOT EXISTS consignment_entry_lines_uom_id_fkx ON public.consignment_entry_lines (uom_id);
CREATE INDEX IF NOT EXISTS finance_requisitions_payment_entry_id_fkx ON public.finance_requisitions (payment_entry_id);
CREATE INDEX IF NOT EXISTS loyalty_program_settings_updated_by_fkx ON public.loyalty_program_settings (updated_by);
CREATE INDEX IF NOT EXISTS loyalty_ledger_sales_invoice_id_fkx ON public.loyalty_ledger (sales_invoice_id);
CREATE INDEX IF NOT EXISTS loyalty_ledger_journal_entry_id_fkx ON public.loyalty_ledger (journal_entry_id);
CREATE INDEX IF NOT EXISTS loyalty_ledger_reverses_ledger_id_fkx ON public.loyalty_ledger (reverses_ledger_id);
CREATE INDEX IF NOT EXISTS loyalty_ledger_created_by_fkx ON public.loyalty_ledger (created_by);
CREATE INDEX IF NOT EXISTS ai_report_subscriptions_created_by_fkx ON public.ai_report_subscriptions (created_by);
CREATE INDEX IF NOT EXISTS finance_requisitions_cancelled_by_fkx ON public.finance_requisitions (cancelled_by);
CREATE INDEX IF NOT EXISTS chat_threads_customer_id_fkx ON public.chat_threads (customer_id);
CREATE INDEX IF NOT EXISTS chat_threads_closed_by_fkx ON public.chat_threads (closed_by);
CREATE INDEX IF NOT EXISTS chat_messages_sender_user_id_fkx ON public.chat_messages (sender_user_id);
CREATE INDEX IF NOT EXISTS panic_events_delivery_job_id_fkx ON public.panic_events (delivery_job_id);
CREATE INDEX IF NOT EXISTS panic_events_acknowledged_by_fkx ON public.panic_events (acknowledged_by);
CREATE INDEX IF NOT EXISTS finance_requisitions_journal_entry_id_fkx ON public.finance_requisitions (journal_entry_id);
CREATE INDEX IF NOT EXISTS staff_ops_notifications_delivery_job_id_fkx ON public.staff_ops_notifications (delivery_job_id);
CREATE INDEX IF NOT EXISTS daily_exchange_rates_set_by_fkx ON public.daily_exchange_rates (set_by);
CREATE INDEX IF NOT EXISTS fleet_vehicles_created_by_fkx ON public.fleet_vehicles (created_by);
CREATE INDEX IF NOT EXISTS account_period_balances_opened_by_fkx ON public.account_period_balances (opened_by);
CREATE INDEX IF NOT EXISTS account_period_balances_closed_by_fkx ON public.account_period_balances (closed_by);
CREATE INDEX IF NOT EXISTS finance_requisitions_expense_account_code_fkx ON public.finance_requisitions (expense_account_code);
CREATE INDEX IF NOT EXISTS finance_requisitions_cash_account_code_fkx ON public.finance_requisitions (cash_account_code);
CREATE INDEX IF NOT EXISTS finance_requisitions_approved_by_fkx ON public.finance_requisitions (approved_by);
CREATE INDEX IF NOT EXISTS finance_requisitions_rejected_by_fkx ON public.finance_requisitions (rejected_by);
CREATE INDEX IF NOT EXISTS finance_requisitions_disbursed_by_fkx ON public.finance_requisitions (disbursed_by);
CREATE INDEX IF NOT EXISTS finance_requisition_lines_expense_account_code_fkx ON public.finance_requisition_lines (expense_account_code);
CREATE INDEX IF NOT EXISTS purchase_orders_approved_by_fkx ON public.purchase_orders (approved_by);
CREATE INDEX IF NOT EXISTS purchase_orders_rejected_by_fkx ON public.purchase_orders (rejected_by);
CREATE INDEX IF NOT EXISTS material_requests_approved_by_fkx ON public.material_requests (approved_by);
CREATE INDEX IF NOT EXISTS material_requests_rejected_by_fkx ON public.material_requests (rejected_by);
CREATE INDEX IF NOT EXISTS ecocash_payment_intents_whatsapp_flow_order_id_fkx ON public.ecocash_payment_intents (whatsapp_flow_order_id);
CREATE INDEX IF NOT EXISTS ecocash_payment_intents_created_by_fkx ON public.ecocash_payment_intents (created_by);
CREATE INDEX IF NOT EXISTS payment_tender_gl_accounts_account_code_fkx ON public.payment_tender_gl_accounts (account_code);
CREATE INDEX IF NOT EXISTS hr_roles_created_by_fkx ON public.hr_roles (created_by);
CREATE INDEX IF NOT EXISTS employees_grade_id_fkx ON public.employees (grade_id);
CREATE INDEX IF NOT EXISTS inventory_ai_directives_created_by_fkx ON public.inventory_ai_directives (created_by);
CREATE INDEX IF NOT EXISTS finance_requisition_approvals_approver_user_id_fkx ON public.finance_requisition_approvals (approver_user_id);
CREATE INDEX IF NOT EXISTS finance_audit_log_actor_user_id_fkx ON public.finance_audit_log (actor_user_id);
CREATE INDEX IF NOT EXISTS finance_refunds_reversing_journal_entry_id_fkx ON public.finance_refunds (reversing_journal_entry_id);
CREATE INDEX IF NOT EXISTS finance_refunds_posted_by_fkx ON public.finance_refunds (posted_by);
CREATE INDEX IF NOT EXISTS hr_onboarding_drafts_created_by_fkx ON public.hr_onboarding_drafts (created_by);
CREATE INDEX IF NOT EXISTS hr_onboarding_drafts_updated_by_fkx ON public.hr_onboarding_drafts (updated_by);
CREATE INDEX IF NOT EXISTS hr_leave_balances_leave_type_id_fkx ON public.hr_leave_balances (leave_type_id);
CREATE INDEX IF NOT EXISTS hr_leave_requests_leave_type_id_fkx ON public.hr_leave_requests (leave_type_id);
CREATE INDEX IF NOT EXISTS hr_leave_requests_decided_by_fkx ON public.hr_leave_requests (decided_by);
CREATE INDEX IF NOT EXISTS hr_credential_outbox_user_id_fkx ON public.hr_credential_outbox (user_id);
CREATE INDEX IF NOT EXISTS hr_credential_outbox_created_by_fkx ON public.hr_credential_outbox (created_by);
CREATE INDEX IF NOT EXISTS catalog_meili_sync_state_updated_by_fkx ON public.catalog_meili_sync_state (updated_by);
CREATE INDEX IF NOT EXISTS inventory_reservations_warehouse_id_fkx ON public.inventory_reservations (warehouse_id);
CREATE INDEX IF NOT EXISTS employees_signature_updated_by_fkx ON public.employees (signature_updated_by);
CREATE INDEX IF NOT EXISTS customer_return_request_lines_stock_item_id_fkx ON public.customer_return_request_lines (stock_item_id);
CREATE INDEX IF NOT EXISTS customer_return_request_lines_uom_id_fkx ON public.customer_return_request_lines (uom_id);
CREATE INDEX IF NOT EXISTS supplier_preferred_skus_stock_item_id_fkx ON public.supplier_preferred_skus (stock_item_id);
CREATE INDEX IF NOT EXISTS procurement_fund_releases_approved_by_fkx ON public.procurement_fund_releases (approved_by);
CREATE INDEX IF NOT EXISTS stock_item_shop_merch_updated_by_fkx ON public.stock_item_shop_merch (updated_by);
CREATE INDEX IF NOT EXISTS stock_item_images_created_by_fkx ON public.stock_item_images (created_by);
CREATE INDEX IF NOT EXISTS pos_split_payment_sessions_final_invoice_id_fkx ON public.pos_split_payment_sessions (final_invoice_id);
CREATE INDEX IF NOT EXISTS finance_requisition_receipts_created_by_fkx ON public.finance_requisition_receipts (created_by);
CREATE INDEX IF NOT EXISTS payroll_run_funding_funded_by_fkx ON public.payroll_run_funding (funded_by);
CREATE INDEX IF NOT EXISTS catalog_offline_device_grants_user_id_fkx ON public.catalog_offline_device_grants (user_id);
CREATE INDEX IF NOT EXISTS pos_split_payment_sessions_created_by_fkx ON public.pos_split_payment_sessions (created_by);
CREATE INDEX IF NOT EXISTS pos_split_payment_sessions_updated_by_fkx ON public.pos_split_payment_sessions (updated_by);
CREATE INDEX IF NOT EXISTS pos_split_payment_legs_allocation_journal_entry_id_fkx ON public.pos_split_payment_legs (allocation_journal_entry_id);
CREATE INDEX IF NOT EXISTS pos_split_payment_legs_created_by_fkx ON public.pos_split_payment_legs (created_by);
CREATE INDEX IF NOT EXISTS pos_split_refund_requests_approved_by_fkx ON public.pos_split_refund_requests (approved_by);
CREATE INDEX IF NOT EXISTS pos_split_refund_requests_settled_by_fkx ON public.pos_split_refund_requests (settled_by);
CREATE INDEX IF NOT EXISTS pos_split_refund_requests_created_by_fkx ON public.pos_split_refund_requests (created_by);
CREATE INDEX IF NOT EXISTS pos_split_acceptance_lines_cart_line_id_fkx ON public.pos_split_acceptance_lines (cart_line_id);
CREATE INDEX IF NOT EXISTS pos_split_acceptance_lines_accepted_by_fkx ON public.pos_split_acceptance_lines (accepted_by);
CREATE INDEX IF NOT EXISTS pos_split_payment_sessions_reduced_basket_accepted_by_fkx ON public.pos_split_payment_sessions (reduced_basket_accepted_by);
CREATE INDEX IF NOT EXISTS business_document_profile_updated_by_fkx ON public.business_document_profile (updated_by);
CREATE INDEX IF NOT EXISTS payment_resolution_letters_commerce_order_id_fkx ON public.payment_resolution_letters (commerce_order_id);
CREATE INDEX IF NOT EXISTS payment_resolution_letters_manager_user_id_fkx ON public.payment_resolution_letters (manager_user_id);
CREATE INDEX IF NOT EXISTS payment_resolution_letters_manager_employee_id_fkx ON public.payment_resolution_letters (manager_employee_id);
CREATE INDEX IF NOT EXISTS delivery_cash_collections_sales_invoice_id_fkx ON public.delivery_cash_collections (sales_invoice_id);
CREATE INDEX IF NOT EXISTS pos_operator_hidden_bestsellers_stock_item_id_fkx ON public.pos_operator_hidden_bestsellers (stock_item_id);
CREATE INDEX IF NOT EXISTS staff_approver_assignments_granted_by_fkx ON public.staff_approver_assignments (granted_by);
CREATE INDEX IF NOT EXISTS staff_approver_assignments_revoked_by_fkx ON public.staff_approver_assignments (revoked_by);
CREATE INDEX IF NOT EXISTS staff_approval_badges_user_id_fkx ON public.staff_approval_badges (user_id);
CREATE INDEX IF NOT EXISTS staff_approval_badges_issued_by_fkx ON public.staff_approval_badges (issued_by);
CREATE INDEX IF NOT EXISTS staff_approval_badges_revoked_by_fkx ON public.staff_approval_badges (revoked_by);
CREATE INDEX IF NOT EXISTS staff_badge_approvals_badge_id_fkx ON public.staff_badge_approvals (badge_id);
CREATE INDEX IF NOT EXISTS staff_badge_approvals_approver_employee_id_fkx ON public.staff_badge_approvals (approver_employee_id);
CREATE INDEX IF NOT EXISTS staff_badge_approvals_approver_user_id_fkx ON public.staff_badge_approvals (approver_user_id);
CREATE INDEX IF NOT EXISTS staff_approver_admin_events_actor_id_fkx ON public.staff_approver_admin_events (actor_id);
CREATE INDEX IF NOT EXISTS staff_approver_admin_events_employee_id_fkx ON public.staff_approver_admin_events (employee_id);
CREATE INDEX IF NOT EXISTS staff_approver_admin_events_user_id_fkx ON public.staff_approver_admin_events (user_id);
CREATE INDEX IF NOT EXISTS staff_approver_admin_events_badge_id_fkx ON public.staff_approver_admin_events (badge_id);
CREATE INDEX IF NOT EXISTS pos_action_audit_approved_by_employee_id_fkx ON public.pos_action_audit (approved_by_employee_id);
CREATE INDEX IF NOT EXISTS delivery_balance_approvals_customer_id_fkx ON public.delivery_balance_approvals (customer_id);
CREATE INDEX IF NOT EXISTS pos_fulfillment_deposits_till_session_id_fkx ON public.pos_fulfillment_deposits (till_session_id);
CREATE INDEX IF NOT EXISTS delivery_balance_approvals_sales_invoice_id_fkx ON public.delivery_balance_approvals (sales_invoice_id);
CREATE INDEX IF NOT EXISTS driver_cash_handins_received_by_fkx ON public.driver_cash_handins (received_by);
CREATE INDEX IF NOT EXISTS driver_cash_handins_approved_by_fkx ON public.driver_cash_handins (approved_by);
CREATE INDEX IF NOT EXISTS delivery_cash_collections_handin_id_fkx ON public.delivery_cash_collections (handin_id);
CREATE INDEX IF NOT EXISTS driver_cash_handins_journal_entry_id_fkx ON public.driver_cash_handins (journal_entry_id);
CREATE INDEX IF NOT EXISTS driver_cash_recoveries_journal_entry_id_fkx ON public.driver_cash_recoveries (journal_entry_id);
CREATE INDEX IF NOT EXISTS driver_cash_recoveries_recorded_by_fkx ON public.driver_cash_recoveries (recorded_by);
CREATE INDEX IF NOT EXISTS pos_fulfillment_deposits_journal_entry_id_fkx ON public.pos_fulfillment_deposits (journal_entry_id);
CREATE INDEX IF NOT EXISTS pos_fulfillment_deposits_store_credit_ledger_id_fkx ON public.pos_fulfillment_deposits (store_credit_ledger_id);
CREATE INDEX IF NOT EXISTS pos_fulfillment_deposits_refund_journal_entry_id_fkx ON public.pos_fulfillment_deposits (refund_journal_entry_id);
CREATE INDEX IF NOT EXISTS pos_fulfillment_deposits_refund_till_session_id_fkx ON public.pos_fulfillment_deposits (refund_till_session_id);
CREATE INDEX IF NOT EXISTS pos_fulfillment_deposits_refunded_by_fkx ON public.pos_fulfillment_deposits (refunded_by);
CREATE INDEX IF NOT EXISTS pos_fulfillment_deposits_created_by_fkx ON public.pos_fulfillment_deposits (created_by);
CREATE INDEX IF NOT EXISTS pos_card_terminal_attempts_credit_note_id_fkx ON public.pos_card_terminal_attempts (credit_note_id);
CREATE INDEX IF NOT EXISTS stock_reorder_points_warehouse_id_fkx ON public.stock_reorder_points (warehouse_id);
CREATE INDEX IF NOT EXISTS stock_reorder_points_updated_by_fkx ON public.stock_reorder_points (updated_by);
CREATE INDEX IF NOT EXISTS lost_demand_warehouse_id_fkx ON public.lost_demand (warehouse_id);
CREATE INDEX IF NOT EXISTS lost_demand_created_by_fkx ON public.lost_demand (created_by);
CREATE INDEX IF NOT EXISTS price_changes_price_list_item_id_fkx ON public.price_changes (price_list_item_id);
CREATE INDEX IF NOT EXISTS price_changes_changed_by_fkx ON public.price_changes (changed_by);
