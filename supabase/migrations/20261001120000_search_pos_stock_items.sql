-- POS free-text search over the shop's own stock (web POS + tablet).
--
-- search_catalog('part', ...) only matches catalogue fitment rows (part_fitment), so a stocked part
-- without a fitment row could not be found by name or even by its exact part number at the counter.
-- This searches stock_items by part number and by every word of the description; the clients merge
-- it with the catalogue hits (deduplicated by OEM). Read-only, same response shape as search_catalog.
--
-- No new table (no RLS change). SECURITY INVOKER: stock_items RLS still applies to the caller.

CREATE OR REPLACE FUNCTION public.search_pos_stock_items(
  p_query TEXT,
  p_limit INTEGER DEFAULT 50
)
RETURNS JSONB
LANGUAGE plpgsql
STABLE
SET search_path = public
AS $$
DECLARE
  v_query TEXT := trim(COALESCE(p_query, ''));
  v_limit INTEGER := GREATEST(1, LEAST(COALESCE(p_limit, 50), 100));
  v_words TEXT[];
  v_results JSONB;
BEGIN
  PERFORM public._require_sales_staff();

  IF char_length(v_query) < 2 THEN
    RETURN jsonb_build_object('mode', 'part', 'query', v_query, 'results', '[]'::jsonb);
  END IF;

  -- Each word must appear in the part number or the description ("oil filter navara").
  v_words := ARRAY(
    SELECT replace(replace(replace(w, '\', '\\'), '%', '\%'), '_', '\_')
    FROM regexp_split_to_table(v_query, '\s+') AS w
    WHERE w <> ''
  );

  SELECT COALESCE(jsonb_agg(row_to_json(t)::jsonb ORDER BY t.rank, t.oem_part_number), '[]'::jsonb)
  INTO v_results
  FROM (
    SELECT
      'part'::TEXT AS type,
      si.oem_part_number::TEXT AS oem_part_number,
      si.description,
      CASE
        WHEN upper(si.oem_part_number) = upper(v_query) THEN 0
        WHEN upper(si.oem_part_number) LIKE upper(v_words[1]) || '%' THEN 1
        ELSE 2
      END AS rank
    FROM public.stock_items si
    WHERE NOT EXISTS (
      SELECT 1 FROM unnest(v_words) AS w
      WHERE NOT (
        si.oem_part_number ILIKE '%' || w || '%'
        OR COALESCE(si.description, '') ILIKE '%' || w || '%'
      )
    )
    ORDER BY rank, si.oem_part_number
    LIMIT v_limit
  ) t;

  RETURN jsonb_build_object('mode', 'part', 'query', v_query, 'results', v_results);
END;
$$;

COMMENT ON FUNCTION public.search_pos_stock_items(TEXT, INTEGER) IS
  'POS counter search over stock_items by part number and description words (sales/warehouse/admin). Merged client-side with search_catalog part hits.';

REVOKE ALL ON FUNCTION public.search_pos_stock_items(TEXT, INTEGER) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.search_pos_stock_items(TEXT, INTEGER) FROM anon;
GRANT EXECUTE ON FUNCTION public.search_pos_stock_items(TEXT, INTEGER) TO authenticated, service_role;
