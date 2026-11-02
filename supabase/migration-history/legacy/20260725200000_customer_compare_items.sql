-- Cross-platform shop ops: server-side compare list for authenticated customers.
-- Guests stay localStorage on web; auth sync via these RPCs + own-row RLS.
-- NO ZIMRA / payroll tax. RLS in this same file.

-- ---------------------------------------------------------------------------
-- customer_compare_items
-- ---------------------------------------------------------------------------
CREATE TABLE public.customer_compare_items (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  customer_id UUID NOT NULL REFERENCES public.customers (id) ON DELETE CASCADE,
  stock_item_id UUID NOT NULL REFERENCES public.stock_items (id) ON DELETE CASCADE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT customer_compare_items_unique UNIQUE (customer_id, stock_item_id)
);

CREATE INDEX customer_compare_customer_idx
  ON public.customer_compare_items (customer_id, created_at DESC);

CREATE INDEX customer_compare_stock_item_idx
  ON public.customer_compare_items (stock_item_id);

COMMENT ON TABLE public.customer_compare_items IS
  'Storefront compare list (auth sync). Own-row RLS via customers.profile_id; staff may SELECT only.';

ALTER TABLE public.customer_compare_items ENABLE ROW LEVEL SECURITY;

CREATE POLICY compare_customer_all ON public.customer_compare_items
  FOR ALL TO authenticated
  USING (customer_id = public._current_customer_id())
  WITH CHECK (customer_id = public._current_customer_id());

CREATE POLICY compare_staff_select ON public.customer_compare_items
  FOR SELECT TO authenticated
  USING (public.has_staff_role(ARRAY['admin', 'sales']::public.staff_role[]));

GRANT SELECT ON TABLE public.customer_compare_items TO authenticated, service_role;
GRANT INSERT, DELETE ON TABLE public.customer_compare_items TO authenticated;
GRANT INSERT, UPDATE, DELETE ON TABLE public.customer_compare_items TO service_role;

-- Soft cap so compare matrices stay usable on mobile/web.
CREATE OR REPLACE FUNCTION public._customer_compare_max_items()
RETURNS INT
LANGUAGE sql
IMMUTABLE
AS $$
  SELECT 8;
$$;

CREATE OR REPLACE FUNCTION public.add_customer_compare_item(
  p_stock_item_id UUID DEFAULT NULL,
  p_oem_part_number TEXT DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_cust UUID := public._current_customer_id();
  v_item UUID;
  v_id UUID;
  v_count INT;
BEGIN
  IF v_cust IS NULL THEN
    RAISE EXCEPTION 'customer profile required';
  END IF;

  v_item := public._resolve_customer_stock_item(p_stock_item_id, p_oem_part_number);

  SELECT count(*)::int INTO v_count
  FROM public.customer_compare_items
  WHERE customer_id = v_cust;

  IF NOT EXISTS (
    SELECT 1 FROM public.customer_compare_items
    WHERE customer_id = v_cust AND stock_item_id = v_item
  ) AND v_count >= public._customer_compare_max_items() THEN
    RAISE EXCEPTION 'compare list is full (max % items)', public._customer_compare_max_items();
  END IF;

  INSERT INTO public.customer_compare_items (customer_id, stock_item_id)
  VALUES (v_cust, v_item)
  ON CONFLICT (customer_id, stock_item_id) DO UPDATE
    SET created_at = public.customer_compare_items.created_at
  RETURNING id INTO v_id;

  RETURN v_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.remove_customer_compare_item(
  p_stock_item_id UUID DEFAULT NULL,
  p_oem_part_number TEXT DEFAULT NULL,
  p_compare_id UUID DEFAULT NULL
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_cust UUID := public._current_customer_id();
  v_item UUID;
  v_deleted INT := 0;
BEGIN
  IF v_cust IS NULL THEN
    RAISE EXCEPTION 'customer profile required';
  END IF;

  IF p_compare_id IS NOT NULL THEN
    DELETE FROM public.customer_compare_items
    WHERE id = p_compare_id AND customer_id = v_cust;
    GET DIAGNOSTICS v_deleted = ROW_COUNT;
    IF v_deleted = 0 THEN
      RAISE EXCEPTION 'compare item not found';
    END IF;
    RETURN;
  END IF;

  v_item := public._resolve_customer_stock_item(p_stock_item_id, p_oem_part_number);

  DELETE FROM public.customer_compare_items
  WHERE customer_id = v_cust AND stock_item_id = v_item;
  GET DIAGNOSTICS v_deleted = ROW_COUNT;
  IF v_deleted = 0 THEN
    RAISE EXCEPTION 'compare item not found';
  END IF;
END;
$$;

CREATE OR REPLACE FUNCTION public.list_customer_compare_items()
RETURNS TABLE (
  id UUID,
  stock_item_id UUID,
  oem_part_number TEXT,
  description TEXT,
  created_at TIMESTAMPTZ
)
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_cust UUID := public._current_customer_id();
BEGIN
  IF v_cust IS NULL THEN
    RAISE EXCEPTION 'customer profile required';
  END IF;

  RETURN QUERY
  SELECT
    c.id,
    c.stock_item_id,
    si.oem_part_number,
    si.description,
    c.created_at
  FROM public.customer_compare_items c
  JOIN public.stock_items si ON si.id = c.stock_item_id
  WHERE c.customer_id = v_cust
  ORDER BY c.created_at DESC;
END;
$$;

REVOKE ALL ON FUNCTION public._customer_compare_max_items() FROM PUBLIC;
REVOKE ALL ON FUNCTION public.add_customer_compare_item(UUID, TEXT) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.remove_customer_compare_item(UUID, TEXT, UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.list_customer_compare_items() FROM PUBLIC;

GRANT EXECUTE ON FUNCTION public.add_customer_compare_item(UUID, TEXT)
  TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.remove_customer_compare_item(UUID, TEXT, UUID)
  TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.list_customer_compare_items()
  TO authenticated, service_role;
