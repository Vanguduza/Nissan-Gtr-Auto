# Catalog search — PG FTS interim, Storage, OE cross-refs
-- Meilisearch is deferred; see docs/decisions/2026-07-24-search-index-interim-pg-fts.md

-- ---------------------------------------------------------------------------
-- Natural-key unique indexes for idempotent pipeline upsert
-- COALESCE handles nullable columns without breaking existing rows.
-- ---------------------------------------------------------------------------

CREATE UNIQUE INDEX IF NOT EXISTS vehicle_master_natural_key_idx
  ON public.vehicle_master (
    COALESCE(vin_prefix, ''),
    chassis_code,
    COALESCE(engine_code, ''),
    COALESCE(production_year, 0),
    model_variant
  );

CREATE UNIQUE INDEX IF NOT EXISTS part_fitment_natural_key_idx
  ON public.part_fitment (
    oem_part_number,
    COALESCE(chassis_code, ''),
    COALESCE(engine_code, ''),
    COALESCE(pnc_code, '')
  );

-- ---------------------------------------------------------------------------
-- OE cross-reference numbers (aftermarket / legacy OE ↔ Nissan OEM)
-- ---------------------------------------------------------------------------

CREATE TABLE public.oe_cross_refs (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  oem_part_number VARCHAR(32) NOT NULL,
  oe_number VARCHAR(32) NOT NULL,
  brand TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX oe_cross_refs_natural_key_idx
  ON public.oe_cross_refs (oem_part_number, oe_number, brand);

CREATE INDEX oe_cross_refs_oe_number_idx ON public.oe_cross_refs (oe_number);
CREATE INDEX oe_cross_refs_oem_idx ON public.oe_cross_refs (oem_part_number);

ALTER TABLE public.oe_cross_refs ENABLE ROW LEVEL SECURITY;

CREATE POLICY oe_cross_refs_select_all
  ON public.oe_cross_refs FOR SELECT TO authenticated
  USING (true);

CREATE POLICY oe_cross_refs_staff_write
  ON public.oe_cross_refs FOR ALL TO authenticated
  USING (public.has_staff_role(ARRAY['admin', 'warehouse', 'sales']::public.staff_role[]))
  WITH CHECK (public.has_staff_role(ARRAY['admin', 'warehouse']::public.staff_role[]));

-- ---------------------------------------------------------------------------
-- Full-text search vectors (interim — Meilisearch deferred)
-- ---------------------------------------------------------------------------

ALTER TABLE public.vehicle_master
  ADD COLUMN IF NOT EXISTS search_vector tsvector
  GENERATED ALWAYS AS (
    to_tsvector(
      'simple',
      coalesce(model_variant, '') || ' ' ||
      coalesce(chassis_code, '') || ' ' ||
      coalesce(engine_code, '') || ' ' ||
      coalesce(vin_prefix, '')
    )
  ) STORED;

CREATE INDEX IF NOT EXISTS vehicle_master_search_vector_idx
  ON public.vehicle_master USING gin (search_vector);

ALTER TABLE public.pnc_categories
  ADD COLUMN IF NOT EXISTS search_vector tsvector
  GENERATED ALWAYS AS (
    to_tsvector(
      'simple',
      coalesce(pnc_code, '') || ' ' ||
      coalesce(category_name, '') || ' ' ||
      coalesce(subcategory_name, '')
    )
  ) STORED;

CREATE INDEX IF NOT EXISTS pnc_categories_search_vector_idx
  ON public.pnc_categories USING gin (search_vector);

ALTER TABLE public.part_fitment
  ADD COLUMN IF NOT EXISTS search_vector tsvector
  GENERATED ALWAYS AS (
    to_tsvector(
      'simple',
      coalesce(oem_part_number, '') || ' ' ||
      coalesce(superseded_by, '') || ' ' ||
      coalesce(pnc_code, '') || ' ' ||
      coalesce(chassis_code, '') || ' ' ||
      coalesce(engine_code, '')
    )
  ) STORED;

CREATE INDEX IF NOT EXISTS part_fitment_search_vector_idx
  ON public.part_fitment USING gin (search_vector);

-- ---------------------------------------------------------------------------
-- Storage: catalog diagram images (public read; staff/service write)
-- ---------------------------------------------------------------------------

INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
  'catalog-diagrams',
  'catalog-diagrams',
  true,
  5242880,
  ARRAY['image/png', 'image/jpeg', 'image/webp']
)
ON CONFLICT (id) DO NOTHING;

