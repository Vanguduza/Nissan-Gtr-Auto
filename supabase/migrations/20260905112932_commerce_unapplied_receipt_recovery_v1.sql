CREATE OR REPLACE FUNCTION private.post_unapplied_payment_entry(
  p_payment_entry_id UUID,
  p_reason TEXT
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  v_pe public.payment_entries%ROWTYPE;
  v_cash_acct TEXT;
  v_journal UUID;
  v_allocated NUMERIC;
BEGIN
  SELECT * INTO v_pe
  FROM public.payment_entries
  WHERE id = p_payment_entry_id
  FOR UPDATE;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'payment entry not found: %', p_payment_entry_id;
  END IF;
  IF v_pe.status = 'posted' THEN
    RETURN v_pe.id;
  END IF;
  IF v_pe.status <> 'draft' THEN
    RAISE EXCEPTION 'only draft payments can be posted as unapplied receipts';
  END IF;
  IF v_pe.tender = 'store_credit' THEN
    RAISE EXCEPTION 'store_credit is not a cash receipt tender';
  END IF;

  SELECT COALESCE(SUM(amount),0)
    INTO v_allocated
  FROM public.payment_allocations
  WHERE payment_entry_id = v_pe.id;
  IF v_allocated > 0.001 THEN
    RAISE EXCEPTION 'unapplied receipt cannot already have invoice allocations';
  END IF;

  v_cash_acct := public.gl_account_for_payment_tender(v_pe.tender);
  v_journal := public.post_journal_entry(
    CURRENT_DATE,
    format('Unapplied receipt %s', COALESCE(v_pe.document_number, v_pe.id::text)),
    v_pe.currency,
    v_pe.exchange_rate_applied,
    jsonb_build_array(
      jsonb_build_object(
        'account_code', v_cash_acct,
        'debit', v_pe.amount,
        'credit', 0,
        'currency', v_pe.currency
      ),
      jsonb_build_object(
        'account_code', '2200',
        'debit', 0,
        'credit', v_pe.amount,
        'currency', v_pe.currency
      )
    )
  );

  PERFORM public._append_store_credit(
    v_pe.customer_id,
    'issue',
    v_pe.amount,
    v_pe.currency,
    v_pe.exchange_rate_applied,
    v_pe.id,
    v_journal,
    COALESCE(NULLIF(trim(COALESCE(p_reason,'')),''), 'Unapplied commerce receipt')
  );

  UPDATE public.payment_entries
  SET status = 'posted',
      journal_entry_id = v_journal,
      store_credit_issued = v_pe.amount,
      posted_by = auth.uid(),
      posted_at = now(),
      updated_at = now()
  WHERE id = v_pe.id;

  PERFORM public.emit_domain_event(
    'payment_received',
    'payment:unapplied:' || v_pe.id::text,
    jsonb_build_object(
      'payment_entry_id', v_pe.id,
      'amount', v_pe.amount,
      'allocated', 0,
      'store_credit_issued', v_pe.amount,
      'currency', v_pe.currency,
      'tender', v_pe.tender,
      'gl_account', v_cash_acct,
      'reason', p_reason
    ),
    auth.uid(),
    format('GTR Auto: unapplied payment received %s %s', v_pe.amount, v_pe.currency)
  );

  RETURN v_pe.id;
END;
$$;

REVOKE ALL ON FUNCTION private.post_unapplied_payment_entry(UUID,TEXT) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION private.post_unapplied_payment_entry(UUID,TEXT) TO service_role;

CREATE OR REPLACE FUNCTION private.settle_commerce_payment(
  p_order_id UUID,
  p_provider TEXT,
  p_provider_intent_id UUID,
  p_provider_ref TEXT,
  p_amount NUMERIC,
  p_currency public.currency_code,
  p_exchange_rate NUMERIC,
  p_settlement_currency public.currency_code,
  p_settlement_amount NUMERIC,
  p_settlement_exchange_rate NUMERIC
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  v_order public.commerce_orders%ROWTYPE;
  v_inv UUID;
  v_pe UUID;
  v_error TEXT;
BEGIN
  IF p_provider NOT IN ('contipay','paynow','ecocash','cash','bank') THEN
    RAISE EXCEPTION 'unsupported provider';
  END IF;

  SELECT * INTO v_order
  FROM public.commerce_orders
  WHERE id=p_order_id
  FOR UPDATE;
  IF NOT FOUND THEN RAISE EXCEPTION 'commerce order not found'; END IF;

  IF abs(p_amount-v_order.total)>0.001 OR p_currency<>v_order.currency THEN
    RAISE EXCEPTION 'payment does not match immutable checkout snapshot';
  END IF;

  IF v_order.settled_payment_entry_id IS NOT NULL THEN
    v_pe:=public.create_payment_entry(
      v_order.customer_id,
      p_provider::public.payment_tender,
      p_amount,
      p_currency,
      p_exchange_rate,
      format('%s duplicate settlement for commerce order %s',p_provider,v_order.id),
      p_settlement_currency,
      p_settlement_amount,
      p_settlement_exchange_rate
    );
    PERFORM private.post_unapplied_payment_entry(
      v_pe,
      format('Duplicate %s settlement for commerce order %s; held as customer credit pending refund/review.', p_provider, v_order.id)
    );
    INSERT INTO public.commerce_payment_exceptions(
      commerce_order_id,provider,provider_intent_id,provider_ref,amount,currency,
      exception_code,detail,payment_entry_id
    ) VALUES(
      v_order.id,p_provider,p_provider_intent_id,p_provider_ref,p_amount,p_currency,
      'DUPLICATE_PROVIDER_SETTLEMENT',
      'Order already has a settled payment; duplicate posted as unapplied customer credit pending refund/review.',
      v_pe
    );
    PERFORM private.enqueue_commerce_event(
      'commerce:'||v_order.id::text||':duplicate-settlement:'||p_provider||':'||COALESCE(p_provider_intent_id::text,p_provider_ref,'unknown'),
      'commerce.payment_exception',
      v_order.id,
      jsonb_build_object('code','DUPLICATE_PROVIDER_SETTLEMENT','provider',p_provider,'payment_entry_id',v_pe)
    );
    RETURN v_pe;
  END IF;

  BEGIN
    v_inv:=private.finalize_commerce_order(v_order.id);
  EXCEPTION WHEN OTHERS THEN
    v_error:=SQLERRM;
  END;

  IF v_inv IS NULL THEN
    v_pe:=public.create_payment_entry(
      v_order.customer_id,
      p_provider::public.payment_tender,
      p_amount,
      p_currency,
      p_exchange_rate,
      format('%s settlement pending order repair %s',p_provider,v_order.id),
      p_settlement_currency,
      p_settlement_amount,
      p_settlement_exchange_rate
    );
    PERFORM private.post_unapplied_payment_entry(
      v_pe,
      format('%s settlement received for commerce order %s but finalization failed: %s',p_provider,v_order.id,COALESCE(v_error,'unknown error'))
    );
    UPDATE public.commerce_orders
      SET state='allocation_pending',
          settled_payment_entry_id=v_pe,
          settled_provider=p_provider,
          settled_provider_ref=p_provider_ref,
          payment_exception=v_error,
          updated_at=now()
      WHERE id=v_order.id;
    INSERT INTO public.commerce_payment_exceptions(
      commerce_order_id,provider,provider_intent_id,provider_ref,amount,currency,
      exception_code,detail,payment_entry_id
    ) VALUES(
      v_order.id,p_provider,p_provider_intent_id,p_provider_ref,p_amount,p_currency,
      'PAID_ORDER_FINALIZATION_FAILED',v_error,v_pe
    );
    PERFORM private.enqueue_commerce_event(
      'commerce:'||v_order.id::text||':finalization-failed:'||COALESCE(p_provider_intent_id::text,p_provider_ref,'unknown'),
      'commerce.payment_exception',
      v_order.id,
      jsonb_build_object('code','PAID_ORDER_FINALIZATION_FAILED','provider',p_provider,'payment_entry_id',v_pe,'detail',v_error)
    );
    RETURN v_pe;
  END IF;

  v_pe:=public.create_payment_entry(
    v_order.customer_id,
    p_provider::public.payment_tender,
    p_amount,
    p_currency,
    p_exchange_rate,
    format('%s commerce order %s',p_provider,v_order.id),
    p_settlement_currency,
    p_settlement_amount,
    p_settlement_exchange_rate
  );
  PERFORM public.allocate_payment(
    v_pe,
    jsonb_build_array(jsonb_build_object('sales_invoice_id',v_inv,'amount',p_amount))
  );
  PERFORM public.post_payment_entry(v_pe);
  UPDATE public.commerce_orders
    SET settled_payment_entry_id=v_pe,
        settled_provider=p_provider,
        settled_provider_ref=p_provider_ref,
        state=CASE WHEN fulfillment_mode='dispatch'
          THEN 'allocation_pending'::public.commerce_order_state
          ELSE 'paid'::public.commerce_order_state END,
        payment_exception=NULL,
        updated_at=now()
    WHERE id=v_order.id;
  PERFORM private.enqueue_commerce_event(
    'commerce:'||v_order.id::text||':paid',
    'commerce.payment_settled',
    v_order.id,
    jsonb_build_object('provider',p_provider,'provider_ref',p_provider_ref,'payment_entry_id',v_pe,'sales_invoice_id',v_inv)
  );
  RETURN v_pe;
END;
$$;

REVOKE ALL ON FUNCTION private.settle_commerce_payment(UUID,TEXT,UUID,TEXT,NUMERIC,public.currency_code,NUMERIC,public.currency_code,NUMERIC,NUMERIC) FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION private.settle_commerce_payment(UUID,TEXT,UUID,TEXT,NUMERIC,public.currency_code,NUMERIC,public.currency_code,NUMERIC,NUMERIC) TO service_role;

COMMENT ON FUNCTION private.post_unapplied_payment_entry(UUID,TEXT) IS
  'Posts a verified but unapplied cash/provider receipt to its tender GL account and customer-credit liability, preserving money received when commerce finalization cannot allocate it.';