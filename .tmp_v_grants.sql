-- EXECUTE grants on Batch 2 claim/complete/list/stub RPCs
SELECT p.proname,
       COALESCE(r.rolname, 'PUBLIC') AS grantee
FROM pg_proc p
JOIN pg_namespace n ON n.oid = p.pronamespace
LEFT JOIN LATERAL aclexplode(COALESCE(p.proacl, acldefault('f', p.proowner))) e ON true
LEFT JOIN pg_roles r ON r.oid = e.grantee
WHERE n.nspname = 'public'
  AND p.proname IN (
    'claim_receipt_outbox_batch',
    'claim_sms_outbox_batch',
    'complete_receipt_outbox',
    'complete_sms_outbox',
    'list_receipt_documents_needing_pdf',
    'process_receipt_outbox_batch',
    'drain_sms_outbox_batch'
  )
  AND e.privilege_type = 'EXECUTE'
ORDER BY 1, 2;

-- storage policies for customer-receipts
SELECT polname, cmd::text
FROM pg_policies
WHERE schemaname = 'storage' AND tablename = 'objects' AND policyname LIKE 'customer_receipts%';
