-- Structural smoke for POS vehicle selection/search propagation.
DO $$
BEGIN
  IF to_regprocedure('public.set_pos_cart_vehicle(uuid,text,text,text,text,text)') IS NULL THEN
    RAISE EXCEPTION 'set_pos_cart_vehicle missing';
  END IF;
  IF to_regprocedure('public.search_pos_vehicle_spares(text,text,text,text,integer)') IS NULL THEN
    RAISE EXCEPTION 'search_pos_vehicle_spares missing';
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema='public' AND table_name='sales_invoices' AND column_name='vehicle_engine_code'
  ) THEN
    RAISE EXCEPTION 'sales invoice vehicle snapshot columns missing';
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema='public' AND table_name='pos_carts' AND column_name='vehicle_model_slug'
  ) THEN
    RAISE EXCEPTION 'pos cart vehicle columns missing';
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM pg_trigger
    WHERE tgname='sales_invoices_vehicle_snapshot' AND NOT tgisinternal
  ) THEN
    RAISE EXCEPTION 'invoice vehicle snapshot trigger missing';
  END IF;
END $$;
