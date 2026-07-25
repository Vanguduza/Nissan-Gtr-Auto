-- Phase 2 shop-floor POS: optional companion scan sessions + checkout receipt
-- contacts / customer bind. Standalone POS requires NO scan session.
-- Exclusions: no ZIMRA, no payroll tax, no HTML5 QR.

-- ---------------------------------------------------------------------------
-- profiles.phone_e164 (auth OTP / verified phone)
-- ---------------------------------------------------------------------------
ALTER TABLE public.profiles
  ADD COLUMN IF NOT EXISTS phone_e164 TEXT;

CREATE UNIQUE INDEX IF NOT EXISTS profiles_phone_e164_uidx
  ON public.profiles (phone_e164)
  WHERE phone_e164 IS NOT NULL AND length(trim(phone_e164)) > 0;

REVOKE UPDATE ON TABLE public.profiles FROM authenticated;
GRANT UPDATE (full_name, phone_e164, updated_at) ON TABLE public.profiles TO authenticated;

-- ---------------------------------------------------------------------------
-- Optional companion: pos_scan_sessions (cart usable with zero sessions)
-- ---------------------------------------------------------------------------
CREATE TYPE public.pos_scan_session_status AS ENUM (
  'open',
  'claimed',
  'revoked',
  'expired'
);

CREATE TABLE public.pos_scan_sessions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  cart_id UUID NOT NULL REFERENCES public.pos_carts (id) ON DELETE CASCADE,
  pairing_code TEXT NOT NULL,
  owner_user_id UUID NOT NULL REFERENCES public.profiles (id) ON DELETE CASCADE,
  scanner_user_id UUID REFERENCES public.profiles (id) ON DELETE SET NULL,
  status public.pos_scan_session_status NOT NULL DEFAULT 'open',
  expires_at TIMESTAMPTZ NOT NULL,
  claimed_at TIMESTAMPTZ,
  revoked_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT pos_scan_sessions_pairing_code_format CHECK (pairing_code ~ '^[0-9]{6}$'),
  CONSTRAINT pos_scan_sessions_claimed_has_scanner CHECK (
    status <> 'claimed' OR scanner_user_id IS NOT NULL
  )
);

COMMENT ON TABLE public.pos_scan_sessions IS
  'Optional multi-device companion pairing. Open carts work with zero sessions (standalone POS).';

-- One open/claimed session per cart; cart may have none.
CREATE UNIQUE INDEX pos_scan_sessions_one_active_per_cart
  ON public.pos_scan_sessions (cart_id)
  WHERE status IN ('open', 'claimed');

CREATE UNIQUE INDEX pos_scan_sessions_active_code_uidx
  ON public.pos_scan_sessions (pairing_code)
  WHERE status IN ('open', 'claimed');

CREATE INDEX pos_scan_sessions_cart_idx ON public.pos_scan_sessions (cart_id);
CREATE INDEX pos_scan_sessions_owner_idx ON public.pos_scan_sessions (owner_user_id);

ALTER TABLE public.pos_scan_sessions ENABLE ROW LEVEL SECURITY;

CREATE POLICY pos_scan_sessions_staff_select
  ON public.pos_scan_sessions FOR SELECT TO authenticated
  USING (
    public.has_staff_role(ARRAY['admin', 'sales', 'warehouse']::public.staff_role[])
    AND (
      owner_user_id = auth.uid()
      OR scanner_user_id = auth.uid()
      OR public.has_staff_role(ARRAY['admin']::public.staff_role[])
    )
  );

-- Mutations via SECURITY DEFINER RPCs only
GRANT SELECT ON TABLE public.pos_scan_sessions TO authenticated, service_role;
GRANT INSERT, UPDATE, DELETE ON TABLE public.pos_scan_sessions TO service_role;

