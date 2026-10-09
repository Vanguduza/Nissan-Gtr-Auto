-- Exported from the hosted project's supabase_migrations.schema_migrations (20260908120000 pos_operations_p0_p1).
-- Source of record for what production ran; see supabase/live-history/README.md.

-- POS P0/P1 operating-system expansion: governed approvals, till accountability,
-- cross-warehouse fulfilment, payment recovery, professional returns/core/warranty,
-- and pickup handoff. Existing finance/inventory/commerce functions remain SoR.

-- ---------------------------------------------------------------------------
-- 1. Manager governance: configurable numeric thresholds + controlled reasons.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.pos_approval_policies (
  action TEXT PRIMARY KEY,
  threshold_value NUMERIC(18,4) NOT NULL DEFAULT 0 CHECK (threshold_value >= 0),
  always_require_manager BOOLEAN NOT NULL DEFAULT true,
  reason_required BOOLEAN NOT NULL DEFAULT true,
  updated_by UUID REFERENCES auth.users(id),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.pos_approval_reason_codes (
  action TEXT NOT NULL,
  code TEXT NOT NULL,
  label TEXT NOT NULL,
  is_active BOOLEAN NOT NULL DEFAULT true,
  sort_order INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY(action, code)
);

INSERT INTO public.pos_approval_policies(action,threshold_value,always_require_manager,reason_required) VALUES
 ('discount_percent',0,true,true),
 ('price_override_delta_percent',0,true,true),
 ('till_variance',0,true,true),
 ('cash_out',0,true,true),
 ('return_post',0,true,true),
 ('core_return',0,true,true)
ON CONFLICT(action) DO NOTHING;

INSERT INTO public.pos_approval_reason_codes(action,code,label,sort_order) VALUES
 ('discount_percent','customer_retention','Customer retention',10),
 ('discount_percent','price_match','Approved price match',20),
 ('discount_percent','damaged_packaging','Damaged packaging',30),
 ('price_override_delta_percent','supplier_price','Supplier / landed-cost correction',10),
 ('price_override_delta_percent','advertised_price','Advertised price correction',20),
 ('price_override_delta_percent','data_correction','Catalog / price data correction',30),
 ('till_variance','count_error','Recount / counting error',10),
 ('till_variance','cash_movement_missing','Missing cash movement entry',20),
 ('till_variance','investigation','Variance under investigation',30),
 ('cash_out','petty_cash','Petty cash',10),
 ('cash_out','bank_drop','Bank / safe drop',20),
 ('cash_out','customer_refund','Customer cash refund',30),
 ('return_post','wrong_part','Wrong part supplied',10),
 ('return_post','customer_changed_mind','Customer changed mind',20),
 ('return_post','defective','Defective part',30),
 ('return_post','fitment_issue','Fitment issue',40),
 ('core_return','eligible_core','Eligible exchange core returned',10),
 ('core_return','manager_exception','Manager exception',20)
ON CONFLICT(action,code) DO NOTHING;

ALTER TABLE public.pos_action_audit
  ADD COLUMN IF NOT EXISTS reason_code TEXT,
  ADD COLUMN IF NOT EXISTS approved_by_user_id UUID REFERENCES auth.users(id),
  ADD COLUMN IF NOT EXISTS approval_policy_action TEXT;

CREATE OR REPLACE FUNCTION public.pos_action_requires_manager(p_action TEXT,p_value NUMERIC DEFAULT 0)
RETURNS BOOLEAN LANGUAGE sql STABLE SECURITY DEFINER SET search_path='' AS $$
 SELECT COALESCE((SELECT always_require_manager OR COALESCE(p_value,0) > threshold_value
   FROM public.pos_approval_policies WHERE action=p_action), true);
$$;

CREATE OR REPLACE FUNCTION public.list_pos_approval_reasons(p_action TEXT)
RETURNS TABLE(code TEXT,label TEXT,sort_order INTEGER)
LANGUAGE sql STABLE SECURITY DEFINER SET search_path='' AS $$
 SELECT r.code,r.label,r.sort_order FROM public.pos_approval_reason_codes r
 WHERE r.action=p_action AND r.is_active ORDER BY r.sort_order,r.label;
$$;

CREATE OR REPLACE FUNCTION public.get_pos_approval_policy(p_action TEXT)
RETURNS JSONB LANGUAGE sql STABLE SECURITY DEFINER SET search_path='' AS $$
 SELECT COALESCE((SELECT jsonb_build_object('action',action,'threshold_value',threshold_value,
   'always_require_manager',always_require_manager,'reason_required',reason_required)
   FROM public.pos_approval_policies WHERE action=p_action),'{}'::jsonb);
$$;

-- ---------------------------------------------------------------------------
-- 2. Till / cashier session accountability.
-- ---------------------------------------------------------------------------
DO $$ BEGIN
 IF NOT EXISTS(SELECT 1 FROM pg_type WHERE typname='pos_till_session_status') THEN
   CREATE TYPE public.pos_till_session_status AS ENUM('open','variance_pending','closed');
 END IF;
 IF NOT EXISTS(SELECT 1 FROM pg_type WHERE typname='pos_till_cash_movement_kind') THEN
   CREATE TYPE public.pos_till_cash_movement_kind AS ENUM('cash_in','cash_out','petty_cash','bank_drop','cash_refund');
 END IF;
END $$;

CREATE TABLE IF NOT EXISTS public.pos_till_sessions (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 device_id TEXT NOT NULL,
 warehouse_id UUID NOT NULL REFERENCES public.warehouses(id),
 currency public.currency_code NOT NULL,
 operator_user_id UUID NOT NULL REFERENCES auth.users(id),
 opened_by UUID NOT NULL REFERENCES auth.users(id),
 opening_float NUMERIC(18,2) NOT NULL CHECK(opening_float>=0),
 status public.pos_till_session_status NOT NULL DEFAULT 'open',
 expected_cash NUMERIC(18,2), counted_cash NUMERIC(18,2), variance NUMERIC(18,2),
 variance_reason_code TEXT, close_notes TEXT,
 approved_by UUID REFERENCES auth.users(id), approved_at TIMESTAMPTZ,
 opened_at TIMESTAMPTZ NOT NULL DEFAULT now(), closed_at TIMESTAMPTZ,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS pos_till_one_open_device_uidx
 ON public.pos_till_sessions(device_id) WHERE status IN('open','variance_pending');

CREATE UNIQUE INDEX IF NOT EXISTS pos_till_one_open_operator_uidx
 ON public.pos_till_sessions(operator_user_id) WHERE status IN('open','variance_pending');

CREATE TABLE IF NOT EXISTS public.pos_till_cash_movements (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), session_id UUID NOT NULL REFERENCES public.pos_till_sessions(id) ON DELETE RESTRICT,
 kind public.pos_till_cash_movement_kind NOT NULL, amount NUMERIC(18,2) NOT NULL CHECK(amount>0),
 reason_code TEXT NOT NULL, notes TEXT, finance_refund_id UUID REFERENCES public.finance_refunds(id),
 actor_user_id UUID NOT NULL REFERENCES auth.users(id), created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS pos_till_cash_movements_session_idx ON public.pos_till_cash_movements(session_id,created_at);

CREATE TABLE IF NOT EXISTS public.pos_till_session_events (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), session_id UUID NOT NULL REFERENCES public.pos_till_sessions(id) ON DELETE RESTRICT,
 event_type TEXT NOT NULL, actor_user_id UUID REFERENCES auth.users(id), detail JSONB NOT NULL DEFAULT '{}'::jsonb,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE public.pos_carts ADD COLUMN IF NOT EXISTS till_session_id UUID REFERENCES public.pos_till_sessions(id);

ALTER TABLE public.sales_invoices ADD COLUMN IF NOT EXISTS till_session_id UUID REFERENCES public.pos_till_sessions(id);

CREATE INDEX IF NOT EXISTS sales_invoices_till_session_idx ON public.sales_invoices(till_session_id,posted_at);

CREATE OR REPLACE FUNCTION private.pos_invoice_till_session_sync()
RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
BEGIN
 IF NEW.till_session_id IS NULL AND NEW.cart_id IS NOT NULL THEN
  SELECT c.till_session_id INTO NEW.till_session_id FROM public.pos_carts c WHERE c.id=NEW.cart_id;
 END IF; RETURN NEW;
END $$;

DROP TRIGGER IF EXISTS trg_pos_invoice_till_session_sync ON public.sales_invoices;

CREATE TRIGGER trg_pos_invoice_till_session_sync BEFORE INSERT ON public.sales_invoices
 FOR EACH ROW EXECUTE FUNCTION private.pos_invoice_till_session_sync();

CREATE OR REPLACE FUNCTION public.open_pos_till_session(p_warehouse_id UUID,p_device_id TEXT,p_opening_float NUMERIC,p_currency public.currency_code DEFAULT 'USD')
RETURNS UUID LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE v_id UUID;
BEGIN
 PERFORM public._require_sales_staff();
 IF trim(COALESCE(p_device_id,''))='' THEN RAISE EXCEPTION 'device_id required'; END IF;
 IF COALESCE(p_opening_float,-1)<0 THEN RAISE EXCEPTION 'opening_float must be >= 0'; END IF;
 IF NOT EXISTS(SELECT 1 FROM public.warehouses WHERE id=p_warehouse_id AND is_active) THEN RAISE EXCEPTION 'active warehouse required'; END IF;
 IF EXISTS(SELECT 1 FROM public.pos_till_sessions WHERE (device_id=trim(p_device_id) OR operator_user_id=auth.uid()) AND status IN('open','variance_pending')) THEN
   RAISE EXCEPTION 'device or operator already has an open till session';
 END IF;
 INSERT INTO public.pos_till_sessions(device_id,warehouse_id,currency,operator_user_id,opened_by,opening_float)
 VALUES(trim(p_device_id),p_warehouse_id,p_currency,auth.uid(),auth.uid(),round(p_opening_float,2)) RETURNING id INTO v_id;
 INSERT INTO public.pos_till_session_events(session_id,event_type,actor_user_id,detail)
 VALUES(v_id,'opened',auth.uid(),jsonb_build_object('opening_float',round(p_opening_float,2),'currency',p_currency,'warehouse_id',p_warehouse_id));
 RETURN v_id;
END $$;

CREATE OR REPLACE FUNCTION public.get_my_open_pos_till_session(p_device_id TEXT DEFAULT NULL)
RETURNS JSONB LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path='' AS $$
DECLARE s public.pos_till_sessions%ROWTYPE;
BEGIN
 SELECT * INTO s FROM public.pos_till_sessions
 WHERE status IN('open','variance_pending') AND (operator_user_id=auth.uid() OR (p_device_id IS NOT NULL AND device_id=p_device_id))
 ORDER BY opened_at DESC LIMIT 1;
 IF NOT FOUND THEN RETURN NULL; END IF;
 RETURN to_jsonb(s);
END $$;

CREATE OR REPLACE FUNCTION public.attach_pos_cart_till_session(p_cart_id UUID,p_session_id UUID)
RETURNS UUID LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE s public.pos_till_sessions%ROWTYPE;
BEGIN
 PERFORM public._require_sales_staff();
 SELECT * INTO s FROM public.pos_till_sessions WHERE id=p_session_id FOR UPDATE;
 IF NOT FOUND OR s.status<>'open' OR s.operator_user_id<>auth.uid() THEN RAISE EXCEPTION 'active operator till session required'; END IF;
 UPDATE public.pos_carts SET till_session_id=p_session_id,updated_at=now()
 WHERE id=p_cart_id AND status='open' AND warehouse_id=s.warehouse_id AND created_by=auth.uid();
 IF NOT FOUND THEN RAISE EXCEPTION 'open cart at till warehouse required'; END IF; RETURN p_cart_id;
END $$;

CREATE OR REPLACE FUNCTION public.record_pos_till_cash_movement(p_session_id UUID,p_kind public.pos_till_cash_movement_kind,p_amount NUMERIC,p_reason_code TEXT,p_notes TEXT DEFAULT NULL,p_finance_refund_id UUID DEFAULT NULL)
RETURNS UUID LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE s public.pos_till_sessions%ROWTYPE; v_id UUID;
BEGIN
 PERFORM public._require_sales_staff(); SELECT * INTO s FROM public.pos_till_sessions WHERE id=p_session_id FOR UPDATE;
 IF NOT FOUND OR s.status<>'open' THEN RAISE EXCEPTION 'open till session required'; END IF;
 IF s.operator_user_id<>auth.uid() AND NOT public.is_pos_approver() THEN RAISE EXCEPTION 'current operator or manager required'; END IF;
 IF p_kind IN('cash_out','petty_cash','bank_drop','cash_refund') AND NOT public.is_pos_approver() THEN RAISE EXCEPTION 'manager approval required for cash-out movement'; END IF;
 IF COALESCE(p_amount,0)<=0 THEN RAISE EXCEPTION 'amount must be > 0'; END IF;
 IF trim(COALESCE(p_reason_code,''))='' THEN RAISE EXCEPTION 'reason_code required'; END IF;
 INSERT INTO public.pos_till_cash_movements(session_id,kind,amount,reason_code,notes,finance_refund_id,actor_user_id)
 VALUES(p_session_id,p_kind,round(p_amount,2),trim(p_reason_code),p_notes,p_finance_refund_id,auth.uid()) RETURNING id INTO v_id;
 INSERT INTO public.pos_till_session_events(session_id,event_type,actor_user_id,detail)
 VALUES(p_session_id,'cash_movement',auth.uid(),jsonb_build_object('movement_id',v_id,'kind',p_kind,'amount',round(p_amount,2),'reason_code',p_reason_code));
 RETURN v_id;
END $$;

CREATE OR REPLACE FUNCTION public.pos_till_expected_cash(p_session_id UUID)
RETURNS NUMERIC LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path='' AS $$
DECLARE s public.pos_till_sessions%ROWTYPE; v_cash NUMERIC:=0; v_direct NUMERIC:=0; v_in NUMERIC:=0; v_out NUMERIC:=0;
BEGIN
 PERFORM public._require_sales_staff();
 SELECT * INTO s FROM public.pos_till_sessions WHERE id=p_session_id;
 IF NOT FOUND THEN RAISE EXCEPTION 'till session not found'; END IF;
 IF s.operator_user_id<>auth.uid() AND NOT public.is_pos_approver() AND NOT public.has_staff_role(ARRAY['finance']::public.staff_role[]) THEN
   RAISE EXCEPTION 'till session access denied';
 END IF;
 SELECT COALESCE(SUM(a.amount),0) INTO v_cash
 FROM public.payment_allocations a JOIN public.payment_entries p ON p.id=a.payment_entry_id
 JOIN public.sales_invoices i ON i.id=a.sales_invoice_id
 WHERE i.till_session_id=p_session_id AND p.status='posted' AND p.tender='cash';
 SELECT COALESCE(SUM(i.total),0) INTO v_direct FROM public.sales_invoices i
 WHERE i.till_session_id=p_session_id AND i.status='posted' AND i.doc_type='invoice' AND i.customer_id IS NULL
   AND NOT EXISTS(SELECT 1 FROM public.payment_allocations a WHERE a.sales_invoice_id=i.id);
 SELECT COALESCE(SUM(amount),0) INTO v_in FROM public.pos_till_cash_movements
 WHERE session_id=p_session_id AND kind='cash_in';
 SELECT COALESCE(SUM(amount),0) INTO v_out FROM public.pos_till_cash_movements
 WHERE session_id=p_session_id AND kind IN('cash_out','petty_cash','bank_drop','cash_refund');
 RETURN round(s.opening_float+v_cash+v_direct+v_in-v_out,2);
END $$;

CREATE OR REPLACE FUNCTION public.close_pos_till_session(p_session_id UUID,p_counted_cash NUMERIC,p_variance_reason_code TEXT DEFAULT NULL,p_notes TEXT DEFAULT NULL)
RETURNS JSONB LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE s public.pos_till_sessions%ROWTYPE; v_expected NUMERIC; v_var NUMERIC; p public.pos_approval_policies%ROWTYPE; v_pending BOOLEAN;
BEGIN
 PERFORM public._require_sales_staff(); SELECT * INTO s FROM public.pos_till_sessions WHERE id=p_session_id FOR UPDATE;
 IF NOT FOUND OR s.status<>'open' THEN RAISE EXCEPTION 'open till session required'; END IF;
 IF s.operator_user_id<>auth.uid() THEN RAISE EXCEPTION 'only the current till operator can perform blind close count'; END IF;
 IF COALESCE(p_counted_cash,-1)<0 THEN RAISE EXCEPTION 'counted cash must be >= 0'; END IF;
 v_expected:=public.pos_till_expected_cash(p_session_id); v_var:=round(p_counted_cash-v_expected,2);
 SELECT * INTO p FROM public.pos_approval_policies WHERE action='till_variance';
 v_pending:=abs(v_var)>COALESCE(p.threshold_value,0) OR (COALESCE(p.always_require_manager,false) AND abs(v_var)>0.009);
 IF abs(v_var)>0.009 AND trim(COALESCE(p_variance_reason_code,''))='' THEN RAISE EXCEPTION 'variance reason required'; END IF;
 UPDATE public.pos_till_sessions SET expected_cash=v_expected,counted_cash=round(p_counted_cash,2),variance=v_var,
   variance_reason_code=NULLIF(trim(COALESCE(p_variance_reason_code,'')),''),close_notes=p_notes,
   status=CASE WHEN v_pending THEN 'variance_pending'::public.pos_till_session_status ELSE 'closed'::public.pos_till_session_status END,
   closed_at=CASE WHEN v_pending THEN NULL ELSE now() END,updated_at=now() WHERE id=p_session_id;
 INSERT INTO public.pos_till_session_events(session_id,event_type,actor_user_id,detail) VALUES
 (p_session_id,CASE WHEN v_pending THEN 'close_submitted' ELSE 'closed' END,auth.uid(),
  jsonb_build_object('expected_cash',v_expected,'counted_cash',round(p_counted_cash,2),'variance',v_var,'reason_code',p_variance_reason_code));
 RETURN jsonb_build_object('session_id',p_session_id,'expected_cash',v_expected,'counted_cash',round(p_counted_cash,2),'variance',v_var,
   'status',CASE WHEN v_pending THEN 'variance_pending' ELSE 'closed' END);
END $$;

CREATE OR REPLACE FUNCTION public.approve_pos_till_variance(p_session_id UUID,p_reason_code TEXT,p_notes TEXT DEFAULT NULL)
RETURNS UUID LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE s public.pos_till_sessions%ROWTYPE;
BEGIN
 IF NOT public.is_pos_approver() THEN RAISE EXCEPTION 'POS manager approval required'; END IF;
 SELECT * INTO s FROM public.pos_till_sessions WHERE id=p_session_id FOR UPDATE;
 IF NOT FOUND OR s.status<>'variance_pending' THEN RAISE EXCEPTION 'variance-pending till session required'; END IF;
 IF trim(COALESCE(p_reason_code,''))='' THEN RAISE EXCEPTION 'reason_code required'; END IF;
 UPDATE public.pos_till_sessions SET status='closed',approved_by=auth.uid(),approved_at=now(),closed_at=now(),
  variance_reason_code=p_reason_code,close_notes=concat_ws(E'\n',close_notes,p_notes),updated_at=now() WHERE id=p_session_id;
 INSERT INTO public.pos_till_session_events(session_id,event_type,actor_user_id,detail)
 VALUES(p_session_id,'variance_approved',auth.uid(),jsonb_build_object('reason_code',p_reason_code,'notes',p_notes,'variance',s.variance));
 PERFORM public._log_pos_action('till_variance_approved','pos_till_sessions',p_session_id,to_jsonb(s),
  (SELECT to_jsonb(x) FROM public.pos_till_sessions x WHERE x.id=p_session_id),p_notes);
 UPDATE public.pos_action_audit SET reason_code=p_reason_code,approved_by_user_id=auth.uid(),approval_policy_action='till_variance'
 WHERE id=(SELECT id FROM public.pos_action_audit WHERE entity_type='pos_till_sessions' AND entity_id=p_session_id ORDER BY created_at DESC LIMIT 1);
 RETURN p_session_id;
END $$;

CREATE OR REPLACE FUNCTION public.handover_pos_till_session(p_session_id UUID,p_new_operator_user_id UUID,p_notes TEXT DEFAULT NULL)
RETURNS UUID LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE s public.pos_till_sessions%ROWTYPE;
BEGIN
 IF NOT public.is_pos_approver() THEN RAISE EXCEPTION 'POS manager approval required'; END IF;
 SELECT * INTO s FROM public.pos_till_sessions WHERE id=p_session_id FOR UPDATE;
 IF NOT FOUND OR s.status<>'open' THEN RAISE EXCEPTION 'open till session required'; END IF;
 IF NOT EXISTS(SELECT 1 FROM public.employees e WHERE e.user_id=p_new_operator_user_id AND e.status='active') THEN RAISE EXCEPTION 'active employee required'; END IF;
 IF EXISTS(SELECT 1 FROM public.pos_till_sessions WHERE operator_user_id=p_new_operator_user_id AND status IN('open','variance_pending') AND id<>p_session_id) THEN
   RAISE EXCEPTION 'new operator already has an open till';
 END IF;
 UPDATE public.pos_till_sessions SET operator_user_id=p_new_operator_user_id,updated_at=now() WHERE id=p_session_id;
 INSERT INTO public.pos_till_session_events(session_id,event_type,actor_user_id,detail)
 VALUES(p_session_id,'handover',auth.uid(),jsonb_build_object('from_operator_user_id',s.operator_user_id,'to_operator_user_id',p_new_operator_user_id,'notes',p_notes));
 RETURN p_session_id;
END $$;

CREATE OR REPLACE FUNCTION public.list_pos_till_sessions(p_status TEXT DEFAULT NULL,p_limit INTEGER DEFAULT 50)
RETURNS SETOF public.pos_till_sessions LANGUAGE sql STABLE SECURITY DEFINER SET search_path='' AS $$
 SELECT s.* FROM public.pos_till_sessions s WHERE (p_status IS NULL OR s.status::text=p_status)
  AND (s.operator_user_id=auth.uid() OR public.is_pos_approver() OR public.has_staff_role(ARRAY['finance']::public.staff_role[]))
 ORDER BY s.opened_at DESC LIMIT LEAST(GREATEST(COALESCE(p_limit,50),1),200);
$$;

-- ---------------------------------------------------------------------------
-- 3. Cross-warehouse fulfilment / stock holds. Inventory reservations remain SoR.
-- ---------------------------------------------------------------------------
DO $$ BEGIN
 IF NOT EXISTS(SELECT 1 FROM pg_type WHERE typname='pos_fulfillment_kind') THEN
  CREATE TYPE public.pos_fulfillment_kind AS ENUM('branch_transfer','alternate_pickup','customer_collection','backorder');
 END IF;
 IF NOT EXISTS(SELECT 1 FROM pg_type WHERE typname='pos_fulfillment_status') THEN
  CREATE TYPE public.pos_fulfillment_status AS ENUM('requested','reserved','awaiting_transfer_approval','ready','collected','cancelled','rejected');
 END IF;
END $$;

CREATE TABLE IF NOT EXISTS public.pos_fulfillment_requests (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), document_number TEXT NOT NULL UNIQUE,
 kind public.pos_fulfillment_kind NOT NULL, status public.pos_fulfillment_status NOT NULL DEFAULT 'requested',
 stock_item_id UUID NOT NULL REFERENCES public.stock_items(id), uom_id UUID NOT NULL REFERENCES public.uoms(id),
 qty NUMERIC(18,3) NOT NULL CHECK(qty>0), source_warehouse_id UUID REFERENCES public.warehouses(id),
 destination_warehouse_id UUID REFERENCES public.warehouses(id), customer_id UUID REFERENCES public.customers(id),
 cart_id UUID REFERENCES public.pos_carts(id) ON DELETE SET NULL, invoice_id UUID REFERENCES public.sales_invoices(id) ON DELETE SET NULL,
 stock_entry_id UUID REFERENCES public.stock_entries(id) ON DELETE SET NULL,
 notes TEXT, requested_by UUID NOT NULL REFERENCES auth.users(id), approved_by UUID REFERENCES auth.users(id),
 expires_at TIMESTAMPTZ, ready_at TIMESTAMPTZ, collected_at TIMESTAMPTZ, cancelled_at TIMESTAMPTZ,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 CONSTRAINT pos_fulfillment_transfer_wh_check CHECK(kind<>'branch_transfer' OR
  (source_warehouse_id IS NOT NULL AND destination_warehouse_id IS NOT NULL AND source_warehouse_id<>destination_warehouse_id))
);

CREATE INDEX IF NOT EXISTS pos_fulfillment_status_idx ON public.pos_fulfillment_requests(status,created_at DESC);

CREATE INDEX IF NOT EXISTS pos_fulfillment_item_idx ON public.pos_fulfillment_requests(stock_item_id,source_warehouse_id,status);

CREATE INDEX IF NOT EXISTS pos_fulfillment_customer_idx ON public.pos_fulfillment_requests(customer_id,created_at DESC) WHERE customer_id IS NOT NULL;

ALTER TABLE public.inventory_reservations ALTER COLUMN commerce_order_id DROP NOT NULL;

ALTER TABLE public.inventory_reservations ADD COLUMN IF NOT EXISTS pos_fulfillment_request_id UUID
 REFERENCES public.pos_fulfillment_requests(id) ON DELETE CASCADE;

DO $$ BEGIN
 IF NOT EXISTS(SELECT 1 FROM pg_constraint WHERE conname='inventory_reservations_exact_owner_check') THEN
  ALTER TABLE public.inventory_reservations ADD CONSTRAINT inventory_reservations_exact_owner_check CHECK(
   (commerce_order_id IS NOT NULL AND pos_fulfillment_request_id IS NULL) OR
   (commerce_order_id IS NULL AND pos_fulfillment_request_id IS NOT NULL));
 END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS inventory_reservations_pos_request_uidx
 ON public.inventory_reservations(pos_fulfillment_request_id,stock_item_id,warehouse_id)
 WHERE pos_fulfillment_request_id IS NOT NULL;

CREATE OR REPLACE FUNCTION public.list_pos_stock_availability(p_stock_item_id UUID)
RETURNS TABLE(warehouse_id UUID,warehouse_code TEXT,warehouse_name TEXT,on_hand NUMERIC,reserved NUMERIC,available NUMERIC,transfer_incoming NUMERIC)
LANGUAGE sql STABLE SECURITY DEFINER SET search_path='' AS $$
 SELECT w.id,w.code::text,w.name,COALESCE(sl.quantity,0),
  COALESCE((SELECT SUM(GREATEST(r.reserved_qty-r.consumed_qty,0)) FROM public.inventory_reservations r
    WHERE r.stock_item_id=p_stock_item_id AND r.warehouse_id=w.id AND r.state IN('active','allocated')
      AND (r.state='allocated' OR r.expires_at IS NULL OR r.expires_at>now())),0),
  public.get_inventory_available_quantity(p_stock_item_id,w.id),
  COALESCE((SELECT SUM(sel.qty_base) FROM public.stock_entries se JOIN public.stock_entry_lines sel ON sel.stock_entry_id=se.id
    WHERE se.entry_type='transfer' AND se.status='pending_approval' AND se.to_warehouse_id=w.id AND sel.stock_item_id=p_stock_item_id),0)
 FROM public.warehouses w LEFT JOIN public.stock_levels sl ON sl.warehouse_id=w.id AND sl.stock_item_id=p_stock_item_id
 WHERE w.is_active AND NOT w.is_quarantine ORDER BY public.get_inventory_available_quantity(p_stock_item_id,w.id) DESC,w.code;
$$;

CREATE OR REPLACE FUNCTION public.create_pos_fulfillment_request(
 p_kind public.pos_fulfillment_kind,p_stock_item_id UUID,p_uom_id UUID,p_qty NUMERIC,
 p_source_warehouse_id UUID DEFAULT NULL,p_destination_warehouse_id UUID DEFAULT NULL,p_customer_id UUID DEFAULT NULL,
 p_cart_id UUID DEFAULT NULL,p_invoice_id UUID DEFAULT NULL,p_notes TEXT DEFAULT NULL,p_hold_minutes INTEGER DEFAULT 120)
RETURNS UUID LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE v_id UUID; v_qty_base NUMERIC; v_exp TIMESTAMPTZ; v_avail NUMERIC;
BEGIN
 PERFORM public._require_sales_staff();
 IF COALESCE(p_qty,0)<=0 THEN RAISE EXCEPTION 'qty must be > 0'; END IF;
 IF p_kind='branch_transfer' AND (p_source_warehouse_id IS NULL OR p_destination_warehouse_id IS NULL OR p_source_warehouse_id=p_destination_warehouse_id) THEN
  RAISE EXCEPTION 'branch transfer requires distinct source and destination warehouses';
 END IF;
 IF p_kind IN('alternate_pickup','customer_collection') AND p_source_warehouse_id IS NULL THEN RAISE EXCEPTION 'source warehouse required for stock hold'; END IF;
 v_qty_base:=public.convert_to_base_uom(p_stock_item_id,p_uom_id,p_qty);
 IF p_kind<>'backorder' THEN
  v_avail:=public.get_inventory_available_quantity(p_stock_item_id,p_source_warehouse_id);
  IF v_avail<v_qty_base THEN RAISE EXCEPTION 'insufficient available stock at source warehouse (available %, requested %)',v_avail,v_qty_base; END IF;
  v_exp:=now()+make_interval(mins=>LEAST(GREATEST(COALESCE(p_hold_minutes,120),15),1440));
 END IF;
 INSERT INTO public.pos_fulfillment_requests(document_number,kind,status,stock_item_id,uom_id,qty,source_warehouse_id,destination_warehouse_id,
  customer_id,cart_id,invoice_id,notes,requested_by,expires_at)
 VALUES(public.next_series_value('PFR-'),p_kind,CASE WHEN p_kind='backorder' THEN 'requested'::public.pos_fulfillment_status ELSE 'reserved'::public.pos_fulfillment_status END,
  p_stock_item_id,p_uom_id,p_qty,p_source_warehouse_id,p_destination_warehouse_id,p_customer_id,p_cart_id,p_invoice_id,p_notes,auth.uid(),v_exp)
 RETURNING id INTO v_id;
 IF p_kind<>'backorder' THEN
  INSERT INTO public.inventory_reservations(commerce_order_id,pos_fulfillment_request_id,stock_item_id,warehouse_id,required_qty,reserved_qty,expires_at,state,reservation_reason)
  VALUES(NULL,v_id,p_stock_item_id,p_source_warehouse_id,v_qty_base,v_qty_base,v_exp,'active','pos_'||p_kind::text);
 END IF;
 PERFORM public.emit_domain_event('transfer_pending_approval','pos-fulfillment:'||v_id::text,
  jsonb_build_object('request_id',v_id,'kind',p_kind,'stock_item_id',p_stock_item_id,'qty',p_qty,'source_warehouse_id',p_source_warehouse_id,'destination_warehouse_id',p_destination_warehouse_id));
 RETURN v_id;
END $$;

CREATE OR REPLACE FUNCTION public.approve_pos_fulfillment_request(p_request_id UUID,p_notes TEXT DEFAULT NULL)
RETURNS UUID LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE r public.pos_fulfillment_requests%ROWTYPE; v_entry UUID;
BEGIN
 PERFORM public._require_warehouse_staff(); SELECT * INTO r FROM public.pos_fulfillment_requests WHERE id=p_request_id FOR UPDATE;
 IF NOT FOUND THEN RAISE EXCEPTION 'fulfillment request not found'; END IF;
 IF r.kind<>'branch_transfer' OR r.status<>'reserved' THEN RAISE EXCEPTION 'reserved branch-transfer request required'; END IF;
 UPDATE public.inventory_reservations SET state='released',released_at=now() WHERE pos_fulfillment_request_id=p_request_id AND state='active';
 IF public.get_inventory_available_quantity(r.stock_item_id,r.source_warehouse_id)<public.convert_to_base_uom(r.stock_item_id,r.uom_id,r.qty) THEN RAISE EXCEPTION 'source availability no longer supports request'; END IF;
 v_entry:=public.create_stock_transfer(r.source_warehouse_id,r.destination_warehouse_id,
  concat_ws(' · ',r.document_number,r.notes,p_notes),jsonb_build_array(jsonb_build_object('stock_item_id',r.stock_item_id,'uom_id',r.uom_id,'qty',r.qty,'valuation_method','FIFO')));
 UPDATE public.pos_fulfillment_requests SET status='awaiting_transfer_approval',stock_entry_id=v_entry,approved_by=auth.uid(),updated_at=now() WHERE id=p_request_id;
 RETURN v_entry;
END $$;

CREATE OR REPLACE FUNCTION private.sync_pos_fulfillment_transfer()
RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
BEGIN
 IF NEW.entry_type='transfer' AND OLD.status IS DISTINCT FROM NEW.status THEN
  IF NEW.status='posted' THEN
   UPDATE public.inventory_reservations SET state='released',released_at=now()
    WHERE pos_fulfillment_request_id IN(SELECT id FROM public.pos_fulfillment_requests WHERE stock_entry_id=NEW.id) AND state IN('active','allocated');
   UPDATE public.pos_fulfillment_requests SET status='ready',ready_at=now(),expires_at=NULL,updated_at=now()
    WHERE stock_entry_id=NEW.id AND status='awaiting_transfer_approval';
  ELSIF NEW.status='rejected' THEN
   UPDATE public.inventory_reservations SET state='released',released_at=now()
    WHERE pos_fulfillment_request_id IN(SELECT id FROM public.pos_fulfillment_requests WHERE stock_entry_id=NEW.id) AND state IN('active','allocated');
   UPDATE public.pos_fulfillment_requests SET status='rejected',updated_at=now() WHERE stock_entry_id=NEW.id;
  END IF;
 END IF; RETURN NEW;
END $$;

DROP TRIGGER IF EXISTS trg_sync_pos_fulfillment_transfer ON public.stock_entries;

CREATE TRIGGER trg_sync_pos_fulfillment_transfer AFTER UPDATE OF status ON public.stock_entries
 FOR EACH ROW EXECUTE FUNCTION private.sync_pos_fulfillment_transfer();

CREATE OR REPLACE FUNCTION private.sync_pos_fulfillment_invoice()
RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
BEGIN
 IF NEW.status='posted' AND NEW.cart_id IS NOT NULL THEN
  UPDATE public.inventory_reservations SET state='released',released_at=now()
   WHERE pos_fulfillment_request_id IN(SELECT id FROM public.pos_fulfillment_requests WHERE cart_id=NEW.cart_id AND status='reserved') AND state='active';
  UPDATE public.pos_fulfillment_requests SET invoice_id=NEW.id,status='ready',ready_at=now(),expires_at=NULL,updated_at=now()
   WHERE cart_id=NEW.cart_id AND status='reserved' AND kind IN('alternate_pickup','customer_collection');
 END IF; RETURN NEW;
END $$;

DROP TRIGGER IF EXISTS trg_sync_pos_fulfillment_invoice ON public.sales_invoices;

CREATE TRIGGER trg_sync_pos_fulfillment_invoice AFTER INSERT OR UPDATE OF status ON public.sales_invoices
 FOR EACH ROW EXECUTE FUNCTION private.sync_pos_fulfillment_invoice();

CREATE OR REPLACE FUNCTION public.mark_pos_fulfillment_ready(p_request_id UUID,p_notes TEXT DEFAULT NULL)
RETURNS UUID LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
BEGIN
 PERFORM public._require_sales_staff();
 UPDATE public.pos_fulfillment_requests SET status='ready',ready_at=now(),notes=concat_ws(E'\n',notes,p_notes),updated_at=now()
 WHERE id=p_request_id AND status IN('requested','reserved') AND kind IN('customer_collection','alternate_pickup','backorder');
 IF NOT FOUND THEN RAISE EXCEPTION 'request cannot be marked ready'; END IF; RETURN p_request_id;
END $$;

CREATE OR REPLACE FUNCTION public.collect_pos_fulfillment_request(p_request_id UUID,p_notes TEXT DEFAULT NULL)
RETURNS UUID LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE r public.pos_fulfillment_requests%ROWTYPE; i public.sales_invoices%ROWTYPE;
BEGIN
 PERFORM public._require_sales_staff(); SELECT * INTO r FROM public.pos_fulfillment_requests WHERE id=p_request_id FOR UPDATE;
 IF NOT FOUND OR r.status<>'ready' THEN RAISE EXCEPTION 'ready fulfillment request required'; END IF;
 IF r.invoice_id IS NOT NULL THEN SELECT * INTO i FROM public.sales_invoices WHERE id=r.invoice_id;
  IF NOT FOUND OR i.status<>'posted' OR COALESCE(i.amount_paid,0)+0.01<i.total THEN RAISE EXCEPTION 'order must be fully paid before collection'; END IF;
 END IF;
 IF r.invoice_id IS NULL AND r.kind<>'branch_transfer' THEN RAISE EXCEPTION 'sale/invoice must be linked before customer collection'; END IF;
 UPDATE public.inventory_reservations SET state='released',released_at=now() WHERE pos_fulfillment_request_id=p_request_id AND state IN('active','allocated');
 UPDATE public.pos_fulfillment_requests SET status='collected',collected_at=now(),notes=concat_ws(E'\n',notes,p_notes),updated_at=now() WHERE id=p_request_id;
 RETURN p_request_id;
END $$;

CREATE OR REPLACE FUNCTION public.cancel_pos_fulfillment_request(p_request_id UUID,p_notes TEXT DEFAULT NULL)
RETURNS UUID LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE r public.pos_fulfillment_requests%ROWTYPE;
BEGIN
 PERFORM public._require_sales_staff(); SELECT * INTO r FROM public.pos_fulfillment_requests WHERE id=p_request_id FOR UPDATE;
 IF NOT FOUND OR r.status IN('collected','cancelled','rejected') THEN RAISE EXCEPTION 'active fulfillment request required'; END IF;
 IF r.status='awaiting_transfer_approval' THEN RAISE EXCEPTION 'warehouse transfer already submitted; reject/cancel via warehouse control'; END IF;
 UPDATE public.inventory_reservations SET state='released',released_at=now() WHERE pos_fulfillment_request_id=p_request_id AND state IN('active','allocated');
 UPDATE public.pos_fulfillment_requests SET status='cancelled',cancelled_at=now(),notes=concat_ws(E'\n',notes,p_notes),updated_at=now() WHERE id=p_request_id;
 RETURN p_request_id;
END $$;

CREATE OR REPLACE FUNCTION public.list_pos_fulfillment_requests(p_query TEXT DEFAULT NULL,p_status TEXT DEFAULT NULL,p_limit INTEGER DEFAULT 100)
RETURNS TABLE(id UUID,document_number TEXT,kind TEXT,status TEXT,stock_item_id UUID,oem_part_number TEXT,description TEXT,uom_id UUID,qty NUMERIC,
 source_warehouse_id UUID,source_warehouse_name TEXT,destination_warehouse_id UUID,destination_warehouse_name TEXT,customer_id UUID,cart_id UUID,invoice_id UUID,
 stock_entry_id UUID,expires_at TIMESTAMPTZ,ready_at TIMESTAMPTZ,collected_at TIMESTAMPTZ,created_at TIMESTAMPTZ)
LANGUAGE sql STABLE SECURITY DEFINER SET search_path='' AS $$
 SELECT r.id,r.document_number,r.kind::text,r.status::text,r.stock_item_id,s.oem_part_number::text,s.description,r.uom_id,r.qty,
  r.source_warehouse_id,sw.name,r.destination_warehouse_id,dw.name,r.customer_id,r.cart_id,r.invoice_id,r.stock_entry_id,r.expires_at,r.ready_at,r.collected_at,r.created_at
 FROM public.pos_fulfillment_requests r JOIN public.stock_items s ON s.id=r.stock_item_id
 LEFT JOIN public.warehouses sw ON sw.id=r.source_warehouse_id LEFT JOIN public.warehouses dw ON dw.id=r.destination_warehouse_id
 WHERE (p_status IS NULL OR r.status::text=p_status) AND (p_query IS NULL OR trim(p_query)='' OR r.document_number ILIKE '%'||trim(p_query)||'%' OR s.oem_part_number ILIKE '%'||trim(p_query)||'%' OR COALESCE(s.description,'') ILIKE '%'||trim(p_query)||'%')
 ORDER BY r.created_at DESC LIMIT LEAST(GREATEST(COALESCE(p_limit,100),1),250);
$$;

-- ---------------------------------------------------------------------------
-- 4. Professional return cases + correct external return receipt into Quarantine.
-- ---------------------------------------------------------------------------
DO $$ BEGIN
 IF NOT EXISTS(SELECT 1 FROM pg_type WHERE typname='pos_return_case_status') THEN
  CREATE TYPE public.pos_return_case_status AS ENUM('draft','posted','cancelled');
 END IF;
 IF NOT EXISTS(SELECT 1 FROM pg_type WHERE typname='pos_return_resolution') THEN
  CREATE TYPE public.pos_return_resolution AS ENUM('credit_note','cash_refund','store_credit','replacement','warranty');
 END IF;
 IF NOT EXISTS(SELECT 1 FROM pg_type WHERE typname='pos_return_condition') THEN
  CREATE TYPE public.pos_return_condition AS ENUM('sealed','unopened','opened','damaged','defective');
 END IF;
END $$;

CREATE TABLE IF NOT EXISTS public.pos_return_cases (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), document_number TEXT NOT NULL UNIQUE,
 source_invoice_id UUID NOT NULL REFERENCES public.sales_invoices(id), status public.pos_return_case_status NOT NULL DEFAULT 'draft',
 resolution public.pos_return_resolution NOT NULL, reason_code TEXT NOT NULL, notes TEXT,
 replacement_lines JSONB, till_session_id UUID REFERENCES public.pos_till_sessions(id),
 credit_note_id UUID REFERENCES public.sales_invoices(id), warranty_claim_id UUID REFERENCES public.warranty_claims(id),
 replacement_stock_entry_id UUID REFERENCES public.stock_entries(id), adjustment_journal_entry_id UUID REFERENCES public.journal_entries(id),
 store_credit_ledger_id UUID REFERENCES public.store_credit_ledger(id),
 created_by UUID NOT NULL REFERENCES auth.users(id), approved_by UUID REFERENCES auth.users(id), approved_at TIMESTAMPTZ,
 posted_at TIMESTAMPTZ, cancelled_at TIMESTAMPTZ, created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.pos_return_case_lines (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), return_case_id UUID NOT NULL REFERENCES public.pos_return_cases(id) ON DELETE CASCADE,
 source_invoice_line_id UUID NOT NULL REFERENCES public.sales_invoice_lines(id), stock_item_id UUID NOT NULL REFERENCES public.stock_items(id),
 uom_id UUID NOT NULL REFERENCES public.uoms(id), qty NUMERIC(18,3) NOT NULL CHECK(qty>0), unit_price NUMERIC(18,4) NOT NULL CHECK(unit_price>=0),
 condition public.pos_return_condition NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(return_case_id,source_invoice_line_id)
);

