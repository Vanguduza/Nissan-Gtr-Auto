-- verifier apply: record migration versions after SQL files succeed
INSERT INTO supabase_migrations.schema_migrations (version)
VALUES ('20260724140000'), ('20260724150000')
ON CONFLICT DO NOTHING;
