-- Phase 8c: procurement mutation guards (mirror Phase 4b / 5b).
-- SECURITY DEFINER RPCs set app.procurement_rpc=1 (transaction-local) before table writes.

CREATE OR REPLACE FUNCTION public._procurement_rpc_active()
RETURNS boolean
LANGUAGE sql
STABLE
AS $$
  SELECT COALESCE(current_setting('app.procurement_rpc', true), '') = '1';
$$;

CREATE OR REPLACE FUNCTION public._procurement_begin_rpc()
RETURNS void
LANGUAGE sql
AS $$
  SELECT set_config('app.procurement_rpc', '1', true);
$$;

CREATE OR REPLACE FUNCTION public.guard_procurement_doc_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  IF public._procurement_rpc_active() THEN
    IF TG_OP = 'DELETE' THEN
      RETURN OLD;
    END IF;
    RETURN NEW;
  END IF;

  IF TG_OP = 'INSERT' THEN
    IF NEW.status IS DISTINCT FROM 'draft' THEN
      RAISE EXCEPTION '%: new rows must be draft; use procurement RPCs', TG_TABLE_NAME;
    END IF;
    RAISE EXCEPTION '%: use procurement RPCs for insert', TG_TABLE_NAME;
  ELSIF TG_OP = 'DELETE' THEN
    RAISE EXCEPTION '%: direct delete not allowed', TG_TABLE_NAME;
  ELSIF TG_OP = 'UPDATE' THEN
    IF OLD.status = 'cancelled' THEN
      RAISE EXCEPTION '%: cancelled records are immutable', TG_TABLE_NAME;
    END IF;
    RAISE EXCEPTION '%: use procurement RPCs for updates', TG_TABLE_NAME;
  END IF;

  RETURN NULL;
END;
$$;

CREATE OR REPLACE FUNCTION public.guard_material_request_line_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
  v_parent UUID;
  v_status public.procurement_doc_status;
BEGIN
  IF public._procurement_rpc_active() THEN
    IF TG_OP = 'DELETE' THEN
      RETURN OLD;
    END IF;
    RETURN NEW;
  END IF;

  v_parent := COALESCE(NEW.material_request_id, OLD.material_request_id);
  SELECT status INTO v_status FROM public.material_requests WHERE id = v_parent;
  IF v_status IS NULL THEN
    RAISE EXCEPTION 'material_request_lines: parent not found';
  END IF;
  IF v_status <> 'draft' THEN
    RAISE EXCEPTION 'material_request_lines: parent must be draft (status=%)', v_status;
  END IF;
  RAISE EXCEPTION 'material_request_lines: use procurement RPCs';
END;
$$;

CREATE OR REPLACE FUNCTION public.guard_purchase_order_line_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
  v_parent UUID;
  v_status public.procurement_doc_status;
BEGIN
  IF public._procurement_rpc_active() THEN
    IF TG_OP = 'DELETE' THEN
      RETURN OLD;
    END IF;
    RETURN NEW;
  END IF;

  v_parent := COALESCE(NEW.purchase_order_id, OLD.purchase_order_id);
  SELECT status INTO v_status FROM public.purchase_orders WHERE id = v_parent;
  IF v_status IS NULL THEN
    RAISE EXCEPTION 'purchase_order_lines: parent not found';
  END IF;
  IF v_status <> 'draft' THEN
    RAISE EXCEPTION 'purchase_order_lines: parent must be draft (status=%)', v_status;
  END IF;
  RAISE EXCEPTION 'purchase_order_lines: use procurement RPCs';
END;
$$;

CREATE OR REPLACE FUNCTION public.guard_goods_receipt_line_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
  v_parent UUID;
  v_status public.procurement_doc_status;
BEGIN
  IF public._procurement_rpc_active() THEN
    IF TG_OP = 'DELETE' THEN
      RETURN OLD;
    END IF;
    RETURN NEW;
  END IF;

  v_parent := COALESCE(NEW.goods_receipt_id, OLD.goods_receipt_id);
  SELECT status INTO v_status FROM public.goods_receipts WHERE id = v_parent;
  IF v_status IS NULL THEN
    RAISE EXCEPTION 'goods_receipt_lines: parent not found';
  END IF;
  IF v_status <> 'draft' THEN
    RAISE EXCEPTION 'goods_receipt_lines: parent must be draft (status=%)', v_status;
  END IF;
  RAISE EXCEPTION 'goods_receipt_lines: use procurement RPCs';
END;
$$;

CREATE OR REPLACE FUNCTION public.guard_landed_cost_child_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
  v_parent UUID;
  v_status public.procurement_doc_status;
BEGIN
  IF public._procurement_rpc_active() THEN
    IF TG_OP = 'DELETE' THEN
      RETURN OLD;
    END IF;
    RETURN NEW;
  END IF;

  v_parent := COALESCE(NEW.landed_cost_voucher_id, OLD.landed_cost_voucher_id);
  SELECT status INTO v_status FROM public.landed_cost_vouchers WHERE id = v_parent;
  IF v_status IS NULL THEN
    RAISE EXCEPTION '%: parent voucher not found', TG_TABLE_NAME;
  END IF;
  IF v_status <> 'draft' THEN
    RAISE EXCEPTION '%: parent must be draft (status=%)', TG_TABLE_NAME, v_status;
  END IF;
  RAISE EXCEPTION '%: use procurement RPCs', TG_TABLE_NAME;
END;
$$;

DROP TRIGGER IF EXISTS material_requests_mutation_guard ON public.material_requests;
CREATE TRIGGER material_requests_mutation_guard
  BEFORE INSERT OR UPDATE OR DELETE ON public.material_requests
  FOR EACH ROW EXECUTE PROCEDURE public.guard_procurement_doc_mutation();

