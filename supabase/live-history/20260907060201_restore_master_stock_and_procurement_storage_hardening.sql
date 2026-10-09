-- Exported from the hosted project's supabase_migrations.schema_migrations (20260907060201 restore_master_stock_and_procurement_storage_hardening).
-- Source of record for what production ran; see supabase/live-history/README.md.

REVOKE SELECT ON public.v_master_stock FROM authenticated;
GRANT SELECT ON public.v_master_stock TO service_role;
COMMENT ON VIEW public.v_master_stock IS 'Master stock totals + WH1/WH2. No SELECT for authenticated — use list_master_stock.';
DROP POLICY IF EXISTS procurement_invoices_staff_rw ON storage.objects;
DROP POLICY IF EXISTS procurement_invoices_staff_select ON storage.objects;
CREATE POLICY procurement_invoices_staff_select ON storage.objects FOR SELECT TO authenticated
USING (bucket_id='procurement-invoices' AND public.has_staff_role(ARRAY['admin','warehouse','finance']::public.staff_role[]));
DROP POLICY IF EXISTS procurement_invoices_staff_insert ON storage.objects;
CREATE POLICY procurement_invoices_staff_insert ON storage.objects FOR INSERT TO authenticated
WITH CHECK (bucket_id='procurement-invoices' AND public.has_staff_role(ARRAY['admin','warehouse','finance']::public.staff_role[]));
DROP POLICY IF EXISTS procurement_invoices_staff_update ON storage.objects;
CREATE POLICY procurement_invoices_staff_update ON storage.objects FOR UPDATE TO authenticated
USING (bucket_id='procurement-invoices' AND public.has_staff_role(ARRAY['admin','warehouse','finance']::public.staff_role[]))
WITH CHECK (bucket_id='procurement-invoices' AND public.has_staff_role(ARRAY['admin','warehouse','finance']::public.staff_role[]));
DROP POLICY IF EXISTS procurement_invoices_admin_delete ON storage.objects;
CREATE POLICY procurement_invoices_admin_delete ON storage.objects FOR DELETE TO authenticated
USING (bucket_id='procurement-invoices' AND public.has_staff_role(ARRAY['admin']::public.staff_role[]));
