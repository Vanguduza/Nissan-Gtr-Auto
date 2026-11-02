-- Customer storefront: expose active delivery job id on get_customer_order.
-- Authenticated owner path uses job id with get_delivery_track_point(p_delivery_job_id).
-- Track share tokens stay deep-link only (staff mint; plaintext not recoverable).
-- Does not grant customers SELECT on delivery_locations (trail stays staff/RPC).

CREATE OR REPLACE FUNCTION public.get_customer_order(p_invoice_id UUID)
RETURNS JSONB
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_inv public.sales_invoices%ROWTYPE;
  v_dn_status public.delivery_note_status;
  v_pick_status public.pick_list_status;
  v_active_job_id UUID;
BEGIN
  v_inv := public._assert_customer_owns_invoice(p_invoice_id);

  SELECT dn.status INTO v_dn_status
  FROM public.delivery_notes dn
  WHERE dn.sales_invoice_id = v_inv.id
  ORDER BY dn.created_at DESC
  LIMIT 1;

  SELECT pl.status INTO v_pick_status
  FROM public.pick_lists pl
  WHERE pl.sales_invoice_id = v_inv.id
  ORDER BY pl.created_at DESC
  LIMIT 1;

  -- Non-terminal only (pending|dispatched). Prefer dispatched for track screens.
  SELECT dj.id INTO v_active_job_id
  FROM public.delivery_jobs dj
  JOIN public.delivery_notes dn ON dn.id = dj.delivery_note_id
  WHERE dn.sales_invoice_id = v_inv.id
    AND dj.status IN ('pending', 'dispatched')
  ORDER BY
    CASE dj.status WHEN 'dispatched' THEN 0 ELSE 1 END,
    dj.updated_at DESC,
    dj.created_at DESC
  LIMIT 1;

  RETURN jsonb_build_object(
    'invoice_id', v_inv.id,
    'document_number', v_inv.document_number,
    'doc_type', v_inv.doc_type,
    'status', v_inv.status,
    'fulfillment_mode', v_inv.fulfillment_mode,
    'currency', v_inv.currency,
    'exchange_rate_applied', v_inv.exchange_rate_applied,
    'subtotal', v_inv.subtotal,
    'total', v_inv.total,
    'amount_paid', v_inv.amount_paid,
    'amount_open', v_inv.total - v_inv.amount_paid,
    'cart_id', v_inv.cart_id,
    'posted_at', v_inv.posted_at,
    'pick_list_status', v_pick_status,
    'delivery_note_status', v_dn_status,
    'active_delivery_job_id', v_active_job_id
  );
END;
$$;

COMMENT ON FUNCTION public.get_customer_order(UUID) IS
  'Storefront order detail for owning customer. Includes active_delivery_job_id '
  '(non-terminal pending|dispatched job on a DN for this invoice; prefers dispatched). '
  'No GPS trail or track token. Track via get_delivery_track_point(job_id) as owner.';

REVOKE ALL ON FUNCTION public.get_customer_order(UUID) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.get_customer_order(UUID) TO authenticated, service_role;
