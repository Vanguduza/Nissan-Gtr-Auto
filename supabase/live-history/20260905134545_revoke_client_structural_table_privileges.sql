-- Exported from the hosted project's supabase_migrations.schema_migrations (20260905134545 revoke_client_structural_table_privileges).
-- Source of record for what production ran; see supabase/live-history/README.md.

-- P0 least privilege: browser/mobile roles never need structural table privileges.
-- RLS does not govern TRUNCATE, so these grants are especially unsafe.
REVOKE TRUNCATE, REFERENCES, TRIGGER ON ALL TABLES IN SCHEMA public FROM anon, authenticated;

-- Prevent future tables from inheriting the same structural privileges from postgres-owned defaults.
ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public
  REVOKE TRUNCATE, REFERENCES, TRIGGER ON TABLES FROM anon, authenticated;
