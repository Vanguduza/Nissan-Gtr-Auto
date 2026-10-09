-- Exported from the hosted project's supabase_migrations.schema_migrations (20260908127000 pos_p0_p1_performance_hardening).
-- Source of record for what production ran; see supabase/live-history/README.md.

-- POS P0/P1 performance hardening based on post-deploy Supabase advisors.
-- Cover only foreign keys introduced/altered by the POS operating-system work.

CREATE INDEX IF NOT EXISTS pos_action_audit_approved_by_idx
  ON public.pos_action_audit (approved_by_user_id);

CREATE INDEX IF NOT EXISTS pos_approval_policies_updated_by_idx
  ON public.pos_approval_policies (updated_by);

CREATE INDEX IF NOT EXISTS pos_carts_till_session_idx
  ON public.pos_carts (till_session_id);

CREATE INDEX IF NOT EXISTS pos_tender_settlements_order_idx
  ON public.pos_commerce_tender_settlements (commerce_order_id);

CREATE INDEX IF NOT EXISTS pos_tender_settlements_invoice_idx
  ON public.pos_commerce_tender_settlements (invoice_id);

CREATE INDEX IF NOT EXISTS pos_tender_settlements_created_by_idx
  ON public.pos_commerce_tender_settlements (created_by);

CREATE INDEX IF NOT EXISTS pos_core_returns_invoice_idx
  ON public.pos_core_returns (source_invoice_id);

CREATE INDEX IF NOT EXISTS pos_core_returns_till_idx
  ON public.pos_core_returns (till_session_id);

CREATE INDEX IF NOT EXISTS pos_core_returns_credit_note_idx
  ON public.pos_core_returns (credit_note_id);

CREATE INDEX IF NOT EXISTS pos_core_returns_quarantine_idx
  ON public.pos_core_returns (quarantine_stock_entry_id);

CREATE INDEX IF NOT EXISTS pos_core_returns_journal_idx
  ON public.pos_core_returns (journal_entry_id);

CREATE INDEX IF NOT EXISTS pos_core_returns_store_credit_idx
  ON public.pos_core_returns (store_credit_ledger_id);

CREATE INDEX IF NOT EXISTS pos_core_returns_posted_by_idx
  ON public.pos_core_returns (posted_by);

CREATE INDEX IF NOT EXISTS pos_fulfillment_uom_idx
  ON public.pos_fulfillment_requests (uom_id);

CREATE INDEX IF NOT EXISTS pos_fulfillment_source_wh_idx
  ON public.pos_fulfillment_requests (source_warehouse_id);

CREATE INDEX IF NOT EXISTS pos_fulfillment_destination_wh_idx
  ON public.pos_fulfillment_requests (destination_warehouse_id);

CREATE INDEX IF NOT EXISTS pos_fulfillment_cart_idx
  ON public.pos_fulfillment_requests (cart_id);

CREATE INDEX IF NOT EXISTS pos_fulfillment_invoice_idx
  ON public.pos_fulfillment_requests (invoice_id);

CREATE INDEX IF NOT EXISTS pos_fulfillment_stock_entry_idx
  ON public.pos_fulfillment_requests (stock_entry_id);

CREATE INDEX IF NOT EXISTS pos_fulfillment_requested_by_idx
  ON public.pos_fulfillment_requests (requested_by);

CREATE INDEX IF NOT EXISTS pos_fulfillment_approved_by_idx
  ON public.pos_fulfillment_requests (approved_by);

CREATE INDEX IF NOT EXISTS pos_return_lines_stock_item_idx
  ON public.pos_return_case_lines (stock_item_id);

CREATE INDEX IF NOT EXISTS pos_return_lines_uom_idx
  ON public.pos_return_case_lines (uom_id);

CREATE INDEX IF NOT EXISTS pos_return_cases_till_idx
  ON public.pos_return_cases (till_session_id);

CREATE INDEX IF NOT EXISTS pos_return_cases_credit_note_idx
  ON public.pos_return_cases (credit_note_id);