DROP TRIGGER IF EXISTS material_request_lines_mutation_guard ON public.material_request_lines;
CREATE TRIGGER material_request_lines_mutation_guard
  BEFORE INSERT OR UPDATE OR DELETE ON public.material_request_lines
  FOR EACH ROW EXECUTE PROCEDURE public.guard_material_request_line_mutation();

DROP TRIGGER IF EXISTS purchase_orders_mutation_guard ON public.purchase_orders;
CREATE TRIGGER purchase_orders_mutation_guard
  BEFORE INSERT OR UPDATE OR DELETE ON public.purchase_orders
  FOR EACH ROW EXECUTE PROCEDURE public.guard_procurement_doc_mutation();

DROP TRIGGER IF EXISTS purchase_order_lines_mutation_guard ON public.purchase_order_lines;
CREATE TRIGGER purchase_order_lines_mutation_guard
  BEFORE INSERT OR UPDATE OR DELETE ON public.purchase_order_lines
  FOR EACH ROW EXECUTE PROCEDURE public.guard_purchase_order_line_mutation();

DROP TRIGGER IF EXISTS goods_receipts_mutation_guard ON public.goods_receipts;
CREATE TRIGGER goods_receipts_mutation_guard
  BEFORE INSERT OR UPDATE OR DELETE ON public.goods_receipts
  FOR EACH ROW EXECUTE PROCEDURE public.guard_procurement_doc_mutation();

DROP TRIGGER IF EXISTS goods_receipt_lines_mutation_guard ON public.goods_receipt_lines;
CREATE TRIGGER goods_receipt_lines_mutation_guard
  BEFORE INSERT OR UPDATE OR DELETE ON public.goods_receipt_lines
  FOR EACH ROW EXECUTE PROCEDURE public.guard_goods_receipt_line_mutation();

DROP TRIGGER IF EXISTS landed_cost_vouchers_mutation_guard ON public.landed_cost_vouchers;
CREATE TRIGGER landed_cost_vouchers_mutation_guard
  BEFORE INSERT OR UPDATE OR DELETE ON public.landed_cost_vouchers
  FOR EACH ROW EXECUTE PROCEDURE public.guard_procurement_doc_mutation();

DROP TRIGGER IF EXISTS landed_cost_charges_mutation_guard ON public.landed_cost_charges;
CREATE TRIGGER landed_cost_charges_mutation_guard
  BEFORE INSERT OR UPDATE OR DELETE ON public.landed_cost_charges
  FOR EACH ROW EXECUTE PROCEDURE public.guard_landed_cost_child_mutation();

DROP TRIGGER IF EXISTS landed_cost_allocations_mutation_guard ON public.landed_cost_allocations;
CREATE TRIGGER landed_cost_allocations_mutation_guard
  BEFORE INSERT OR UPDATE OR DELETE ON public.landed_cost_allocations
  FOR EACH ROW EXECUTE PROCEDURE public.guard_landed_cost_child_mutation();

-- RPC-only writes: keep staff/supplier SELECT; drop direct DML policies.
DROP POLICY IF EXISTS material_requests_staff ON public.material_requests;
CREATE POLICY material_requests_staff_select
  ON public.material_requests FOR SELECT TO authenticated
  USING (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'warehouse', 'finance']::public.staff_role[])
  );

DROP POLICY IF EXISTS material_request_lines_staff ON public.material_request_lines;
CREATE POLICY material_request_lines_staff_select
  ON public.material_request_lines FOR SELECT TO authenticated
  USING (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'warehouse', 'finance']::public.staff_role[])
  );

DROP POLICY IF EXISTS purchase_orders_staff ON public.purchase_orders;
CREATE POLICY purchase_orders_staff_select
  ON public.purchase_orders FOR SELECT TO authenticated
  USING (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'warehouse', 'finance']::public.staff_role[])
  );

DROP POLICY IF EXISTS purchase_order_lines_staff ON public.purchase_order_lines;
CREATE POLICY purchase_order_lines_staff_select
  ON public.purchase_order_lines FOR SELECT TO authenticated
  USING (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'warehouse', 'finance']::public.staff_role[])
  );

DROP POLICY IF EXISTS goods_receipts_staff ON public.goods_receipts;
CREATE POLICY goods_receipts_staff_select
  ON public.goods_receipts FOR SELECT TO authenticated
  USING (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'warehouse', 'finance']::public.staff_role[])
  );

DROP POLICY IF EXISTS goods_receipt_lines_staff ON public.goods_receipt_lines;
CREATE POLICY goods_receipt_lines_staff_select
  ON public.goods_receipt_lines FOR SELECT TO authenticated
  USING (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'warehouse', 'finance']::public.staff_role[])
  );

DROP POLICY IF EXISTS landed_cost_staff ON public.landed_cost_vouchers;
CREATE POLICY landed_cost_staff_select
  ON public.landed_cost_vouchers FOR SELECT TO authenticated
  USING (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'finance']::public.staff_role[])
  );

DROP POLICY IF EXISTS landed_cost_charges_staff ON public.landed_cost_charges;
CREATE POLICY landed_cost_charges_staff_select
  ON public.landed_cost_charges FOR SELECT TO authenticated
  USING (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'finance']::public.staff_role[])
  );

DROP POLICY IF EXISTS landed_cost_allocations_staff ON public.landed_cost_allocations;
CREATE POLICY landed_cost_allocations_staff_select
  ON public.landed_cost_allocations FOR SELECT TO authenticated
  USING (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'finance']::public.staff_role[])
  );

REVOKE ALL ON FUNCTION public._procurement_rpc_active() FROM PUBLIC;
REVOKE ALL ON FUNCTION public._procurement_begin_rpc() FROM PUBLIC;

-- PATCH_MARKER_RPCS