-- ---------------------------------------------------------------------------
-- Auth OTP challenges (Edge service_role only; fail-closed send outside)
-- ---------------------------------------------------------------------------
CREATE TABLE public.auth_otp_challenges (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  channel TEXT NOT NULL CHECK (channel IN ('email', 'phone')),
  identifier TEXT NOT NULL,
  code_hash TEXT NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  consumed_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT auth_otp_challenges_identifier_nonempty CHECK (length(trim(identifier)) > 0),
  CONSTRAINT auth_otp_challenges_hash_nonempty CHECK (length(trim(code_hash)) > 0)
);

CREATE INDEX auth_otp_challenges_lookup_idx
  ON public.auth_otp_challenges (channel, identifier, expires_at DESC);

ALTER TABLE public.auth_otp_challenges ENABLE ROW LEVEL SECURITY;
-- No policies for authenticated/anon — service_role bypasses RLS for Edge.
GRANT ALL ON TABLE public.auth_otp_challenges TO service_role;

-- ---------------------------------------------------------------------------
-- Contact normalize helpers
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public._normalize_receipt_email(p_email TEXT)
RETURNS TEXT
LANGUAGE sql
IMMUTABLE
AS $$
  SELECT NULLIF(lower(trim(p_email)), '');
$$;

CREATE OR REPLACE FUNCTION public._normalize_e164(p_phone TEXT)
RETURNS TEXT
LANGUAGE plpgsql
IMMUTABLE
AS $$
DECLARE
  v TEXT;
BEGIN
  v := NULLIF(trim(COALESCE(p_phone, '')), '');
  IF v IS NULL THEN
    RETURN NULL;
  END IF;
  v := regexp_replace(v, '[\s\-()]', '', 'g');
  IF v !~ '^\+?[0-9]{8,15}$' THEN
    RETURN NULL;
  END IF;
  IF left(v, 1) <> '+' THEN
    v := '+' || v;
  END IF;
  RETURN v;
END;
$$;

