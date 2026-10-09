-- Exported from the hosted project's supabase_migrations.schema_migrations (20260908061516 catalog_r2_release_inventory_work_table).
-- Source of record for what production ran; see supabase/live-history/README.md.

CREATE UNLOGGED TABLE IF NOT EXISTS public.catalog_r2_release_inventory_work(request_id bigint PRIMARY KEY, kind text NOT NULL, prefix text NOT NULL, created_at timestamptz NOT NULL DEFAULT now()); REVOKE ALL ON TABLE public.catalog_r2_release_inventory_work FROM PUBLIC; GRANT SELECT,INSERT,DELETE ON TABLE public.catalog_r2_release_inventory_work TO service_role;
