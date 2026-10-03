-- Exported from the hosted project's supabase_migrations.schema_migrations (20260908134843 pos_card_terminal_ecr).
-- Source of record for what production ran; see supabase/live-history/README.md.

-- Physical ECR/card-terminal tender. Kept separate because PostgreSQL enum values
-- added with ALTER TYPE cannot be safely consumed later in the same migration txn.
ALTER TYPE public.payment_tender ADD VALUE IF NOT EXISTS 'card_terminal';
