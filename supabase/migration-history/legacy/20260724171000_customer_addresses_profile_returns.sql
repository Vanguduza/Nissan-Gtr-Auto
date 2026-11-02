-- Customer storefront gaps: addresses, own-profile contact/prefs UPDATE,
-- quarantine-only customer return RPC (no staff RPC grant abuse).
-- Follow-up 20260724172000 tightens open_balance under storefront RPC.
-- NO ZIMRA / payroll tax / HTML5 QR.

-- ---------------------------------------------------------------------------
-- Allow post_return_credit_note when called under storefront RPC flag
-- (set only by SECURITY DEFINER customer wrappers — not by clients).
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public._require_return_post()
RETURNS void
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  IF public._storefront_rpc_active() THEN
    RETURN;
  END IF;
  PERFORM public._require_sales_staff();
END;
$$;

REVOKE ALL ON FUNCTION public._require_return_post() FROM PUBLIC;
REVOKE ALL ON FUNCTION public._require_return_post() FROM authenticated;

DO $$
DECLARE
  v_def TEXT;
BEGIN
  SELECT pg_get_functiondef(p.oid) INTO v_def
  FROM pg_proc p
  JOIN pg_namespace n ON n.oid = p.pronamespace
  WHERE n.nspname = 'public' AND p.proname = 'post_return_credit_note'
  LIMIT 1;

  IF v_def IS NULL THEN
    RAISE EXCEPTION 'post_return_credit_note not found';
  END IF;

  IF position('PERFORM public._require_return_post();' IN v_def) = 0 THEN
    IF position('PERFORM public._require_sales_staff();' IN v_def) = 0 THEN
      RAISE EXCEPTION 'post_return_credit_note patch failed: sales staff gate not found';
    END IF;
    -- Only the entry gate (first occurrence)
    v_def := regexp_replace(
      v_def,
      'PERFORM public\._require_sales_staff\(\);',
      'PERFORM public._require_return_post();',
      1
    );
    IF position('PERFORM public._require_return_post();' IN v_def) = 0 THEN
      RAISE EXCEPTION 'post_return_credit_note patch replace miss';
    END IF;
    EXECUTE v_def;
  END IF;
END;
$$;