CREATE INDEX IF NOT EXISTS pos_return_cases_warranty_idx
  ON public.pos_return_cases (warranty_claim_id);

CREATE INDEX IF NOT EXISTS pos_return_cases_replacement_idx
  ON public.pos_return_cases (replacement_stock_entry_id);

CREATE INDEX IF NOT EXISTS pos_return_cases_adjustment_journal_idx
  ON public.pos_return_cases (adjustment_journal_entry_id);

CREATE INDEX IF NOT EXISTS pos_return_cases_store_credit_idx
  ON public.pos_return_cases (store_credit_ledger_id);

CREATE INDEX IF NOT EXISTS pos_return_cases_created_by_idx
  ON public.pos_return_cases (created_by);

CREATE INDEX IF NOT EXISTS pos_return_cases_approved_by_idx
  ON public.pos_return_cases (approved_by);

CREATE INDEX IF NOT EXISTS pos_till_movements_actor_idx
  ON public.pos_till_cash_movements (actor_user_id);

CREATE INDEX IF NOT EXISTS pos_till_movements_refund_idx
  ON public.pos_till_cash_movements (finance_refund_id);

CREATE INDEX IF NOT EXISTS pos_till_counts_counted_by_idx
  ON public.pos_till_count_lines (counted_by);

CREATE INDEX IF NOT EXISTS pos_till_events_session_idx
  ON public.pos_till_session_events (session_id);

CREATE INDEX IF NOT EXISTS pos_till_events_actor_idx
  ON public.pos_till_session_events (actor_user_id);

CREATE INDEX IF NOT EXISTS pos_till_sessions_warehouse_idx
  ON public.pos_till_sessions (warehouse_id);

CREATE INDEX IF NOT EXISTS pos_till_sessions_opened_by_idx
  ON public.pos_till_sessions (opened_by);

CREATE INDEX IF NOT EXISTS pos_till_sessions_approved_by_idx
  ON public.pos_till_sessions (approved_by);

-- Avoid re-evaluating auth.uid() for every row in till visibility policies.
DROP POLICY IF EXISTS pos_till_sessions_select ON public.pos_till_sessions;

CREATE POLICY pos_till_sessions_select ON public.pos_till_sessions
  FOR SELECT TO authenticated
  USING (operator_user_id = (SELECT auth.uid()) OR public.is_pos_approver()
    OR public.has_staff_role(ARRAY['finance']::public.staff_role[]));

DROP POLICY IF EXISTS pos_till_movements_select ON public.pos_till_cash_movements;

CREATE POLICY pos_till_movements_select ON public.pos_till_cash_movements
  FOR SELECT TO authenticated
  USING (EXISTS (
    SELECT 1 FROM public.pos_till_sessions s
    WHERE s.id = session_id
      AND (s.operator_user_id = (SELECT auth.uid()) OR public.is_pos_approver()
        OR public.has_staff_role(ARRAY['finance']::public.staff_role[]))
  ));

DROP POLICY IF EXISTS pos_till_events_select ON public.pos_till_session_events;

CREATE POLICY pos_till_events_select ON public.pos_till_session_events
  FOR SELECT TO authenticated
  USING (EXISTS (
    SELECT 1 FROM public.pos_till_sessions s
    WHERE s.id = session_id
      AND (s.operator_user_id = (SELECT auth.uid()) OR public.is_pos_approver()
        OR public.has_staff_role(ARRAY['finance']::public.staff_role[]))
  ));

DROP POLICY IF EXISTS pos_till_count_lines_select ON public.pos_till_count_lines;

CREATE POLICY pos_till_count_lines_select ON public.pos_till_count_lines
  FOR SELECT TO authenticated
  USING (EXISTS (
    SELECT 1 FROM public.pos_till_sessions s
    WHERE s.id = session_id
      AND (s.operator_user_id = (SELECT auth.uid()) OR public.is_pos_approver()
        OR public.has_staff_role(ARRAY['finance']::public.staff_role[]))
  ));