CREATE POLICY catalog_diagrams_public_read
  ON storage.objects FOR SELECT
  TO public
  USING (bucket_id = 'catalog-diagrams');

CREATE POLICY catalog_diagrams_staff_insert
  ON storage.objects FOR INSERT
  TO authenticated
  WITH CHECK (
    bucket_id = 'catalog-diagrams'
    AND public.has_staff_role(ARRAY['admin', 'warehouse']::public.staff_role[])
  );

CREATE POLICY catalog_diagrams_staff_update
  ON storage.objects FOR UPDATE
  TO authenticated
  USING (
    bucket_id = 'catalog-diagrams'
    AND public.has_staff_role(ARRAY['admin', 'warehouse']::public.staff_role[])
  )
  WITH CHECK (
    bucket_id = 'catalog-diagrams'
    AND public.has_staff_role(ARRAY['admin', 'warehouse']::public.staff_role[])
  );

CREATE POLICY catalog_diagrams_staff_delete
  ON storage.objects FOR DELETE
  TO authenticated
  USING (
    bucket_id = 'catalog-diagrams'
    AND public.has_staff_role(ARRAY['admin', 'warehouse']::public.staff_role[])
  );

-- ---------------------------------------------------------------------------
-- search_catalog RPC — 4-way search (part | vin | model | pnc)
-- SECURITY INVOKER: respects RLS on underlying tables.
-- ---------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION public.search_catalog(p_mode text, p_query text)
RETURNS jsonb
LANGUAGE plpgsql
STABLE
SECURITY INVOKER
SET search_path = public
AS $$
DECLARE
  v_mode text := lower(trim(p_mode));
  v_query text := trim(p_query);
  v_tsquery tsquery;
  v_vin_prefix text;
  v_results jsonb := '[]'::jsonb;
