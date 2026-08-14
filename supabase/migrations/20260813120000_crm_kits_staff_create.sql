-- CRM kits: widen mutation roles to admin|sales|warehouse; composite create RPC.
-- Kit OEM is staff-entered; optional chassis → part_fitment; sell_mode default explode.
-- No ZIMRA / payroll tax / HTML5 QR.

-- ---------------------------------------------------------------------------
-- Role helper (parity with product-pages write roles)
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public._require_kit_staff()
RETURNS void
LANGUAGE plpgsql
STABLE
AS $$
BEGIN
  IF NOT (
    auth.role() = 'service_role'
    OR public.has_staff_role(
      ARRAY['admin', 'sales', 'warehouse']::public.staff_role[]
    )
  ) THEN
    RAISE EXCEPTION 'admin, sales, or warehouse role required for kit mutations';
  END IF;
END;
$$;

COMMENT ON FUNCTION public._require_kit_staff() IS
  'Kit BOM create/update/component mutations — admin | sales | warehouse.';

-- ---------------------------------------------------------------------------
-- RLS: sales may write kits (WITH CHECK was warehouse|admin only)
-- ---------------------------------------------------------------------------
DROP POLICY IF EXISTS item_kits_staff_write ON public.item_kits;
CREATE POLICY item_kits_staff_write
  ON public.item_kits FOR ALL TO authenticated
  USING (
    public.has_staff_role(
      ARRAY['admin', 'warehouse', 'sales']::public.staff_role[]
    )
  )
  WITH CHECK (
    public.has_staff_role(
      ARRAY['admin', 'warehouse', 'sales']::public.staff_role[]
    )
  );

DROP POLICY IF EXISTS item_kit_components_staff_write ON public.item_kit_components;
CREATE POLICY item_kit_components_staff_write
  ON public.item_kit_components FOR ALL TO authenticated
  USING (
    public.has_staff_role(
      ARRAY['admin', 'warehouse', 'sales']::public.staff_role[]
    )
  )
  WITH CHECK (
    public.has_staff_role(
      ARRAY['admin', 'warehouse', 'sales']::public.staff_role[]
    )
  );