CREATE INDEX IF NOT EXISTS pos_return_cases_invoice_idx ON public.pos_return_cases(source_invoice_id,created_at DESC);

CREATE INDEX IF NOT EXISTS pos_return_case_lines_source_idx ON public.pos_return_case_lines(source_invoice_line_id);

CREATE OR REPLACE FUNCTION private.receive_external_return_to_quarantine(p_lines JSONB,p_notes TEXT)
RETURNS UUID LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE v_quar UUID; v_entry UUID; e JSONB; v_item UUID; v_uom UUID; v_qty NUMERIC; v_base NUMERIC; v_price NUMERIC; v_currency public.currency_code;
BEGIN
 SELECT id INTO v_quar FROM public.warehouses WHERE is_quarantine AND is_active ORDER BY code LIMIT 1;
 IF v_quar IS NULL THEN RAISE EXCEPTION 'quarantine warehouse required'; END IF;
 INSERT INTO public.stock_entries(entry_type,status,document_number,to_warehouse_id,notes,created_by,posted_at)
 VALUES('receipt','posted',public.next_series_value('RCV-'),v_quar,COALESCE(p_notes,'Customer return to Quarantine'),auth.uid(),now()) RETURNING id INTO v_entry;
 FOR e IN SELECT * FROM jsonb_array_elements(COALESCE(p_lines,'[]'::jsonb)) LOOP
  v_item:=(e->>'stock_item_id')::uuid; v_uom:=(e->>'uom_id')::uuid; v_qty:=(e->>'qty')::numeric;
  IF v_qty<=0 THEN RAISE EXCEPTION 'return qty must be > 0'; END IF;
  v_base:=public.convert_to_base_uom(v_item,v_uom,v_qty); v_price:=COALESCE(NULLIF(e->>'unit_price','')::numeric,0);
  v_currency:=COALESCE(NULLIF(e->>'currency','')::public.currency_code,'USD');
  INSERT INTO public.stock_entry_lines(stock_entry_id,stock_item_id,uom_id,qty,qty_base,unit_cost,currency,valuation_method)
   VALUES(v_entry,v_item,v_uom,v_qty,v_base,0,v_currency,'FIFO');
  PERFORM public._adjust_stock_level(v_item,v_quar,v_base,'FIFO',0,v_currency);
  INSERT INTO public.stock_batches(batch_code,stock_item_id,warehouse_id,valuation_method,unit_cost,currency,qty_on_hand)
   VALUES(public.next_series_value('BATCH-'),v_item,v_quar,'FIFO',0,v_currency,v_base);
 END LOOP;
 PERFORM public.emit_domain_event('quarantine_received','pos-external-return:'||v_entry::text,jsonb_build_object('stock_entry_id',v_entry,'notes',p_notes));
 RETURN v_entry;