BEGIN
  IF v_query IS NULL OR v_query = '' THEN
    RETURN jsonb_build_object('mode', v_mode, 'query', v_query, 'results', v_results);
  END IF;

  IF v_mode = 'part' THEN
    v_tsquery := plainto_tsquery('simple', v_query);
    SELECT COALESCE(jsonb_agg(row_to_json(t)::jsonb ORDER BY t.oem_part_number), '[]'::jsonb)
    INTO v_results
    FROM (
      SELECT DISTINCT ON (pf.oem_part_number)
        'part'::text AS type,
        pf.oem_part_number,
        pf.pnc_code,
        pf.chassis_code,
        pf.engine_code,
        pf.superseded_by,
        pf.diagram_path,
        pc.category_name,
        pc.subcategory_name,
        xref.oe_number AS matched_oe_number,
        xref.brand AS matched_brand
      FROM public.part_fitment pf
      LEFT JOIN public.pnc_categories pc ON pc.pnc_code = pf.pnc_code
      LEFT JOIN public.oe_cross_refs xref
        ON xref.oem_part_number = pf.oem_part_number
       AND upper(xref.oe_number) = upper(v_query)
      WHERE pf.search_vector @@ v_tsquery
         OR upper(pf.oem_part_number) = upper(v_query)
         OR upper(pf.superseded_by) = upper(v_query)
         OR EXISTS (
           SELECT 1 FROM public.oe_cross_refs x
           WHERE x.oem_part_number = pf.oem_part_number
             AND upper(x.oe_number) = upper(v_query)
         )
      ORDER BY pf.oem_part_number
      LIMIT 50
    ) t;

  ELSIF v_mode = 'vin' THEN
    v_vin_prefix := upper(substring(v_query FROM 1 FOR 11));
    SELECT COALESCE(jsonb_agg(row_to_json(t)::jsonb), '[]'::jsonb)
    INTO v_results
    FROM (
      SELECT
        'vehicle'::text AS type,
        vm.vin_prefix,
        vm.model_variant,
        vm.chassis_code,
        vm.engine_code,
        vm.production_year,
        (
          SELECT COALESCE(jsonb_agg(jsonb_build_object(
            'type', 'part',
            'oem_part_number', pf.oem_part_number,
            'pnc_code', pf.pnc_code,
            'chassis_code', pf.chassis_code,
            'engine_code', pf.engine_code,
            'superseded_by', pf.superseded_by,
            'diagram_path', pf.diagram_path,
            'category_name', pc.category_name,
            'subcategory_name', pc.subcategory_name
          ) ORDER BY pf.oem_part_number), '[]'::jsonb)
          FROM public.part_fitment pf
          LEFT JOIN public.pnc_categories pc ON pc.pnc_code = pf.pnc_code
          WHERE pf.chassis_code = vm.chassis_code
            AND (vm.engine_code IS NULL OR pf.engine_code = vm.engine_code)
          LIMIT 40
        ) AS fitments
      FROM public.vehicle_master vm
      WHERE upper(coalesce(vm.vin_prefix, '')) <> ''
        AND (
          v_vin_prefix LIKE upper(vm.vin_prefix) || '%'
          OR upper(vm.vin_prefix) LIKE v_vin_prefix || '%'
        )
      LIMIT 20
    ) t;

  ELSIF v_mode = 'model' THEN
    v_tsquery := plainto_tsquery('simple', v_query);
    SELECT COALESCE(jsonb_agg(row_to_json(t)::jsonb ORDER BY t.model_variant), '[]'::jsonb)
    INTO v_results
    FROM (
      SELECT
        'vehicle'::text AS type,
        vm.vin_prefix,
        vm.model_variant,
        vm.chassis_code,
        vm.engine_code,
        vm.production_year
      FROM public.vehicle_master vm
      WHERE vm.search_vector @@ v_tsquery
         OR lower(vm.model_variant) LIKE '%' || lower(v_query) || '%'
         OR lower(coalesce(vm.chassis_code, '')) LIKE '%' || lower(v_query) || '%'
         OR lower(coalesce(vm.engine_code, '')) LIKE '%' || lower(v_query) || '%'
      ORDER BY vm.model_variant
      LIMIT 50
    ) t;

  ELSIF v_mode = 'pnc' THEN
    v_tsquery := plainto_tsquery('simple', v_query);
    SELECT COALESCE(jsonb_agg(row_to_json(t)::jsonb ORDER BY t.pnc_code), '[]'::jsonb)
    INTO v_results
    FROM (
      SELECT
        'pnc'::text AS type,
        pc.pnc_code,
        pc.category_name,
        pc.subcategory_name,
        (
          SELECT COALESCE(jsonb_agg(jsonb_build_object(
            'type', 'part',
            'oem_part_number', pf.oem_part_number,
            'pnc_code', pf.pnc_code,
            'chassis_code', pf.chassis_code,
            'engine_code', pf.engine_code,
            'superseded_by', pf.superseded_by,
            'diagram_path', pf.diagram_path
          ) ORDER BY pf.oem_part_number), '[]'::jsonb)
          FROM public.part_fitment pf
          WHERE pf.pnc_code = pc.pnc_code
          LIMIT 40
        ) AS fitments
      FROM public.pnc_categories pc
      WHERE pc.search_vector @@ v_tsquery
         OR pc.pnc_code = v_query
         OR lower(pc.category_name) LIKE '%' || lower(v_query) || '%'
         OR lower(coalesce(pc.subcategory_name, '')) LIKE '%' || lower(v_query) || '%'
      ORDER BY pc.pnc_code
      LIMIT 50
    ) t;

  ELSE
    RAISE EXCEPTION 'search_catalog: unsupported mode % (expected part|vin|model|pnc)', p_mode;
  END IF;

  RETURN jsonb_build_object('mode', v_mode, 'query', v_query, 'results', v_results);
END;
$$;

REVOKE ALL ON FUNCTION public.search_catalog(text, text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.search_catalog(text, text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.search_catalog(text, text) TO service_role;

COMMENT ON FUNCTION public.search_catalog IS
  'Interim 4-way catalog search via PostgreSQL FTS. Meilisearch indexing deferred.';
