-- Driver-scoped COD / settlement for delivery jobs (H4 follow-on).
-- Drivers cannot SELECT sales_invoices under invoices_staff RLS; expose only
-- the amount snapshot for the assignee (or admin) via SECURITY DEFINER.
-- Prefer *_minor via _major_to_minor on header majors (header minors not dual-written yet).
-- AI never invents payable amounts — values derive from invoice rows only.

CREATE OR REPLACE FUNCTION public.get_delivery_job_settlement(p_delivery_job_id UUID)
RETURNS TABLE (
  delivery_job_id UUID,
  currency public.currency_code,
  invoice_total NUMERIC,
  amount_paid NUMERIC,
  amount_due NUMERIC,
  invoice_total_minor BIGINT,
  amount_paid_minor BIGINT,
  amount_due_minor BIGINT
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_job public.delivery_jobs%ROWTYPE;
  v_inv public.sales_invoices%ROWTYPE;
  v_due NUMERIC;
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
    RAISE EXCEPTION 'not authorized for delivery job settlement';
  END IF;

  SELECT si.* INTO v_inv
  FROM public.delivery_notes dn
  JOIN public.sales_invoices si ON si.id = dn.sales_invoice_id
  WHERE dn.id = v_job.delivery_note_id;

  IF NOT FOUND THEN
    RETURN;
  END IF;

  v_due := GREATEST(COALESCE(v_inv.total, 0) - COALESCE(v_inv.amount_paid, 0), 0);

  RETURN QUERY
  SELECT
    v_job.id,
    v_inv.currency,
    v_inv.total,
    v_inv.amount_paid,
    v_due,
    public._major_to_minor(v_inv.total),
    public._major_to_minor(v_inv.amount_paid),
    public._major_to_minor(v_due);
END;
$$;

COMMENT ON FUNCTION public.get_delivery_job_settlement(UUID) IS
  'Driver/admin COD snapshot for a delivery job; SECURITY DEFINER — no sales_invoices SELECT for drivers.';

REVOKE ALL ON FUNCTION public.get_delivery_job_settlement(UUID) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.get_delivery_job_settlement(UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_delivery_job_settlement(UUID) TO service_role;
