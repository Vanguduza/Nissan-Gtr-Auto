-- Harden attach_goods_receipt_invoice (SECURITY DEFINER):
-- 1) Draft GRNs only — refuse after submit/posted.
-- 2) Require real storage.objects row in procurement-invoices.
-- 3) Bind path prefix to GRN purchase_order_id (matches web upload: {poId}/{file}).
-- Keeps _procurement_begin_rpc / _procurement_end_rpc from 20260812050000.
--
-- WARNING deferred: storage.objects policy procurement_invoices_staff_rw remains FOR ALL
-- (includes DELETE). Narrow DELETE to admin-only in a follow-up if product needs retention.
-- Fund-release ON CONFLICT UPDATE left as-is (20260812030000); insert-once is a separate change.

CREATE OR REPLACE FUNCTION public.attach_goods_receipt_invoice(
  p_goods_receipt_id UUID,
  p_storage_path TEXT
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_grn public.goods_receipts%ROWTYPE;
  v_path TEXT;
  v_prefix TEXT;
BEGIN
  PERFORM public._procurement_begin_rpc();

  IF NOT public.has_staff_role(ARRAY['admin', 'warehouse', 'finance']::public.staff_role[]) THEN
    RAISE EXCEPTION 'not authorized';
  END IF;

  IF p_storage_path IS NULL OR length(trim(p_storage_path)) = 0 THEN
    RAISE EXCEPTION 'storage path required';
  END IF;

  v_path := trim(both FROM p_storage_path);
  v_path := trim(both '/' FROM v_path);
  IF v_path ~ '\.\.' OR v_path ~ '//' OR position('\' IN v_path) > 0 THEN
    RAISE EXCEPTION 'invalid invoice storage path';
  END IF;
  IF left(v_path, length('procurement-invoices/')) = 'procurement-invoices/' THEN
    v_path := substr(v_path, length('procurement-invoices/') + 1);
    v_path := trim(both '/' FROM v_path);
  END IF;
  IF v_path IS NULL OR length(v_path) = 0 OR position('/' IN v_path) = 0 THEN
    RAISE EXCEPTION 'invoice path must be {purchase_order_id}/{filename}';
  END IF;

  SELECT * INTO v_grn
  FROM public.goods_receipts
  WHERE id = p_goods_receipt_id
  FOR UPDATE;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'goods receipt not found';
  END IF;

  IF v_grn.status IS DISTINCT FROM 'draft' THEN
    RAISE EXCEPTION 'invoice attach only allowed on draft goods receipts (status=%)', v_grn.status;
  END IF;

  v_prefix := v_grn.purchase_order_id::text || '/';
  IF left(v_path, length(v_prefix)) IS DISTINCT FROM v_prefix THEN
    RAISE EXCEPTION
      'invoice path must start with % (got %)',
      v_prefix,
      v_path;
  END IF;

  IF length(substr(v_path, length(v_prefix) + 1)) = 0 THEN
    RAISE EXCEPTION 'invoice path must include a filename under the PO folder';
  END IF;

  IF NOT EXISTS (
    SELECT 1
    FROM storage.objects o
    WHERE o.bucket_id = 'procurement-invoices'
      AND o.name = v_path
  ) THEN
    RAISE EXCEPTION
      'invoice object missing in procurement-invoices bucket at %',
      v_path;
  END IF;

  UPDATE public.goods_receipts
  SET
    supplier_invoice_path = v_path,
    supplier_invoice_uploaded_at = now(),
    updated_at = now()
  WHERE id = p_goods_receipt_id
    AND status = 'draft';

  IF NOT FOUND THEN
    RAISE EXCEPTION 'goods receipt not found or no longer draft';
  END IF;

  PERFORM public._procurement_end_rpc();
  RETURN p_goods_receipt_id;
EXCEPTION
  WHEN OTHERS THEN
    PERFORM public._procurement_end_rpc();
    RAISE;
END;
$$;

COMMENT ON FUNCTION public.attach_goods_receipt_invoice(UUID, TEXT) IS
  'Staff attach supplier invoice path to draft GRN only; path must exist under '
  'procurement-invoices as {purchase_order_id}/{filename} (web upload compatible).';

REVOKE ALL ON FUNCTION public.attach_goods_receipt_invoice(UUID, TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.attach_goods_receipt_invoice(UUID, TEXT) TO authenticated, service_role;