END $$;

REVOKE ALL ON FUNCTION private.receive_external_return_to_quarantine(JSONB,TEXT) FROM PUBLIC,anon,authenticated;

GRANT EXECUTE ON FUNCTION private.receive_external_return_to_quarantine(JSONB,TEXT) TO service_role;

-- Core-deposit returns are separate from normal sales returns because account 4200 is authoritative.
DO $$ BEGIN
 IF NOT EXISTS(SELECT 1 FROM pg_type WHERE typname='pos_core_return_resolution') THEN
  CREATE TYPE public.pos_core_return_resolution AS ENUM('cash_refund','account_credit','store_credit');
 END IF;
END $$;

CREATE TABLE IF NOT EXISTS public.pos_core_returns (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), document_number TEXT NOT NULL UNIQUE, source_invoice_id UUID NOT NULL REFERENCES public.sales_invoices(id),
 source_core_line_id UUID NOT NULL REFERENCES public.sales_invoice_lines(id), qty NUMERIC(18,3) NOT NULL CHECK(qty>0), amount NUMERIC(18,2) NOT NULL CHECK(amount>0),
 resolution public.pos_core_return_resolution NOT NULL, reason_code TEXT NOT NULL, notes TEXT, till_session_id UUID REFERENCES public.pos_till_sessions(id),
 credit_note_id UUID REFERENCES public.sales_invoices(id), quarantine_stock_entry_id UUID REFERENCES public.stock_entries(id), journal_entry_id UUID REFERENCES public.journal_entries(id),
 store_credit_ledger_id UUID REFERENCES public.store_credit_ledger(id), posted_by UUID NOT NULL REFERENCES auth.users(id), posted_at TIMESTAMPTZ NOT NULL DEFAULT now(), created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS pos_core_returns_source_idx ON public.pos_core_returns(source_core_line_id,posted_at);

