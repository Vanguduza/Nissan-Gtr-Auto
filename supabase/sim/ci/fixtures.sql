-- SIMULATION ONLY. ci/run.sh opts in on its fixed localhost throwaway database.
-- This is not a production seed or migration. Reference IDs are looked up from
-- the active baseline; no UUIDs or rows are copied from historical snapshots.
DO $simulation_fixtures$
DECLARE
  v_uom uuid;
  v_retail uuid;
  v_item uuid;
  fixture record;
BEGIN
  IF current_setting('gtr.simulation_project', true) IS DISTINCT FROM 'gtr-sim-ci' THEN
    RAISE EXCEPTION 'Simulation fixtures require the gtr-sim-ci local harness';
  END IF;
  SELECT id INTO STRICT v_uom FROM public.uoms WHERE code = 'EA';
  SELECT id INTO STRICT v_retail FROM public.price_lists
    WHERE code = 'RETAIL' AND currency = 'USD' AND is_active AND is_default;
  IF (SELECT count(*) FROM public.warehouses WHERE code IN ('MAIN', 'WH2') AND is_active) <> 2 THEN
    RAISE EXCEPTION 'Simulation requires the active MAIN and WH2 reference warehouses';
  END IF;

  -- The COD scenario collects USD 30 + 20 for two units. Every non-serial
  -- opening SKU therefore costs USD 25; selection order cannot change its due.
  -- Multiple SKUs leave stock available across all eight sequential scenarios.
  FOR fixture IN SELECT * FROM (VALUES
    ('SIM-CI-OIL-FILTER', 'Simulated oil filter', false, 25::numeric),
    ('SIM-CI-BRAKE-PADS', 'Simulated brake pads', false, 25::numeric),
    ('SIM-CI-SPARK-PLUG', 'Simulated spark plug', false, 25::numeric),
    ('SIM-CI-AIR-FILTER', 'Simulated air filter', false, 25::numeric),
    ('SIM-CI-CABIN-FILTER', 'Simulated cabin filter', false, 25::numeric),
    ('SIM-CI-CHAIN-GUIDE', 'Simulated timing chain guide', false, 25::numeric),
    ('SIM-CI-SERIAL-UNIT', 'Simulated serialized component', true, 310::numeric)
  ) AS f(code, description, requires_serial, price)
  LOOP
    INSERT INTO public.stock_items(oem_part_number, description, base_uom_id, requires_serial)
      VALUES (fixture.code, fixture.description, v_uom, fixture.requires_serial)
      ON CONFLICT (oem_part_number) DO NOTHING;
    SELECT id INTO STRICT v_item FROM public.stock_items WHERE oem_part_number = fixture.code;
    IF NOT EXISTS (SELECT 1 FROM public.stock_items
      WHERE id = v_item AND base_uom_id = v_uom AND requires_serial = fixture.requires_serial) THEN
      RAISE EXCEPTION 'Conflicting simulation SKU: %', fixture.code;
    END IF;
    INSERT INTO public.price_list_items(price_list_id, stock_item_id, unit_price)
      VALUES (v_retail, v_item, fixture.price)
      ON CONFLICT (price_list_id, stock_item_id) DO UPDATE SET unit_price = EXCLUDED.unit_price;
  END LOOP;
  RAISE NOTICE 'Simulation catalogue ready: six non-serial SKUs and one serialized SKU';
END;
$simulation_fixtures$;