-- Unique registered / trade customer for till contacts (no auto-insert).
CREATE OR REPLACE FUNCTION public.resolve_customer_for_receipt_contacts(
  p_email TEXT DEFAULT NULL,
  p_whatsapp_e164 TEXT DEFAULT NULL,
  p_phone_e164 TEXT DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_email TEXT := public._normalize_receipt_email(p_email);
  v_wa TEXT := public._normalize_e164(p_whatsapp_e164);
  v_phone TEXT := public._normalize_e164(p_phone_e164);
  v_ids UUID[];
BEGIN
  IF v_email IS NULL AND v_wa IS NULL AND v_phone IS NULL THEN
    RETURN NULL;
  END IF;

  SELECT array_agg(DISTINCT c.id)
  INTO v_ids
  FROM public.customers c
  LEFT JOIN public.price_lists pl ON pl.id = c.price_list_id
  WHERE (
      c.profile_id IS NOT NULL
      OR COALESCE(pl.code, 'RETAIL') <> 'RETAIL'
    )
    AND (
      (v_email IS NOT NULL AND lower(trim(COALESCE(c.email, ''))) = v_email)
      OR (v_phone IS NOT NULL AND (
        public._normalize_e164(c.phone_e164) = v_phone
        OR public._normalize_e164(c.whatsapp_e164) = v_phone
      ))
      OR (v_wa IS NOT NULL AND (
        public._normalize_e164(c.whatsapp_e164) = v_wa
        OR public._normalize_e164(c.phone_e164) = v_wa
      ))
    );

  IF v_ids IS NULL OR cardinality(v_ids) <> 1 THEN
    RETURN NULL;
  END IF;
  RETURN v_ids[1];
END;
$$;

REVOKE ALL ON FUNCTION public.resolve_customer_for_receipt_contacts(TEXT, TEXT, TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.resolve_customer_for_receipt_contacts(TEXT, TEXT, TEXT)
  TO authenticated, service_role;

-- ---------------------------------------------------------------------------
-- Companion session RPCs (optional — never required for cart line-add / checkout)
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public._expire_stale_pos_scan_sessions(p_cart_id UUID DEFAULT NULL)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  UPDATE public.pos_scan_sessions
  SET status = 'expired', updated_at = now()
  WHERE status IN ('open', 'claimed')
    AND expires_at <= now()
    AND (p_cart_id IS NULL OR cart_id = p_cart_id);
END;
$$;

CREATE OR REPLACE FUNCTION public.create_pos_scan_session(
  p_cart_id UUID,
  p_ttl INTERVAL DEFAULT interval '10 minutes'
)
RETURNS TABLE (session_id UUID, pairing_code TEXT, expires_at TIMESTAMPTZ)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_cart public.pos_carts%ROWTYPE;
  v_code TEXT;
  v_id UUID;
  v_exp TIMESTAMPTZ;
  v_tries INT := 0;
BEGIN
  PERFORM public._require_sales_staff();
  IF auth.uid() IS NULL THEN
    RAISE EXCEPTION 'authenticated user required';
  END IF;

  PERFORM public._expire_stale_pos_scan_sessions(p_cart_id);

  SELECT * INTO v_cart FROM public.pos_carts WHERE id = p_cart_id FOR UPDATE;
  IF NOT FOUND OR v_cart.status <> 'open' THEN
    RAISE EXCEPTION 'open cart not found';
  END IF;
  IF COALESCE(v_cart.channel, 'pos') <> 'pos' THEN
    RAISE EXCEPTION 'scan sessions only for POS carts';
  END IF;

  -- Replace any active companion session for this cart
  UPDATE public.pos_scan_sessions
  SET status = 'revoked', revoked_at = now(), updated_at = now()
  WHERE cart_id = p_cart_id AND status IN ('open', 'claimed');

  v_exp := now() + COALESCE(p_ttl, interval '10 minutes');
  IF v_exp <= now() OR v_exp > now() + interval '15 minutes' THEN
    RAISE EXCEPTION 'pairing TTL must be between 0 and 15 minutes';
  END IF;

  LOOP
    v_tries := v_tries + 1;
    IF v_tries > 20 THEN
      RAISE EXCEPTION 'could not allocate pairing code';
    END IF;
    v_code := lpad((floor(random() * 1000000))::integer::text, 6, '0');
    BEGIN
      INSERT INTO public.pos_scan_sessions (
        cart_id, pairing_code, owner_user_id, status, expires_at
      )
      VALUES (
        p_cart_id, v_code, auth.uid(), 'open', v_exp
      )
      RETURNING id INTO v_id;
      EXIT;
    EXCEPTION
      WHEN unique_violation THEN
        CONTINUE;
    END;
  END LOOP;

  session_id := v_id;
  pairing_code := v_code;
  expires_at := v_exp;
  RETURN NEXT;
END;
$$;

CREATE OR REPLACE FUNCTION public.claim_pos_scan_session(p_pairing_code TEXT)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_code TEXT := trim(COALESCE(p_pairing_code, ''));
  v_sess public.pos_scan_sessions%ROWTYPE;
BEGIN
  PERFORM public._require_sales_staff();
  IF auth.uid() IS NULL THEN
    RAISE EXCEPTION 'authenticated user required';
  END IF;
  IF v_code !~ '^[0-9]{6}$' THEN
    RAISE EXCEPTION 'invalid pairing code';
  END IF;

  PERFORM public._expire_stale_pos_scan_sessions(NULL);

  SELECT * INTO v_sess
  FROM public.pos_scan_sessions
  WHERE pairing_code = v_code AND status = 'open'
  FOR UPDATE;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'pairing code not found or not open';
  END IF;
  IF v_sess.expires_at <= now() THEN
    UPDATE public.pos_scan_sessions
    SET status = 'expired', updated_at = now()
    WHERE id = v_sess.id;
    RAISE EXCEPTION 'pairing code expired';
  END IF;

  -- Same-user claim only (no cross-rep)
  IF v_sess.owner_user_id IS DISTINCT FROM auth.uid() THEN
    RAISE EXCEPTION 'cross-rep claim not allowed; claim with the cart owner account';
  END IF;

  UPDATE public.pos_scan_sessions
  SET
    status = 'claimed',
    scanner_user_id = auth.uid(),
    claimed_at = now(),
    updated_at = now()
  WHERE id = v_sess.id;

  RETURN v_sess.id;
END;
$$;

CREATE OR REPLACE FUNCTION public.revoke_pos_scan_session(p_session_id UUID)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_sess public.pos_scan_sessions%ROWTYPE;
BEGIN
  PERFORM public._require_sales_staff();

  SELECT * INTO v_sess
  FROM public.pos_scan_sessions
  WHERE id = p_session_id
  FOR UPDATE;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'scan session not found';
  END IF;

  IF v_sess.owner_user_id IS DISTINCT FROM auth.uid()
     AND NOT public.has_staff_role(ARRAY['admin']::public.staff_role[]) THEN
    RAISE EXCEPTION 'only session owner or admin may revoke';
  END IF;

  IF v_sess.status IN ('revoked', 'expired') THEN
    RETURN v_sess.id;
  END IF;

  UPDATE public.pos_scan_sessions
  SET status = 'revoked', revoked_at = now(), updated_at = now()
  WHERE id = p_session_id;

  RETURN p_session_id;
END;
$$;

REVOKE ALL ON FUNCTION public.create_pos_scan_session(UUID, INTERVAL) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.claim_pos_scan_session(TEXT) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.revoke_pos_scan_session(UUID) FROM PUBLIC;

GRANT EXECUTE ON FUNCTION public.create_pos_scan_session(UUID, INTERVAL)
  TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.claim_pos_scan_session(TEXT)
  TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.revoke_pos_scan_session(UUID)
  TO authenticated, service_role;

-- ---------------------------------------------------------------------------
-- add_cart_line_from_qr: staff standalone OR claimed companion scanner.
-- Scan session is NEVER required for staff on an open cart.
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.add_cart_line_from_qr(
  p_cart_id UUID,
  p_qr_payload TEXT,
  p_qty NUMERIC DEFAULT 1
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_oem TEXT;
  v_item UUID;
  v_uom UUID;
  v_m TEXT[];
  v_staff BOOLEAN;
  v_companion BOOLEAN;
BEGIN
  PERFORM public._expire_stale_pos_scan_sessions(p_cart_id);

  v_staff := (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'sales', 'warehouse']::public.staff_role[])
  );
  v_companion := EXISTS (
    SELECT 1
    FROM public.pos_scan_sessions s
    WHERE s.cart_id = p_cart_id
      AND s.status = 'claimed'
      AND s.scanner_user_id = auth.uid()
      AND s.expires_at > now()
  );

  -- Standalone: sales/warehouse/admin — no session required.
  -- Companion: claimed scanner may also add via this RPC.
  IF NOT v_staff AND NOT v_companion THEN
    RAISE EXCEPTION 'sales staff or claimed scan companion required';
  END IF;

  v_m := regexp_match(
    trim(p_qr_payload),
    '^gtr://part/([^?]+)\?batch=([^&]+)&valuation=(FIFO|AVG)$'
  );
  IF v_m IS NULL THEN
    RAISE EXCEPTION 'invalid inventory QR payload';
  END IF;
  v_oem := v_m[1];

  SELECT id, base_uom_id INTO v_item, v_uom
  FROM public.stock_items WHERE oem_part_number = v_oem;
  IF v_item IS NULL THEN
    RAISE EXCEPTION 'unknown part %', v_oem;
  END IF;

  -- add_cart_line uses _require_cart_mutate (staff). Companion who is staff is fine;
  -- if only companion flag (should not happen — claim requires staff), temporarily
  -- allow via service path by calling with storefront flag off — still need staff.
  IF NOT v_staff THEN
    RAISE EXCEPTION 'companion claim requires sales staff account';
  END IF;

  RETURN public.add_cart_line(p_cart_id, v_item, v_uom, p_qty);
END;
$$;

-- ---------------------------------------------------------------------------
-- checkout_pos_cart: optional receipt contacts + bind; same for standalone/paired
-- ---------------------------------------------------------------------------
DROP FUNCTION IF EXISTS public.checkout_pos_cart(UUID);

CREATE OR REPLACE FUNCTION public.checkout_pos_cart(
  p_cart_id UUID,
  p_receipt_email TEXT DEFAULT NULL,
  p_receipt_whatsapp_e164 TEXT DEFAULT NULL,
  p_receipt_phone_e164 TEXT DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_cart public.pos_carts%ROWTYPE;
  v_cust public.customers%ROWTYPE;
  v_inv UUID;
  v_line RECORD;
  v_subtotal NUMERIC := 0;
  v_total NUMERIC := 0;
  v_journal UUID;
  v_lines JSONB := '[]'::jsonb;
  v_rev NUMERIC := 0;
  v_core NUMERIC := 0;
  v_cogs NUMERIC := 0;
  v_unit_cost NUMERIC;
  v_large NUMERIC := 1000;
  v_fulfill public.fulfillment_mode;
  v_qty_fulfilled NUMERIC;
  v_inv_line_id UUID;
  v_parent_inv UUID;
  v_cart_to_inv JSONB := '{}'::jsonb;
  v_issues BOOLEAN;
  v_email TEXT;
  v_wa TEXT;
  v_phone TEXT;
  v_bound UUID;
BEGIN
  PERFORM public._require_cart_mutate(p_cart_id);
  SELECT * INTO v_cart FROM public.pos_carts WHERE id = p_cart_id FOR UPDATE;
  IF NOT FOUND OR v_cart.status <> 'open' THEN
    RAISE EXCEPTION 'open cart not found';
  END IF;

  v_fulfill := COALESCE(v_cart.fulfillment_mode, 'immediate');

  v_email := public._normalize_receipt_email(p_receipt_email);
  v_wa := public._normalize_e164(p_receipt_whatsapp_e164);
  v_phone := public._normalize_e164(p_receipt_phone_e164);

  -- Best-effort bind when cart has no customer yet (walk-in → registered/trade).
  IF v_cart.customer_id IS NULL THEN
    v_bound := public.resolve_customer_for_receipt_contacts(v_email, v_wa, v_phone);
    IF v_bound IS NOT NULL THEN
      UPDATE public.pos_carts
      SET customer_id = v_bound, updated_at = now()
      WHERE id = p_cart_id;
      v_cart.customer_id := v_bound;
    END IF;
  END IF;

  IF v_cart.customer_id IS NOT NULL THEN
    SELECT * INTO v_cust FROM public.customers WHERE id = v_cart.customer_id;
  END IF;

  -- Invoice contacts: till capture overrides, else customer profile.
  v_email := COALESCE(v_email, public._normalize_receipt_email(v_cust.email));
  v_wa := COALESCE(v_wa, public._normalize_e164(v_cust.whatsapp_e164));
  v_phone := COALESCE(
    v_phone,
    public._normalize_e164(v_cust.phone_e164),
    v_wa
  );
  v_wa := COALESCE(v_wa, v_phone);

  IF v_cart.customer_id IS NOT NULL THEN
    IF v_cust.credit_hold THEN
      INSERT INTO public.sales_invoices (
        doc_type, status, customer_id, warehouse_id, currency,
        exchange_rate_applied, cart_id, fulfillment_mode,
        customer_phone_e164, customer_email, customer_whatsapp_e164
      )
      VALUES (
        'invoice', 'on_hold', v_cart.customer_id, v_cart.warehouse_id, v_cart.currency,
        v_cart.exchange_rate_applied, p_cart_id, v_fulfill,
        v_phone, v_email, v_wa
      )
      RETURNING id INTO v_inv;

      PERFORM public.emit_domain_event(
        'order_on_hold',
        'invoice:hold:' || v_inv::text,
        jsonb_build_object('invoice_id', v_inv, 'reason', 'credit_hold')
      );
      RETURN v_inv;
    END IF;
  END IF;

  SELECT COALESCE(SUM(line_total), 0) INTO v_subtotal
  FROM public.pos_cart_lines WHERE cart_id = p_cart_id;
  v_total := v_subtotal;

  IF v_cart.customer_id IS NOT NULL
     AND v_cust.credit_limit > 0
     AND (v_cust.open_balance + v_total) > v_cust.credit_limit THEN
    INSERT INTO public.sales_invoices (
      doc_type, status, customer_id, warehouse_id, currency,
      exchange_rate_applied, cart_id, subtotal, total, fulfillment_mode,
      customer_phone_e164, customer_email, customer_whatsapp_e164
    )
    VALUES (
      'invoice', 'on_hold', v_cart.customer_id, v_cart.warehouse_id, v_cart.currency,
      v_cart.exchange_rate_applied, p_cart_id, v_subtotal, v_total, v_fulfill,
      v_phone, v_email, v_wa
    )
    RETURNING id INTO v_inv;

    PERFORM public.emit_domain_event(
      'order_on_hold',
      'invoice:limit:' || v_inv::text,
      jsonb_build_object('invoice_id', v_inv, 'reason', 'credit_limit')
    );
    RETURN v_inv;
  END IF;

  INSERT INTO public.sales_invoices (
    doc_type, status, document_number, customer_id, warehouse_id, currency,
    exchange_rate_applied, subtotal, total, cart_id, fulfillment_mode,
    customer_phone_e164, customer_email, customer_whatsapp_e164,
    posted_by, posted_at
  )
  VALUES (
    'invoice', 'draft', public.next_series_value('SINV-'),
    v_cart.customer_id, v_cart.warehouse_id, v_cart.currency,
    v_cart.exchange_rate_applied, v_subtotal, v_total, p_cart_id, v_fulfill,
    v_phone, v_email, v_wa,
    auth.uid(), now()
  )
  RETURNING id INTO v_inv;

  PERFORM public.emit_domain_event(
    'order_received',
    'invoice:received:' || v_inv::text,
    jsonb_build_object(
      'invoice_id', v_inv,
      'fulfillment_mode', v_fulfill::text
    )
  );

  FOR v_line IN
    SELECT * FROM public.pos_cart_lines
    WHERE cart_id = p_cart_id
    ORDER BY created_at
  LOOP
    v_issues := COALESCE(v_line.issues_stock, true);

    IF v_line.is_core_charge THEN
      v_qty_fulfilled := 0;
    ELSIF NOT v_issues THEN
      v_qty_fulfilled := v_line.qty_base;
    ELSIF v_fulfill = 'immediate' THEN
      v_qty_fulfilled := v_line.qty_base;
    ELSE
      v_qty_fulfilled := 0;
    END IF;

    v_parent_inv := NULL;
    IF v_line.parent_line_id IS NOT NULL THEN
      v_parent_inv := (v_cart_to_inv ->> v_line.parent_line_id::text)::uuid;
    END IF;

    INSERT INTO public.sales_invoice_lines (
      invoice_id, stock_item_id, parent_line_id, is_core_charge, uom_id,
      qty, qty_base, unit_price, line_total, qty_fulfilled,
      issues_stock, kit_id, kit_line_kind
    )
    VALUES (
      v_inv, v_line.stock_item_id, v_parent_inv, v_line.is_core_charge, v_line.uom_id,
      v_line.qty, v_line.qty_base, v_line.unit_price, v_line.line_total, v_qty_fulfilled,
      v_issues, v_line.kit_id, v_line.kit_line_kind
    )
    RETURNING id INTO v_inv_line_id;

    v_cart_to_inv := v_cart_to_inv || jsonb_build_object(v_line.id::text, v_inv_line_id);

    IF v_line.is_core_charge THEN
      v_core := v_core + v_line.line_total;
    ELSE
      v_rev := v_rev + v_line.line_total;

      IF v_issues AND v_fulfill = 'immediate' THEN
        SELECT unit_cost INTO v_unit_cost
        FROM public.stock_levels
        WHERE stock_item_id = v_line.stock_item_id AND warehouse_id = v_cart.warehouse_id;

        PERFORM public._consume_fifo_batches(
          v_line.stock_item_id, v_cart.warehouse_id, v_line.qty_base
        );
        PERFORM public._adjust_stock_level(
          v_line.stock_item_id, v_cart.warehouse_id, -v_line.qty_base,
          'FIFO', COALESCE(v_unit_cost, 0), v_cart.currency
        );
        v_cogs := v_cogs + COALESCE(v_unit_cost, 0) * v_line.qty_base;
      END IF;
    END IF;
  END LOOP;

  v_lines := jsonb_build_array(
    jsonb_build_object(
      'account_code', CASE WHEN v_cart.customer_id IS NULL THEN '1100' ELSE '1200' END,
      'debit', v_total, 'credit', 0, 'currency', v_cart.currency
    ),
    jsonb_build_object(
      'account_code', '4100',
      'debit', 0, 'credit', v_rev, 'currency', v_cart.currency
    )
  );
  IF v_core > 0 THEN
    v_lines := v_lines || jsonb_build_array(
      jsonb_build_object(
        'account_code', '4200',
        'debit', 0, 'credit', v_core, 'currency', v_cart.currency
      )
    );
  END IF;
  IF v_cogs > 0 THEN
    v_lines := v_lines || jsonb_build_array(
      jsonb_build_object(
        'account_code', '5100',
        'debit', round(v_cogs, 2), 'credit', 0, 'currency', v_cart.currency
      ),
      jsonb_build_object(
        'account_code', '1300',
        'debit', 0, 'credit', round(v_cogs, 2), 'currency', v_cart.currency
      )
    );
  END IF;

  v_journal := public.post_journal_entry(
    CURRENT_DATE,
    format('Sale %s', (SELECT document_number FROM public.sales_invoices WHERE id = v_inv)),
    v_cart.currency,
    v_cart.exchange_rate_applied,
    v_lines
  );

  UPDATE public.sales_invoices
  SET status = 'posted', journal_entry_id = v_journal, posted_at = now()
  WHERE id = v_inv;

  UPDATE public.pos_carts SET status = 'checked_out', updated_at = now() WHERE id = p_cart_id;

  -- Close any companion sessions on checkout
  UPDATE public.pos_scan_sessions
  SET status = 'revoked', revoked_at = now(), updated_at = now()
  WHERE cart_id = p_cart_id AND status IN ('open', 'claimed');

  IF v_cart.customer_id IS NOT NULL THEN
    UPDATE public.customers
    SET open_balance = open_balance + v_total, updated_at = now()
    WHERE id = v_cart.customer_id;
  END IF;

  -- Enqueue only — sale never blocks on channel send (Phase 13 worker).
  PERFORM public.enqueue_customer_receipts(v_inv);

  IF v_total >= v_large THEN
    PERFORM public.emit_domain_event(
      'large_order',
      'invoice:large:' || v_inv::text,
      jsonb_build_object('invoice_id', v_inv, 'total', v_total)
    );
  END IF;

  RETURN v_inv;
END;
$$;

REVOKE ALL ON FUNCTION public.checkout_pos_cart(UUID, TEXT, TEXT, TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.checkout_pos_cart(UUID, TEXT, TEXT, TEXT)
  TO authenticated, service_role;

REVOKE ALL ON FUNCTION public.add_cart_line_from_qr(UUID, TEXT, NUMERIC) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.add_cart_line_from_qr(UUID, TEXT, NUMERIC)
  TO authenticated, service_role;
