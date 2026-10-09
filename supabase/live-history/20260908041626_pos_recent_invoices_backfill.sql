-- Exported from the hosted project's supabase_migrations.schema_migrations (20260908041626 pos_recent_invoices_backfill).
-- Source of record for what production ran; see supabase/live-history/README.md.

-- Replacement-project catch-up: restore the POS recent invoice read model without
-- overwriting the newer image-aware list_pos_popular_spares implementation.
CREATE OR REPLACE FUNCTION public.list_pos_recent_invoices(
  p_query text DEFAULT NULL,
  p_limit integer DEFAULT 50
)
RETURNS TABLE (
  id uuid,
  document_number text,
  customer_id uuid,
  customer_name text,
  total numeric,
  currency public.currency_code,
  posted_at timestamptz
)
LANGUAGE plpgsql
STABLE
SECURITY INVOKER
SET search_path = public
AS $$
DECLARE
  v_query text := NULLIF(trim(p_query), '');
BEGIN
  PERFORM public._require_sales_staff();
  RETURN QUERY
  SELECT
    inv.id,
    inv.document_number,
    inv.customer_id,
    c.display_name,
    inv.total,
    inv.currency,
    COALESCE(inv.posted_at, inv.created_at)
  FROM public.sales_invoices inv
  LEFT JOIN public.customers c ON c.id = inv.customer_id
  WHERE inv.doc_type = 'invoice'
    AND inv.status = 'posted'
    AND (
      v_query IS NULL
      OR inv.document_number ILIKE '%' || v_query || '%'
      OR inv.id::text = v_query
      OR c.display_name ILIKE '%' || v_query || '%'
      OR c.email ILIKE '%' || v_query || '%'
      OR c.phone_e164 ILIKE '%' || v_query || '%'
    )
  ORDER BY COALESCE(inv.posted_at, inv.created_at) DESC
  LIMIT GREATEST(1, LEAST(p_limit, 200));
END;
$$;

REVOKE ALL ON FUNCTION public.list_pos_recent_invoices(text, integer) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.list_pos_recent_invoices(text, integer) TO authenticated, service_role;
COMMENT ON FUNCTION public.list_pos_recent_invoices IS
  'Operator POS recent posted invoice search for order history and manager-approved refund selection.';