CREATE OR REPLACE FUNCTION public.get_pos_invoice_detail(p_invoice_id UUID)
RETURNS JSONB LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path='' AS $$
DECLARE i public.sales_invoices%ROWTYPE; v_lines JSONB;
BEGIN
 PERFORM public._require_sales_staff(); SELECT * INTO i FROM public.sales_invoices WHERE id=p_invoice_id AND status='posted';
 IF NOT FOUND THEN RAISE EXCEPTION 'posted invoice not found'; END IF;
 SELECT COALESCE(jsonb_agg(jsonb_build_object(
   'id',l.id,'stock_item_id',l.stock_item_id,'oem_part_number',s.oem_part_number,'description',s.description,'uom_id',l.uom_id,
   'qty',l.qty,'unit_price',l.unit_price,'line_total',l.line_total,'is_core_charge',l.is_core_charge,
   'returnable_qty',CASE WHEN l.is_core_charge THEN GREATEST(l.qty-COALESCE((SELECT SUM(cr.qty) FROM public.pos_core_returns cr WHERE cr.source_core_line_id=l.id),0),0)
     ELSE GREATEST(l.qty-COALESCE((SELECT SUM(rl.qty) FROM public.pos_return_case_lines rl JOIN public.pos_return_cases rc ON rc.id=rl.return_case_id
       WHERE rl.source_invoice_line_id=l.id AND rc.status='posted'),0),0) END
  ) ORDER BY l.is_core_charge,l.created_at),'[]'::jsonb) INTO v_lines
 FROM public.sales_invoice_lines l JOIN public.stock_items s ON s.id=l.stock_item_id WHERE l.invoice_id=p_invoice_id;
 RETURN jsonb_build_object('id',i.id,'document_number',i.document_number,'customer_id',i.customer_id,'warehouse_id',i.warehouse_id,
  'currency',i.currency,'total',i.total,'amount_paid',i.amount_paid,'posted_at',i.posted_at,'till_session_id',i.till_session_id,'lines',v_lines);
END $$;

CREATE OR REPLACE FUNCTION public.create_pos_return_case(p_invoice_id UUID,p_resolution public.pos_return_resolution,p_reason_code TEXT,p_lines JSONB,
 p_notes TEXT DEFAULT NULL,p_replacement_lines JSONB DEFAULT NULL,p_till_session_id UUID DEFAULT NULL)
RETURNS UUID LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE i public.sales_invoices%ROWTYPE; e JSONB; l public.sales_invoice_lines%ROWTYPE; v_id UUID; v_qty NUMERIC; v_returned NUMERIC;
BEGIN
 PERFORM public._require_sales_staff(); SELECT * INTO i FROM public.sales_invoices WHERE id=p_invoice_id AND status='posted' AND doc_type='invoice';
 IF NOT FOUND THEN RAISE EXCEPTION 'posted source invoice required'; END IF;
 IF trim(COALESCE(p_reason_code,''))='' THEN RAISE EXCEPTION 'return reason required'; END IF;
 IF p_lines IS NULL OR jsonb_typeof(p_lines)<>'array' OR jsonb_array_length(p_lines)=0 THEN RAISE EXCEPTION 'at least one return line required'; END IF;
 IF p_resolution='store_credit' AND i.customer_id IS NULL THEN RAISE EXCEPTION 'store credit requires named customer'; END IF;
 IF p_resolution='replacement' AND (p_replacement_lines IS NULL OR jsonb_typeof(p_replacement_lines)<>'array' OR jsonb_array_length(p_replacement_lines)=0) THEN RAISE EXCEPTION 'replacement lines required'; END IF;
 INSERT INTO public.pos_return_cases(document_number,source_invoice_id,resolution,reason_code,notes,replacement_lines,till_session_id,created_by)
 VALUES(public.next_series_value('RET-'),p_invoice_id,p_resolution,p_reason_code,p_notes,p_replacement_lines,p_till_session_id,auth.uid()) RETURNING id INTO v_id;
 FOR e IN SELECT * FROM jsonb_array_elements(p_lines) LOOP
  SELECT * INTO l FROM public.sales_invoice_lines WHERE id=(e->>'invoice_line_id')::uuid AND invoice_id=p_invoice_id FOR UPDATE;
  IF NOT FOUND OR l.is_core_charge THEN RAISE EXCEPTION 'return line must be a non-core line from source invoice'; END IF;
  v_qty:=COALESCE((e->>'qty')::numeric,0); IF v_qty<=0 THEN RAISE EXCEPTION 'return qty must be > 0'; END IF;
  SELECT COALESCE(SUM(rl.qty),0) INTO v_returned FROM public.pos_return_case_lines rl JOIN public.pos_return_cases rc ON rc.id=rl.return_case_id
   WHERE rl.source_invoice_line_id=l.id AND rc.status='posted';
  IF v_qty>l.qty-v_returned THEN RAISE EXCEPTION 'return qty % exceeds remaining returnable qty %',v_qty,l.qty-v_returned; END IF;
  INSERT INTO public.pos_return_case_lines(return_case_id,source_invoice_line_id,stock_item_id,uom_id,qty,unit_price,condition)
  VALUES(v_id,l.id,l.stock_item_id,l.uom_id,v_qty,l.unit_price,COALESCE(NULLIF(e->>'condition','')::public.pos_return_condition,'opened'));
 END LOOP;
 RETURN v_id;
END $$;

CREATE OR REPLACE FUNCTION private.issue_pos_replacement_stock(p_from_warehouse_id UUID,p_lines JSONB,p_notes TEXT)
RETURNS UUID LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE v_entry UUID; e JSONB; v_item UUID; v_uom UUID; v_qty NUMERIC; v_base NUMERIC; v_cost NUMERIC; v_cur public.currency_code; v_val public.valuation_method; v_serial UUID;
BEGIN
 IF p_lines IS NULL OR jsonb_typeof(p_lines)<>'array' OR jsonb_array_length(p_lines)=0 THEN RAISE EXCEPTION 'replacement lines required'; END IF;
 IF EXISTS(SELECT 1 FROM public.warehouses WHERE id=p_from_warehouse_id AND is_quarantine) THEN RAISE EXCEPTION 'replacement source must be saleable warehouse'; END IF;
 INSERT INTO public.stock_entries(entry_type,status,from_warehouse_id,notes,created_by,posted_at,document_number)
 VALUES('issue','posted',p_from_warehouse_id,COALESCE(p_notes,'POS replacement issue'),auth.uid(),now(),public.next_series_value('ISS-')) RETURNING id INTO v_entry;
 FOR e IN SELECT * FROM jsonb_array_elements(p_lines) LOOP
  v_item:=(e->>'stock_item_id')::uuid; v_uom:=(e->>'uom_id')::uuid; v_qty:=(e->>'qty')::numeric;
  IF v_qty<=0 THEN RAISE EXCEPTION 'replacement qty must be > 0'; END IF;
  v_base:=public.convert_to_base_uom(v_item,v_uom,v_qty); v_val:=COALESCE(NULLIF(e->>'valuation_method','')::public.valuation_method,'FIFO');
  SELECT unit_cost,currency INTO v_cost,v_cur FROM public.stock_levels WHERE stock_item_id=v_item AND warehouse_id=p_from_warehouse_id FOR UPDATE;
  IF COALESCE((SELECT quantity FROM public.stock_levels WHERE stock_item_id=v_item AND warehouse_id=p_from_warehouse_id),0)<v_base THEN RAISE EXCEPTION 'insufficient replacement stock'; END IF;
  PERFORM public._consume_fifo_batches(v_item,p_from_warehouse_id,v_base);
  PERFORM public._adjust_stock_level(v_item,p_from_warehouse_id,-v_base,v_val,COALESCE(v_cost,0),COALESCE(v_cur,'USD'));
  INSERT INTO public.stock_entry_lines(stock_entry_id,stock_item_id,uom_id,qty,qty_base,unit_cost,currency,valuation_method)
   VALUES(v_entry,v_item,v_uom,v_qty,v_base,COALESCE(v_cost,0),COALESCE(v_cur,'USD'),v_val);
  IF e ? 'replacement_serial_id' THEN
   v_serial:=(e->>'replacement_serial_id')::uuid;
   UPDATE public.stock_serials SET status='sold',warehouse_id=NULL WHERE id=v_serial AND stock_item_id=v_item AND status='in_stock';
   IF NOT FOUND THEN RAISE EXCEPTION 'replacement serial unavailable'; END IF;
  END IF;
 END LOOP;
 RETURN v_entry;
END $$;

REVOKE ALL ON FUNCTION private.issue_pos_replacement_stock(UUID,JSONB,TEXT) FROM PUBLIC,anon,authenticated;

GRANT EXECUTE ON FUNCTION private.issue_pos_replacement_stock(UUID,JSONB,TEXT) TO service_role;

CREATE OR REPLACE FUNCTION public.post_pos_return_case(p_return_case_id UUID)
RETURNS UUID LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE c public.pos_return_cases%ROWTYPE; i public.sales_invoices%ROWTYPE; v_lines JSONB; v_cn UUID; v_amount NUMERIC:=0;
 v_adj UUID; v_sc UUID; v_repl UUID; v_claim UUID; v_first_item UUID; v_current NUMERIC; r RECORD; v_till UUID;
BEGIN
 IF NOT public.is_pos_approver() THEN RAISE EXCEPTION 'POS manager approval required'; END IF;
 SELECT * INTO c FROM public.pos_return_cases WHERE id=p_return_case_id FOR UPDATE;
 IF NOT FOUND OR c.status<>'draft' THEN RAISE EXCEPTION 'draft return case required'; END IF;
 SELECT * INTO i FROM public.sales_invoices WHERE id=c.source_invoice_id AND status='posted' AND doc_type='invoice' FOR UPDATE;
 IF NOT FOUND THEN RAISE EXCEPTION 'posted source invoice required'; END IF;
 FOR r IN SELECT rl.source_invoice_line_id,rl.qty,sl.qty sold_qty FROM public.pos_return_case_lines rl JOIN public.sales_invoice_lines sl ON sl.id=rl.source_invoice_line_id WHERE rl.return_case_id=p_return_case_id LOOP
  SELECT COALESCE(SUM(x.qty),0) INTO v_current FROM public.pos_return_case_lines x JOIN public.pos_return_cases xc ON xc.id=x.return_case_id
   WHERE x.source_invoice_line_id=r.source_invoice_line_id AND xc.status='posted';
  IF r.qty+v_current>r.sold_qty THEN RAISE EXCEPTION 'return quantity exceeds remaining sold quantity'; END IF;
 END LOOP;
 SELECT COALESCE(jsonb_agg(jsonb_build_object('stock_item_id',l.stock_item_id,'uom_id',l.uom_id,'qty',l.qty,'unit_price',l.unit_price,'currency',i.currency)),'[]'::jsonb),
        COALESCE(SUM(round(l.qty*l.unit_price,2)),0),min(l.stock_item_id::text)::uuid
 INTO v_lines,v_amount,v_first_item FROM public.pos_return_case_lines l WHERE l.return_case_id=p_return_case_id;
 IF jsonb_array_length(v_lines)=0 THEN RAISE EXCEPTION 'return case has no lines'; END IF;
 v_till:=COALESCE(c.till_session_id,i.till_session_id);

 IF c.resolution IN('credit_note','cash_refund','store_credit') THEN
  v_cn:=public.post_return_credit_note(c.source_invoice_id,v_lines);
  IF c.resolution='cash_refund' THEN
   IF v_till IS NULL THEN RAISE EXCEPTION 'cash refund requires active till session'; END IF;
   IF i.customer_id IS NOT NULL THEN
    v_adj:=public.post_journal_entry(CURRENT_DATE,format('Cash payout for return %s',c.document_number),i.currency,i.exchange_rate_applied,
      jsonb_build_array(jsonb_build_object('account_code','1200','debit',v_amount,'credit',0,'currency',i.currency),jsonb_build_object('account_code','1100','debit',0,'credit',v_amount,'currency',i.currency)));
    UPDATE public.customers SET open_balance=open_balance+v_amount,updated_at=now() WHERE id=i.customer_id;
   END IF;
   PERFORM public.record_pos_till_cash_movement(v_till,'cash_refund',v_amount,'customer_refund',c.notes,NULL);
  ELSIF c.resolution='store_credit' THEN
   IF i.customer_id IS NULL THEN RAISE EXCEPTION 'store credit requires named customer'; END IF;
   v_sc:=public.issue_store_credit(i.customer_id,v_amount,i.currency,i.exchange_rate_applied,format('Return %s store credit',c.document_number),'1200');
   UPDATE public.customers SET open_balance=open_balance+v_amount,updated_at=now() WHERE id=i.customer_id;
  END IF;
 ELSIF c.resolution='replacement' THEN
  PERFORM private.receive_external_return_to_quarantine(v_lines,format('Return %s replacement received',c.document_number));
  v_repl:=private.issue_pos_replacement_stock(i.warehouse_id,c.replacement_lines,format('Return %s replacement issued',c.document_number));
 ELSIF c.resolution='warranty' THEN
  IF jsonb_array_length(v_lines)<>1 THEN RAISE EXCEPTION 'warranty submission supports one claimed item per case'; END IF;
  v_claim:=public.open_warranty_claim(NULL,c.source_invoice_id,NULL,concat_ws(' · ',c.document_number,c.notes));
  UPDATE public.warranty_claims SET stock_item_id=v_first_item WHERE id=v_claim;
 ELSE RAISE EXCEPTION 'unsupported return resolution'; END IF;

 UPDATE public.pos_return_cases SET status='posted',credit_note_id=v_cn,warranty_claim_id=v_claim,replacement_stock_entry_id=v_repl,
  adjustment_journal_entry_id=v_adj,store_credit_ledger_id=v_sc,approved_by=auth.uid(),approved_at=now(),posted_at=now(),updated_at=now()
 WHERE id=p_return_case_id;
 PERFORM public._log_pos_action('return_posted','pos_return_cases',p_return_case_id,NULL,
  jsonb_build_object('resolution',c.resolution,'amount',v_amount,'credit_note_id',v_cn,'warranty_claim_id',v_claim,'replacement_stock_entry_id',v_repl),c.notes);
 UPDATE public.pos_action_audit SET reason_code=c.reason_code,approved_by_user_id=auth.uid(),approval_policy_action='return_post'
 WHERE id=(SELECT id FROM public.pos_action_audit WHERE entity_type='pos_return_cases' AND entity_id=p_return_case_id ORDER BY created_at DESC LIMIT 1);
 RETURN p_return_case_id;
