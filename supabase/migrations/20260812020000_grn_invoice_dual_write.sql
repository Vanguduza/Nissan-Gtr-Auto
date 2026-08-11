-- GRN supplier invoice attachment + storage; OEM receive helper; dual-write money columns (nullable)

ALTER TABLE public.goods_receipts
  ADD COLUMN IF NOT EXISTS supplier_invoice_path TEXT,
  ADD COLUMN IF NOT EXISTS supplier_invoice_uploaded_at TIMESTAMPTZ;

COMMENT ON COLUMN public.goods_receipts.supplier_invoice_path IS
  'Storage path in procurement-invoices bucket; invoice doubles as GRN evidence.';

INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
  'procurement-invoices',
  'procurement-invoices',
  false,
  15728640,
  ARRAY['application/pdf', 'image/jpeg', 'image/png', 'image/webp']::text[]
)
ON CONFLICT (id) DO NOTHING;

DROP POLICY IF EXISTS procurement_invoices_staff_rw ON storage.objects;
CREATE POLICY procurement_invoices_staff_rw
  ON storage.objects FOR ALL TO authenticated
  USING (
    bucket_id = 'procurement-invoices'
    AND public.has_staff_role(ARRAY['admin', 'warehouse', 'finance']::public.staff_role[])
  )
  WITH CHECK (
    bucket_id = 'procurement-invoices'
    AND public.has_staff_role(ARRAY['admin', 'warehouse', 'finance']::public.staff_role[])
  );

CREATE OR REPLACE FUNCTION public.attach_goods_receipt_invoice(
  p_goods_receipt_id UUID,
  p_storage_path TEXT
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  IF NOT public.has_staff_role(ARRAY['admin', 'warehouse', 'finance']::public.staff_role[]) THEN
    RAISE EXCEPTION 'not authorized';
  END IF;
  UPDATE public.goods_receipts
  SET
    supplier_invoice_path = p_storage_path,
    supplier_invoice_uploaded_at = now()
  WHERE id = p_goods_receipt_id;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'goods receipt not found';
  END IF;
  RETURN p_goods_receipt_id;
END;
$$;

REVOKE ALL ON FUNCTION public.attach_goods_receipt_invoice(UUID, TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.attach_goods_receipt_invoice(UUID, TEXT) TO authenticated, service_role;

-- Dual-write amount_minor columns (nullable) for gradual cutover (E5)
ALTER TABLE public.purchase_order_lines
  ADD COLUMN IF NOT EXISTS unit_price_minor BIGINT;
ALTER TABLE public.procurement_fund_releases
  ADD COLUMN IF NOT EXISTS amount_minor BIGINT;

COMMENT ON COLUMN public.purchase_order_lines.unit_price_minor IS
  'Dual-write minor units; prefer with unit_price until cutover.';
COMMENT ON COLUMN public.procurement_fund_releases.amount_minor IS
  'Dual-write minor units alongside amount.';

-- Keep progress_step in sync on submit
CREATE OR REPLACE FUNCTION public._po_set_progress_on_submit()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  IF NEW.status = 'submitted' AND (OLD.status IS DISTINCT FROM 'submitted') THEN
    NEW.progress_step := 'submitted';
  END IF;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS purchase_orders_progress_submit ON public.purchase_orders;
CREATE TRIGGER purchase_orders_progress_submit
  BEFORE UPDATE OF status ON public.purchase_orders
  FOR EACH ROW
  EXECUTE FUNCTION public._po_set_progress_on_submit();
