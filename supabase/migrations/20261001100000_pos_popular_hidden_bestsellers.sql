-- Owner decision D1 (2026-10-01): the POS Popular Items row is server best sellers + operator pins,
-- and the operator can remove ANY item. Removing a best seller hides it for that operator only,
-- server-side, until they add it back. Pins already have their own table; this adds the hide list.

CREATE TABLE IF NOT EXISTS public.pos_operator_hidden_bestsellers (
  user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  stock_item_id UUID NOT NULL REFERENCES public.stock_items(id) ON DELETE CASCADE,
  hidden_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, stock_item_id)
);

ALTER TABLE public.pos_operator_hidden_bestsellers ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.pos_operator_hidden_bestsellers FROM anon;

DROP POLICY IF EXISTS pos_operator_hidden_bestsellers_own_read ON public.pos_operator_hidden_bestsellers;
CREATE POLICY pos_operator_hidden_bestsellers_own_read
  ON public.pos_operator_hidden_bestsellers FOR SELECT TO authenticated
  USING (user_id = auth.uid());

DROP POLICY IF EXISTS pos_operator_hidden_bestsellers_own_write ON public.pos_operator_hidden_bestsellers;
CREATE POLICY pos_operator_hidden_bestsellers_own_write
  ON public.pos_operator_hidden_bestsellers FOR ALL TO authenticated
  USING (user_id = auth.uid())
  WITH CHECK (user_id = auth.uid());

CREATE OR REPLACE FUNCTION public.list_pos_hidden_bestsellers()
RETURNS TABLE (stock_item_id uuid, hidden_at timestamptz)
LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path = public AS $$
BEGIN
  PERFORM public._require_sales_staff();
  RETURN QUERY
  SELECT h.stock_item_id, h.hidden_at
  FROM public.pos_operator_hidden_bestsellers h
  WHERE h.user_id = auth.uid()
  ORDER BY h.hidden_at DESC;
END;
$$;

CREATE OR REPLACE FUNCTION public.hide_pos_bestseller(p_stock_item_id uuid)
RETURNS boolean
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
BEGIN
  PERFORM public._require_sales_staff();
  IF p_stock_item_id IS NULL THEN
    RAISE EXCEPTION 'stock item required';
  END IF;
  INSERT INTO public.pos_operator_hidden_bestsellers (user_id, stock_item_id)
  VALUES (auth.uid(), p_stock_item_id)
  ON CONFLICT (user_id, stock_item_id) DO UPDATE SET hidden_at = now();
  RETURN true;
END;
$$;

CREATE OR REPLACE FUNCTION public.unhide_pos_bestseller(p_stock_item_id uuid)
RETURNS boolean
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE v_count integer;
BEGIN
  PERFORM public._require_sales_staff();
  DELETE FROM public.pos_operator_hidden_bestsellers
  WHERE user_id = auth.uid() AND stock_item_id = p_stock_item_id;
  GET DIAGNOSTICS v_count = ROW_COUNT;
  RETURN v_count > 0;
END;
$$;

REVOKE ALL ON FUNCTION public.list_pos_hidden_bestsellers() FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.hide_pos_bestseller(uuid) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.unhide_pos_bestseller(uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.list_pos_hidden_bestsellers() TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.hide_pos_bestseller(uuid) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.unhide_pos_bestseller(uuid) TO authenticated, service_role;
