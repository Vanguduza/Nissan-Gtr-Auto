-- Operator-owned Popular Items pins for the tablet POS.
-- Algorithmic best sellers remain read-only; these rows are explicit per-staff shortcuts.

CREATE TABLE IF NOT EXISTS public.pos_operator_popular_pins (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  item_type TEXT NOT NULL CHECK (item_type IN ('part','model','category','subcategory')),
  item_key TEXT NOT NULL,
  label TEXT NOT NULL,
  subtitle TEXT,
  search_query TEXT NOT NULL,
  maker_slug TEXT,
  model_slug TEXT,
  category_name TEXT,
  subcategory_name TEXT,
  oem_part_number TEXT,
  image_url TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (user_id, item_type, item_key),
  CHECK (length(trim(item_key)) BETWEEN 1 AND 240),
  CHECK (length(trim(label)) BETWEEN 1 AND 240),
  CHECK (length(trim(search_query)) BETWEEN 1 AND 240)
);

CREATE INDEX IF NOT EXISTS pos_operator_popular_pins_user_updated_idx
  ON public.pos_operator_popular_pins (user_id, updated_at DESC);

ALTER TABLE public.pos_operator_popular_pins ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS pos_operator_popular_pins_own_read ON public.pos_operator_popular_pins;
CREATE POLICY pos_operator_popular_pins_own_read
  ON public.pos_operator_popular_pins FOR SELECT TO authenticated
  USING (user_id = auth.uid());

DROP POLICY IF EXISTS pos_operator_popular_pins_own_write ON public.pos_operator_popular_pins;
CREATE POLICY pos_operator_popular_pins_own_write
  ON public.pos_operator_popular_pins FOR ALL TO authenticated
  USING (user_id = auth.uid())
  WITH CHECK (user_id = auth.uid());

CREATE OR REPLACE FUNCTION public.list_pos_popular_pins()
RETURNS TABLE (
  item_type text, item_key text, label text, subtitle text, search_query text,
  maker_slug text, model_slug text, category_name text, subcategory_name text,
  oem_part_number text, image_url text, updated_at timestamptz
)
LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path = public AS $$
BEGIN
  PERFORM public._require_sales_staff();
  RETURN QUERY
  SELECT p.item_type, p.item_key, p.label, p.subtitle, p.search_query,
         p.maker_slug, p.model_slug, p.category_name, p.subcategory_name,
         p.oem_part_number, p.image_url, p.updated_at
  FROM public.pos_operator_popular_pins p
  WHERE p.user_id = auth.uid()
  ORDER BY p.updated_at DESC, p.label
  LIMIT 24;
END;
$$;

CREATE OR REPLACE FUNCTION public.upsert_pos_popular_pin(
  p_item_type text,
  p_item_key text,
  p_label text,
  p_subtitle text DEFAULT NULL,
  p_search_query text DEFAULT NULL,
  p_maker_slug text DEFAULT NULL,
  p_model_slug text DEFAULT NULL,
  p_category_name text DEFAULT NULL,
  p_subcategory_name text DEFAULT NULL,
  p_oem_part_number text DEFAULT NULL,
  p_image_url text DEFAULT NULL
)
RETURNS uuid
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
  v_id uuid;
  v_type text := lower(trim(COALESCE(p_item_type, '')));
  v_key text := trim(COALESCE(p_item_key, ''));
  v_label text := trim(COALESCE(p_label, ''));
  v_query text := trim(COALESCE(p_search_query, p_label, ''));
  v_image text := NULLIF(trim(COALESCE(p_image_url, '')), '');
BEGIN
  PERFORM public._require_sales_staff();
  IF auth.uid() IS NULL THEN RAISE EXCEPTION 'authenticated staff required'; END IF;
  IF v_type NOT IN ('part','model','category','subcategory') THEN RAISE EXCEPTION 'unsupported popular item type'; END IF;
  IF v_key = '' OR v_label = '' OR v_query = '' THEN RAISE EXCEPTION 'item key, label and search query required'; END IF;

  IF v_image IS NULL AND v_type = 'part' AND NULLIF(trim(COALESCE(p_oem_part_number,'')), '') IS NOT NULL THEN
    SELECT sii.storage_path INTO v_image
    FROM public.stock_items si
    JOIN public.stock_item_images sii ON sii.stock_item_id = si.id
    WHERE si.oem_part_number = trim(p_oem_part_number)
    ORDER BY sii.is_primary DESC, sii.sort_order, sii.created_at
    LIMIT 1;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM public.pos_operator_popular_pins
    WHERE user_id = auth.uid() AND item_type = v_type AND item_key = v_key
  ) AND (
    SELECT count(*) FROM public.pos_operator_popular_pins WHERE user_id = auth.uid()
  ) >= 24 THEN
    RAISE EXCEPTION 'popular item pin limit reached (24)';
  END IF;

  INSERT INTO public.pos_operator_popular_pins (
    user_id, item_type, item_key, label, subtitle, search_query,
    maker_slug, model_slug, category_name, subcategory_name, oem_part_number, image_url
  ) VALUES (
    auth.uid(), v_type, v_key, v_label, NULLIF(trim(COALESCE(p_subtitle,'')),''), v_query,
    NULLIF(trim(COALESCE(p_maker_slug,'')),''), NULLIF(trim(COALESCE(p_model_slug,'')),''),
    NULLIF(trim(COALESCE(p_category_name,'')),''), NULLIF(trim(COALESCE(p_subcategory_name,'')),''),
    NULLIF(trim(COALESCE(p_oem_part_number,'')),''), v_image
  )
  ON CONFLICT (user_id, item_type, item_key) DO UPDATE SET
    label = EXCLUDED.label,
    subtitle = EXCLUDED.subtitle,
    search_query = EXCLUDED.search_query,
    maker_slug = EXCLUDED.maker_slug,
    model_slug = EXCLUDED.model_slug,
    category_name = EXCLUDED.category_name,
    subcategory_name = EXCLUDED.subcategory_name,
    oem_part_number = EXCLUDED.oem_part_number,
    image_url = EXCLUDED.image_url,
    updated_at = now()
  RETURNING id INTO v_id;
  RETURN v_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.delete_pos_popular_pin(p_item_type text, p_item_key text)
RETURNS boolean
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE v_count integer;
BEGIN
  PERFORM public._require_sales_staff();
  DELETE FROM public.pos_operator_popular_pins
  WHERE user_id = auth.uid()
    AND item_type = lower(trim(COALESCE(p_item_type,'')))
    AND item_key = trim(COALESCE(p_item_key,''));
  GET DIAGNOSTICS v_count = ROW_COUNT;
  RETURN v_count > 0;
END;
$$;

REVOKE ALL ON FUNCTION public.list_pos_popular_pins() FROM PUBLIC;
REVOKE ALL ON FUNCTION public.upsert_pos_popular_pin(text,text,text,text,text,text,text,text,text,text,text) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.delete_pos_popular_pin(text,text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.list_pos_popular_pins() TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.upsert_pos_popular_pin(text,text,text,text,text,text,text,text,text,text,text) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.delete_pos_popular_pin(text,text) TO authenticated, service_role;