END $$;

CREATE OR REPLACE FUNCTION public.post_pos_core_return(p_invoice_id UUID,p_source_core_line_id UUID,p_qty NUMERIC,p_resolution public.pos_core_return_resolution,
 p_reason_code TEXT,p_till_session_id UUID DEFAULT NULL,p_notes TEXT DEFAULT NULL)
RETURNS UUID LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE i public.sales_invoices%ROWTYPE; l public.sales_invoice_lines%ROWTYPE; v_prev NUMERIC; v_amt NUMERIC; v_j UUID; v_cn UUID; v_id UUID; v_sc UUID; v_stock UUID;
 v_lines JSONB; v_till UUID;
BEGIN
 IF NOT public.is_pos_approver() THEN RAISE EXCEPTION 'POS manager approval required'; END IF;
 SELECT * INTO i FROM public.sales_invoices WHERE id=p_invoice_id AND status='posted' AND doc_type='invoice' FOR UPDATE;
 IF NOT FOUND THEN RAISE EXCEPTION 'posted source invoice required'; END IF;
 SELECT * INTO l FROM public.sales_invoice_lines WHERE id=p_source_core_line_id AND invoice_id=p_invoice_id AND is_core_charge FOR UPDATE;
 IF NOT FOUND THEN RAISE EXCEPTION 'core-charge invoice line required'; END IF;
 IF COALESCE(p_qty,0)<=0 THEN RAISE EXCEPTION 'core return qty must be > 0'; END IF;
 SELECT COALESCE(SUM(qty),0) INTO v_prev FROM public.pos_core_returns WHERE source_core_line_id=l.id;
 IF p_qty>l.qty-v_prev THEN RAISE EXCEPTION 'core return qty exceeds remaining eligible core qty'; END IF;
 IF trim(COALESCE(p_reason_code,''))='' THEN RAISE EXCEPTION 'core return reason required'; END IF;
 v_amt:=round(p_qty*l.unit_price,2); v_till:=COALESCE(p_till_session_id,i.till_session_id);
 IF p_resolution='account_credit' AND i.customer_id IS NULL THEN RAISE EXCEPTION 'account credit requires named customer'; END IF;
 IF p_resolution='store_credit' AND i.customer_id IS NULL THEN RAISE EXCEPTION 'store credit requires named customer'; END IF;
 IF p_resolution='cash_refund' AND v_till IS NULL THEN RAISE EXCEPTION 'cash core refund requires active till session'; END IF;
 v_j:=public.post_journal_entry(CURRENT_DATE,format('Core return against %s',i.document_number),i.currency,i.exchange_rate_applied,
  CASE p_resolution
   WHEN 'cash_refund' THEN jsonb_build_array(jsonb_build_object('account_code','4200','debit',v_amt,'credit',0,'currency',i.currency),jsonb_build_object('account_code','1100','debit',0,'credit',v_amt,'currency',i.currency))
   WHEN 'account_credit' THEN jsonb_build_array(jsonb_build_object('account_code','4200','debit',v_amt,'credit',0,'currency',i.currency),jsonb_build_object('account_code','1200','debit',0,'credit',v_amt,'currency',i.currency))
   ELSE jsonb_build_array(jsonb_build_object('account_code','4200','debit',v_amt,'credit',0,'currency',i.currency),jsonb_build_object('account_code','2200','debit',0,'credit',v_amt,'currency',i.currency)) END);
 IF p_resolution='account_credit' THEN UPDATE public.customers SET open_balance=open_balance-v_amt,updated_at=now() WHERE id=i.customer_id;
 ELSIF p_resolution='store_credit' THEN v_sc:=public._append_store_credit(i.customer_id,'issue',v_amt,i.currency,i.exchange_rate_applied,NULL,v_j,format('Core return %s',i.document_number));
 END IF;
 INSERT INTO public.sales_invoices(doc_type,status,document_number,customer_id,warehouse_id,currency,exchange_rate_applied,subtotal,total,amount_paid,return_against_id,journal_entry_id,
  customer_phone_e164,customer_email,customer_whatsapp_e164,posted_by,posted_at,till_session_id)
 VALUES('credit_note','posted',public.next_series_value('CN-'),i.customer_id,i.warehouse_id,i.currency,i.exchange_rate_applied,v_amt,v_amt,0,p_invoice_id,v_j,
  i.customer_phone_e164,i.customer_email,i.customer_whatsapp_e164,auth.uid(),now(),v_till) RETURNING id INTO v_cn;
 INSERT INTO public.sales_invoice_lines(invoice_id,stock_item_id,parent_line_id,is_core_charge,uom_id,qty,qty_base,unit_price,line_total,qty_fulfilled)
 VALUES(v_cn,l.stock_item_id,NULL,true,l.uom_id,p_qty,public.convert_to_base_uom(l.stock_item_id,l.uom_id,p_qty),l.unit_price,v_amt,public.convert_to_base_uom(l.stock_item_id,l.uom_id,p_qty));
 v_lines:=jsonb_build_array(jsonb_build_object('stock_item_id',l.stock_item_id,'uom_id',l.uom_id,'qty',p_qty,'unit_price',0,'currency',i.currency));
 v_stock:=private.receive_external_return_to_quarantine(v_lines,format('Core returned against %s',i.document_number));
 INSERT INTO public.pos_core_returns(document_number,source_invoice_id,source_core_line_id,qty,amount,resolution,reason_code,notes,till_session_id,credit_note_id,quarantine_stock_entry_id,journal_entry_id,store_credit_ledger_id,posted_by)
 VALUES(public.next_series_value('CORE-'),p_invoice_id,p_source_core_line_id,p_qty,v_amt,p_resolution,p_reason_code,p_notes,v_till,v_cn,v_stock,v_j,v_sc,auth.uid()) RETURNING id INTO v_id;
 IF p_resolution='cash_refund' THEN PERFORM public.record_pos_till_cash_movement(v_till,'cash_refund',v_amt,'customer_refund',concat_ws(' · ','Core return',p_notes),NULL); END IF;
 PERFORM public._log_pos_action('core_return_posted','pos_core_returns',v_id,NULL,jsonb_build_object('invoice_id',p_invoice_id,'amount',v_amt,'resolution',p_resolution,'credit_note_id',v_cn),p_notes);
 UPDATE public.pos_action_audit SET reason_code=p_reason_code,approved_by_user_id=auth.uid(),approval_policy_action='core_return'
 WHERE id=(SELECT id FROM public.pos_action_audit WHERE entity_type='pos_core_returns' AND entity_id=v_id ORDER BY created_at DESC LIMIT 1);
 RETURN v_id;
END $$;

-- Correct POS warranty orchestration. The legacy public warranty primitives remain compatible,
-- while POS decisions use external-return receipt semantics and governed replacement issue.
CREATE OR REPLACE FUNCTION public.find_pos_warranty_serial(p_serial_number TEXT)
RETURNS TABLE(id UUID,serial_number TEXT,stock_item_id UUID,oem_part_number TEXT,status TEXT,warehouse_id UUID)
LANGUAGE sql STABLE SECURITY DEFINER SET search_path='' AS $$
 SELECT ss.id,ss.serial_number,ss.stock_item_id,si.oem_part_number::text,ss.status,ss.warehouse_id
 FROM public.stock_serials ss JOIN public.stock_items si ON si.id=ss.stock_item_id
 WHERE lower(ss.serial_number)=lower(trim(p_serial_number)) LIMIT 1;
$$;

CREATE OR REPLACE FUNCTION public.open_pos_warranty_claim(p_sales_invoice_id UUID,p_invoice_line_id UUID,p_stock_serial_id UUID DEFAULT NULL,p_notes TEXT DEFAULT NULL)
RETURNS UUID LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE l public.sales_invoice_lines%ROWTYPE; s public.stock_serials%ROWTYPE; v_id UUID;
BEGIN
 PERFORM public._require_warranty_staff();
 SELECT * INTO l FROM public.sales_invoice_lines WHERE id=p_invoice_line_id AND invoice_id=p_sales_invoice_id AND NOT is_core_charge;
 IF NOT FOUND THEN RAISE EXCEPTION 'non-core source invoice line required'; END IF;
 IF p_stock_serial_id IS NOT NULL THEN
  SELECT * INTO s FROM public.stock_serials WHERE id=p_stock_serial_id;
  IF NOT FOUND OR s.stock_item_id<>l.stock_item_id THEN RAISE EXCEPTION 'serial does not match claimed invoice item'; END IF;
 END IF;
 v_id:=public.open_warranty_claim(p_stock_serial_id,p_sales_invoice_id,NULL,p_notes);
 UPDATE public.warranty_claims SET stock_item_id=l.stock_item_id WHERE id=v_id;
 RETURN v_id;
END $$;

CREATE OR REPLACE FUNCTION public.list_pos_warranty_claims(p_query TEXT DEFAULT NULL,p_status TEXT DEFAULT NULL,p_limit INTEGER DEFAULT 100)
RETURNS TABLE(id UUID,document_number TEXT,status TEXT,resolution TEXT,sales_invoice_id UUID,invoice_number TEXT,stock_item_id UUID,oem_part_number TEXT,
 stock_serial_id UUID,serial_number TEXT,customer_id UUID,notes TEXT,reject_reason TEXT,credit_note_id UUID,replacement_stock_entry_id UUID,quarantine_stock_entry_id UUID,created_at TIMESTAMPTZ,decided_at TIMESTAMPTZ,closed_at TIMESTAMPTZ)
LANGUAGE sql STABLE SECURITY DEFINER SET search_path='' AS $$
 SELECT w.id,w.document_number,w.status::text,w.resolution::text,w.sales_invoice_id,i.document_number,w.stock_item_id,si.oem_part_number::text,
  w.stock_serial_id,ss.serial_number,w.customer_id,w.notes,w.reject_reason,w.credit_note_id,w.replacement_stock_entry_id,w.quarantine_stock_entry_id,w.created_at,w.decided_at,w.closed_at
 FROM public.warranty_claims w LEFT JOIN public.sales_invoices i ON i.id=w.sales_invoice_id LEFT JOIN public.stock_items si ON si.id=w.stock_item_id LEFT JOIN public.stock_serials ss ON ss.id=w.stock_serial_id
 WHERE (p_status IS NULL OR w.status::text=p_status) AND (p_query IS NULL OR trim(p_query)='' OR w.document_number ILIKE '%'||trim(p_query)||'%' OR COALESCE(i.document_number,'') ILIKE '%'||trim(p_query)||'%' OR COALESCE(si.oem_part_number,'') ILIKE '%'||trim(p_query)||'%' OR COALESCE(ss.serial_number,'') ILIKE '%'||trim(p_query)||'%')
 ORDER BY w.created_at DESC LIMIT LEAST(GREATEST(COALESCE(p_limit,100),1),250);
$$;

CREATE OR REPLACE FUNCTION public.approve_pos_warranty_claim(p_claim_id UUID,p_resolution public.warranty_claim_resolution,p_lines JSONB DEFAULT NULL,p_replacement_lines JSONB DEFAULT NULL)
RETURNS UUID LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE w public.warranty_claims%ROWTYPE; i public.sales_invoices%ROWTYPE; v_lines JSONB; v_uom UUID; v_cn UUID; v_quar UUID; v_repl UUID;
BEGIN
 IF NOT public.is_pos_approver() THEN RAISE EXCEPTION 'POS manager approval required'; END IF;
 SELECT * INTO w FROM public.warranty_claims WHERE id=p_claim_id FOR UPDATE;
 IF NOT FOUND OR w.status<>'open' THEN RAISE EXCEPTION 'open warranty claim required'; END IF;
 IF p_resolution NOT IN('replacement','credit_note','return_only') THEN RAISE EXCEPTION 'invalid warranty approval resolution'; END IF;
 IF w.sales_invoice_id IS NOT NULL THEN SELECT * INTO i FROM public.sales_invoices WHERE id=w.sales_invoice_id; END IF;
 v_lines:=p_lines;
 IF v_lines IS NULL AND w.stock_item_id IS NOT NULL THEN
  SELECT id INTO v_uom FROM public.uoms WHERE code='EA' ORDER BY created_at LIMIT 1;
  IF v_uom IS NULL THEN RAISE EXCEPTION 'EA UOM required for default warranty return'; END IF;
  v_lines:=jsonb_build_array(jsonb_build_object('stock_item_id',w.stock_item_id,'uom_id',v_uom,'qty',1,'unit_price',0,'currency',COALESCE(i.currency,'USD')));
 END IF;
 IF p_resolution='credit_note' THEN
  IF w.sales_invoice_id IS NULL OR p_lines IS NULL OR jsonb_array_length(p_lines)=0 THEN RAISE EXCEPTION 'credit-note warranty requires source invoice and return lines'; END IF;
  v_cn:=public.post_return_credit_note(w.sales_invoice_id,p_lines);
 ELSE
  IF v_lines IS NULL OR jsonb_array_length(v_lines)=0 THEN RAISE EXCEPTION 'physical warranty return lines required'; END IF;
  v_quar:=private.receive_external_return_to_quarantine(v_lines,format('Warranty return %s',w.document_number));
 END IF;
 IF p_resolution='replacement' THEN
  IF p_replacement_lines IS NULL OR jsonb_typeof(p_replacement_lines)<>'array' OR jsonb_array_length(p_replacement_lines)=0 THEN RAISE EXCEPTION 'replacement lines required'; END IF;
  v_repl:=private.issue_pos_replacement_stock(COALESCE(i.warehouse_id,(SELECT id FROM public.warehouses WHERE code='MAIN' AND is_active LIMIT 1)),p_replacement_lines,format('Warranty replacement %s',w.document_number));
 END IF;
 IF w.stock_serial_id IS NOT NULL THEN
  UPDATE public.stock_serials SET status='quarantine',warehouse_id=(SELECT id FROM public.warehouses WHERE is_quarantine AND is_active ORDER BY code LIMIT 1) WHERE id=w.stock_serial_id;
 END IF;
 UPDATE public.warranty_claims SET status='approved',resolution=p_resolution,credit_note_id=v_cn,quarantine_stock_entry_id=v_quar,
  replacement_stock_entry_id=v_repl,decided_by=auth.uid(),decided_at=now(),updated_at=now() WHERE id=p_claim_id;
 PERFORM public.emit_domain_event('warranty_claim_approved','warranty:pos-approved:'||p_claim_id::text,
  jsonb_build_object('warranty_claim_id',p_claim_id,'resolution',p_resolution,'credit_note_id',v_cn,'quarantine_stock_entry_id',v_quar,'replacement_stock_entry_id',v_repl));
 PERFORM public._log_pos_action('warranty_approved','warranty_claims',p_claim_id,to_jsonb(w),(SELECT to_jsonb(x) FROM public.warranty_claims x WHERE x.id=p_claim_id),NULL);
 RETURN p_claim_id;
