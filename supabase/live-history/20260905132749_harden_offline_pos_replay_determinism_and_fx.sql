-- Exported from the hosted project's supabase_migrations.schema_migrations (20260905132749 harden_offline_pos_replay_determinism_and_fx).
-- Source of record for what production ran; see supabase/live-history/README.md.

CREATE OR REPLACE FUNCTION public.replay_offline_pos_sale(p_client_sale_id uuid,p_payload jsonb)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $$
DECLARE
  v_existing uuid;
  v_existing_hash text;
  v_warehouse uuid;
  v_currency public.currency_code;
  v_rate numeric:=1;
  v_device text;
  v_lines jsonb;
  v_tenders jsonb;
  v_line jsonb;
  v_tender jsonb;
  v_cart uuid;
  v_inv uuid;
  v_stock uuid;
  v_uom uuid;
  v_qty numeric;
  v_expected numeric;
  v_price numeric;
  v_hash text;
  v_email text;
  v_wa text;
  v_phone text;
  v_amt numeric;
  v_tender_code text;
BEGIN
  PERFORM public._require_payments_staff();
  IF p_client_sale_id IS NULL THEN RAISE EXCEPTION 'client_sale_id required'; END IF;
  IF p_payload IS NULL OR jsonb_typeof(p_payload)<>'object' THEN RAISE EXCEPTION 'payload object required'; END IF;

  v_hash:=encode(extensions.digest(convert_to(p_payload::text,'UTF8'),'sha256'),'hex');

  SELECT r.invoice_id,r.payload_hash INTO v_existing,v_existing_hash
  FROM public.pos_offline_sale_receipts r
  WHERE r.client_sale_id=p_client_sale_id;
  IF v_existing IS NOT NULL THEN
    IF v_existing_hash IS DISTINCT FROM v_hash THEN
      RAISE EXCEPTION 'offline_replay_payload_conflict: client_sale_id already committed with different payload';
    END IF;
    RETURN v_existing;
  END IF;

  v_warehouse:=NULLIF(p_payload->>'warehouse_id','')::uuid;
  IF v_warehouse IS NULL THEN RAISE EXCEPTION 'warehouse_id required in payload'; END IF;
  IF NOT EXISTS(
    SELECT 1 FROM public.warehouses w
    WHERE w.id=v_warehouse AND w.is_active=true AND COALESCE(w.is_quarantine,false)=false
  ) THEN
    RAISE EXCEPTION 'offline warehouse must be active and saleable';
  END IF;

  v_currency:=COALESCE(NULLIF(p_payload->>'currency','')::public.currency_code,'USD'::public.currency_code);
  IF v_currency<>'USD'::public.currency_code THEN
    RAISE EXCEPTION 'offline_non_usd_not_supported: ZiG/offline FX requires a server-issued signed rate snapshot';
  END IF;
  v_rate:=1;

  v_device:=NULLIF(trim(COALESCE(p_payload->>'device_id','')),'');
  v_lines:=p_payload->'lines';
  v_tenders:=p_payload->'tenders';
  IF v_lines IS NULL OR jsonb_typeof(v_lines)<>'array' OR jsonb_array_length(v_lines)<1 THEN RAISE EXCEPTION 'lines required'; END IF;
  IF v_tenders IS NULL OR jsonb_typeof(v_tenders)<>'array' OR jsonb_array_length(v_tenders)<1 THEN RAISE EXCEPTION 'tenders required'; END IF;

  FOR v_tender IN SELECT * FROM jsonb_array_elements(v_tenders)
  LOOP
    v_tender_code:=lower(trim(COALESCE(v_tender->>'tender','')));
    IF v_tender_code IS DISTINCT FROM 'cash' THEN
      RAISE EXCEPTION 'offline_tender_not_allowed: % (cash only)',v_tender_code;
    END IF;
    v_amt:=NULLIF(v_tender->>'amount','')::numeric;
    IF v_amt IS NULL OR v_amt<=0 THEN RAISE EXCEPTION 'tender amount must be > 0'; END IF;
  END LOOP;

  v_email:=NULLIF(trim(COALESCE(p_payload->>'receipt_email','')),'');
  v_wa:=NULLIF(trim(COALESCE(p_payload->>'receipt_whatsapp_e164','')),'');
  v_phone:=NULLIF(trim(COALESCE(p_payload->>'receipt_phone_e164','')),'');

  v_cart:=public.create_pos_cart(v_warehouse,NULL,'USD','immediate'::public.fulfillment_mode);
  IF v_cart IS NULL THEN RAISE EXCEPTION 'create_pos_cart failed'; END IF;
  UPDATE public.pos_carts SET exchange_rate_applied=1,updated_at=now() WHERE id=v_cart;

  FOR v_line IN SELECT * FROM jsonb_array_elements(v_lines)
  LOOP
    v_stock:=NULLIF(v_line->>'stock_item_id','')::uuid;
    v_uom:=NULLIF(v_line->>'uom_id','')::uuid;
    v_qty:=NULLIF(v_line->>'qty','')::numeric;
    v_expected:=NULLIF(v_line->>'expected_unit_price','')::numeric;
    IF v_stock IS NULL OR v_uom IS NULL THEN RAISE EXCEPTION 'line stock_item_id and uom_id required'; END IF;
    IF v_qty IS NULL OR v_qty<=0 THEN RAISE EXCEPTION 'line qty must be > 0'; END IF;
    IF v_expected IS NULL OR v_expected<0 THEN
      RAISE EXCEPTION 'offline_expected_unit_price_required: every offline line must carry its captured expected price';
    END IF;

    SELECT r.unit_price INTO v_price FROM public.resolve_item_price(NULL,v_stock) r;
    IF v_price IS NULL THEN RAISE EXCEPTION 'no server price for offline item %',v_stock; END IF;
    IF abs(v_price-v_expected)>0.05 THEN
      RAISE EXCEPTION 'offline_price_conflict: item % expected % got %',v_stock,v_expected,v_price;
    END IF;
    PERFORM public.add_cart_line(v_cart,v_stock,v_uom,v_qty);
  END LOOP;

  v_inv:=public.checkout_pos_cart_with_tenders(v_cart,v_tenders,v_email,v_wa,v_phone);

  INSERT INTO public.pos_offline_sale_receipts(
    client_sale_id,invoice_id,actor_user_id,device_id,warehouse_id,currency,payload_hash
  ) VALUES(
    p_client_sale_id,v_inv,auth.uid(),v_device,v_warehouse,'USD',v_hash
  );
  RETURN v_inv;
EXCEPTION
  WHEN unique_violation THEN
    SELECT r.invoice_id,r.payload_hash INTO v_existing,v_existing_hash
    FROM public.pos_offline_sale_receipts r
    WHERE r.client_sale_id=p_client_sale_id;
    IF v_existing IS NOT NULL THEN
      IF v_existing_hash IS DISTINCT FROM v_hash THEN
        RAISE EXCEPTION 'offline_replay_payload_conflict: concurrent client_sale_id used with different payload';
      END IF;
      RETURN v_existing;
    END IF;
    RAISE;
END;
$$;

REVOKE EXECUTE ON FUNCTION public.replay_offline_pos_sale(uuid,jsonb) FROM anon;
