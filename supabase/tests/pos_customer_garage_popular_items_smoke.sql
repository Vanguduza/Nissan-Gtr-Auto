-- Structural smoke for customer management, multi-vehicle sale context and Popular Items pins.
DO $$
BEGIN
  IF to_regprocedure('public.list_pos_customers(text,integer)') IS NULL THEN
    RAISE EXCEPTION 'list_pos_customers missing';
  END IF;
  IF to_regprocedure('public.create_pos_customer(text,text,text,text,text,text)') IS NULL THEN
    RAISE EXCEPTION 'create_pos_customer missing';
  END IF;
  IF to_regprocedure('public.upsert_pos_customer_garage_vehicle(uuid,uuid,text,text,text,text,text,text,text,boolean)') IS NULL THEN
    RAISE EXCEPTION 'upsert_pos_customer_garage_vehicle missing';
  END IF;
  IF to_regprocedure('public.list_pos_customer_garage(uuid)') IS NULL THEN
    RAISE EXCEPTION 'list_pos_customer_garage missing';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema='public' AND table_name='pos_carts' AND column_name='vehicle_contexts'
  ) THEN
    RAISE EXCEPTION 'pos_carts.vehicle_contexts missing';
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema='public' AND table_name='sales_invoices' AND column_name='customer_display_name'
  ) THEN
    RAISE EXCEPTION 'sales_invoices customer receipt snapshot missing';
  END IF;

  IF to_regclass('public.pos_operator_popular_pins') IS NULL THEN
    RAISE EXCEPTION 'pos_operator_popular_pins missing';
  END IF;
  IF to_regprocedure('public.list_pos_popular_pins()') IS NULL THEN
    RAISE EXCEPTION 'list_pos_popular_pins missing';
  END IF;
  IF to_regprocedure('public.upsert_pos_popular_pin(text,text,text,text,text,text,text,text,text,text,text)') IS NULL THEN
    RAISE EXCEPTION 'upsert_pos_popular_pin missing';
  END IF;
  IF to_regprocedure('public.delete_pos_popular_pin(text,text)') IS NULL THEN
    RAISE EXCEPTION 'delete_pos_popular_pin missing';
  END IF;

  IF to_regprocedure('public.list_catalog_diagrams(text,text,text,text)') IS NULL THEN
    RAISE EXCEPTION 'list_catalog_diagrams missing';
  END IF;
  IF to_regprocedure('public.get_catalog_diagram_by_slug(text,text,text,text,text)') IS NULL THEN
    RAISE EXCEPTION 'get_catalog_diagram_by_slug missing';
  END IF;
END;
$$;