END $$;

CREATE OR REPLACE FUNCTION public.reject_pos_warranty_claim(p_claim_id UUID,p_reason TEXT)
RETURNS UUID LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
BEGIN
 IF NOT public.is_pos_approver() THEN RAISE EXCEPTION 'POS manager approval required'; END IF;
 IF trim(COALESCE(p_reason,''))='' THEN RAISE EXCEPTION 'rejection reason required'; END IF;
 RETURN public.reject_warranty_claim(p_claim_id,p_reason);
END $$;

-- ---------------------------------------------------------------------------
-- 5. Reserve-first staff checkout + provider-linked payment recovery.
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.prepare_pos_commerce_checkout(p_cart_id UUID,p_checkout_request_id UUID,p_reservation_ttl INTERVAL DEFAULT interval '20 minutes')
RETURNS UUID LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE c public.pos_carts%ROWTYPE; o public.commerce_orders%ROWTYPE; v_order UUID; v_customer UUID; r RECORD; v_available NUMERIC; v_existing UUID; s public.pos_till_sessions%ROWTYPE;
BEGIN
 PERFORM public._require_sales_staff();
 IF p_checkout_request_id IS NULL THEN RAISE EXCEPTION 'checkout_request_id required'; END IF;
 IF p_reservation_ttl IS NULL OR p_reservation_ttl<interval '2 minutes' OR p_reservation_ttl>interval '60 minutes' THEN RAISE EXCEPTION 'reservation TTL must be between 2 and 60 minutes'; END IF;
 SELECT * INTO c FROM public.pos_carts WHERE id=p_cart_id FOR UPDATE;
 IF NOT FOUND OR c.status<>'open' THEN RAISE EXCEPTION 'open POS cart required'; END IF;
 IF c.created_by IS DISTINCT FROM auth.uid() THEN RAISE EXCEPTION 'POS cart belongs to another operator'; END IF;
 IF c.till_session_id IS NULL THEN RAISE EXCEPTION 'open till session required before payment'; END IF;
 SELECT * INTO s FROM public.pos_till_sessions WHERE id=c.till_session_id FOR UPDATE;
 IF NOT FOUND OR s.status<>'open' OR s.operator_user_id<>auth.uid() OR s.warehouse_id<>c.warehouse_id THEN RAISE EXCEPTION 'active operator till session at cart warehouse required'; END IF;
 IF NOT EXISTS(SELECT 1 FROM public.pos_cart_lines WHERE cart_id=p_cart_id) THEN RAISE EXCEPTION 'cart has no lines'; END IF;
 IF c.checkout_order_id IS NOT NULL THEN
  SELECT * INTO o FROM public.commerce_orders WHERE id=c.checkout_order_id FOR UPDATE;
  IF FOUND AND o.finalized_at IS NULL AND o.state IN('awaiting_payment','payment_processing','payment_failed') AND o.reservation_expires_at>now() THEN RETURN o.id; END IF;
  IF FOUND AND o.finalized_at IS NULL AND o.state IN('awaiting_payment','payment_processing','payment_failed') THEN PERFORM private.release_commerce_order(o.id,'payment_expired','POS checkout reservation expired before retry'); END IF;
  SELECT * INTO c FROM public.pos_carts WHERE id=p_cart_id FOR UPDATE;
 END IF;
 SELECT id INTO v_existing FROM public.commerce_orders WHERE checkout_request_id=p_checkout_request_id AND cart_id=p_cart_id;
 IF v_existing IS NOT NULL THEN RETURN v_existing; END IF;
 v_customer:=c.customer_id;
 IF v_customer IS NULL THEN v_customer:=public.ensure_pos_walkin_customer(); UPDATE public.pos_carts SET customer_id=v_customer,updated_at=now() WHERE id=p_cart_id; END IF;
 INSERT INTO public.commerce_orders(customer_id,cart_id,checkout_request_id,warehouse_id,fulfillment_mode,currency,exchange_rate_applied,subtotal,total,state,checkout_snapshot,reservation_expires_at)
 SELECT v_customer,c.id,p_checkout_request_id,c.warehouse_id,c.fulfillment_mode,c.currency,c.exchange_rate_applied,
  COALESCE(SUM(l.line_total),0),COALESCE(SUM(l.line_total),0),'checkout_pending',
  jsonb_build_object('cart_id',c.id,'till_session_id',c.till_session_id,'customer_id',v_customer,'warehouse_id',c.warehouse_id,'currency',c.currency,'fulfillment_mode',c.fulfillment_mode,
   'lines',COALESCE(jsonb_agg(jsonb_build_object('line_id',l.id,'stock_item_id',l.stock_item_id,'uom_id',l.uom_id,'qty',l.qty,'qty_base',l.qty_base,'unit_price',l.unit_price,'line_total',l.line_total,'is_core_charge',l.is_core_charge) ORDER BY l.created_at),'[]'::jsonb)),
  now()+p_reservation_ttl
 FROM public.pos_cart_lines l WHERE l.cart_id=c.id GROUP BY c.id,c.warehouse_id,c.fulfillment_mode,c.currency,c.exchange_rate_applied,c.till_session_id
 RETURNING id INTO v_order;
 FOR r IN SELECT stock_item_id,SUM(qty_base)::numeric required_qty FROM public.pos_cart_lines WHERE cart_id=p_cart_id AND COALESCE(issues_stock,true)=true AND NOT is_core_charge GROUP BY stock_item_id ORDER BY stock_item_id LOOP
  v_available:=public.get_inventory_available_quantity(r.stock_item_id,c.warehouse_id);
  IF v_available<r.required_qty THEN RAISE EXCEPTION 'insufficient available stock for item % (available %, required %)',r.stock_item_id,v_available,r.required_qty; END IF;
  INSERT INTO public.inventory_reservations(commerce_order_id,stock_item_id,warehouse_id,required_qty,reserved_qty,expires_at,state,reservation_reason)
  VALUES(v_order,r.stock_item_id,c.warehouse_id,r.required_qty,r.required_qty,now()+p_reservation_ttl,'active','pos_checkout_payment_hold');
 END LOOP;
 UPDATE public.commerce_orders SET state='awaiting_payment',updated_at=now() WHERE id=v_order;
 UPDATE public.pos_carts SET checkout_locked_at=now(),checkout_order_id=v_order,updated_at=now() WHERE id=p_cart_id;
 PERFORM private.enqueue_commerce_event('commerce:'||v_order::text||':pos-prepared','commerce.checkout_prepared',v_order,jsonb_build_object('cart_id',p_cart_id,'till_session_id',c.till_session_id));
 RETURN v_order;
EXCEPTION WHEN unique_violation THEN
 SELECT id INTO v_existing FROM public.commerce_orders WHERE checkout_request_id=p_checkout_request_id AND cart_id=p_cart_id; IF v_existing IS NOT NULL THEN RETURN v_existing; END IF; RAISE;
END $$;

CREATE OR REPLACE FUNCTION public.get_pos_payment_status(p_order_id UUID)
RETURNS JSONB LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path='' AS $$
DECLARE o public.commerce_orders%ROWTYPE; v_status TEXT; v_failure TEXT; v_ex JSONB;
BEGIN
 PERFORM public._require_sales_staff(); SELECT * INTO o FROM public.commerce_orders WHERE id=p_order_id;
 IF NOT FOUND THEN RAISE EXCEPTION 'commerce order not found'; END IF;
 IF o.active_payment_provider='ecocash' THEN SELECT status::text,failure_reason INTO v_status,v_failure FROM public.ecocash_payment_intents WHERE id=o.active_payment_intent_id;
 ELSIF o.active_payment_provider='paynow' THEN SELECT status::text,failure_reason INTO v_status,v_failure FROM public.paynow_payment_intents WHERE id=o.active_payment_intent_id;
 ELSIF o.active_payment_provider='contipay' THEN SELECT status::text,failure_reason INTO v_status,v_failure FROM public.contipay_payment_intents WHERE id=o.active_payment_intent_id; END IF;
 SELECT COALESCE(jsonb_agg(jsonb_build_object('id',e.id,'provider',e.provider,'code',e.exception_code,'detail',e.detail,'resolved_at',e.resolved_at,'resolution',e.resolution,'created_at',e.created_at) ORDER BY e.created_at DESC),'[]'::jsonb)
 INTO v_ex FROM public.commerce_payment_exceptions e WHERE e.commerce_order_id=p_order_id;
 RETURN jsonb_build_object('order_id',o.id,'cart_id',o.cart_id,'state',o.state,'total',o.total,'currency',o.currency,'reservation_expires_at',o.reservation_expires_at,
  'active_provider',o.active_payment_provider,'active_intent_id',o.active_payment_intent_id,'provider_status',v_status,'provider_failure',v_failure,
  'settled_payment_entry_id',o.settled_payment_entry_id,'settled_provider',o.settled_provider,'settled_provider_ref',o.settled_provider_ref,
  'sales_invoice_id',o.sales_invoice_id,'payment_exception',o.payment_exception,'exceptions',v_ex,'finalized_at',o.finalized_at);
END $$;

CREATE OR REPLACE FUNCTION public.create_pos_ecocash_intent(p_order_id UUID,p_payer_msisdn TEXT,p_external_ref TEXT,p_metadata JSONB DEFAULT '{}'::jsonb)
RETURNS UUID LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE o public.commerce_orders%ROWTYPE; v_id UUID; v_msisdn TEXT;
BEGIN
 PERFORM public._require_payments_staff(); SELECT * INTO o FROM public.commerce_orders WHERE id=p_order_id FOR UPDATE;
 IF NOT FOUND OR o.settled_payment_entry_id IS NOT NULL THEN RAISE EXCEPTION 'unsettled commerce order required'; END IF;
 IF o.reservation_expires_at IS NULL OR o.reservation_expires_at<=now() THEN RAISE EXCEPTION 'commerce reservation expired'; END IF;
 IF o.state='payment_processing' AND o.active_payment_provider IS DISTINCT FROM 'ecocash' THEN RAISE EXCEPTION 'another payment provider is already processing'; END IF;
 SELECT id INTO v_id FROM public.ecocash_payment_intents WHERE commerce_order_id=p_order_id AND status IN('pending','authorized') ORDER BY created_at DESC LIMIT 1;
 IF v_id IS NOT NULL THEN RETURN v_id; END IF;
 v_msisdn:=regexp_replace(trim(COALESCE(p_payer_msisdn,'')),'[^0-9]','','g'); IF v_msisdn~'^0[0-9]{9}$' THEN v_msisdn:='263'||substr(v_msisdn,2); END IF;
 IF v_msisdn!~'^263[0-9]{9}$' THEN RAISE EXCEPTION 'payer_msisdn must normalize to 263XXXXXXXXX'; END IF;
 INSERT INTO public.ecocash_payment_intents(external_ref,payer_msisdn,payer_mode,channel,customer_id,amount,currency,exchange_rate_applied,commerce_order_id,metadata,created_by)
 VALUES(trim(p_external_ref),v_msisdn,'pos_entered','pos',o.customer_id,o.total,o.currency,o.exchange_rate_applied,o.id,COALESCE(p_metadata,'{}'::jsonb)||jsonb_build_object('pos_commerce_order_id',o.id),auth.uid()) RETURNING id INTO v_id;
 UPDATE public.commerce_orders SET state='payment_processing',active_payment_provider='ecocash',active_payment_intent_id=v_id,payment_exception=NULL,updated_at=now() WHERE id=o.id;
 RETURN v_id;
END $$;

CREATE OR REPLACE FUNCTION public.create_pos_paynow_intent(p_order_id UUID,p_method public.paynow_method,p_external_ref TEXT,p_metadata JSONB DEFAULT '{}'::jsonb)
RETURNS UUID LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE o public.commerce_orders%ROWTYPE; v_id UUID;
BEGIN
 PERFORM public._require_payments_staff(); SELECT * INTO o FROM public.commerce_orders WHERE id=p_order_id FOR UPDATE;
 IF NOT FOUND OR o.settled_payment_entry_id IS NOT NULL THEN RAISE EXCEPTION 'unsettled commerce order required'; END IF;
 IF o.reservation_expires_at IS NULL OR o.reservation_expires_at<=now() THEN RAISE EXCEPTION 'commerce reservation expired'; END IF;
 IF o.state='payment_processing' AND o.active_payment_provider IS DISTINCT FROM 'paynow' THEN RAISE EXCEPTION 'another payment provider is already processing'; END IF;
 SELECT id INTO v_id FROM public.paynow_payment_intents WHERE commerce_order_id=p_order_id AND status IN('pending','authorized') ORDER BY created_at DESC LIMIT 1;
 IF v_id IS NOT NULL THEN RETURN v_id; END IF;
 INSERT INTO public.paynow_payment_intents(external_ref,method,customer_id,amount,currency,exchange_rate_applied,commerce_order_id,metadata,created_by)
 VALUES(trim(p_external_ref),p_method,o.customer_id,o.total,o.currency,o.exchange_rate_applied,o.id,COALESCE(p_metadata,'{}'::jsonb)||jsonb_build_object('pos_commerce_order_id',o.id),auth.uid()) RETURNING id INTO v_id;
 UPDATE public.commerce_orders SET state='payment_processing',active_payment_provider='paynow',active_payment_intent_id=v_id,payment_exception=NULL,updated_at=now() WHERE id=o.id;
 RETURN v_id;
END $$;

CREATE OR REPLACE FUNCTION public.create_pos_contipay_intent(p_order_id UUID,p_method public.contipay_method,p_external_ref TEXT,p_metadata JSONB DEFAULT '{}'::jsonb)
RETURNS UUID LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE o public.commerce_orders%ROWTYPE; v_id UUID;
BEGIN
 PERFORM public._require_payments_staff(); SELECT * INTO o FROM public.commerce_orders WHERE id=p_order_id FOR UPDATE;
 IF NOT FOUND OR o.settled_payment_entry_id IS NOT NULL THEN RAISE EXCEPTION 'unsettled commerce order required'; END IF;
 IF o.reservation_expires_at IS NULL OR o.reservation_expires_at<=now() THEN RAISE EXCEPTION 'commerce reservation expired'; END IF;
 IF o.state='payment_processing' AND o.active_payment_provider IS DISTINCT FROM 'contipay' THEN RAISE EXCEPTION 'another payment provider is already processing'; END IF;
 SELECT id INTO v_id FROM public.contipay_payment_intents WHERE commerce_order_id=p_order_id AND status IN('pending','authorized') ORDER BY created_at DESC LIMIT 1;
 IF v_id IS NOT NULL THEN RETURN v_id; END IF;
 INSERT INTO public.contipay_payment_intents(external_ref,method,customer_id,amount,currency,exchange_rate_applied,commerce_order_id,metadata,created_by)
 VALUES(trim(p_external_ref),p_method,o.customer_id,o.total,o.currency,o.exchange_rate_applied,o.id,COALESCE(p_metadata,'{}'::jsonb)||jsonb_build_object('pos_commerce_order_id',o.id),auth.uid()) RETURNING id INTO v_id;
 UPDATE public.commerce_orders SET state='payment_processing',active_payment_provider='contipay',active_payment_intent_id=v_id,payment_exception=NULL,updated_at=now() WHERE id=o.id;
 RETURN v_id;
