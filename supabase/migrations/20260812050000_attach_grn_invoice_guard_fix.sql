-- Fix attach_goods_receipt_invoice: respect procurement mutation guard (begin/end RPC).

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
  PERFORM public._procurement_begin_rpc();

  IF NOT public.has_staff_role(ARRAY['admin', 'warehouse', 'finance']::public.staff_role[]) THEN
    RAISE EXCEPTION 'not authorized';
  END IF;

  IF p_storage_path IS NULL OR length(trim(p_storage_path)) = 0 THEN
    RAISE EXCEPTION 'storage path required';
  END IF;

  UPDATE public.goods_receipts
  SET
    supplier_invoice_path = trim(p_storage_path),
    supplier_invoice_uploaded_at = now()
  WHERE id = p_goods_receipt_id;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'goods receipt not found';
  END IF;

  PERFORM public._procurement_end_rpc();
  RETURN p_goods_receipt_id;
EXCEPTION
  WHEN OTHERS THEN
    PERFORM public._procurement_end_rpc();
    RAISE;
END;
$$;

REVOKE ALL ON FUNCTION public.attach_goods_receipt_invoice(UUID, TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.attach_goods_receipt_invoice(UUID, TEXT) TO authenticated, service_role;
