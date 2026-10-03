-- Exported from the hosted project's supabase_migrations.schema_migrations (20260907155351 enable_pg_net_for_worker_dispatch).
-- Source of record for what production ran; see supabase/live-history/README.md.

create extension if not exists pg_net with schema extensions;
comment on extension pg_net is 'Async HTTP transport for scheduled Supabase Edge worker dispatch and bounded platform recovery operations.';