END $$;

CREATE OR REPLACE FUNCTION public.cancel_pos_commerce_checkout(p_order_id UUID,p_reason TEXT)
RETURNS UUID LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE o public.commerce_orders%ROWTYPE; v_status TEXT;
BEGIN
 PERFORM public._require_sales_staff(); SELECT * INTO o FROM public.commerce_orders WHERE id=p_order_id FOR UPDATE;
 IF NOT FOUND THEN RAISE EXCEPTION 'commerce order not found'; END IF;
 IF o.settled_payment_entry_id IS NOT NULL THEN RAISE EXCEPTION 'settled order cannot be cancelled'; END IF;
 IF o.active_payment_provider='ecocash' THEN SELECT status::text INTO v_status FROM public.ecocash_payment_intents WHERE id=o.active_payment_intent_id;
 ELSIF o.active_payment_provider='paynow' THEN SELECT status::text INTO v_status FROM public.paynow_payment_intents WHERE id=o.active_payment_intent_id;
 ELSIF o.active_payment_provider='contipay' THEN SELECT status::text INTO v_status FROM public.contipay_payment_intents WHERE id=o.active_payment_intent_id; END IF;
 IF v_status IN('pending','authorized') THEN RAISE EXCEPTION 'provider payment still pending; confirm failure/cancellation before releasing stock'; END IF;
 PERFORM private.release_commerce_order(o.id,'cancelled',COALESCE(NULLIF(trim(p_reason),''),'POS checkout cancelled'));
 RETURN o.id;
END $$;

CREATE OR REPLACE FUNCTION public.list_pos_payment_recovery(p_limit INTEGER DEFAULT 100)
RETURNS TABLE(order_id UUID,cart_id UUID,state TEXT,total NUMERIC,currency TEXT,active_provider TEXT,active_intent_id UUID,settled_provider TEXT,settled_provider_ref TEXT,
 sales_invoice_id UUID,payment_exception TEXT,reservation_expires_at TIMESTAMPTZ,updated_at TIMESTAMPTZ,open_exception_count BIGINT)
LANGUAGE sql STABLE SECURITY DEFINER SET search_path='' AS $$
 SELECT o.id,o.cart_id,o.state::text,o.total,o.currency::text,o.active_payment_provider,o.active_payment_intent_id,o.settled_provider,o.settled_provider_ref,
  o.sales_invoice_id,o.payment_exception,o.reservation_expires_at,o.updated_at,
  (SELECT count(*) FROM public.commerce_payment_exceptions e WHERE e.commerce_order_id=o.id AND e.resolved_at IS NULL)
 FROM public.commerce_orders o WHERE o.state IN('payment_processing','payment_failed','allocation_pending') OR o.payment_exception IS NOT NULL OR
  EXISTS(SELECT 1 FROM public.commerce_payment_exceptions e WHERE e.commerce_order_id=o.id AND e.resolved_at IS NULL)
 ORDER BY o.updated_at DESC LIMIT LEAST(GREATEST(COALESCE(p_limit,100),1),250);
$$;

CREATE OR REPLACE FUNCTION public.repair_pos_paid_order(p_order_id UUID,p_notes TEXT DEFAULT NULL)
RETURNS UUID LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE o public.commerce_orders%ROWTYPE; pe public.payment_entries%ROWTYPE; r RECORD; v_other NUMERIC; v_inv UUID; v_redeem UUID;
BEGIN
 IF NOT (public.is_pos_approver() OR public.has_staff_role(ARRAY['finance']::public.staff_role[])) THEN RAISE EXCEPTION 'manager or finance approval required'; END IF;
 SELECT * INTO o FROM public.commerce_orders WHERE id=p_order_id FOR UPDATE;
 IF NOT FOUND OR o.sales_invoice_id IS NOT NULL OR o.settled_payment_entry_id IS NULL OR o.state<>'allocation_pending' THEN RAISE EXCEPTION 'paid-but-unfinalized commerce order required'; END IF;
 SELECT * INTO pe FROM public.payment_entries WHERE id=o.settled_payment_entry_id FOR UPDATE;
 IF NOT FOUND OR pe.status<>'posted' OR pe.store_credit_issued+0.01<o.total THEN RAISE EXCEPTION 'unapplied posted provider receipt required for safe repair'; END IF;
 FOR r IN SELECT * FROM public.inventory_reservations WHERE commerce_order_id=o.id FOR UPDATE LOOP
  SELECT COALESCE(SUM(GREATEST(x.reserved_qty-x.consumed_qty,0)),0) INTO v_other FROM public.inventory_reservations x
   WHERE x.stock_item_id=r.stock_item_id AND x.warehouse_id=r.warehouse_id AND x.id<>r.id AND x.state IN('active','allocated') AND (x.state='allocated' OR x.expires_at IS NULL OR x.expires_at>now());
  IF COALESCE((SELECT quantity FROM public.stock_levels WHERE stock_item_id=r.stock_item_id AND warehouse_id=r.warehouse_id),0)-v_other<r.required_qty THEN
   RAISE EXCEPTION 'cannot repair paid order: stock no longer available for item %',r.stock_item_id;
  END IF;
 END LOOP;
 UPDATE public.inventory_reservations SET state='active',reserved_qty=required_qty,consumed_qty=0,expires_at=now()+interval '20 minutes',released_at=NULL WHERE commerce_order_id=o.id;
 UPDATE public.commerce_orders SET state='payment_failed',reservation_expires_at=now()+interval '20 minutes',updated_at=now() WHERE id=o.id;
 v_inv:=private.finalize_commerce_order(o.id);
 v_redeem:=public.redeem_store_credit(o.customer_id,o.total,o.currency,o.exchange_rate_applied,v_inv,concat_ws(' · ','Recovery of captured provider payment',p_notes));
 UPDATE public.commerce_orders SET state=CASE WHEN fulfillment_mode='dispatch' THEN 'allocation_pending'::public.commerce_order_state ELSE 'paid'::public.commerce_order_state END,
  payment_exception=NULL,updated_at=now() WHERE id=o.id;
 UPDATE public.commerce_payment_exceptions SET resolved_at=now(),resolution=concat_ws(' · ','order repaired; unapplied receipt redeemed to finalized invoice',p_notes)
 WHERE commerce_order_id=o.id AND resolved_at IS NULL AND exception_code='PAID_ORDER_FINALIZATION_FAILED';
 PERFORM public._log_pos_action('paid_order_repaired','commerce_orders',o.id,to_jsonb(o),(SELECT to_jsonb(x) FROM public.commerce_orders x WHERE x.id=o.id),p_notes);
 RETURN v_inv;
END $$;

-- ---------------------------------------------------------------------------
-- 6. Governed approval wrappers with reason codes and configurable thresholds.
-- ---------------------------------------------------------------------------
INSERT INTO public.pos_approval_policies(action,threshold_value,always_require_manager,reason_required) VALUES
 ('void_cart',0,true,true),('warranty_decision',0,true,true)
ON CONFLICT(action) DO NOTHING;

INSERT INTO public.pos_approval_reason_codes(action,code,label,sort_order) VALUES
 ('void_cart','customer_cancelled','Customer cancelled sale',10),('void_cart','operator_error','Operator entry error',20),('void_cart','duplicate_cart','Duplicate cart',30),
 ('warranty_decision','verified_defect','Verified defect',10),('warranty_decision','policy_exception','Approved policy exception',20),('warranty_decision','claim_rejected','Claim does not meet warranty policy',30)
ON CONFLICT(action,code) DO NOTHING;

CREATE OR REPLACE FUNCTION private.require_pos_reason(p_action TEXT,p_reason_code TEXT)
RETURNS void LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path='' AS $$
BEGIN
 IF COALESCE((SELECT reason_required FROM public.pos_approval_policies WHERE action=p_action),true) THEN
  IF trim(COALESCE(p_reason_code,''))='' OR NOT EXISTS(SELECT 1 FROM public.pos_approval_reason_codes WHERE action=p_action AND code=p_reason_code AND is_active) THEN
   RAISE EXCEPTION 'valid active reason code required for %',p_action;
  END IF;
 END IF;
END $$;

REVOKE ALL ON FUNCTION private.require_pos_reason(TEXT,TEXT) FROM PUBLIC,anon,authenticated;

CREATE OR REPLACE FUNCTION public.apply_pos_cart_discount_governed(p_cart_id UUID,p_discount_percent NUMERIC,p_reason_code TEXT,p_notes TEXT DEFAULT NULL)
RETURNS UUID LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE c public.pos_carts%ROWTYPE; v_before JSONB; v_after JSONB; v_audit UUID;
BEGIN
 PERFORM public._require_sales_staff(); PERFORM private.require_pos_reason('discount_percent',p_reason_code);
 IF p_discount_percent IS NULL OR p_discount_percent<0 OR p_discount_percent>100 THEN RAISE EXCEPTION 'discount percent must be between 0 and 100'; END IF;
 IF public.pos_action_requires_manager('discount_percent',p_discount_percent) AND NOT public.is_pos_approver() THEN RAISE EXCEPTION 'POS manager approval required'; END IF;
 PERFORM public._require_cart_mutate(p_cart_id); SELECT * INTO c FROM public.pos_carts WHERE id=p_cart_id FOR UPDATE;
 IF NOT FOUND OR c.status<>'open' THEN RAISE EXCEPTION 'open cart required'; END IF;
 SELECT COALESCE(jsonb_agg(jsonb_build_object('id',id,'unit_price',unit_price,'line_total',line_total,'is_core_charge',is_core_charge)),'[]'::jsonb) INTO v_before FROM public.pos_cart_lines WHERE cart_id=p_cart_id;
 UPDATE public.pos_cart_lines SET unit_price=round(unit_price*(1-p_discount_percent/100.0),4),line_total=round(round(unit_price*(1-p_discount_percent/100.0),4)*qty,2)
  WHERE cart_id=p_cart_id AND NOT is_core_charge;
 UPDATE public.pos_carts SET updated_at=now() WHERE id=p_cart_id;
 SELECT COALESCE(jsonb_agg(jsonb_build_object('id',id,'unit_price',unit_price,'line_total',line_total,'is_core_charge',is_core_charge)),'[]'::jsonb) INTO v_after FROM public.pos_cart_lines WHERE cart_id=p_cart_id;
 v_audit:=public._log_pos_action('discount_applied','pos_carts',p_cart_id,v_before,v_after||jsonb_build_object('discount_percent',p_discount_percent),concat_ws(' · ',p_reason_code,p_notes));
 UPDATE public.pos_action_audit SET reason_code=p_reason_code,approved_by_user_id=CASE WHEN public.is_pos_approver() THEN auth.uid() ELSE NULL END,approval_policy_action='discount_percent' WHERE id=v_audit;
 RETURN p_cart_id;
END $$;

CREATE OR REPLACE FUNCTION public.apply_pos_line_price_override_governed(p_line_id UUID,p_unit_price NUMERIC,p_reason_code TEXT,p_notes TEXT DEFAULT NULL)
RETURNS UUID LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE l public.pos_cart_lines%ROWTYPE; c public.pos_carts%ROWTYPE; v_delta NUMERIC; v_before JSONB; v_after JSONB; v_audit UUID;
BEGIN
 PERFORM public._require_sales_staff(); PERFORM private.require_pos_reason('price_override_delta_percent',p_reason_code);
 IF p_unit_price IS NULL OR p_unit_price<0 THEN RAISE EXCEPTION 'unit price must be >= 0'; END IF;
 SELECT * INTO l FROM public.pos_cart_lines WHERE id=p_line_id FOR UPDATE; IF NOT FOUND OR l.is_core_charge THEN RAISE EXCEPTION 'non-core cart line required'; END IF;
 v_delta:=CASE WHEN l.unit_price=0 THEN CASE WHEN p_unit_price=0 THEN 0 ELSE 100 END ELSE abs(p_unit_price-l.unit_price)/l.unit_price*100 END;
 IF public.pos_action_requires_manager('price_override_delta_percent',v_delta) AND NOT public.is_pos_approver() THEN RAISE EXCEPTION 'POS manager approval required'; END IF;
 SELECT * INTO c FROM public.pos_carts WHERE id=l.cart_id FOR UPDATE; IF NOT FOUND OR c.status<>'open' THEN RAISE EXCEPTION 'open cart required'; END IF;
 PERFORM public._require_cart_mutate(c.id); v_before:=jsonb_build_object('id',l.id,'unit_price',l.unit_price,'line_total',l.line_total,'qty',l.qty);
 UPDATE public.pos_cart_lines SET unit_price=round(p_unit_price,4),line_total=round(round(p_unit_price,4)*qty,2) WHERE id=p_line_id RETURNING * INTO l;
 v_after:=jsonb_build_object('id',l.id,'unit_price',l.unit_price,'line_total',l.line_total,'qty',l.qty,'delta_percent',v_delta);
 v_audit:=public._log_pos_action('price_override','pos_cart_line',p_line_id,v_before,v_after,concat_ws(' · ',p_reason_code,p_notes));
 UPDATE public.pos_action_audit SET reason_code=p_reason_code,approved_by_user_id=CASE WHEN public.is_pos_approver() THEN auth.uid() ELSE NULL END,approval_policy_action='price_override_delta_percent' WHERE id=v_audit;
 RETURN p_line_id;
END $$;

CREATE OR REPLACE FUNCTION public.void_pos_cart_governed(p_cart_id UUID,p_reason_code TEXT,p_notes TEXT DEFAULT NULL)
RETURNS UUID LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE v UUID;
BEGIN
 PERFORM private.require_pos_reason('void_cart',p_reason_code);
 IF public.pos_action_requires_manager('void_cart',0) AND NOT public.is_pos_approver() THEN RAISE EXCEPTION 'POS manager approval required'; END IF;
 v:=public.void_pos_cart(p_cart_id,concat_ws(' · ',p_reason_code,p_notes));
 UPDATE public.pos_action_audit SET reason_code=p_reason_code,approved_by_user_id=CASE WHEN public.is_pos_approver() THEN auth.uid() ELSE NULL END,approval_policy_action='void_cart'
 WHERE id=(SELECT id FROM public.pos_action_audit WHERE entity_type='pos_carts' AND entity_id=p_cart_id ORDER BY created_at DESC LIMIT 1);
 RETURN v;
END $$;

CREATE OR REPLACE FUNCTION public.set_pos_approval_policy(p_action TEXT,p_threshold_value NUMERIC,p_always_require_manager BOOLEAN,p_reason_required BOOLEAN)
RETURNS TEXT LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
BEGIN
 IF NOT public.has_staff_role(ARRAY['admin']::public.staff_role[]) THEN RAISE EXCEPTION 'admin role required'; END IF;
 IF COALESCE(p_threshold_value,-1)<0 THEN RAISE EXCEPTION 'threshold must be >= 0'; END IF;
 INSERT INTO public.pos_approval_policies(action,threshold_value,always_require_manager,reason_required,updated_by,updated_at)
 VALUES(p_action,p_threshold_value,p_always_require_manager,p_reason_required,auth.uid(),now())
 ON CONFLICT(action) DO UPDATE SET threshold_value=EXCLUDED.threshold_value,always_require_manager=EXCLUDED.always_require_manager,
  reason_required=EXCLUDED.reason_required,updated_by=auth.uid(),updated_at=now(); RETURN p_action;
