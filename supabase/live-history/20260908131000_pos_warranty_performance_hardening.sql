-- Exported from the hosted project's supabase_migrations.schema_migrations (20260908131000 pos_warranty_performance_hardening).
-- Source of record for what production ran; see supabase/live-history/README.md.

-- POS P0/P1 follow-up: optimize the governed audit policy and index warranty paths now used by the POS desk.
ALTER POLICY pos_action_audit_insert ON public.pos_action_audit
  WITH CHECK (
    public.has_staff_role(ARRAY['admin', 'finance', 'sales']::public.staff_role[])
    AND actor_user_id = (SELECT auth.uid())
  );

CREATE INDEX IF NOT EXISTS warranty_claims_stock_batch_idx
  ON public.warranty_claims (stock_batch_id);

CREATE INDEX IF NOT EXISTS warranty_claims_stock_item_idx
  ON public.warranty_claims (stock_item_id);

CREATE INDEX IF NOT EXISTS warranty_claims_customer_idx
  ON public.warranty_claims (customer_id);

CREATE INDEX IF NOT EXISTS warranty_claims_credit_note_idx
  ON public.warranty_claims (credit_note_id);

CREATE INDEX IF NOT EXISTS warranty_claims_replacement_entry_idx
  ON public.warranty_claims (replacement_stock_entry_id);

CREATE INDEX IF NOT EXISTS warranty_claims_quarantine_entry_idx
  ON public.warranty_claims (quarantine_stock_entry_id);

CREATE INDEX IF NOT EXISTS warranty_claims_created_by_idx
  ON public.warranty_claims (created_by);

CREATE INDEX IF NOT EXISTS warranty_claims_decided_by_idx
  ON public.warranty_claims (decided_by);
