-- Driver-scoped delivery-note / invoice line items for job detail receipt copy.
-- Drivers cannot SELECT sales_invoice_lines / stock_items under staff RLS for
-- unpaid invoices in all cases; expose qty + OEM/description + line amounts
-- for the assignee (or admin) via SECURITY DEFINER — same authz as settlement.
-- Amounts come from sales_invoice_lines (sell price), never DN unit_cost (COGS).

CREATE OR REPLACE FUNCTION public.get_delivery_job_lines(p_delivery_job_id UUID)
RETURNS TABLE (
  delivery_job_id UUID,
  line_id UUID,
  qty NUMERIC,
  oem_part_number TEXT,
  description TEXT,
  currency public.currency_code,
  unit_price NUMERIC,
  line_total NUMERIC,
  unit_price_minor BIGINT,
  line_total_minor BIGINT,
  is_core_charge BOOLEAN
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_job public.delivery_jobs%ROWTYPE;
BEGIN
  IF p_delivery_job_id IS NULL THEN
    RAISE EXCEPTION 'delivery_job_id required';
  END IF;

  SELECT * INTO v_job
  FROM public.delivery_jobs
  WHERE id = p_delivery_job_id;

  IF NOT FOUND THEN
    RETURN;
  END IF;

  IF auth.role() <> 'service_role'
     AND NOT public.has_staff_role(ARRAY['admin']::public.staff_role[])
     AND v_job.assignee_user_id IS DISTINCT FROM auth.uid() THEN
    RAISE EXCEPTION 'not authorized for delivery job lines';
  END IF;

  RETURN QUERY
  SELECT
    v_job.id,
    dnl.id,
    dnl.qty,
    si.oem_part_number,
    si.description,
    COALESCE(inv.currency, dnl.currency),
    sil.unit_price,
    sil.line_total,
    COALESCE(sil.unit_price_minor, public._major_to_minor(sil.unit_price)),
    COALESCE(sil.line_total_minor, public._major_to_minor(sil.line_total)),
    COALESCE(sil.is_core_charge, false)
  FROM public.delivery_note_lines dnl
  JOIN public.sales_invoice_lines sil ON sil.id = dnl.sales_invoice_line_id
  JOIN public.stock_items si ON si.id = dnl.stock_item_id
  LEFT JOIN public.sales_invoices inv ON inv.id = sil.invoice_id
  WHERE dnl.delivery_note_id = v_job.delivery_note_id
  ORDER BY dnl.created_at ASC, dnl.id ASC;
END;
$$;

COMMENT ON FUNCTION public.get_delivery_job_lines(UUID) IS
  'Driver/admin DN/invoice line items for a delivery job; SECURITY DEFINER — sell amounts from invoice lines, not DN COGS.';

REVOKE ALL ON FUNCTION public.get_delivery_job_lines(UUID) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.get_delivery_job_lines(UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_delivery_job_lines(UUID) TO service_role;