END $$;

-- ---------------------------------------------------------------------------
-- 7. Naming, RLS and grants for new POS operational objects.
-- ---------------------------------------------------------------------------
INSERT INTO public.naming_series(prefix,description,pad_length) VALUES
 ('PFR-','POS fulfilment request',6),('RET-','POS return case',6),('CORE-','Core return',6)
ON CONFLICT(prefix) DO NOTHING;

ALTER TABLE public.pos_approval_policies ENABLE ROW LEVEL SECURITY;

ALTER TABLE public.pos_approval_reason_codes ENABLE ROW LEVEL SECURITY;

ALTER TABLE public.pos_till_sessions ENABLE ROW LEVEL SECURITY;

ALTER TABLE public.pos_till_cash_movements ENABLE ROW LEVEL SECURITY;

ALTER TABLE public.pos_till_session_events ENABLE ROW LEVEL SECURITY;

ALTER TABLE public.pos_fulfillment_requests ENABLE ROW LEVEL SECURITY;

ALTER TABLE public.pos_return_cases ENABLE ROW LEVEL SECURITY;

ALTER TABLE public.pos_return_case_lines ENABLE ROW LEVEL SECURITY;

ALTER TABLE public.pos_core_returns ENABLE ROW LEVEL SECURITY;

CREATE POLICY pos_approval_policies_staff_select ON public.pos_approval_policies FOR SELECT TO authenticated USING(public.is_staff());

CREATE POLICY pos_approval_reasons_staff_select ON public.pos_approval_reason_codes FOR SELECT TO authenticated USING(public.is_staff());

CREATE POLICY pos_till_sessions_select ON public.pos_till_sessions FOR SELECT TO authenticated USING(operator_user_id=auth.uid() OR public.is_pos_approver() OR public.has_staff_role(ARRAY['finance']::public.staff_role[]));

CREATE POLICY pos_till_movements_select ON public.pos_till_cash_movements FOR SELECT TO authenticated USING(EXISTS(SELECT 1 FROM public.pos_till_sessions s WHERE s.id=session_id AND (s.operator_user_id=auth.uid() OR public.is_pos_approver() OR public.has_staff_role(ARRAY['finance']::public.staff_role[]))));

CREATE POLICY pos_till_events_select ON public.pos_till_session_events FOR SELECT TO authenticated USING(EXISTS(SELECT 1 FROM public.pos_till_sessions s WHERE s.id=session_id AND (s.operator_user_id=auth.uid() OR public.is_pos_approver() OR public.has_staff_role(ARRAY['finance']::public.staff_role[]))));

CREATE POLICY pos_fulfillment_staff_select ON public.pos_fulfillment_requests FOR SELECT TO authenticated USING(public.has_staff_role(ARRAY['admin','sales','warehouse','finance']::public.staff_role[]));

CREATE POLICY pos_return_cases_staff_select ON public.pos_return_cases FOR SELECT TO authenticated USING(public.has_staff_role(ARRAY['admin','sales','warehouse','finance']::public.staff_role[]));

CREATE POLICY pos_return_lines_staff_select ON public.pos_return_case_lines FOR SELECT TO authenticated USING(public.has_staff_role(ARRAY['admin','sales','warehouse','finance']::public.staff_role[]));

CREATE POLICY pos_core_returns_staff_select ON public.pos_core_returns FOR SELECT TO authenticated USING(public.has_staff_role(ARRAY['admin','sales','warehouse','finance']::public.staff_role[]));

GRANT SELECT ON public.pos_approval_policies,public.pos_approval_reason_codes,public.pos_till_sessions,public.pos_till_cash_movements,public.pos_till_session_events,
 public.pos_fulfillment_requests,public.pos_return_cases,public.pos_return_case_lines,public.pos_core_returns TO authenticated,service_role;

GRANT ALL ON public.pos_approval_policies,public.pos_approval_reason_codes,public.pos_till_sessions,public.pos_till_cash_movements,public.pos_till_session_events,
 public.pos_fulfillment_requests,public.pos_return_cases,public.pos_return_case_lines,public.pos_core_returns TO service_role;

GRANT EXECUTE ON FUNCTION public.pos_action_requires_manager(TEXT,NUMERIC),public.list_pos_approval_reasons(TEXT),public.get_pos_approval_policy(TEXT),
 public.open_pos_till_session(UUID,TEXT,NUMERIC,public.currency_code),public.get_my_open_pos_till_session(TEXT),public.attach_pos_cart_till_session(UUID,UUID),
 public.record_pos_till_cash_movement(UUID,public.pos_till_cash_movement_kind,NUMERIC,TEXT,TEXT,UUID),public.pos_till_expected_cash(UUID),
 public.close_pos_till_session(UUID,NUMERIC,TEXT,TEXT),public.approve_pos_till_variance(UUID,TEXT,TEXT),public.handover_pos_till_session(UUID,UUID,TEXT),public.list_pos_till_sessions(TEXT,INTEGER),
 public.list_pos_stock_availability(UUID),public.create_pos_fulfillment_request(public.pos_fulfillment_kind,UUID,UUID,NUMERIC,UUID,UUID,UUID,UUID,UUID,TEXT,INTEGER),
 public.approve_pos_fulfillment_request(UUID,TEXT),public.mark_pos_fulfillment_ready(UUID,TEXT),public.collect_pos_fulfillment_request(UUID,TEXT),public.cancel_pos_fulfillment_request(UUID,TEXT),public.list_pos_fulfillment_requests(TEXT,TEXT,INTEGER),
 public.get_pos_invoice_detail(UUID),public.create_pos_return_case(UUID,public.pos_return_resolution,TEXT,JSONB,TEXT,JSONB,UUID),public.post_pos_return_case(UUID),
 public.post_pos_core_return(UUID,UUID,NUMERIC,public.pos_core_return_resolution,TEXT,UUID,TEXT),public.find_pos_warranty_serial(TEXT),public.open_pos_warranty_claim(UUID,UUID,UUID,TEXT),
 public.list_pos_warranty_claims(TEXT,TEXT,INTEGER),public.approve_pos_warranty_claim(UUID,public.warranty_claim_resolution,JSONB,JSONB),public.reject_pos_warranty_claim(UUID,TEXT),
 public.prepare_pos_commerce_checkout(UUID,UUID,INTERVAL),public.get_pos_payment_status(UUID),public.create_pos_ecocash_intent(UUID,TEXT,TEXT,JSONB),
 public.create_pos_paynow_intent(UUID,public.paynow_method,TEXT,JSONB),public.create_pos_contipay_intent(UUID,public.contipay_method,TEXT,JSONB),public.cancel_pos_commerce_checkout(UUID,TEXT),
 public.list_pos_payment_recovery(INTEGER),public.repair_pos_paid_order(UUID,TEXT),public.apply_pos_cart_discount_governed(UUID,NUMERIC,TEXT,TEXT),
 public.apply_pos_line_price_override_governed(UUID,NUMERIC,TEXT,TEXT),public.void_pos_cart_governed(UUID,TEXT,TEXT),public.set_pos_approval_policy(TEXT,NUMERIC,BOOLEAN,BOOLEAN)
 TO authenticated,service_role;

-- Denomination-level blind counts: retained as audit evidence for every submitted close.
CREATE TABLE IF NOT EXISTS public.pos_till_count_lines (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),session_id UUID NOT NULL REFERENCES public.pos_till_sessions(id) ON DELETE RESTRICT,
 denomination NUMERIC(18,2) NOT NULL CHECK(denomination>0),quantity INTEGER NOT NULL CHECK(quantity>=0),amount NUMERIC(18,2) NOT NULL CHECK(amount>=0),
 counted_by UUID NOT NULL REFERENCES auth.users(id),created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS pos_till_count_lines_session_idx ON public.pos_till_count_lines(session_id,created_at);

ALTER TABLE public.pos_till_count_lines ENABLE ROW LEVEL SECURITY;

CREATE POLICY pos_till_count_lines_select ON public.pos_till_count_lines FOR SELECT TO authenticated USING(EXISTS(SELECT 1 FROM public.pos_till_sessions s WHERE s.id=session_id AND (s.operator_user_id=auth.uid() OR public.is_pos_approver() OR public.has_staff_role(ARRAY['finance']::public.staff_role[]))));

GRANT SELECT ON public.pos_till_count_lines TO authenticated,service_role;

GRANT ALL ON public.pos_till_count_lines TO service_role;

CREATE OR REPLACE FUNCTION public.submit_pos_till_denominated_close(p_session_id UUID,p_denominations JSONB,p_variance_reason_code TEXT DEFAULT NULL,p_notes TEXT DEFAULT NULL)
RETURNS JSONB LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE s public.pos_till_sessions%ROWTYPE; e JSONB; v_denom NUMERIC; v_qty INTEGER; v_total NUMERIC:=0;
BEGIN
 PERFORM public._require_sales_staff(); SELECT * INTO s FROM public.pos_till_sessions WHERE id=p_session_id FOR UPDATE;
 IF NOT FOUND OR s.status<>'open' OR s.operator_user_id<>auth.uid() THEN RAISE EXCEPTION 'open operator till session required'; END IF;
 IF p_denominations IS NULL OR jsonb_typeof(p_denominations)<>'array' OR jsonb_array_length(p_denominations)=0 THEN RAISE EXCEPTION 'denomination count required'; END IF;
 DELETE FROM public.pos_till_count_lines WHERE session_id=p_session_id;
 FOR e IN SELECT * FROM jsonb_array_elements(p_denominations) LOOP
  v_denom:=COALESCE((e->>'denomination')::numeric,0); v_qty:=COALESCE((e->>'quantity')::integer,-1);
  IF v_denom<=0 OR v_qty<0 THEN RAISE EXCEPTION 'invalid denomination count'; END IF;
  INSERT INTO public.pos_till_count_lines(session_id,denomination,quantity,amount,counted_by)
  VALUES(p_session_id,v_denom,v_qty,round(v_denom*v_qty,2),auth.uid()); v_total:=v_total+round(v_denom*v_qty,2);
 END LOOP;
 RETURN public.close_pos_till_session(p_session_id,v_total,p_variance_reason_code,p_notes);
END $$;

GRANT EXECUTE ON FUNCTION public.submit_pos_till_denominated_close(UUID,JSONB,TEXT,TEXT) TO authenticated,service_role;

-- Existing paid customer/POS commerce orders can be handed over at the counter.
CREATE OR REPLACE FUNCTION public.list_pos_pickup_orders(p_query TEXT DEFAULT NULL,p_limit INTEGER DEFAULT 100)
RETURNS TABLE(order_id UUID,document_number TEXT,customer_id UUID,customer_name TEXT,state TEXT,total NUMERIC,currency TEXT,sales_invoice_id UUID,settled_provider TEXT,updated_at TIMESTAMPTZ)
LANGUAGE sql STABLE SECURITY DEFINER SET search_path='' AS $$
 SELECT o.id,COALESCE(i.document_number,c.document_number),o.customer_id,cu.display_name,o.state::text,o.total,o.currency::text,o.sales_invoice_id,o.settled_provider,o.updated_at
 FROM public.commerce_orders o JOIN public.pos_carts c ON c.id=o.cart_id JOIN public.customers cu ON cu.id=o.customer_id LEFT JOIN public.sales_invoices i ON i.id=o.sales_invoice_id
 WHERE o.fulfillment_mode='immediate' AND o.sales_invoice_id IS NOT NULL AND o.state IN('paid','dispatch_ready')
  AND (p_query IS NULL OR trim(p_query)='' OR COALESCE(i.document_number,c.document_number,'') ILIKE '%'||trim(p_query)||'%' OR cu.display_name ILIKE '%'||trim(p_query)||'%')
 ORDER BY o.updated_at DESC LIMIT LEAST(GREATEST(COALESCE(p_limit,100),1),250);
$$;

CREATE OR REPLACE FUNCTION public.collect_pos_commerce_order(p_order_id UUID,p_notes TEXT DEFAULT NULL)
RETURNS UUID LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE o public.commerce_orders%ROWTYPE; i public.sales_invoices%ROWTYPE;
BEGIN
 PERFORM public._require_sales_staff(); SELECT * INTO o FROM public.commerce_orders WHERE id=p_order_id FOR UPDATE;
 IF NOT FOUND OR o.fulfillment_mode<>'immediate' OR o.state NOT IN('paid','dispatch_ready') OR o.sales_invoice_id IS NULL THEN RAISE EXCEPTION 'paid pickup order required'; END IF;
 SELECT * INTO i FROM public.sales_invoices WHERE id=o.sales_invoice_id; IF NOT FOUND OR i.status<>'posted' OR i.amount_paid+0.01<i.total THEN RAISE EXCEPTION 'fully paid posted invoice required'; END IF;
 UPDATE public.commerce_orders SET state='delivered',updated_at=now() WHERE id=o.id;
 PERFORM private.enqueue_commerce_event('commerce:'||o.id::text||':counter-collected','commerce.order_collected',o.id,jsonb_build_object('sales_invoice_id',o.sales_invoice_id,'notes',p_notes,'collected_by',auth.uid()));
 RETURN o.id;
END $$;

GRANT EXECUTE ON FUNCTION public.list_pos_pickup_orders(TEXT,INTEGER),public.collect_pos_commerce_order(UUID,TEXT) TO authenticated,service_role;

-- Security hardening: SECURITY DEFINER routines do not inherit PostgreSQL's default PUBLIC execute.
DO $$
DECLARE r RECORD;
BEGIN
 FOR r IN
  SELECT p.oid::regprocedure AS ident
  FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
  WHERE n.nspname IN ('public','private') AND p.proname = ANY(ARRAY[
   'pos_action_requires_manager','list_pos_approval_reasons','get_pos_approval_policy','pos_invoice_till_session_sync',
   'open_pos_till_session','get_my_open_pos_till_session','attach_pos_cart_till_session','record_pos_till_cash_movement',
   'pos_till_expected_cash','close_pos_till_session','approve_pos_till_variance','handover_pos_till_session','list_pos_till_sessions',
   'list_pos_stock_availability','create_pos_fulfillment_request','approve_pos_fulfillment_request','sync_pos_fulfillment_transfer',
   'sync_pos_fulfillment_invoice','mark_pos_fulfillment_ready','collect_pos_fulfillment_request','cancel_pos_fulfillment_request',
   'list_pos_fulfillment_requests','receive_external_return_to_quarantine','get_pos_invoice_detail','create_pos_return_case',
   'issue_pos_replacement_stock','post_pos_return_case','post_pos_core_return','find_pos_warranty_serial','open_pos_warranty_claim',
   'list_pos_warranty_claims','approve_pos_warranty_claim','reject_pos_warranty_claim','prepare_pos_commerce_checkout',
   'get_pos_payment_status','create_pos_ecocash_intent','create_pos_paynow_intent','create_pos_contipay_intent',
   'cancel_pos_commerce_checkout','list_pos_payment_recovery','repair_pos_paid_order','require_pos_reason',
   'apply_pos_cart_discount_governed','apply_pos_line_price_override_governed','void_pos_cart_governed','set_pos_approval_policy',
   'submit_pos_till_denominated_close','list_pos_pickup_orders','collect_pos_commerce_order'
  ])
 LOOP
  EXECUTE format('REVOKE ALL ON FUNCTION %s FROM PUBLIC, anon', r.ident);
 END LOOP;
END $$;
