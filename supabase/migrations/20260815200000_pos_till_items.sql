-- P0b: list_pos_till_items + TillItem shape on pull_pos_offline_snapshot.
-- One tile DTO for online grid and offline cache (§16.1).
-- Qty from stock_levels only — never Meili/FTS. No ZIMRA / tax. No second cart/ledger.
-- Privilege: sales|finance|admin via _require_payments_staff() (same as offline snapshot).

-- ---------------------------------------------------------------------------
-- Extend offline snapshot items → TillItem (additive fields)
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.pull_pos_offline_snapshot(p_warehouse_id UUID)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_list UUID;
  v_currency public.currency_code;
  v_items JSONB;
BEGIN
  PERFORM public._require_payments_staff();

  IF p_warehouse_id IS NULL THEN
    RAISE EXCEPTION 'warehouse_id required';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM public.warehouses w
    WHERE w.id = p_warehouse_id AND w.is_active AND NOT w.is_quarantine
  ) THEN
    RAISE EXCEPTION 'warehouse not found or not saleable';
  END IF;

  SELECT pl.id, pl.currency INTO v_list, v_currency
  FROM public.price_lists pl
  WHERE pl.is_default AND pl.is_active
  LIMIT 1;

  IF v_list IS NULL THEN
    SELECT pl.id, pl.currency INTO v_list, v_currency
    FROM public.price_lists pl
    WHERE pl.code = 'RETAIL' AND pl.is_active
    LIMIT 1;
  END IF;

  IF v_list IS NULL THEN
    RAISE EXCEPTION 'no active default/RETAIL price list';
  END IF;

  SELECT COALESCE(jsonb_agg(row_to_json(x)::jsonb ORDER BY x.oem_part_number), '[]'::jsonb)
  INTO v_items
  FROM (
    SELECT
      si.id AS stock_item_id,
      si.oem_part_number,
      si.description,
      si.base_uom_id AS uom_id,
      COALESCE(pli.unit_price, 0)::numeric AS unit_price,
      COALESCE(pli.core_charge, 0)::numeric AS core_charge,
      COALESCE(sl.quantity, 0)::numeric AS saleable_qty,
      COALESCE(sl.currency, v_currency) AS currency,
      wb.code AS bin_code,
      (
        SELECT pf.pnc_code
        FROM public.part_fitment pf
        WHERE pf.oem_part_number = si.oem_part_number
          AND pf.pnc_code IS NOT NULL
          AND btrim(pf.pnc_code) <> ''
        ORDER BY pf.pnc_code
        LIMIT 1
      ) AS pnc_code,
      (
        SELECT pc.category_name
        FROM public.part_fitment pf
        JOIN public.pnc_categories pc ON pc.pnc_code = pf.pnc_code
        WHERE pf.oem_part_number = si.oem_part_number
        ORDER BY pc.category_name NULLS LAST
        LIMIT 1
      ) AS category_name,
      (
        SELECT pf.superseded_by
        FROM public.part_fitment pf
        WHERE pf.oem_part_number = si.oem_part_number
          AND pf.superseded_by IS NOT NULL
          AND btrim(pf.superseded_by) <> ''
        ORDER BY pf.superseded_by
        LIMIT 1
      ) AS superseded_by,
      COALESCE((
        SELECT array_agg(DISTINCT c ORDER BY c)
        FROM (
          SELECT btrim(pf.chassis_code) AS c
          FROM public.part_fitment pf
          WHERE pf.oem_part_number = si.oem_part_number
            AND pf.chassis_code IS NOT NULL
            AND btrim(pf.chassis_code) <> ''
        ) s
      ), '{}'::text[]) AS chassis_codes,
      COALESCE((
        SELECT array_agg(DISTINCT e ORDER BY e)
        FROM (
          SELECT btrim(pf.engine_code) AS e
          FROM public.part_fitment pf
          WHERE pf.oem_part_number = si.oem_part_number
            AND pf.engine_code IS NOT NULL
            AND btrim(pf.engine_code) <> ''
        ) s
      ), '{}'::text[]) AS engine_codes
    FROM public.stock_items si
    JOIN public.price_list_items pli
      ON pli.stock_item_id = si.id AND pli.price_list_id = v_list
    LEFT JOIN public.stock_levels sl
      ON sl.stock_item_id = si.id AND sl.warehouse_id = p_warehouse_id
    LEFT JOIN public.warehouse_bins wb
      ON wb.id = sl.bin_id AND wb.is_active
    WHERE si.base_uom_id IS NOT NULL
      AND pli.unit_price >= 0
  ) x;

  RETURN jsonb_build_object(
    'warehouse_id', p_warehouse_id,
    'pulled_at', now(),
    'price_list_id', v_list,
    'currency', v_currency,
    'items', v_items
  );
