-- Phase 16: gate get_pick_path_hints (SECURITY DEFINER) to warehouse/logistics staff
-- Roles: admin, warehouse, dispatcher (no separate 'logistics' enum value)

CREATE OR REPLACE FUNCTION public.get_pick_path_hints(
  p_warehouse_id UUID,
  p_stock_item_ids UUID[] DEFAULT NULL
)
RETURNS TABLE (
  stock_item_id UUID,
  oem_part_number VARCHAR(32),
  quantity NUMERIC,
  bin_id UUID,
  bin_code VARCHAR(64),
  bin_name TEXT,
  pick_path_seq INTEGER,
  aisle TEXT,
  rack TEXT,
  shelf TEXT
)
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  IF NOT (
    auth.role() = 'service_role'
    OR public.has_staff_role(
      ARRAY['admin', 'warehouse', 'dispatcher']::public.staff_role[]
    )
  ) THEN
    RAISE EXCEPTION 'warehouse, dispatcher, or admin role required';
  END IF;

  RETURN QUERY
  SELECT
    sl.stock_item_id,
    si.oem_part_number,
    sl.quantity,
    wb.id AS bin_id,
    wb.code AS bin_code,
    wb.name AS bin_name,
    wb.pick_path_seq,
    wb.aisle,
    wb.rack,
    wb.shelf
  FROM public.stock_levels sl
  JOIN public.stock_items si ON si.id = sl.stock_item_id
  LEFT JOIN public.warehouse_bins wb
    ON wb.id = sl.bin_id AND wb.is_active
  WHERE sl.warehouse_id = p_warehouse_id
    AND sl.quantity > 0
    AND (
      p_stock_item_ids IS NULL
      OR cardinality(p_stock_item_ids) = 0
      OR sl.stock_item_id = ANY (p_stock_item_ids)
    )
  ORDER BY
    wb.pick_path_seq NULLS LAST,
    wb.code NULLS LAST,
    si.oem_part_number;
END;
$$;

REVOKE ALL ON FUNCTION public.get_pick_path_hints(UUID, UUID[]) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.get_pick_path_hints(UUID, UUID[]) TO authenticated, service_role;
