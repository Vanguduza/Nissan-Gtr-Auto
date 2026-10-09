-- Exported from the hosted project's supabase_migrations.schema_migrations (20260908123100 pos_account_credit_functions).
-- Source of record for what production ran; see supabase/live-history/README.md.

-- Reserve-first account-credit finalization for named POS customers.
CREATE OR REPLACE FUNCTION public.finalize_pos_account_credit_order(p_order_id UUID)
RETURNS UUID LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE o public.commerce_orders%ROWTYPE; v_inv UUID; v_customer_name TEXT;
BEGIN
 PERFORM public._require_sales_staff();
 SELECT * INTO o FROM public.commerce_orders WHERE id=p_order_id FOR UPDATE;
 IF NOT FOUND THEN RAISE EXCEPTION 'commerce order not found'; END IF;
 IF o.settled_payment_entry_id IS NOT NULL THEN RAISE EXCEPTION 'order already has payment settlement'; END IF;
 SELECT display_name INTO v_customer_name FROM public.customers WHERE id=o.customer_id;
 IF v_customer_name='POS Walk-in' THEN RAISE EXCEPTION 'walk-in customer cannot use account credit'; END IF;
 IF o.reservation_expires_at IS NULL OR o.reservation_expires_at<=now() THEN RAISE EXCEPTION 'commerce reservation expired'; END IF;
 IF o.state NOT IN('awaiting_payment','payment_failed') THEN RAISE EXCEPTION 'account credit cannot finalize order in state %',o.state; END IF;
 v_inv:=private.finalize_commerce_order(o.id);
 UPDATE public.commerce_orders
 SET state='account_invoiced',settled_provider=NULL,settled_provider_ref=NULL,payment_exception=NULL,updated_at=now()
 WHERE id=o.id;
 PERFORM private.enqueue_commerce_event('commerce:'||o.id::text||':account-invoiced',
  'commerce.account_invoiced',o.id,jsonb_build_object('sales_invoice_id',v_inv,'customer_id',o.customer_id));
 RETURN v_inv;
END $$;

REVOKE ALL ON FUNCTION public.finalize_pos_account_credit_order(UUID) FROM PUBLIC,anon;

GRANT EXECUTE ON FUNCTION public.finalize_pos_account_credit_order(UUID) TO authenticated,service_role;

CREATE OR REPLACE FUNCTION public.list_pos_pickup_orders(p_query TEXT DEFAULT NULL,p_limit INTEGER DEFAULT 100)
RETURNS TABLE(order_id UUID,document_number TEXT,customer_id UUID,customer_name TEXT,state TEXT,total NUMERIC,currency TEXT,sales_invoice_id UUID,settled_provider TEXT,updated_at TIMESTAMPTZ)
LANGUAGE sql STABLE SECURITY DEFINER SET search_path='' AS $$
 SELECT o.id,COALESCE(i.document_number,c.document_number),o.customer_id,cu.display_name,o.state::text,o.total,o.currency::text,o.sales_invoice_id,o.settled_provider,o.updated_at
 FROM public.commerce_orders o JOIN public.pos_carts c ON c.id=o.cart_id JOIN public.customers cu ON cu.id=o.customer_id LEFT JOIN public.sales_invoices i ON i.id=o.sales_invoice_id
 WHERE o.fulfillment_mode='immediate' AND o.sales_invoice_id IS NOT NULL AND o.state IN('paid','dispatch_ready','account_invoiced')
  AND (p_query IS NULL OR trim(p_query)='' OR COALESCE(i.document_number,c.document_number,'') ILIKE '%'||trim(p_query)||'%' OR cu.display_name ILIKE '%'||trim(p_query)||'%')
 ORDER BY o.updated_at DESC LIMIT LEAST(GREATEST(COALESCE(p_limit,100),1),250);
$$;

REVOKE ALL ON FUNCTION public.list_pos_pickup_orders(TEXT,INTEGER) FROM PUBLIC,anon;

GRANT EXECUTE ON FUNCTION public.list_pos_pickup_orders(TEXT,INTEGER) TO authenticated,service_role;

CREATE OR REPLACE FUNCTION public.collect_pos_commerce_order(p_order_id UUID,p_notes TEXT DEFAULT NULL)
RETURNS UUID LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE o public.commerce_orders%ROWTYPE; i public.sales_invoices%ROWTYPE;
BEGIN
 PERFORM public._require_sales_staff(); SELECT * INTO o FROM public.commerce_orders WHERE id=p_order_id FOR UPDATE;
 IF NOT FOUND OR o.fulfillment_mode<>'immediate' OR o.state NOT IN('paid','dispatch_ready','account_invoiced') OR o.sales_invoice_id IS NULL
  THEN RAISE EXCEPTION 'eligible pickup order required'; END IF;
 SELECT * INTO i FROM public.sales_invoices WHERE id=o.sales_invoice_id;
 IF NOT FOUND OR i.status<>'posted' THEN RAISE EXCEPTION 'posted invoice required'; END IF;
 IF o.state<>'account_invoiced' AND i.amount_paid+0.01<i.total THEN RAISE EXCEPTION 'fully paid invoice required'; END IF;
 UPDATE public.commerce_orders SET state='delivered',updated_at=now() WHERE id=o.id;
 PERFORM private.enqueue_commerce_event('commerce:'||o.id::text||':counter-collected','commerce.order_collected',o.id,
  jsonb_build_object('sales_invoice_id',o.sales_invoice_id,'notes',p_notes,'collected_by',auth.uid(),'account_credit',o.state='account_invoiced'));
 RETURN o.id;
END $$;

REVOKE ALL ON FUNCTION public.collect_pos_commerce_order(UUID,TEXT) FROM PUBLIC,anon;

GRANT EXECUTE ON FUNCTION public.collect_pos_commerce_order(UUID,TEXT) TO authenticated,service_role;
