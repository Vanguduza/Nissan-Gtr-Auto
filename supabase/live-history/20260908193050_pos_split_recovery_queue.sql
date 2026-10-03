-- Exported from the hosted project's supabase_migrations.schema_migrations (20260908193050 pos_split_recovery_queue).
-- Source of record for what production ran; see supabase/live-history/README.md.

-- Persistent operational queue for staged split sessions/refunds that still require action.
CREATE OR REPLACE FUNCTION public.list_pos_split_payment_recovery(p_limit INTEGER DEFAULT 100)
RETURNS TABLE(
 session_id UUID, order_id UUID, status TEXT, document_number TEXT, customer_name TEXT,
 total NUMERIC, currency TEXT, updated_at TIMESTAMPTZ, payload JSONB
)
LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path='' AS $$
BEGIN
 PERFORM public._require_payments_staff();
 RETURN QUERY
 SELECT s.id,s.commerce_order_id,s.status::text,o.document_number,c.display_name,
        s.total_amount,s.currency::text,s.updated_at,private.pos_split_payment_payload(s.id)
 FROM public.pos_split_payment_sessions s
 JOIN public.commerce_orders o ON o.id=s.commerce_order_id
 LEFT JOIN public.customers c ON c.id=o.customer_id
 WHERE s.status IN('partially_captured','leg_pending','fully_committed','finalization_failed','refund_review','refund_pending')
    OR EXISTS(SELECT 1 FROM public.pos_split_refund_requests r WHERE r.session_id=s.id AND r.status NOT IN('settled','cancelled'))
    OR EXISTS(SELECT 1 FROM public.pos_split_payment_legs l WHERE l.session_id=s.id AND l.status IN('pending','unknown','refund_review','refund_pending'))
 ORDER BY s.updated_at DESC
 LIMIT LEAST(GREATEST(COALESCE(p_limit,100),1),250);
END $$;
REVOKE ALL ON FUNCTION public.list_pos_split_payment_recovery(INTEGER) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.list_pos_split_payment_recovery(INTEGER) TO authenticated,service_role;
