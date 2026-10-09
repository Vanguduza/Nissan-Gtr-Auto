-- Exported from the hosted project's supabase_migrations.schema_migrations (20260907060437 restore_petty_float_requisition_enum).
-- Source of record for what production ran; see supabase/live-history/README.md.

DO $$ BEGIN ALTER TYPE public.finance_requisition_type ADD VALUE IF NOT EXISTS 'petty_float'; EXCEPTION WHEN duplicate_object THEN NULL; END $$;
