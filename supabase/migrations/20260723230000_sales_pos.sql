-- Phase 5: customers, price lists, cart/invoices, returns, customer receipt outbox
-- Exclusions: no ContiPay capture, no ZIMRA/fiscal, no HTML5 QR

CREATE TYPE public.sales_doc_status AS ENUM ('draft', 'posted', 'cancelled', 'on_hold');
CREATE TYPE public.sales_doc_type AS ENUM ('invoice', 'credit_note');
CREATE TYPE public.receipt_channel AS ENUM ('sms', 'email', 'whatsapp');
CREATE TYPE public.receipt_outbox_status AS ENUM ('pending', 'rendering', 'sending', 'sent', 'failed', 'cancelled');

-- ---------------------------------------------------------------------------
-- Customers + commercial
-- ---------------------------------------------------------------------------
CREATE TABLE public.customers (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  profile_id UUID REFERENCES public.profiles (id) ON DELETE SET NULL,
  display_name TEXT NOT NULL,
  phone_e164 TEXT,
  email TEXT,
  whatsapp_e164 TEXT,
  price_list_id UUID, -- FK added after price_lists
  credit_limit NUMERIC(18, 2) NOT NULL DEFAULT 0 CHECK (credit_limit >= 0),
  credit_hold BOOLEAN NOT NULL DEFAULT false,
  open_balance NUMERIC(18, 2) NOT NULL DEFAULT 0,
  sms_receipts BOOLEAN NOT NULL DEFAULT true,
  email_receipts BOOLEAN NOT NULL DEFAULT true,
  whatsapp_receipts BOOLEAN NOT NULL DEFAULT true,
  currency public.currency_code NOT NULL DEFAULT 'USD',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE public.price_lists (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  code VARCHAR(32) NOT NULL UNIQUE,
  name TEXT NOT NULL,
  currency public.currency_code NOT NULL DEFAULT 'USD',
  is_default BOOLEAN NOT NULL DEFAULT false,
  is_active BOOLEAN NOT NULL DEFAULT true,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE public.price_list_items (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  price_list_id UUID NOT NULL REFERENCES public.price_lists (id) ON DELETE CASCADE,
  stock_item_id UUID NOT NULL REFERENCES public.stock_items (id) ON DELETE CASCADE,
  unit_price NUMERIC(18, 4) NOT NULL CHECK (unit_price >= 0),
  core_charge NUMERIC(18, 4) NOT NULL DEFAULT 0 CHECK (core_charge >= 0),
  UNIQUE (price_list_id, stock_item_id)
);

CREATE TABLE public.customer_price_overrides (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  customer_id UUID NOT NULL REFERENCES public.customers (id) ON DELETE CASCADE,
  stock_item_id UUID NOT NULL REFERENCES public.stock_items (id) ON DELETE CASCADE,
  unit_price NUMERIC(18, 4) NOT NULL CHECK (unit_price >= 0),
  core_charge NUMERIC(18, 4),
  UNIQUE (customer_id, stock_item_id)
);

ALTER TABLE public.customers
  ADD CONSTRAINT customers_price_list_fk
  FOREIGN KEY (price_list_id) REFERENCES public.price_lists (id);

INSERT INTO public.price_lists (code, name, is_default) VALUES
  ('RETAIL', 'Retail', true),
  ('B2B', 'B2B', false),
  ('FLEET', 'Fleet', false)
ON CONFLICT (code) DO NOTHING;

INSERT INTO public.naming_series (prefix, description, pad_length) VALUES
  ('SINV-', 'Sales invoice', 5),
  ('CN-', 'Credit note', 5),
  ('CART-', 'POS cart', 5)
ON CONFLICT (prefix) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Cart (POS working document)
-- ---------------------------------------------------------------------------
CREATE TABLE public.pos_carts (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  document_number TEXT,
  customer_id UUID REFERENCES public.customers (id),
  warehouse_id UUID NOT NULL REFERENCES public.warehouses (id),
  currency public.currency_code NOT NULL DEFAULT 'USD',
  exchange_rate_applied NUMERIC(18, 8) NOT NULL DEFAULT 1,
  status TEXT NOT NULL DEFAULT 'open' CHECK (status IN ('open', 'checked_out', 'abandoned')),
  created_by UUID REFERENCES auth.users (id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE public.pos_cart_lines (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  cart_id UUID NOT NULL REFERENCES public.pos_carts (id) ON DELETE CASCADE,
  stock_item_id UUID NOT NULL REFERENCES public.stock_items (id),
  parent_line_id UUID REFERENCES public.pos_cart_lines (id) ON DELETE CASCADE,
  is_core_charge BOOLEAN NOT NULL DEFAULT false,
  uom_id UUID NOT NULL REFERENCES public.uoms (id),
  qty NUMERIC(18, 3) NOT NULL CHECK (qty > 0),
  qty_base NUMERIC(18, 3) NOT NULL CHECK (qty_base > 0),
  unit_price NUMERIC(18, 4) NOT NULL CHECK (unit_price >= 0),
  line_total NUMERIC(18, 2) NOT NULL CHECK (line_total >= 0),
  qty_fulfilled NUMERIC(18, 3) NOT NULL DEFAULT 0 CHECK (qty_fulfilled >= 0),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX pos_cart_lines_cart_idx ON public.pos_cart_lines (cart_id);

-- ---------------------------------------------------------------------------
-- Sales invoices / credit notes
-- ---------------------------------------------------------------------------
CREATE TABLE public.sales_invoices (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  doc_type public.sales_doc_type NOT NULL DEFAULT 'invoice',
  status public.sales_doc_status NOT NULL DEFAULT 'draft',
  document_number TEXT,
  customer_id UUID REFERENCES public.customers (id),
  warehouse_id UUID NOT NULL REFERENCES public.warehouses (id),
  currency public.currency_code NOT NULL DEFAULT 'USD',
  exchange_rate_applied NUMERIC(18, 8) NOT NULL DEFAULT 1,
  subtotal NUMERIC(18, 2) NOT NULL DEFAULT 0,
  total NUMERIC(18, 2) NOT NULL DEFAULT 0,
  amount_paid NUMERIC(18, 2) NOT NULL DEFAULT 0,
  return_against_id UUID REFERENCES public.sales_invoices (id),
  journal_entry_id UUID REFERENCES public.journal_entries (id),
  cart_id UUID REFERENCES public.pos_carts (id),
  customer_phone_e164 TEXT,
  customer_email TEXT,
  customer_whatsapp_e164 TEXT,
  posted_by UUID REFERENCES auth.users (id),
  posted_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT sales_cn_requires_source CHECK (
    doc_type = 'invoice' OR return_against_id IS NOT NULL
  )
);

CREATE TABLE public.sales_invoice_lines (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  invoice_id UUID NOT NULL REFERENCES public.sales_invoices (id) ON DELETE CASCADE,
  stock_item_id UUID NOT NULL REFERENCES public.stock_items (id),
  parent_line_id UUID REFERENCES public.sales_invoice_lines (id) ON DELETE CASCADE,
  is_core_charge BOOLEAN NOT NULL DEFAULT false,
  uom_id UUID NOT NULL REFERENCES public.uoms (id),
  qty NUMERIC(18, 3) NOT NULL CHECK (qty > 0),
  qty_base NUMERIC(18, 3) NOT NULL CHECK (qty_base > 0),
  unit_price NUMERIC(18, 4) NOT NULL CHECK (unit_price >= 0),
  line_total NUMERIC(18, 2) NOT NULL CHECK (line_total >= 0),
  qty_fulfilled NUMERIC(18, 3) NOT NULL DEFAULT 0,
  stock_batch_id UUID REFERENCES public.stock_batches (id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX sales_invoice_lines_inv_idx ON public.sales_invoice_lines (invoice_id);

-- ---------------------------------------------------------------------------
-- Customer receipt outbox (send in Phase 13)
-- ---------------------------------------------------------------------------
CREATE TABLE public.customer_receipt_outbox (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  document_type TEXT NOT NULL CHECK (document_type IN ('sales_invoice', 'credit_note')),
  document_id UUID NOT NULL REFERENCES public.sales_invoices (id) ON DELETE CASCADE,
  channel public.receipt_channel NOT NULL,
  recipient TEXT NOT NULL,
  summary_body TEXT NOT NULL,
  pdf_storage_path TEXT,
  download_url TEXT,
  status public.receipt_outbox_status NOT NULL DEFAULT 'pending',
  attempt_count INT NOT NULL DEFAULT 0,
  last_error TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  sent_at TIMESTAMPTZ,
  UNIQUE (document_id, channel)
);

-- ---------------------------------------------------------------------------
-- Pricing resolve
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.resolve_item_price(
  p_customer_id UUID,
  p_stock_item_id UUID
)
RETURNS TABLE (unit_price NUMERIC, core_charge NUMERIC, currency public.currency_code)
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_list UUID;
  v_currency public.currency_code := 'USD';
BEGIN
  IF p_customer_id IS NOT NULL THEN
    SELECT o.unit_price, COALESCE(o.core_charge, 0), c.currency
    INTO unit_price, core_charge, currency
    FROM public.customer_price_overrides o
    JOIN public.customers c ON c.id = o.customer_id
    WHERE o.customer_id = p_customer_id AND o.stock_item_id = p_stock_item_id;
    IF FOUND THEN
      RETURN NEXT;
      RETURN;
    END IF;

    SELECT price_list_id, currency INTO v_list, v_currency
    FROM public.customers WHERE id = p_customer_id;
  END IF;

  IF v_list IS NULL THEN
    SELECT id, currency INTO v_list, v_currency
    FROM public.price_lists WHERE is_default AND is_active LIMIT 1;
  END IF;

  SELECT pli.unit_price, pli.core_charge, pl.currency
  INTO unit_price, core_charge, currency
  FROM public.price_list_items pli
  JOIN public.price_lists pl ON pl.id = pli.price_list_id
  WHERE pli.price_list_id = v_list AND pli.stock_item_id = p_stock_item_id;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'no price for stock item % on price list', p_stock_item_id;
  END IF;
  RETURN NEXT;
END;
$$;

CREATE OR REPLACE FUNCTION public._require_sales_staff()
RETURNS void
LANGUAGE plpgsql
STABLE
AS $$
BEGIN
  IF NOT (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'sales', 'warehouse']::public.staff_role[])
  ) THEN
    RAISE EXCEPTION 'sales, warehouse, or admin role required';
  END IF;
END;
$$;

-- ---------------------------------------------------------------------------
-- Cart helpers
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.create_pos_cart(
  p_warehouse_id UUID,
  p_customer_id UUID DEFAULT NULL,
  p_currency public.currency_code DEFAULT 'USD'
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_id UUID;
BEGIN
  PERFORM public._require_sales_staff();
  INSERT INTO public.pos_carts (
    document_number, customer_id, warehouse_id, currency, created_by
  )
  VALUES (
    public.next_series_value('CART-'),
    p_customer_id,
    p_warehouse_id,
    p_currency,
    auth.uid()
  )
  RETURNING id INTO v_id;
  RETURN v_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.add_cart_line(
  p_cart_id UUID,
  p_stock_item_id UUID,
  p_uom_id UUID,
  p_qty NUMERIC
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_cart public.pos_carts%ROWTYPE;
  v_price NUMERIC;
  v_core NUMERIC;
  v_currency public.currency_code;
  v_qty_base NUMERIC;
  v_part_id UUID;
  v_core_id UUID;
BEGIN
  PERFORM public._require_sales_staff();
  SELECT * INTO v_cart FROM public.pos_carts WHERE id = p_cart_id FOR UPDATE;
  IF NOT FOUND OR v_cart.status <> 'open' THEN
    RAISE EXCEPTION 'open cart not found';
  END IF;

  SELECT r.unit_price, r.core_charge, r.currency
  INTO v_price, v_core, v_currency
  FROM public.resolve_item_price(v_cart.customer_id, p_stock_item_id) r;

  v_qty_base := public.convert_to_base_uom(p_stock_item_id, p_uom_id, p_qty);

  INSERT INTO public.pos_cart_lines (
    cart_id, stock_item_id, uom_id, qty, qty_base, unit_price, line_total, is_core_charge
  )
  VALUES (
    p_cart_id, p_stock_item_id, p_uom_id, p_qty, v_qty_base, v_price,
    round(v_price * p_qty, 2), false
  )
  RETURNING id INTO v_part_id;

  IF v_core IS NOT NULL AND v_core > 0 THEN
    INSERT INTO public.pos_cart_lines (
      cart_id, stock_item_id, parent_line_id, uom_id, qty, qty_base,
      unit_price, line_total, is_core_charge
    )
    VALUES (
      p_cart_id, p_stock_item_id, v_part_id, p_uom_id, p_qty, v_qty_base,
      v_core, round(v_core * p_qty, 2), true
    )
    RETURNING id INTO v_core_id;
  END IF;

  UPDATE public.pos_carts SET updated_at = now() WHERE id = p_cart_id;
  RETURN v_part_id;
END;
$$;

-- Resolve QR payload → add to cart (native bridge supplies decoded string)
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
BEGIN
  PERFORM public._require_sales_staff();
  v_m := regexp_match(trim(p_qr_payload), '^gtr://part/([^?]+)\?batch=([^&]+)&valuation=(FIFO|AVG)$');
  IF v_m IS NULL THEN
    RAISE EXCEPTION 'invalid inventory QR payload';
  END IF;
  v_oem := v_m[1];

  SELECT id, base_uom_id INTO v_item, v_uom
  FROM public.stock_items WHERE oem_part_number = v_oem;
  IF v_item IS NULL THEN
    RAISE EXCEPTION 'unknown part %', v_oem;
  END IF;

  RETURN public.add_cart_line(p_cart_id, v_item, v_uom, p_qty);
END;
$$;

-- ---------------------------------------------------------------------------
-- Customer receipt enqueue
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.enqueue_customer_receipts(p_invoice_id UUID)
RETURNS INT
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_inv public.sales_invoices%ROWTYPE;
  v_cust public.customers%ROWTYPE;
  v_summary TEXT;
  v_doc_label TEXT;
  v_count INT := 0;
  v_phone TEXT;
  v_email TEXT;
  v_wa TEXT;
BEGIN
  SELECT * INTO v_inv FROM public.sales_invoices WHERE id = p_invoice_id;
  IF NOT FOUND OR v_inv.status <> 'posted' THEN
    RAISE EXCEPTION 'posted invoice/credit note required';
  END IF;

  IF v_inv.customer_id IS NOT NULL THEN
    SELECT * INTO v_cust FROM public.customers WHERE id = v_inv.customer_id;
  END IF;

  v_doc_label := COALESCE(v_inv.document_number, v_inv.id::text);
  v_summary := format(
    'GTR Auto %s %s Total %s %s. Thank you.',
    CASE WHEN v_inv.doc_type = 'credit_note' THEN 'Credit' ELSE 'Sale' END,
    v_doc_label,
    v_inv.total::text,
    v_inv.currency::text
  );
  -- PDF link appended by Phase 13 worker when download_url is set:
  -- summary_body || E'\n' || download_url

  v_phone := COALESCE(v_inv.customer_phone_e164, v_cust.phone_e164);
  v_email := COALESCE(v_inv.customer_email, v_cust.email);
  v_wa := COALESCE(v_inv.customer_whatsapp_e164, v_cust.whatsapp_e164, v_phone);

  IF v_phone IS NOT NULL AND COALESCE(v_cust.sms_receipts, true) THEN
    INSERT INTO public.customer_receipt_outbox (
      document_type, document_id, channel, recipient, summary_body
    )
    VALUES (
      CASE WHEN v_inv.doc_type = 'credit_note' THEN 'credit_note' ELSE 'sales_invoice' END,
      p_invoice_id,
      'sms',
      v_phone,
      v_summary || E'\n[PDF link pending]'
    )
    ON CONFLICT (document_id, channel) DO NOTHING;
    v_count := v_count + 1;
  END IF;

  IF v_email IS NOT NULL AND COALESCE(v_cust.email_receipts, true) THEN
    INSERT INTO public.customer_receipt_outbox (
      document_type, document_id, channel, recipient, summary_body
    )
    VALUES (
      CASE WHEN v_inv.doc_type = 'credit_note' THEN 'credit_note' ELSE 'sales_invoice' END,
      p_invoice_id,
      'email',
      v_email,
      v_summary
    )
    ON CONFLICT (document_id, channel) DO NOTHING;
    v_count := v_count + 1;
  END IF;

  IF v_wa IS NOT NULL AND COALESCE(v_cust.whatsapp_receipts, true) THEN
    INSERT INTO public.customer_receipt_outbox (
      document_type, document_id, channel, recipient, summary_body
    )
    VALUES (
      CASE WHEN v_inv.doc_type = 'credit_note' THEN 'credit_note' ELSE 'sales_invoice' END,
      p_invoice_id,
      'whatsapp',
      v_wa,
      v_summary || E'\n[PDF link pending]'
    )
    ON CONFLICT (document_id, channel) DO NOTHING;
    v_count := v_count + 1;
  END IF;

  IF v_inv.doc_type = 'invoice' THEN
    PERFORM public.emit_domain_event(
      'order_completed',
      'receipt:' || p_invoice_id::text,
      jsonb_build_object('invoice_id', p_invoice_id),
      auth.uid(),
      NULL
    );
  END IF;

  RETURN v_count;
END;
$$;

-- ---------------------------------------------------------------------------
-- Checkout cart → posted invoice + stock + journals
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.checkout_pos_cart(p_cart_id UUID)
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
BEGIN
  PERFORM public._require_sales_staff();
  SELECT * INTO v_cart FROM public.pos_carts WHERE id = p_cart_id FOR UPDATE;
  IF NOT FOUND OR v_cart.status <> 'open' THEN
    RAISE EXCEPTION 'open cart not found';
  END IF;

  IF v_cart.customer_id IS NOT NULL THEN
    SELECT * INTO v_cust FROM public.customers WHERE id = v_cart.customer_id;
    IF v_cust.credit_hold THEN
      INSERT INTO public.sales_invoices (
        doc_type, status, customer_id, warehouse_id, currency,
        exchange_rate_applied, cart_id, customer_phone_e164, customer_email,
        customer_whatsapp_e164
      )
      VALUES (
        'invoice', 'on_hold', v_cart.customer_id, v_cart.warehouse_id, v_cart.currency,
        v_cart.exchange_rate_applied, p_cart_id, v_cust.phone_e164, v_cust.email,
        v_cust.whatsapp_e164
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

  SELECT COALESCE(SUM(line_total), 0) INTO v_total
  FROM public.pos_cart_lines WHERE cart_id = p_cart_id AND NOT is_core_charge;
  SELECT COALESCE(SUM(line_total), 0) INTO v_subtotal
  FROM public.pos_cart_lines WHERE cart_id = p_cart_id;
  v_total := v_subtotal;

  IF v_cart.customer_id IS NOT NULL
     AND v_cust.credit_limit > 0
     AND (v_cust.open_balance + v_total) > v_cust.credit_limit THEN
    INSERT INTO public.sales_invoices (
      doc_type, status, customer_id, warehouse_id, currency,
      exchange_rate_applied, cart_id, subtotal, total,
      customer_phone_e164, customer_email, customer_whatsapp_e164
    )
    VALUES (
      'invoice', 'on_hold', v_cart.customer_id, v_cart.warehouse_id, v_cart.currency,
      v_cart.exchange_rate_applied, p_cart_id, v_subtotal, v_total,
      v_cust.phone_e164, v_cust.email, v_cust.whatsapp_e164
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
    exchange_rate_applied, subtotal, total, cart_id,
    customer_phone_e164, customer_email, customer_whatsapp_e164,
    posted_by, posted_at
  )
  VALUES (
    'invoice', 'draft', public.next_series_value('SINV-'),
    v_cart.customer_id, v_cart.warehouse_id, v_cart.currency,
    v_cart.exchange_rate_applied, v_subtotal, v_total, p_cart_id,
    v_cust.phone_e164, v_cust.email, v_cust.whatsapp_e164,
    auth.uid(), now()
  )
  RETURNING id INTO v_inv;

  PERFORM public.emit_domain_event(
    'order_received',
    'invoice:received:' || v_inv::text,
    jsonb_build_object('invoice_id', v_inv)
  );

  FOR v_line IN
    SELECT * FROM public.pos_cart_lines WHERE cart_id = p_cart_id ORDER BY is_core_charge, created_at
  LOOP
    INSERT INTO public.sales_invoice_lines (
      invoice_id, stock_item_id, parent_line_id, is_core_charge, uom_id,
      qty, qty_base, unit_price, line_total, qty_fulfilled
    )
    VALUES (
      v_inv, v_line.stock_item_id, NULL, v_line.is_core_charge, v_line.uom_id,
      v_line.qty, v_line.qty_base, v_line.unit_price, v_line.line_total, v_line.qty_base
    );

    IF v_line.is_core_charge THEN
      v_core := v_core + v_line.line_total;
    ELSE
      v_rev := v_rev + v_line.line_total;
      -- Consume stock (FIFO) from cart warehouse — skip core lines
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
  END LOOP;

  -- Journals: DR Cash/AR, CR Revenue; DR COGS, CR Inventory; optional core CR 4200
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

  IF v_cart.customer_id IS NOT NULL THEN
    UPDATE public.customers
    SET open_balance = open_balance + v_total, updated_at = now()
    WHERE id = v_cart.customer_id;
  END IF;

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

-- ---------------------------------------------------------------------------
-- Credit note / return against invoice → Quarantine stock + reverse-ish journals
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.post_return_credit_note(
  p_invoice_id UUID,
  p_lines JSONB
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_src public.sales_invoices%ROWTYPE;
  v_cn UUID;
  v_quar UUID;
  v_line JSONB;
  v_item UUID;
  v_uom UUID;
  v_qty NUMERIC;
  v_qty_base NUMERIC;
  v_price NUMERIC;
  v_total NUMERIC := 0;
  v_journal UUID;
  v_lines JSONB;
BEGIN
  PERFORM public._require_sales_staff();
  SELECT * INTO v_src FROM public.sales_invoices WHERE id = p_invoice_id AND doc_type = 'invoice' AND status = 'posted';
  IF NOT FOUND THEN
    RAISE EXCEPTION 'posted source invoice required';
  END IF;

  SELECT id INTO v_quar FROM public.warehouses WHERE is_quarantine AND is_active ORDER BY code LIMIT 1;
  IF v_quar IS NULL THEN
    RAISE EXCEPTION 'quarantine warehouse required for returns';
  END IF;

  PERFORM public.emit_domain_event(
    'return_initiated',
    'return:init:' || p_invoice_id::text || ':' || gen_random_uuid()::text,
    jsonb_build_object('invoice_id', p_invoice_id)
  );

  INSERT INTO public.sales_invoices (
    doc_type, status, document_number, customer_id, warehouse_id, currency,
    exchange_rate_applied, return_against_id,
    customer_phone_e164, customer_email, customer_whatsapp_e164,
    posted_by, posted_at
  )
  VALUES (
    'credit_note', 'draft', public.next_series_value('CN-'),
    v_src.customer_id, v_quar, v_src.currency, v_src.exchange_rate_applied, p_invoice_id,
    v_src.customer_phone_e164, v_src.customer_email, v_src.customer_whatsapp_e164,
    auth.uid(), now()
  )
  RETURNING id INTO v_cn;

  FOR v_line IN SELECT * FROM jsonb_array_elements(p_lines)
  LOOP
    v_item := (v_line ->> 'stock_item_id')::uuid;
    v_uom := (v_line ->> 'uom_id')::uuid;
    v_qty := (v_line ->> 'qty')::numeric;
    v_price := (v_line ->> 'unit_price')::numeric;
    v_qty_base := public.convert_to_base_uom(v_item, v_uom, v_qty);
    v_total := v_total + round(v_price * v_qty, 2);

    INSERT INTO public.sales_invoice_lines (
      invoice_id, stock_item_id, uom_id, qty, qty_base, unit_price, line_total, qty_fulfilled
    )
    VALUES (v_cn, v_item, v_uom, v_qty, v_qty_base, v_price, round(v_price * v_qty, 2), v_qty_base);

    -- Stock into Quarantine only (never MAIN)
    PERFORM public._adjust_stock_level(
      v_item, v_quar, v_qty_base, 'FIFO', v_price, v_src.currency
    );
    INSERT INTO public.stock_batches (
      batch_code, stock_item_id, warehouse_id, valuation_method, unit_cost, currency, qty_on_hand
    )
    VALUES (
      public.next_series_value('BATCH-'), v_item, v_quar, 'FIFO', v_price, v_src.currency, v_qty_base
    );
  END LOOP;

  v_lines := jsonb_build_array(
    jsonb_build_object('account_code', '4110', 'debit', v_total, 'credit', 0, 'currency', v_src.currency),
    jsonb_build_object(
      'account_code', CASE WHEN v_src.customer_id IS NULL THEN '1100' ELSE '1200' END,
      'debit', 0, 'credit', v_total, 'currency', v_src.currency
    ),
    jsonb_build_object('account_code', '1310', 'debit', v_total, 'credit', 0, 'currency', v_src.currency),
    jsonb_build_object('account_code', '1300', 'debit', 0, 'credit', v_total, 'currency', v_src.currency)
  );

  v_journal := public.post_journal_entry(
    CURRENT_DATE,
    format('Return CN against %s', v_src.document_number),
    v_src.currency,
    v_src.exchange_rate_applied,
    v_lines
  );

  UPDATE public.sales_invoices
  SET status = 'posted', subtotal = v_total, total = v_total, journal_entry_id = v_journal
  WHERE id = v_cn;

  IF v_src.customer_id IS NOT NULL THEN
    UPDATE public.customers
    SET open_balance = open_balance - v_total, updated_at = now()
    WHERE id = v_src.customer_id;
  END IF;

  PERFORM public.emit_domain_event(
    'return_completed',
    'return:done:' || v_cn::text,
    jsonb_build_object('credit_note_id', v_cn, 'invoice_id', p_invoice_id)
  );
  PERFORM public.emit_domain_event(
    'quarantine_received',
    'return:quar:' || v_cn::text,
    jsonb_build_object('credit_note_id', v_cn)
  );

  PERFORM public.enqueue_customer_receipts(v_cn);
  RETURN v_cn;
END;
$$;

-- ---------------------------------------------------------------------------
-- RLS
-- ---------------------------------------------------------------------------
ALTER TABLE public.customers ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.price_lists ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.price_list_items ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.customer_price_overrides ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.pos_carts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.pos_cart_lines ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.sales_invoices ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.sales_invoice_lines ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.customer_receipt_outbox ENABLE ROW LEVEL SECURITY;

CREATE POLICY customers_staff ON public.customers FOR ALL TO authenticated
  USING (public.has_staff_role(ARRAY['admin', 'sales', 'finance']::public.staff_role[]))
  WITH CHECK (public.has_staff_role(ARRAY['admin', 'sales', 'finance']::public.staff_role[]));

CREATE POLICY customers_select_own ON public.customers FOR SELECT TO authenticated
  USING (profile_id = auth.uid());

CREATE POLICY price_lists_select ON public.price_lists FOR SELECT TO authenticated USING (true);
CREATE POLICY price_lists_write ON public.price_lists FOR ALL TO authenticated
  USING (public.has_staff_role(ARRAY['admin', 'sales', 'finance']::public.staff_role[]))
  WITH CHECK (public.has_staff_role(ARRAY['admin', 'sales', 'finance']::public.staff_role[]));

CREATE POLICY price_items_select ON public.price_list_items FOR SELECT TO authenticated USING (true);
CREATE POLICY price_items_write ON public.price_list_items FOR ALL TO authenticated
  USING (public.has_staff_role(ARRAY['admin', 'sales', 'finance']::public.staff_role[]))
  WITH CHECK (public.has_staff_role(ARRAY['admin', 'sales', 'finance']::public.staff_role[]));

CREATE POLICY cust_price_staff ON public.customer_price_overrides FOR ALL TO authenticated
  USING (public.has_staff_role(ARRAY['admin', 'sales', 'finance']::public.staff_role[]))
  WITH CHECK (public.has_staff_role(ARRAY['admin', 'sales', 'finance']::public.staff_role[]));

CREATE POLICY carts_staff ON public.pos_carts FOR ALL TO authenticated
  USING (public.has_staff_role(ARRAY['admin', 'sales', 'warehouse']::public.staff_role[]))
  WITH CHECK (public.has_staff_role(ARRAY['admin', 'sales', 'warehouse']::public.staff_role[]));

CREATE POLICY cart_lines_staff ON public.pos_cart_lines FOR ALL TO authenticated
  USING (public.has_staff_role(ARRAY['admin', 'sales', 'warehouse']::public.staff_role[]))
  WITH CHECK (public.has_staff_role(ARRAY['admin', 'sales', 'warehouse']::public.staff_role[]));

CREATE POLICY invoices_staff ON public.sales_invoices FOR ALL TO authenticated
  USING (public.has_staff_role(ARRAY['admin', 'sales', 'finance', 'warehouse']::public.staff_role[]))
  WITH CHECK (public.has_staff_role(ARRAY['admin', 'sales', 'finance']::public.staff_role[]));

CREATE POLICY invoice_lines_staff ON public.sales_invoice_lines FOR ALL TO authenticated
  USING (public.has_staff_role(ARRAY['admin', 'sales', 'finance', 'warehouse']::public.staff_role[]))
  WITH CHECK (public.has_staff_role(ARRAY['admin', 'sales', 'finance']::public.staff_role[]));

CREATE POLICY receipt_outbox_staff ON public.customer_receipt_outbox FOR SELECT TO authenticated
  USING (public.has_staff_role(ARRAY['admin', 'sales', 'finance']::public.staff_role[]));

-- Grants
REVOKE ALL ON FUNCTION public.create_pos_cart(UUID, UUID, public.currency_code) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.add_cart_line(UUID, UUID, UUID, NUMERIC) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.add_cart_line_from_qr(UUID, TEXT, NUMERIC) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.checkout_pos_cart(UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.post_return_credit_note(UUID, JSONB) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.enqueue_customer_receipts(UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.resolve_item_price(UUID, UUID) FROM PUBLIC;

GRANT EXECUTE ON FUNCTION public.create_pos_cart(UUID, UUID, public.currency_code) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.add_cart_line(UUID, UUID, UUID, NUMERIC) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.add_cart_line_from_qr(UUID, TEXT, NUMERIC) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.checkout_pos_cart(UUID) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.post_return_credit_note(UUID, JSONB) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.enqueue_customer_receipts(UUID) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.resolve_item_price(UUID, UUID) TO authenticated, service_role;