-- ---------------------------------------------------------------------------
-- Customer return / credit note (own invoice only → quarantine path)
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.post_customer_return_credit_note(
  p_invoice_id UUID,
  p_lines JSONB
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_inv public.sales_invoices%ROWTYPE;
  v_line JSONB;
  v_item UUID;
  v_uom UUID;
  v_qty NUMERIC;
  v_price NUMERIC;
  v_src_qty NUMERIC;
  v_returned NUMERIC;
  v_normalized JSONB := '[]'::jsonb;
  v_cn UUID;
BEGIN
  IF auth.uid() IS NULL THEN
    RAISE EXCEPTION 'authentication required';
  END IF;
  IF public.is_staff() THEN
    RAISE EXCEPTION 'use post_return_credit_note for staff returns';
  END IF;

  v_inv := public._assert_customer_owns_invoice(p_invoice_id);

  IF v_inv.doc_type <> 'invoice' OR v_inv.status <> 'posted' THEN
    RAISE EXCEPTION 'posted source invoice required';
  END IF;

  IF p_lines IS NULL OR jsonb_typeof(p_lines) <> 'array' OR jsonb_array_length(p_lines) = 0 THEN
    RAISE EXCEPTION 'return lines required';
  END IF;

  FOR v_line IN SELECT * FROM jsonb_array_elements(p_lines)
  LOOP
    v_item := (v_line ->> 'stock_item_id')::uuid;
    v_uom := (v_line ->> 'uom_id')::uuid;
    v_qty := (v_line ->> 'qty')::numeric;

    IF v_item IS NULL OR v_uom IS NULL OR v_qty IS NULL OR v_qty <= 0 THEN
      RAISE EXCEPTION 'each line needs stock_item_id, uom_id, qty > 0';
    END IF;

    SELECT sil.unit_price, sil.qty
    INTO v_price, v_src_qty
    FROM public.sales_invoice_lines sil
    WHERE sil.invoice_id = p_invoice_id
      AND sil.stock_item_id = v_item
      AND sil.uom_id = v_uom
      AND sil.is_core_charge = false
    ORDER BY sil.created_at ASC
    LIMIT 1;

    IF v_price IS NULL THEN
      RAISE EXCEPTION 'line not on invoice (or is core charge): item=% uom=%', v_item, v_uom;
    END IF;

    SELECT COALESCE(SUM(cil.qty), 0) INTO v_returned
    FROM public.sales_invoices cn
    JOIN public.sales_invoice_lines cil ON cil.invoice_id = cn.id
    WHERE cn.return_against_id = p_invoice_id
      AND cn.doc_type = 'credit_note'
      AND cn.status = 'posted'
      AND cil.stock_item_id = v_item
      AND cil.uom_id = v_uom
      AND cil.is_core_charge = false;

    IF v_qty > (v_src_qty - v_returned) THEN
      RAISE EXCEPTION 'return qty % exceeds remaining % for item %',
        v_qty, (v_src_qty - v_returned), v_item;
    END IF;

    -- Force invoice unit price — customers cannot invent prices
    v_normalized := v_normalized || jsonb_build_array(
      jsonb_build_object(
        'stock_item_id', v_item,
        'uom_id', v_uom,
        'qty', v_qty,
        'unit_price', v_price
      )
    );
  END LOOP;

  PERFORM public._storefront_rpc_enter();
  BEGIN
    v_cn := public.post_return_credit_note(p_invoice_id, v_normalized);
  EXCEPTION
    WHEN OTHERS THEN
      PERFORM public._storefront_rpc_exit();
      RAISE;
  END;
  PERFORM public._storefront_rpc_exit();

  RETURN v_cn;
END;
$$;

-- Alias preferred by some web call sites
CREATE OR REPLACE FUNCTION public.request_customer_return(
  p_invoice_id UUID,
  p_lines JSONB
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  RETURN public.post_customer_return_credit_note(p_invoice_id, p_lines);
END;
$$;

REVOKE ALL ON FUNCTION public.post_customer_return_credit_note(UUID, JSONB) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.request_customer_return(UUID, JSONB) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.post_customer_return_credit_note(UUID, JSONB)
  TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.request_customer_return(UUID, JSONB)
  TO authenticated, service_role;

-- ---------------------------------------------------------------------------
-- Own-row customer contact / receipt prefs
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.customers_protect_privileged_columns()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  IF auth.role() = 'service_role' OR public.is_staff() THEN
    RETURN NEW;
  END IF;

  -- Self-service may only touch contact + receipt prefs
  IF NEW.profile_id IS DISTINCT FROM OLD.profile_id
     OR NEW.price_list_id IS DISTINCT FROM OLD.price_list_id
     OR NEW.credit_limit IS DISTINCT FROM OLD.credit_limit
     OR NEW.credit_hold IS DISTINCT FROM OLD.credit_hold
     OR NEW.open_balance IS DISTINCT FROM OLD.open_balance
     OR NEW.currency IS DISTINCT FROM OLD.currency
     OR NEW.id IS DISTINCT FROM OLD.id
  THEN
    RAISE EXCEPTION 'customers may only update contact and receipt preference fields';
  END IF;

  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS customers_protect_privileged_columns_trg ON public.customers;
CREATE TRIGGER customers_protect_privileged_columns_trg
  BEFORE UPDATE ON public.customers
  FOR EACH ROW
  EXECUTE PROCEDURE public.customers_protect_privileged_columns();

DROP POLICY IF EXISTS customers_update_own ON public.customers;
CREATE POLICY customers_update_own ON public.customers
  FOR UPDATE TO authenticated
  USING (profile_id = auth.uid())
  WITH CHECK (profile_id = auth.uid());

CREATE OR REPLACE FUNCTION public.update_own_customer_profile(
  p_display_name TEXT DEFAULT NULL,
  p_email TEXT DEFAULT NULL,
  p_phone_e164 TEXT DEFAULT NULL,
  p_whatsapp_e164 TEXT DEFAULT NULL,
  p_sms_receipts BOOLEAN DEFAULT NULL,
  p_email_receipts BOOLEAN DEFAULT NULL,
  p_whatsapp_receipts BOOLEAN DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_cust UUID := public._current_customer_id();
BEGIN
  IF v_cust IS NULL THEN
    RAISE EXCEPTION 'customer profile required';
  END IF;

  UPDATE public.customers
  SET
    display_name = COALESCE(NULLIF(trim(p_display_name), ''), display_name),
    email = CASE WHEN p_email IS NULL THEN email ELSE NULLIF(trim(p_email), '') END,
    phone_e164 = CASE WHEN p_phone_e164 IS NULL THEN phone_e164 ELSE NULLIF(trim(p_phone_e164), '') END,
    whatsapp_e164 = CASE
      WHEN p_whatsapp_e164 IS NULL THEN whatsapp_e164
      ELSE NULLIF(trim(p_whatsapp_e164), '')
    END,
    sms_receipts = COALESCE(p_sms_receipts, sms_receipts),
    email_receipts = COALESCE(p_email_receipts, email_receipts),
    whatsapp_receipts = COALESCE(p_whatsapp_receipts, whatsapp_receipts),
    updated_at = now()
  WHERE id = v_cust
    AND profile_id = auth.uid();

  IF NOT FOUND THEN
    RAISE EXCEPTION 'customer row not found';
  END IF;

  RETURN v_cust;
END;
$$;

REVOKE ALL ON FUNCTION public.update_own_customer_profile(
  TEXT, TEXT, TEXT, TEXT, BOOLEAN, BOOLEAN, BOOLEAN
) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.update_own_customer_profile(
  TEXT, TEXT, TEXT, TEXT, BOOLEAN, BOOLEAN, BOOLEAN
) TO authenticated, service_role;

-- ---------------------------------------------------------------------------
-- customer_addresses
-- ---------------------------------------------------------------------------
CREATE TABLE public.customer_addresses (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  customer_id UUID NOT NULL REFERENCES public.customers (id) ON DELETE CASCADE,
  label TEXT NOT NULL DEFAULT '',
  line1 TEXT NOT NULL,
  line2 TEXT,
  city TEXT,
  province TEXT,
  postal_code TEXT,
  country TEXT NOT NULL DEFAULT 'Zimbabwe',
  is_default BOOLEAN NOT NULL DEFAULT false,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT customer_addresses_line1_nonempty CHECK (length(trim(line1)) > 0)
);

CREATE INDEX customer_addresses_customer_idx
  ON public.customer_addresses (customer_id, created_at DESC);

CREATE UNIQUE INDEX customer_addresses_one_default_idx
  ON public.customer_addresses (customer_id)
  WHERE is_default;

COMMENT ON TABLE public.customer_addresses IS
  'Customer shipping/billing addresses. Own-row RLS via customers.profile_id.';

ALTER TABLE public.customer_addresses ENABLE ROW LEVEL SECURITY;

CREATE POLICY customer_addresses_own_all ON public.customer_addresses
  FOR ALL TO authenticated
  USING (customer_id = public._current_customer_id())
  WITH CHECK (customer_id = public._current_customer_id());

CREATE POLICY customer_addresses_staff_select ON public.customer_addresses
  FOR SELECT TO authenticated
  USING (public.has_staff_role(ARRAY['admin', 'sales', 'finance', 'warehouse']::public.staff_role[]));

GRANT SELECT ON TABLE public.customer_addresses TO authenticated, service_role;
GRANT INSERT, UPDATE, DELETE ON TABLE public.customer_addresses TO authenticated;
GRANT INSERT, UPDATE, DELETE ON TABLE public.customer_addresses TO service_role;

-- Optional upsert/delete RPCs (also usable from table policies)
CREATE OR REPLACE FUNCTION public.upsert_customer_address(
  p_id UUID DEFAULT NULL,
  p_label TEXT DEFAULT '',
  p_line1 TEXT DEFAULT NULL,
  p_line2 TEXT DEFAULT NULL,
  p_city TEXT DEFAULT NULL,
  p_province TEXT DEFAULT NULL,
  p_postal_code TEXT DEFAULT NULL,
  p_country TEXT DEFAULT 'Zimbabwe',
  p_is_default BOOLEAN DEFAULT false
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_cust UUID := public._current_customer_id();
  v_id UUID;
BEGIN
  IF v_cust IS NULL THEN
    RAISE EXCEPTION 'customer profile required';
  END IF;
  IF p_line1 IS NULL OR length(trim(p_line1)) = 0 THEN
    RAISE EXCEPTION 'line1 required';
  END IF;

  IF COALESCE(p_is_default, false) THEN
    UPDATE public.customer_addresses
    SET is_default = false, updated_at = now()
    WHERE customer_id = v_cust AND is_default
      AND (p_id IS NULL OR id IS DISTINCT FROM p_id);
  END IF;

  IF p_id IS NOT NULL THEN
    UPDATE public.customer_addresses
    SET
      label = COALESCE(p_label, label),
      line1 = trim(p_line1),
      line2 = p_line2,
      city = p_city,
      province = p_province,
      postal_code = p_postal_code,
      country = COALESCE(NULLIF(trim(p_country), ''), country),
      is_default = COALESCE(p_is_default, is_default),
      updated_at = now()
    WHERE id = p_id AND customer_id = v_cust
    RETURNING id INTO v_id;

    IF v_id IS NULL THEN
      RAISE EXCEPTION 'address not found';
    END IF;
    RETURN v_id;
  END IF;

  INSERT INTO public.customer_addresses (
    customer_id, label, line1, line2, city, province, postal_code, country, is_default
  )
  VALUES (
    v_cust,
    COALESCE(p_label, ''),
    trim(p_line1),
    p_line2,
    p_city,
    p_province,
    p_postal_code,
    COALESCE(NULLIF(trim(p_country), ''), 'Zimbabwe'),
    COALESCE(p_is_default, false)
  )
  RETURNING id INTO v_id;

  RETURN v_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.delete_customer_address(p_id UUID)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_cust UUID := public._current_customer_id();
BEGIN
  IF v_cust IS NULL THEN
    RAISE EXCEPTION 'customer profile required';
  END IF;
  DELETE FROM public.customer_addresses
  WHERE id = p_id AND customer_id = v_cust;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'address not found';
  END IF;
END;
$$;

REVOKE ALL ON FUNCTION public.upsert_customer_address(
  UUID, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, BOOLEAN
) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.delete_customer_address(UUID) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.upsert_customer_address(
  UUID, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, BOOLEAN
) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.delete_customer_address(UUID)
  TO authenticated, service_role;
