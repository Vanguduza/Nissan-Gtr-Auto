-- Temporary verifier grant check (Batch 3)
SELECT routine_name, grantee, privilege_type
FROM information_schema.role_routine_grants
WHERE routine_schema = 'public'
  AND routine_name IN ('search_catalog', 'check_whatsapp_bot_rate_limit')
  AND privilege_type = 'EXECUTE'
ORDER BY 1, 2;

SELECT
  has_table_privilege('anon', 'public.whatsapp_bot_rate_limits', 'SELECT') AS anon_sel,
  has_table_privilege('authenticated', 'public.whatsapp_bot_rate_limits', 'SELECT') AS auth_sel;

SELECT relrowsecurity
FROM pg_class
WHERE relname = 'whatsapp_bot_rate_limits';
