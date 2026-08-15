-- Master stock report filters: chassis + PNC category needles (staff DEFINER).
-- Keeps OEM/description search; raises export-friendly limit to 5000.

DROP FUNCTION IF EXISTS public.list_master_stock(INT, TEXT);

CREATE OR REPLACE FUNCTION public.list_master_stock(
  p_limit INT DEFAULT 200,
  p_query TEXT DEFAULT NULL,
  p_chassis_code TEXT DEFAULT NULL,
  p_category_needles TEXT[] DEFAULT NULL,
  p_subcategory_needles TEXT[] DEFAULT NULL
)
RETURNS TABLE (
  stock_item_id UUID,
  oem_part_number VARCHAR,
  description TEXT,
  qty_total NUMERIC,
  qty_wh1 NUMERIC,
  qty_wh2 NUMERIC,
  chassis_codes TEXT,
  category_name TEXT,
  subcategory_name TEXT
)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT
    m.stock_item_id,
    m.oem_part_number,
    m.description,
    m.qty_total,
    m.qty_wh1,
    m.qty_wh2,
    (
      SELECT string_agg(DISTINCT pf.chassis_code, ', ' ORDER BY pf.chassis_code)
      FROM public.part_fitment pf
      WHERE pf.oem_part_number = m.oem_part_number
        AND pf.chassis_code IS NOT NULL
        AND btrim(pf.chassis_code) <> ''
    ) AS chassis_codes,
    (
      SELECT pc.category_name
      FROM public.part_fitment pf
      JOIN public.pnc_categories pc ON pc.pnc_code = pf.pnc_code
      WHERE pf.oem_part_number = m.oem_part_number
      ORDER BY pc.category_name NULLS LAST
      LIMIT 1
    ) AS category_name,
    (
      SELECT pc.subcategory_name
      FROM public.part_fitment pf
      JOIN public.pnc_categories pc ON pc.pnc_code = pf.pnc_code
      WHERE pf.oem_part_number = m.oem_part_number
      ORDER BY pc.subcategory_name NULLS LAST
      LIMIT 1
    ) AS subcategory_name
  FROM public.v_master_stock m
  WHERE
    public.has_staff_role(ARRAY['admin', 'warehouse', 'sales', 'finance']::public.staff_role[])
    AND (
      p_query IS NULL
      OR btrim(p_query) = ''
      OR m.oem_part_number ILIKE '%' || btrim(p_query) || '%'
      OR COALESCE(m.description, '') ILIKE '%' || btrim(p_query) || '%'
    )
    AND (
      p_chassis_code IS NULL
      OR btrim(p_chassis_code) = ''
      OR EXISTS (
        SELECT 1
        FROM public.part_fitment pf
        WHERE pf.oem_part_number = m.oem_part_number
          AND pf.chassis_code = btrim(p_chassis_code)
      )
    )
    AND (
      p_category_needles IS NULL
      OR cardinality(p_category_needles) = 0
      OR EXISTS (
        SELECT 1
        FROM public.part_fitment pf
        JOIN public.pnc_categories pc ON pc.pnc_code = pf.pnc_code
        WHERE pf.oem_part_number = m.oem_part_number
          AND EXISTS (
            SELECT 1
            FROM unnest(p_category_needles) AS n(needle)
            WHERE lower(COALESCE(pc.category_name, '')) LIKE '%' || lower(n.needle) || '%'
               OR lower(COALESCE(pc.subcategory_name, '')) LIKE '%' || lower(n.needle) || '%'
          )
      )
    )
    AND (
      p_subcategory_needles IS NULL
      OR cardinality(p_subcategory_needles) = 0
      OR EXISTS (
        SELECT 1
        FROM public.part_fitment pf
        JOIN public.pnc_categories pc ON pc.pnc_code = pf.pnc_code
        WHERE pf.oem_part_number = m.oem_part_number
          AND EXISTS (
            SELECT 1
            FROM unnest(p_subcategory_needles) AS n(needle)
            WHERE lower(COALESCE(pc.category_name, '')) LIKE '%' || lower(n.needle) || '%'
               OR lower(COALESCE(pc.subcategory_name, '')) LIKE '%' || lower(n.needle) || '%'
          )
      )
    )
  ORDER BY m.oem_part_number
  LIMIT GREATEST(1, LEAST(COALESCE(p_limit, 200), 5000));
$$;

REVOKE ALL ON FUNCTION public.list_master_stock(INT, TEXT, TEXT, TEXT[], TEXT[]) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.list_master_stock(INT, TEXT, TEXT, TEXT[], TEXT[]) TO authenticated, service_role;

COMMENT ON FUNCTION public.list_master_stock(INT, TEXT, TEXT, TEXT[], TEXT[]) IS
  'Staff master stock report: OEM search + chassis + merch category needles; WH totals.';