END;
$$;

COMMENT ON FUNCTION public.pull_pos_offline_snapshot(UUID) IS
  'Staff pull of retail catalog + warehouse qty for encrypted tablet offline POS cache. '
  'items[] TillItem fields: stock_item_id, oem_part_number, description, uom_id, '
  'unit_price, core_charge, saleable_qty (stock_levels only), currency, bin_code, '
  'pnc_code, category_name, superseded_by, chassis_codes[], engine_codes[].';

REVOKE ALL ON FUNCTION public.pull_pos_offline_snapshot(UUID) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.pull_pos_offline_snapshot(UUID)
  TO authenticated, service_role;

-- ---------------------------------------------------------------------------
-- list_pos_till_items — shop_stock | oems | section
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.list_pos_till_items(
  p_warehouse_id UUID,
  p_source TEXT,
  p_in_stock_only BOOLEAN DEFAULT true,
  p_chassis_code TEXT DEFAULT NULL,
  p_engine_code TEXT DEFAULT NULL,
  p_category TEXT DEFAULT NULL,
  p_oems TEXT[] DEFAULT NULL,
  p_section_key TEXT DEFAULT NULL,
  p_limit INT DEFAULT 80,
  p_offset INT DEFAULT 0
)
RETURNS JSONB
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_list UUID;
  v_currency public.currency_code;
  v_source TEXT;
  v_limit INT;
  v_offset INT;
  v_chassis TEXT;
  v_engine TEXT;
  v_category TEXT;
  v_section TEXT;
  v_items JSONB;
