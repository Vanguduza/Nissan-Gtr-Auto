-- Exported from the hosted project's supabase_migrations.schema_migrations (20260908142103 pos_card_terminal_performance_hardening).
-- Source of record for what production ran; see supabase/live-history/README.md.

create index if not exists pos_card_terminals_warehouse_idx on public.pos_card_terminals(warehouse_id);
create index if not exists pos_card_terminals_created_by_idx on public.pos_card_terminals(created_by);
create index if not exists pos_card_terminals_updated_by_idx on public.pos_card_terminals(updated_by);
create index if not exists pos_card_terminal_attempts_created_by_idx on public.pos_card_terminal_attempts(created_by);
create index if not exists pos_card_terminal_attempts_result_recorded_by_idx on public.pos_card_terminal_attempts(result_recorded_by);
create index if not exists pos_card_terminal_attempts_finalized_by_idx on public.pos_card_terminal_attempts(finalized_by);
create index if not exists pos_card_terminal_attempts_parent_attempt_idx on public.pos_card_terminal_attempts(parent_attempt_id);
create index if not exists pos_card_terminal_attempts_payment_entry_idx on public.pos_card_terminal_attempts(payment_entry_id);
create index if not exists pos_card_terminal_attempts_invoice_idx2 on public.pos_card_terminal_attempts(invoice_id);
create index if not exists pos_card_terminal_attempts_finance_refund_idx on public.pos_card_terminal_attempts(finance_refund_id);
create index if not exists pos_card_terminal_device_keys_created_by_idx on public.pos_card_terminal_device_keys(created_by);
