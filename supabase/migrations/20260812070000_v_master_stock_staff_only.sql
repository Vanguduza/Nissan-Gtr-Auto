-- BLOCKING: v_master_stock must not be SELECT-able by authenticated.
-- Staff clients use list_master_stock (SECURITY DEFINER + has_staff_role).
-- service_role retains SELECT for admin/ops tooling.
--
-- Also clears deferred WARNING from 20260812060000: narrow procurement-invoices
-- storage DELETE to admin-only (SELECT/INSERT/UPDATE stay staff).

REVOKE SELECT ON public.v_master_stock FROM authenticated;
GRANT SELECT ON public.v_master_stock TO service_role;

COMMENT ON VIEW public.v_master_stock IS
  'Master stock totals + WH1/WH2. No SELECT for authenticated — use list_master_stock.';

-- ---------------------------------------------------------------------------
-- procurement-invoices: split FOR ALL → staff R/W + admin DELETE
-- ---------------------------------------------------------------------------
DROP POLICY IF EXISTS procurement_invoices_staff_rw ON storage.objects;

DROP POLICY IF EXISTS procurement_invoices_staff_select ON storage.objects;
CREATE POLICY procurement_invoices_staff_select
  ON storage.objects FOR SELECT
  TO authenticated
  USING (
    bucket_id = 'procurement-invoices'
    AND public.has_staff_role(ARRAY['admin', 'warehouse', 'finance']::public.staff_role[])
  );

DROP POLICY IF EXISTS procurement_invoices_staff_insert ON storage.objects;
CREATE POLICY procurement_invoices_staff_insert
  ON storage.objects FOR INSERT
  TO authenticated
  WITH CHECK (
    bucket_id = 'procurement-invoices'
    AND public.has_staff_role(ARRAY['admin', 'warehouse', 'finance']::public.staff_role[])
  );

DROP POLICY IF EXISTS procurement_invoices_staff_update ON storage.objects;
CREATE POLICY procurement_invoices_staff_update
  ON storage.objects FOR UPDATE
  TO authenticated
  USING (
    bucket_id = 'procurement-invoices'
    AND public.has_staff_role(ARRAY['admin', 'warehouse', 'finance']::public.staff_role[])
  )
  WITH CHECK (
    bucket_id = 'procurement-invoices'
    AND public.has_staff_role(ARRAY['admin', 'warehouse', 'finance']::public.staff_role[])
  );

DROP POLICY IF EXISTS procurement_invoices_admin_delete ON storage.objects;
CREATE POLICY procurement_invoices_admin_delete
  ON storage.objects FOR DELETE
  TO authenticated
  USING (
    bucket_id = 'procurement-invoices'
    AND public.has_staff_role(ARRAY['admin']::public.staff_role[])
  );
