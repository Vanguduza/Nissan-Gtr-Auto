-- Payment resolution letters (Blueprint §10.7, §10.11, phase 8): the letters and the business
-- document profile sit behind RLS with no table policies, reachable only through functions. The POS
-- could create a letter and render one by id, but not find the letters already issued for a payment,
-- nor read the profile it prints. Two read-only functions close that gap; no table policy changes.

CREATE OR REPLACE FUNCTION public.list_payment_resolution_letters(
  p_source_kind public.payment_resolution_source_kind DEFAULT NULL,
  p_source_id uuid DEFAULT NULL,
  p_query text DEFAULT NULL,
  p_limit integer DEFAULT 50
)
RETURNS TABLE(
  id uuid, document_number text, source_kind text, source_id uuid, provider text, observed_status text,
  amount numeric, currency text, customer_name text, invoice_document_number text,
  manager_name text, manager_title text, issued_at timestamptz
)
LANGUAGE plpgsql
STABLE SECURITY DEFINER
SET search_path TO ''
AS $$
BEGIN
  IF NOT (public.is_pos_approver() OR public.has_staff_role(ARRAY['admin','finance']::public.staff_role[])) THEN
    RAISE EXCEPTION 'manager/finance/admin role required';
  END IF;
  RETURN QUERY
  SELECT l.id, l.document_number, l.source_kind::text, l.source_id, l.provider, l.observed_status,
         l.amount, l.currency::text, l.customer_name, l.invoice_document_number,
         l.manager_name, l.manager_title, l.issued_at
  FROM public.payment_resolution_letters l
  WHERE (p_source_kind IS NULL OR l.source_kind = p_source_kind)
    AND (p_source_id IS NULL OR l.source_id = p_source_id)
    AND (p_query IS NULL OR trim(p_query) = ''
         OR l.document_number ILIKE '%' || trim(p_query) || '%'
         OR COALESCE(l.invoice_document_number, '') ILIKE '%' || trim(p_query) || '%'
         OR COALESCE(l.customer_name, '') ILIKE '%' || trim(p_query) || '%')
  ORDER BY l.issued_at DESC
  LIMIT LEAST(GREATEST(COALESCE(p_limit, 50), 1), 200);
END $$;

-- The name and contact details printed on staff documents; any signed-in staff member may read them.
CREATE OR REPLACE FUNCTION public.get_business_document_profile()
RETURNS jsonb
LANGUAGE plpgsql
STABLE SECURITY DEFINER
SET search_path TO ''
AS $$
BEGIN
  IF NOT (auth.role() = 'service_role' OR public.is_staff()) THEN RAISE EXCEPTION 'staff role required'; END IF;
  RETURN (SELECT to_jsonb(b) - 'updated_by' FROM public.business_document_profile b WHERE b.singleton);
END $$;

REVOKE ALL ON FUNCTION public.list_payment_resolution_letters(public.payment_resolution_source_kind, uuid, text, integer) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.get_business_document_profile() FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.list_payment_resolution_letters(public.payment_resolution_source_kind, uuid, text, integer) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.get_business_document_profile() TO authenticated, service_role;