-- ---------------------------------------------------------------------------
-- Existing kit RPCs — widen role gate
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.create_item_kit(
  p_stock_item_id UUID,
  p_sell_mode public.kit_sell_mode DEFAULT 'explode'
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_id UUID;
BEGIN
  PERFORM public._require_kit_staff();

  IF NOT EXISTS (SELECT 1 FROM public.stock_items WHERE id = p_stock_item_id) THEN
    RAISE EXCEPTION 'stock item not found: %', p_stock_item_id;
  END IF;

  INSERT INTO public.item_kits (stock_item_id, sell_mode, created_by)
  VALUES (p_stock_item_id, COALESCE(p_sell_mode, 'explode'), auth.uid())
  RETURNING id INTO v_id;

  RETURN v_id;
END;
$$;

DROP FUNCTION IF EXISTS public.update_item_kit(UUID, public.kit_sell_mode, BOOLEAN);

CREATE OR REPLACE FUNCTION public.update_item_kit(
  p_kit_id UUID,
  p_sell_mode public.kit_sell_mode DEFAULT NULL,
  p_is_active BOOLEAN DEFAULT NULL,
  p_title TEXT DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_stock_item_id UUID;
BEGIN
  PERFORM public._require_kit_staff();

  UPDATE public.item_kits
  SET
    sell_mode = COALESCE(p_sell_mode, sell_mode),
    is_active = COALESCE(p_is_active, is_active),
    updated_at = now()
  WHERE id = p_kit_id
  RETURNING stock_item_id INTO v_stock_item_id;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'kit not found: %', p_kit_id;
  END IF;

  IF p_title IS NOT NULL AND btrim(p_title) <> '' THEN
    UPDATE public.stock_items
    SET description = btrim(p_title)
    WHERE id = v_stock_item_id;
  END IF;

  RETURN p_kit_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.add_kit_component(
  p_kit_id UUID,
  p_component_item_id UUID,
  p_qty NUMERIC,
  p_uom_id UUID
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_id UUID;
BEGIN
  PERFORM public._require_kit_staff();

  IF p_qty IS NULL OR p_qty <= 0 THEN
    RAISE EXCEPTION 'component qty must be > 0';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM public.item_kits WHERE id = p_kit_id) THEN
    RAISE EXCEPTION 'kit not found: %', p_kit_id;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM public.stock_items WHERE id = p_component_item_id) THEN
    RAISE EXCEPTION 'component stock item not found: %', p_component_item_id;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM public.uoms WHERE id = p_uom_id) THEN
    RAISE EXCEPTION 'uom not found: %', p_uom_id;
  END IF;

  INSERT INTO public.item_kit_components (kit_id, component_item_id, qty, uom_id)
  VALUES (p_kit_id, p_component_item_id, p_qty, p_uom_id)
  ON CONFLICT (kit_id, component_item_id) DO UPDATE
  SET qty = EXCLUDED.qty, uom_id = EXCLUDED.uom_id
  RETURNING id INTO v_id;

  RETURN v_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.remove_kit_component(
  p_kit_id UUID,
  p_component_item_id UUID
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_remaining INT;
BEGIN
  PERFORM public._require_kit_staff();

  DELETE FROM public.item_kit_components
  WHERE kit_id = p_kit_id AND component_item_id = p_component_item_id;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'kit component not found';
  END IF;

  SELECT count(*)::int INTO v_remaining
  FROM public.item_kit_components
  WHERE kit_id = p_kit_id;

  IF v_remaining < 2 THEN
    RAISE EXCEPTION 'kit must retain at least 2 components (remaining: %)', v_remaining;
  END IF;
END;
$$;

-- ---------------------------------------------------------------------------
-- Composite create: stock_items header + kit + ≥2 components + optional fitment
-- p_components: [{ "stock_item_id": uuid, "qty": number? }, ...]
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.create_kit_with_components(
  p_oem TEXT,
  p_title TEXT,
  p_components JSONB,
  p_chassis_code TEXT DEFAULT NULL,
  p_sell_mode public.kit_sell_mode DEFAULT 'explode'
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_oem TEXT;
  v_title TEXT;
  v_chassis TEXT;
  v_uom_ea UUID;
  v_stock_item_id UUID;
  v_kit_id UUID;
  v_comp JSONB;
  v_comp_id UUID;
  v_comp_qty NUMERIC;
  v_comp_uom UUID;
  v_seen UUID[] := ARRAY[]::UUID[];
  v_count INT := 0;
BEGIN
  PERFORM public._require_kit_staff();

  v_oem := upper(btrim(COALESCE(p_oem, '')));
  v_title := btrim(COALESCE(p_title, ''));
  v_chassis := NULLIF(upper(btrim(COALESCE(p_chassis_code, ''))), '');

  IF v_oem = '' THEN
    RAISE EXCEPTION 'kit OEM is required';
  END IF;
  IF length(v_oem) > 32 THEN
    RAISE EXCEPTION 'kit OEM must be ≤ 32 characters';
  END IF;
  IF v_title = '' THEN
    RAISE EXCEPTION 'kit title is required';
  END IF;
  IF p_components IS NULL OR jsonb_typeof(p_components) <> 'array' THEN
    RAISE EXCEPTION 'p_components must be a JSON array';
  END IF;

  SELECT id INTO v_uom_ea FROM public.uoms WHERE code = 'EA' LIMIT 1;
  IF v_uom_ea IS NULL THEN
    RAISE EXCEPTION 'EA uom not found';
  END IF;

  FOR v_comp IN SELECT * FROM jsonb_array_elements(p_components)
  LOOP
    v_comp_id := NULLIF(btrim(COALESCE(v_comp->>'stock_item_id', '')), '')::UUID;
    IF v_comp_id IS NULL THEN
      RAISE EXCEPTION 'each component requires stock_item_id';
    END IF;
    IF v_comp_id = ANY (v_seen) THEN
      RAISE EXCEPTION 'duplicate component stock_item_id: %', v_comp_id;
    END IF;
    v_seen := array_append(v_seen, v_comp_id);
    v_count := v_count + 1;
  END LOOP;

  IF v_count < 2 THEN
    RAISE EXCEPTION 'kit requires at least 2 components (got %)', v_count;
  END IF;

  SELECT id INTO v_stock_item_id
  FROM public.stock_items
  WHERE oem_part_number = v_oem;

  IF v_stock_item_id IS NOT NULL THEN
    IF EXISTS (
      SELECT 1 FROM public.item_kits WHERE stock_item_id = v_stock_item_id
    ) THEN
      RAISE EXCEPTION 'kit already exists for OEM %', v_oem;
    END IF;
    UPDATE public.stock_items
    SET
      description = v_title,
      base_uom_id = COALESCE(base_uom_id, v_uom_ea)
    WHERE id = v_stock_item_id;
  ELSE
    INSERT INTO public.stock_items (oem_part_number, description, base_uom_id)
    VALUES (v_oem, v_title, v_uom_ea)
    RETURNING id INTO v_stock_item_id;
  END IF;

  INSERT INTO public.item_kits (stock_item_id, sell_mode, created_by)
  VALUES (
    v_stock_item_id,
    COALESCE(p_sell_mode, 'explode'),
    auth.uid()
  )
  RETURNING id INTO v_kit_id;

  FOR v_comp IN SELECT * FROM jsonb_array_elements(p_components)
  LOOP
    v_comp_id := (v_comp->>'stock_item_id')::UUID;
    v_comp_qty := COALESCE(NULLIF(v_comp->>'qty', '')::NUMERIC, 1);
    IF v_comp_qty <= 0 THEN
      RAISE EXCEPTION 'component qty must be > 0';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM public.stock_items WHERE id = v_comp_id) THEN
      RAISE EXCEPTION 'component stock item not found: %', v_comp_id;
    END IF;
    IF v_comp_id = v_stock_item_id THEN
      RAISE EXCEPTION 'kit cannot include itself as a component';
    END IF;
    IF EXISTS (
      SELECT 1 FROM public.item_kits k
      WHERE k.stock_item_id = v_comp_id AND k.is_active
    ) THEN
      RAISE EXCEPTION 'nested kits as components are not supported';
    END IF;

    SELECT COALESCE(base_uom_id, v_uom_ea) INTO v_comp_uom
    FROM public.stock_items
    WHERE id = v_comp_id;

    INSERT INTO public.item_kit_components (kit_id, component_item_id, qty, uom_id)
    VALUES (v_kit_id, v_comp_id, v_comp_qty, v_comp_uom);
  END LOOP;

  IF v_chassis IS NOT NULL THEN
    IF NOT EXISTS (
      SELECT 1 FROM public.part_fitment pf
      WHERE pf.oem_part_number = v_oem
        AND COALESCE(pf.chassis_code, '') = v_chassis
        AND COALESCE(pf.engine_code, '') = ''
        AND COALESCE(pf.pnc_code, '') = ''
    ) THEN
      INSERT INTO public.part_fitment (oem_part_number, chassis_code)
      VALUES (v_oem, v_chassis);
    END IF;
  END IF;

  RETURN v_kit_id;
END;
$$;

COMMENT ON FUNCTION public.create_kit_with_components(TEXT, TEXT, JSONB, TEXT, public.kit_sell_mode) IS
  'CRM kit create: ensure stock_items header, item_kits(explode default), ≥2 components, optional chassis fitment.';

REVOKE ALL ON FUNCTION public._require_kit_staff() FROM PUBLIC;
REVOKE ALL ON FUNCTION public.create_item_kit(UUID, public.kit_sell_mode) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.update_item_kit(UUID, public.kit_sell_mode, BOOLEAN, TEXT) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.add_kit_component(UUID, UUID, NUMERIC, UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.remove_kit_component(UUID, UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.create_kit_with_components(TEXT, TEXT, JSONB, TEXT, public.kit_sell_mode) FROM PUBLIC;

GRANT EXECUTE ON FUNCTION public._require_kit_staff()
  TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.create_item_kit(UUID, public.kit_sell_mode)
  TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.update_item_kit(UUID, public.kit_sell_mode, BOOLEAN, TEXT)
  TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.add_kit_component(UUID, UUID, NUMERIC, UUID)
  TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.remove_kit_component(UUID, UUID)
  TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.create_kit_with_components(TEXT, TEXT, JSONB, TEXT, public.kit_sell_mode)
  TO authenticated, service_role;
