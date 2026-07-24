-- Phase 13: lightweight demand forecast → Material Request suggestions
-- No auto-PO; staff must submit MR / existing procurement RPCs.

ALTER TABLE public.stock_items
  ADD COLUMN IF NOT EXISTS reorder_point NUMERIC(18, 3) CHECK (reorder_point IS NULL OR reorder_point >= 0),
  ADD COLUMN IF NOT EXISTS reorder_qty NUMERIC(18, 3) CHECK (reorder_qty IS NULL OR reorder_qty > 0);

CREATE TYPE public.forecast_suggestion_status AS ENUM ('open', 'converted', 'dismissed');

CREATE TABLE public.forecast_suggestions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  warehouse_id UUID NOT NULL REFERENCES public.warehouses (id),
  stock_item_id UUID NOT NULL REFERENCES public.stock_items (id),
  suggested_qty NUMERIC(18, 3) NOT NULL CHECK (suggested_qty > 0),
  horizon_days INT NOT NULL DEFAULT 30 CHECK (horizon_days > 0),
  on_hand NUMERIC(18, 3) NOT NULL DEFAULT 0,
  reason JSONB NOT NULL DEFAULT '{}'::jsonb,
  status public.forecast_suggestion_status NOT NULL DEFAULT 'open',
  material_request_id UUID REFERENCES public.material_requests (id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  converted_at TIMESTAMPTZ
);

-- Only one open suggestion per item/warehouse
CREATE UNIQUE INDEX forecast_suggestions_open_uniq
  ON public.forecast_suggestions (warehouse_id, stock_item_id)
  WHERE status = 'open';

CREATE INDEX forecast_suggestions_wh_idx ON public.forecast_suggestions (warehouse_id, status);

CREATE OR REPLACE FUNCTION public.generate_forecast_suggestions(
  p_warehouse_id UUID,
  p_horizon_days INT DEFAULT 30,
  p_default_reorder_qty NUMERIC DEFAULT 10
)
RETURNS INT
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_row RECORD;
  v_qty NUMERIC;
  v_count INT := 0;
BEGIN
  IF NOT (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'warehouse', 'finance']::public.staff_role[])
  ) THEN
    RAISE EXCEPTION 'warehouse/finance/admin role required';
  END IF;

  IF p_warehouse_id IS NULL THEN
    RAISE EXCEPTION 'warehouse_id required';
  END IF;

  FOR v_row IN
    SELECT
      si.id AS stock_item_id,
      si.base_uom_id,
      si.reorder_point,
      si.reorder_qty,
      COALESCE(sl.quantity, 0) AS on_hand
    FROM public.stock_items si
    LEFT JOIN public.stock_levels sl
      ON sl.stock_item_id = si.id AND sl.warehouse_id = p_warehouse_id
    WHERE si.reorder_point IS NOT NULL
      AND COALESCE(sl.quantity, 0) <= si.reorder_point
  LOOP
    v_qty := COALESCE(v_row.reorder_qty, p_default_reorder_qty);
    IF v_qty IS NULL OR v_qty <= 0 THEN
      v_qty := GREATEST(p_default_reorder_qty, 1);
    END IF;

    INSERT INTO public.forecast_suggestions (
      warehouse_id, stock_item_id, suggested_qty, horizon_days, on_hand, reason
    )
    VALUES (
      p_warehouse_id,
      v_row.stock_item_id,
      v_qty,
      GREATEST(COALESCE(p_horizon_days, 30), 1),
      v_row.on_hand,
      jsonb_build_object(
        'rule', 'reorder_point',
        'reorder_point', v_row.reorder_point,
        'on_hand', v_row.on_hand
      )
    )
    ON CONFLICT (warehouse_id, stock_item_id) WHERE status = 'open'
    DO UPDATE SET
      suggested_qty = EXCLUDED.suggested_qty,
      horizon_days = EXCLUDED.horizon_days,
      on_hand = EXCLUDED.on_hand,
      reason = EXCLUDED.reason,
      created_at = now();

    v_count := v_count + 1;
  END LOOP;

  RETURN v_count;
END;
$$;

CREATE OR REPLACE FUNCTION public.create_mr_from_forecast(
  p_suggestion_ids UUID[],
  p_needed_by DATE DEFAULT NULL,
  p_notes TEXT DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_wh UUID;
  v_lines JSONB := '[]'::jsonb;
  v_sug RECORD;
  v_uom UUID;
  v_mr UUID;
  v_ids UUID[];
BEGIN
  IF p_suggestion_ids IS NULL OR cardinality(p_suggestion_ids) = 0 THEN
    RAISE EXCEPTION 'suggestion ids required';
  END IF;

  v_ids := p_suggestion_ids;

  SELECT DISTINCT warehouse_id INTO v_wh
  FROM public.forecast_suggestions
  WHERE id = ANY (v_ids) AND status = 'open';

  IF v_wh IS NULL THEN
    RAISE EXCEPTION 'no open forecast suggestions for given ids';
  END IF;

  IF (
    SELECT count(DISTINCT warehouse_id)
    FROM public.forecast_suggestions
    WHERE id = ANY (v_ids) AND status = 'open'
  ) > 1 THEN
    RAISE EXCEPTION 'all suggestions must share one warehouse';
  END IF;

  FOR v_sug IN
    SELECT * FROM public.forecast_suggestions
    WHERE id = ANY (v_ids) AND status = 'open'
  LOOP
    SELECT base_uom_id INTO v_uom FROM public.stock_items WHERE id = v_sug.stock_item_id;
    IF v_uom IS NULL THEN
      RAISE EXCEPTION 'stock item missing base_uom_id: %', v_sug.stock_item_id;
    END IF;

    v_lines := v_lines || jsonb_build_array(
      jsonb_build_object(
        'stock_item_id', v_sug.stock_item_id,
        'uom_id', v_uom,
        'qty', v_sug.suggested_qty
      )
    );
  END LOOP;

  v_mr := public.create_material_request(
    v_wh,
    p_needed_by,
    v_lines,
    COALESCE(p_notes, 'From demand forecast suggestions')
  );

  UPDATE public.forecast_suggestions
  SET
    status = 'converted',
    material_request_id = v_mr,
    converted_at = now()
  WHERE id = ANY (v_ids) AND status = 'open';

  RETURN v_mr;
END;
$$;

ALTER TABLE public.forecast_suggestions ENABLE ROW LEVEL SECURITY;

CREATE POLICY forecast_suggestions_staff_select ON public.forecast_suggestions
  FOR SELECT TO authenticated
  USING (public.has_staff_role(ARRAY['admin', 'warehouse', 'finance', 'sales']::public.staff_role[]));

CREATE POLICY forecast_suggestions_staff_write ON public.forecast_suggestions
  FOR ALL TO authenticated
  USING (public.has_staff_role(ARRAY['admin', 'warehouse', 'finance']::public.staff_role[]))
  WITH CHECK (public.has_staff_role(ARRAY['admin', 'warehouse', 'finance']::public.staff_role[]));

REVOKE ALL ON FUNCTION public.generate_forecast_suggestions(UUID, INT, NUMERIC) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.create_mr_from_forecast(UUID[], DATE, TEXT) FROM PUBLIC;

GRANT EXECUTE ON FUNCTION public.generate_forecast_suggestions(UUID, INT, NUMERIC)
  TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.create_mr_from_forecast(UUID[], DATE, TEXT)
  TO authenticated, service_role;