BEGIN
  PERFORM public._require_payments_staff();

  IF p_warehouse_id IS NULL THEN
    RAISE EXCEPTION 'warehouse_id required';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM public.warehouses w
    WHERE w.id = p_warehouse_id AND w.is_active AND NOT w.is_quarantine
  ) THEN
    RAISE EXCEPTION 'warehouse not found or not saleable';
  END IF;

  v_source := lower(btrim(COALESCE(p_source, '')));
  IF v_source NOT IN ('shop_stock', 'oems', 'section') THEN
    RAISE EXCEPTION 'p_source must be shop_stock, oems, or section';
  END IF;

  v_limit := GREATEST(1, LEAST(COALESCE(p_limit, 80), 500));
  v_offset := GREATEST(0, COALESCE(p_offset, 0));
  v_chassis := NULLIF(upper(btrim(COALESCE(p_chassis_code, ''))), '');
  v_engine := NULLIF(upper(btrim(COALESCE(p_engine_code, ''))), '');
  v_category := NULLIF(btrim(COALESCE(p_category, '')), '');
  v_section := NULLIF(btrim(COALESCE(p_section_key, '')), '');

  SELECT pl.id, pl.currency INTO v_list, v_currency
  FROM public.price_lists pl
  WHERE pl.is_default AND pl.is_active
  LIMIT 1;

  IF v_list IS NULL THEN
    SELECT pl.id, pl.currency INTO v_list, v_currency
    FROM public.price_lists pl
    WHERE pl.code = 'RETAIL' AND pl.is_active
    LIMIT 1;
  END IF;

  IF v_list IS NULL THEN
    RAISE EXCEPTION 'no active default/RETAIL price list';
  END IF;

  IF v_source = 'oems' THEN
    IF p_oems IS NULL OR cardinality(p_oems) = 0 THEN
      RAISE EXCEPTION 'p_oems required when p_source = oems';
    END IF;

    SELECT COALESCE(jsonb_agg((to_jsonb(x) - 'ord') ORDER BY x.ord), '[]'::jsonb)
    INTO v_items
    FROM (
      SELECT
        si.id AS stock_item_id,
        si.oem_part_number,
        si.description,
        si.base_uom_id AS uom_id,
        pli.unit_price::numeric AS unit_price,
        COALESCE(pli.core_charge, 0)::numeric AS core_charge,
        COALESCE(sl.quantity, 0)::numeric AS saleable_qty,
        COALESCE(sl.currency, v_currency) AS currency,
        wb.code AS bin_code,
        (
          SELECT pf.pnc_code
          FROM public.part_fitment pf
          WHERE pf.oem_part_number = si.oem_part_number
            AND pf.pnc_code IS NOT NULL
            AND btrim(pf.pnc_code) <> ''
          ORDER BY pf.pnc_code
          LIMIT 1
        ) AS pnc_code,
        (
          SELECT pc.category_name
          FROM public.part_fitment pf
          JOIN public.pnc_categories pc ON pc.pnc_code = pf.pnc_code
          WHERE pf.oem_part_number = si.oem_part_number
          ORDER BY pc.category_name NULLS LAST
          LIMIT 1
        ) AS category_name,
        (
          SELECT pf.superseded_by
          FROM public.part_fitment pf
          WHERE pf.oem_part_number = si.oem_part_number
            AND pf.superseded_by IS NOT NULL
            AND btrim(pf.superseded_by) <> ''
          ORDER BY pf.superseded_by
          LIMIT 1
        ) AS superseded_by,
        COALESCE((
          SELECT array_agg(DISTINCT c ORDER BY c)
          FROM (
            SELECT btrim(pf.chassis_code) AS c
            FROM public.part_fitment pf
            WHERE pf.oem_part_number = si.oem_part_number
              AND pf.chassis_code IS NOT NULL
              AND btrim(pf.chassis_code) <> ''
          ) s
        ), '{}'::text[]) AS chassis_codes,
        COALESCE((
          SELECT array_agg(DISTINCT e ORDER BY e)
          FROM (
            SELECT btrim(pf.engine_code) AS e
            FROM public.part_fitment pf
            WHERE pf.oem_part_number = si.oem_part_number
              AND pf.engine_code IS NOT NULL
              AND btrim(pf.engine_code) <> ''
          ) s
        ), '{}'::text[]) AS engine_codes,
        req.ord
      FROM unnest(p_oems) WITH ORDINALITY AS req(oem, ord)
      JOIN public.stock_items si
        ON upper(btrim(si.oem_part_number)) = upper(btrim(req.oem))
      LEFT JOIN public.price_list_items pli
        ON pli.stock_item_id = si.id AND pli.price_list_id = v_list
      LEFT JOIN public.stock_levels sl
        ON sl.stock_item_id = si.id AND sl.warehouse_id = p_warehouse_id
      LEFT JOIN public.warehouse_bins wb
        ON wb.id = sl.bin_id AND wb.is_active
      WHERE si.base_uom_id IS NOT NULL
        AND (
          NOT COALESCE(p_in_stock_only, true)
          OR COALESCE(sl.quantity, 0) > 0
        )
      ORDER BY req.ord
      LIMIT v_limit OFFSET v_offset
    ) x;

  ELSIF v_source = 'section' THEN
    IF v_section IS NULL THEN
      RAISE EXCEPTION 'p_section_key required when p_source = section';
    END IF;

    SELECT COALESCE(jsonb_agg(row_to_json(x)::jsonb ORDER BY x.oem_part_number), '[]'::jsonb)
    INTO v_items
    FROM (
      SELECT
        si.id AS stock_item_id,
        si.oem_part_number,
        si.description,
        si.base_uom_id AS uom_id,
        pli.unit_price::numeric AS unit_price,
        COALESCE(pli.core_charge, 0)::numeric AS core_charge,
        COALESCE(sl.quantity, 0)::numeric AS saleable_qty,
        COALESCE(sl.currency, v_currency) AS currency,
        wb.code AS bin_code,
        (
          SELECT pf.pnc_code
          FROM public.part_fitment pf
          WHERE pf.oem_part_number = si.oem_part_number
            AND pf.pnc_code IS NOT NULL
            AND btrim(pf.pnc_code) <> ''
          ORDER BY pf.pnc_code
          LIMIT 1
        ) AS pnc_code,
        (
          SELECT pc.category_name
          FROM public.part_fitment pf
          JOIN public.pnc_categories pc ON pc.pnc_code = pf.pnc_code
          WHERE pf.oem_part_number = si.oem_part_number
          ORDER BY pc.category_name NULLS LAST
          LIMIT 1
        ) AS category_name,
        (
          SELECT pf.superseded_by
          FROM public.part_fitment pf
          WHERE pf.oem_part_number = si.oem_part_number
            AND pf.superseded_by IS NOT NULL
            AND btrim(pf.superseded_by) <> ''
          ORDER BY pf.superseded_by
          LIMIT 1
        ) AS superseded_by,
        COALESCE((
          SELECT array_agg(DISTINCT c ORDER BY c)
          FROM (
            SELECT btrim(pf.chassis_code) AS c
            FROM public.part_fitment pf
            WHERE pf.oem_part_number = si.oem_part_number
              AND pf.chassis_code IS NOT NULL
              AND btrim(pf.chassis_code) <> ''
          ) s
        ), '{}'::text[]) AS chassis_codes,
        COALESCE((
          SELECT array_agg(DISTINCT e ORDER BY e)
          FROM (
            SELECT btrim(pf.engine_code) AS e
            FROM public.part_fitment pf
            WHERE pf.oem_part_number = si.oem_part_number
              AND pf.engine_code IS NOT NULL
              AND btrim(pf.engine_code) <> ''
          ) s
        ), '{}'::text[]) AS engine_codes
      FROM public.stock_items si
      LEFT JOIN public.price_list_items pli
        ON pli.stock_item_id = si.id AND pli.price_list_id = v_list
      LEFT JOIN public.stock_levels sl
        ON sl.stock_item_id = si.id AND sl.warehouse_id = p_warehouse_id
      LEFT JOIN public.warehouse_bins wb
        ON wb.id = sl.bin_id AND wb.is_active
      WHERE si.base_uom_id IS NOT NULL
        AND EXISTS (
          SELECT 1
          FROM (
            SELECT pf.oem_part_number AS oem
            FROM public.part_fitment pf
            WHERE pf.pnc_code = v_section
            UNION
            SELECT cdp.oem_part_number
            FROM public.catalog_diagram_parts cdp
            WHERE cdp.section_slug = v_section
            UNION
            SELECT pf.oem_part_number
            FROM public.catalog_diagrams cd
            JOIN public.part_fitment pf ON pf.diagram_path = cd.storage_path
            WHERE cd.section_slug = v_section
          ) sec
          WHERE upper(btrim(sec.oem)) = upper(btrim(si.oem_part_number))
        )
        AND (
          NOT COALESCE(p_in_stock_only, true)
          OR COALESCE(sl.quantity, 0) > 0
        )
        AND (
          v_category IS NULL
          OR EXISTS (
            SELECT 1
            FROM public.part_fitment pf
            JOIN public.pnc_categories pc ON pc.pnc_code = pf.pnc_code
            WHERE pf.oem_part_number = si.oem_part_number
              AND lower(COALESCE(pc.category_name, '')) LIKE '%' || lower(v_category) || '%'
          )
        )
      ORDER BY si.oem_part_number
      LIMIT v_limit OFFSET v_offset
    ) x;

  ELSE
    -- shop_stock: priced retail rows; optional in-stock + fitment prefilter (Fits ∪ Verify)
    SELECT COALESCE(jsonb_agg(row_to_json(x)::jsonb ORDER BY x.oem_part_number), '[]'::jsonb)
    INTO v_items
    FROM (
      SELECT
        si.id AS stock_item_id,
        si.oem_part_number,
        si.description,
        si.base_uom_id AS uom_id,
        COALESCE(pli.unit_price, 0)::numeric AS unit_price,
        COALESCE(pli.core_charge, 0)::numeric AS core_charge,
        COALESCE(sl.quantity, 0)::numeric AS saleable_qty,
        COALESCE(sl.currency, v_currency) AS currency,
        wb.code AS bin_code,
        (
          SELECT pf.pnc_code
          FROM public.part_fitment pf
          WHERE pf.oem_part_number = si.oem_part_number
            AND pf.pnc_code IS NOT NULL
            AND btrim(pf.pnc_code) <> ''
          ORDER BY pf.pnc_code
          LIMIT 1
        ) AS pnc_code,
        (
          SELECT pc.category_name
          FROM public.part_fitment pf
          JOIN public.pnc_categories pc ON pc.pnc_code = pf.pnc_code
          WHERE pf.oem_part_number = si.oem_part_number
          ORDER BY pc.category_name NULLS LAST
          LIMIT 1
        ) AS category_name,
        (
          SELECT pf.superseded_by
          FROM public.part_fitment pf
          WHERE pf.oem_part_number = si.oem_part_number
            AND pf.superseded_by IS NOT NULL
            AND btrim(pf.superseded_by) <> ''
          ORDER BY pf.superseded_by
          LIMIT 1
        ) AS superseded_by,
        COALESCE((
          SELECT array_agg(DISTINCT c ORDER BY c)
          FROM (
            SELECT btrim(pf.chassis_code) AS c
            FROM public.part_fitment pf
            WHERE pf.oem_part_number = si.oem_part_number
              AND pf.chassis_code IS NOT NULL
              AND btrim(pf.chassis_code) <> ''
          ) s
        ), '{}'::text[]) AS chassis_codes,
        COALESCE((
          SELECT array_agg(DISTINCT e ORDER BY e)
          FROM (
            SELECT btrim(pf.engine_code) AS e
            FROM public.part_fitment pf
            WHERE pf.oem_part_number = si.oem_part_number
              AND pf.engine_code IS NOT NULL
              AND btrim(pf.engine_code) <> ''
          ) s
        ), '{}'::text[]) AS engine_codes
      FROM public.stock_items si
      JOIN public.price_list_items pli
        ON pli.stock_item_id = si.id AND pli.price_list_id = v_list
      LEFT JOIN public.stock_levels sl
        ON sl.stock_item_id = si.id AND sl.warehouse_id = p_warehouse_id
      LEFT JOIN public.warehouse_bins wb
        ON wb.id = sl.bin_id AND wb.is_active
      WHERE si.base_uom_id IS NOT NULL
        AND pli.unit_price >= 0
        AND (
          NOT COALESCE(p_in_stock_only, true)
          OR COALESCE(sl.quantity, 0) > 0
        )
        AND (
          v_category IS NULL
          OR EXISTS (
            SELECT 1
            FROM public.part_fitment pf
            JOIN public.pnc_categories pc ON pc.pnc_code = pf.pnc_code
            WHERE pf.oem_part_number = si.oem_part_number
              AND lower(COALESCE(pc.category_name, '')) LIKE '%' || lower(v_category) || '%'
          )
        )
        AND (
          v_chassis IS NULL
          OR NOT EXISTS (
            -- Verify: no chassis fitment rows
            SELECT 1
            FROM public.part_fitment pf
            WHERE pf.oem_part_number = si.oem_part_number
              AND pf.chassis_code IS NOT NULL
              AND btrim(pf.chassis_code) <> ''
          )
          OR EXISTS (
            -- Fits: chassis (+ engine when both present on latch and row)
            SELECT 1
            FROM public.part_fitment pf
            WHERE pf.oem_part_number = si.oem_part_number
              AND upper(btrim(pf.chassis_code)) = v_chassis
              AND (
                v_engine IS NULL
                OR pf.engine_code IS NULL
                OR btrim(pf.engine_code) = ''
                OR upper(btrim(pf.engine_code)) = v_engine
              )
          )
        )
      ORDER BY si.oem_part_number
      LIMIT v_limit OFFSET v_offset
    ) x;
  END IF;

  RETURN jsonb_build_object(
    'warehouse_id', p_warehouse_id,
    'source', v_source,
    'currency', v_currency,
    'price_list_id', v_list,
    'limit', v_limit,
    'offset', v_offset,
    'items', COALESCE(v_items, '[]'::jsonb)
  );
END;
$$;

COMMENT ON FUNCTION public.list_pos_till_items(
  UUID, TEXT, BOOLEAN, TEXT, TEXT, TEXT, TEXT[], TEXT, INT, INT
) IS
  'Staff TillItem grid SoR. p_source: shop_stock | oems | section. '
  'saleable_qty from stock_levels only (never Meili). '
  'Response example: {"warehouse_id":"…","source":"shop_stock","currency":"USD",'
  '"items":[{"stock_item_id":"…","oem_part_number":"40206-JF00A","description":"…",'
  '"uom_id":"…","unit_price":89.00,"core_charge":15.00,"saleable_qty":3,'
  '"currency":"USD","bin_code":"A-01","pnc_code":"40206","category_name":"Suspension",'
  '"superseded_by":null,"chassis_codes":["R35"],"engine_codes":["VR38DETT"]}]}';

REVOKE ALL ON FUNCTION public.list_pos_till_items(
  UUID, TEXT, BOOLEAN, TEXT, TEXT, TEXT, TEXT[], TEXT, INT, INT
) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.list_pos_till_items(
  UUID, TEXT, BOOLEAN, TEXT, TEXT, TEXT, TEXT[], TEXT, INT, INT
) TO authenticated, service_role;
