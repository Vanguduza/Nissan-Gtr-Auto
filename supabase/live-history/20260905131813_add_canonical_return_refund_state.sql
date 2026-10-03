-- Exported from the hosted project's supabase_migrations.schema_migrations (20260905131813 add_canonical_return_refund_state).
-- Source of record for what production ran; see supabase/live-history/README.md.

DO $$ BEGIN
  CREATE TYPE public.customer_return_status AS ENUM ('requested','approved','rejected','received','refund_pending','completed','cancelled');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
  CREATE TYPE public.return_resolution_method AS ENUM ('original_payment','store_credit','cash','bank');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
  CREATE TYPE public.return_refund_status AS ENUM ('pending','processing','completed','failed','cancelled');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

INSERT INTO public.chart_of_accounts(code,name,display_name,account_type,is_active)
VALUES('2220','Customer Refunds Payable','Customer Refunds Payable','liability',true)
ON CONFLICT(code) DO UPDATE SET name=EXCLUDED.name,display_name=EXCLUDED.display_name,is_active=true;

CREATE TABLE IF NOT EXISTS public.customer_return_requests (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  customer_id uuid NOT NULL REFERENCES public.customers(id),
  source_invoice_id uuid NOT NULL REFERENCES public.sales_invoices(id),
  status public.customer_return_status NOT NULL DEFAULT 'requested',
  preferred_resolution public.return_resolution_method NOT NULL DEFAULT 'original_payment',
  reason text,
  review_notes text,
  credit_note_id uuid UNIQUE REFERENCES public.sales_invoices(id),
  ar_credit_amount numeric NOT NULL DEFAULT 0 CHECK (ar_credit_amount>=0),
  refund_due_amount numeric NOT NULL DEFAULT 0 CHECK (refund_due_amount>=0),
  requested_by uuid,
  requested_at timestamptz NOT NULL DEFAULT now(),
  reviewed_by uuid,
  reviewed_at timestamptz,
  received_by uuid,
  received_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.customer_return_request_lines (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  return_request_id uuid NOT NULL REFERENCES public.customer_return_requests(id) ON DELETE CASCADE,
  source_invoice_line_id uuid NOT NULL REFERENCES public.sales_invoice_lines(id),
  stock_item_id uuid NOT NULL REFERENCES public.stock_items(id),
  uom_id uuid NOT NULL REFERENCES public.uoms(id),
  qty numeric NOT NULL CHECK (qty>0),
  qty_base numeric NOT NULL CHECK (qty_base>0),
  unit_price_snapshot numeric NOT NULL CHECK (unit_price_snapshot>=0),
  line_total_snapshot numeric NOT NULL CHECK (line_total_snapshot>=0),
  unit_cost_basis numeric NOT NULL CHECK (unit_cost_basis>=0),
  cost_total_basis numeric NOT NULL CHECK (cost_total_basis>=0),
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE(return_request_id,source_invoice_line_id)
);

CREATE TABLE IF NOT EXISTS public.return_refund_requests (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  return_request_id uuid NOT NULL UNIQUE REFERENCES public.customer_return_requests(id),
  credit_note_id uuid NOT NULL UNIQUE REFERENCES public.sales_invoices(id),
  customer_id uuid NOT NULL REFERENCES public.customers(id),
  source_invoice_id uuid NOT NULL REFERENCES public.sales_invoices(id),
  amount numeric NOT NULL CHECK (amount>0),
  currency public.currency_code NOT NULL,
  method public.return_resolution_method NOT NULL DEFAULT 'original_payment',
  status public.return_refund_status NOT NULL DEFAULT 'pending',
  original_payment_entry_id uuid REFERENCES public.payment_entries(id),
  settlement_reference text,
  failure_reason text,
  processed_by uuid,
  processed_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS customer_return_requests_customer_idx ON public.customer_return_requests(customer_id,created_at DESC);
CREATE INDEX IF NOT EXISTS customer_return_requests_invoice_idx ON public.customer_return_requests(source_invoice_id,created_at DESC);
CREATE INDEX IF NOT EXISTS customer_return_requests_status_idx ON public.customer_return_requests(status,updated_at);
CREATE INDEX IF NOT EXISTS customer_return_lines_source_idx ON public.customer_return_request_lines(source_invoice_line_id);
CREATE INDEX IF NOT EXISTS return_refund_requests_status_idx ON public.return_refund_requests(status,updated_at);

ALTER TABLE public.customer_return_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.customer_return_request_lines ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.return_refund_requests ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS customer_return_requests_select ON public.customer_return_requests;
CREATE POLICY customer_return_requests_select ON public.customer_return_requests FOR SELECT TO authenticated
USING (
  customer_id=public._current_customer_id()
  OR public.has_staff_role(ARRAY['admin','sales','warehouse','finance']::public.staff_role[])
);

DROP POLICY IF EXISTS customer_return_lines_select ON public.customer_return_request_lines;
CREATE POLICY customer_return_lines_select ON public.customer_return_request_lines FOR SELECT TO authenticated
USING (EXISTS(
  SELECT 1 FROM public.customer_return_requests rr
  WHERE rr.id=return_request_id AND (
    rr.customer_id=public._current_customer_id()
    OR public.has_staff_role(ARRAY['admin','sales','warehouse','finance']::public.staff_role[])
  )
));

DROP POLICY IF EXISTS return_refund_requests_select ON public.return_refund_requests;
CREATE POLICY return_refund_requests_select ON public.return_refund_requests FOR SELECT TO authenticated
USING (
  customer_id=public._current_customer_id()
  OR public.has_staff_role(ARRAY['admin','sales','finance']::public.staff_role[])
);

REVOKE ALL ON public.customer_return_requests FROM anon;
REVOKE ALL ON public.customer_return_request_lines FROM anon;
REVOKE ALL ON public.return_refund_requests FROM anon;
GRANT SELECT ON public.customer_return_requests TO authenticated;
GRANT SELECT ON public.customer_return_request_lines TO authenticated;
GRANT SELECT ON public.return_refund_requests TO authenticated;
REVOKE INSERT,UPDATE,DELETE ON public.customer_return_requests FROM authenticated;
REVOKE INSERT,UPDATE,DELETE ON public.customer_return_request_lines FROM authenticated;
REVOKE INSERT,UPDATE,DELETE ON public.return_refund_requests FROM authenticated;
