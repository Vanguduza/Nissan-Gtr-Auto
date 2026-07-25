SELECT version FROM supabase_migrations.schema_migrations
WHERE version LIKE '2026072414%' OR version LIKE '2026072415%'
ORDER BY 1;

SELECT c.relname, c.relrowsecurity
FROM pg_class c
JOIN pg_namespace n ON n.oid = c.relnamespace
WHERE n.nspname = 'public'
  AND c.relname IN ('customer_receipt_outbox','sms_outbox','receipt_pdf_artifacts');

SELECT column_name FROM information_schema.columns
WHERE table_schema='public' AND table_name='customer_receipt_outbox' AND column_name='claimed_at';

SELECT id, public FROM storage.buckets WHERE id='customer-receipts';
