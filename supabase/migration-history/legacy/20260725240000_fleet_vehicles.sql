-- Phase A: Company fleet vehicles (ops/delivery vans).
-- Distinct from B2B price_lists code FLEET and customer_garage_vehicles — do not touch those.
-- No Realtime on fleet_vehicles. No ZIMRA / payroll tax.
-- Mutations: RPC-only via app.fleet_rpc GUC + mutation guard (mirror logistics).

-- ---------------------------------------------------------------------------
-- Enum
-- ---------------------------------------------------------------------------
CREATE TYPE public.fleet_vehicle_status AS ENUM ('active', 'in_service', 'retired');

-- ---------------------------------------------------------------------------
-- Table
-- ---------------------------------------------------------------------------
CREATE TABLE public.fleet_vehicles (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  plate TEXT NOT NULL,
  label TEXT,
  status public.fleet_vehicle_status NOT NULL DEFAULT 'active',
  assigned_driver_user_id UUID REFERENCES auth.users (id) ON DELETE SET NULL,
  notes TEXT,
  created_by UUID REFERENCES auth.users (id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT fleet_vehicles_plate_nonempty CHECK (length(trim(plate)) > 0)
);

COMMENT ON TABLE public.fleet_vehicles IS
  'Company delivery/ops vehicles. Not customer_garage_vehicles; not B2B FLEET price list.';

CREATE UNIQUE INDEX fleet_vehicles_plate_uidx ON public.fleet_vehicles (plate);
CREATE INDEX fleet_vehicles_status_idx ON public.fleet_vehicles (status);
CREATE INDEX fleet_vehicles_assignee_idx ON public.fleet_vehicles (assigned_driver_user_id);

-- ---------------------------------------------------------------------------
-- Helpers / mutation gate
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public._fleet_rpc_active()
RETURNS boolean
LANGUAGE sql
STABLE
AS $$
  SELECT COALESCE(current_setting('app.fleet_rpc', true), '') = '1';
$$;

CREATE OR REPLACE FUNCTION public._fleet_begin_rpc()
RETURNS void
LANGUAGE sql
AS $$
  SELECT set_config('app.fleet_rpc', '1', true);
$$;

CREATE OR REPLACE FUNCTION public._require_fleet_staff()
RETURNS void
LANGUAGE plpgsql
STABLE
AS $$
BEGIN
  IF NOT (
    auth.role() = 'service_role'
    OR public.has_staff_role(
      ARRAY['admin', 'warehouse', 'dispatcher']::public.staff_role[]
    )
  ) THEN
    RAISE EXCEPTION 'admin, warehouse, or dispatcher role required for fleet';
  END IF;
END;
$$;

CREATE OR REPLACE FUNCTION public._normalize_fleet_plate(p_plate TEXT)
RETURNS TEXT
LANGUAGE sql
IMMUTABLE
AS $$
  SELECT upper(trim(p_plate));
$$;

CREATE OR REPLACE FUNCTION public._assert_fleet_driver_assignee(p_user_id UUID)
RETURNS void
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  IF p_user_id IS NULL THEN
    RETURN;
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM public.staff_roles
    WHERE user_id = p_user_id AND role = 'driver'
  ) THEN
    RAISE EXCEPTION 'assigned_driver_user_id must have driver staff role (or be null)';
  END IF;
END;
$$;

CREATE OR REPLACE FUNCTION public.guard_fleet_vehicle_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  IF public._fleet_rpc_active() THEN
    IF TG_OP = 'DELETE' THEN
      RETURN OLD;
    END IF;
    RETURN NEW;
  END IF;

  IF TG_OP = 'INSERT' THEN
    RAISE EXCEPTION 'fleet_vehicles: use upsert_fleet_vehicle RPC';
  ELSIF TG_OP = 'DELETE' THEN
    RAISE EXCEPTION 'fleet_vehicles: direct delete not allowed';
  ELSIF TG_OP = 'UPDATE' THEN
    RAISE EXCEPTION 'fleet_vehicles: use upsert_fleet_vehicle / set_fleet_vehicle_status RPCs';
  END IF;

  RETURN NULL;
END;
$$;

DROP TRIGGER IF EXISTS fleet_vehicles_mutation_guard ON public.fleet_vehicles;
CREATE TRIGGER fleet_vehicles_mutation_guard
  BEFORE INSERT OR UPDATE OR DELETE ON public.fleet_vehicles
  FOR EACH ROW
  EXECUTE PROCEDURE public.guard_fleet_vehicle_mutation();

CREATE OR REPLACE FUNCTION public.fleet_vehicles_touch_updated()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  NEW.updated_at := now();
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS fleet_vehicles_touch_updated_trg ON public.fleet_vehicles;
CREATE TRIGGER fleet_vehicles_touch_updated_trg
  BEFORE UPDATE ON public.fleet_vehicles
  FOR EACH ROW
  EXECUTE PROCEDURE public.fleet_vehicles_touch_updated();

-- ---------------------------------------------------------------------------
-- RPCs
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.list_fleet_vehicles(
  p_status public.fleet_vehicle_status DEFAULT NULL
)
RETURNS SETOF public.fleet_vehicles
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  PERFORM public._require_fleet_staff();

  RETURN QUERY
  SELECT fv.*
  FROM public.fleet_vehicles fv
  WHERE p_status IS NULL OR fv.status = p_status
  ORDER BY fv.plate;
END;
$$;

CREATE OR REPLACE FUNCTION public.upsert_fleet_vehicle(
  p_plate TEXT,
  p_label TEXT DEFAULT NULL,
  p_status public.fleet_vehicle_status DEFAULT 'active',
  p_assigned_driver_user_id UUID DEFAULT NULL,
  p_notes TEXT DEFAULT NULL,
  p_id UUID DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_plate TEXT;
  v_id UUID;
BEGIN
  PERFORM public._fleet_begin_rpc();
  PERFORM public._require_fleet_staff();

  v_plate := public._normalize_fleet_plate(p_plate);
  IF v_plate IS NULL OR length(v_plate) = 0 THEN
    RAISE EXCEPTION 'plate is required';
  END IF;

  PERFORM public._assert_fleet_driver_assignee(p_assigned_driver_user_id);

  IF p_id IS NULL THEN
    IF EXISTS (SELECT 1 FROM public.fleet_vehicles WHERE plate = v_plate) THEN
      RAISE EXCEPTION 'fleet plate already exists: %', v_plate;
    END IF;

    INSERT INTO public.fleet_vehicles (
      plate, label, status, assigned_driver_user_id, notes, created_by
    )
    VALUES (
      v_plate,
      NULLIF(trim(p_label), ''),
      COALESCE(p_status, 'active'::public.fleet_vehicle_status),
      p_assigned_driver_user_id,
      NULLIF(trim(p_notes), ''),
      auth.uid()
    )
    RETURNING id INTO v_id;
  ELSE
    IF NOT EXISTS (SELECT 1 FROM public.fleet_vehicles WHERE id = p_id) THEN
      RAISE EXCEPTION 'fleet vehicle not found';
    END IF;
    IF EXISTS (
      SELECT 1 FROM public.fleet_vehicles
      WHERE plate = v_plate AND id IS DISTINCT FROM p_id
    ) THEN
      RAISE EXCEPTION 'fleet plate already exists: %', v_plate;
    END IF;

    UPDATE public.fleet_vehicles
    SET
      plate = v_plate,
      label = NULLIF(trim(p_label), ''),
      status = COALESCE(p_status, status),
      assigned_driver_user_id = p_assigned_driver_user_id,
      notes = NULLIF(trim(p_notes), '')
    WHERE id = p_id
    RETURNING id INTO v_id;
  END IF;

  RETURN v_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.set_fleet_vehicle_status(
  p_id UUID,
  p_status public.fleet_vehicle_status
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_id UUID;
BEGIN
  PERFORM public._fleet_begin_rpc();
  PERFORM public._require_fleet_staff();

  IF p_status IS NULL THEN
    RAISE EXCEPTION 'status is required';
  END IF;

  UPDATE public.fleet_vehicles
  SET status = p_status
  WHERE id = p_id
  RETURNING id INTO v_id;

  IF v_id IS NULL THEN
    RAISE EXCEPTION 'fleet vehicle not found';
  END IF;

  RETURN v_id;
END;
$$;

-- ---------------------------------------------------------------------------
-- RLS (SELECT only — writes via SECURITY DEFINER RPCs + mutation guard)
-- ---------------------------------------------------------------------------
ALTER TABLE public.fleet_vehicles ENABLE ROW LEVEL SECURITY;

CREATE POLICY fleet_vehicles_staff_select ON public.fleet_vehicles
  FOR SELECT TO authenticated
  USING (
    public.has_staff_role(
      ARRAY['admin', 'warehouse', 'dispatcher']::public.staff_role[]
    )
  );

-- No INSERT/UPDATE/DELETE policies for authenticated (RPC-only).
-- No customer policies. No Realtime publication.

-- ---------------------------------------------------------------------------
-- Grants
-- ---------------------------------------------------------------------------
GRANT SELECT ON TABLE public.fleet_vehicles TO authenticated, service_role;

REVOKE ALL ON FUNCTION public._fleet_rpc_active() FROM PUBLIC;
REVOKE ALL ON FUNCTION public._fleet_begin_rpc() FROM PUBLIC;
REVOKE ALL ON FUNCTION public._require_fleet_staff() FROM PUBLIC;
REVOKE ALL ON FUNCTION public._normalize_fleet_plate(TEXT) FROM PUBLIC;
REVOKE ALL ON FUNCTION public._assert_fleet_driver_assignee(UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.guard_fleet_vehicle_mutation() FROM PUBLIC;
REVOKE ALL ON FUNCTION public.fleet_vehicles_touch_updated() FROM PUBLIC;
REVOKE ALL ON FUNCTION public.list_fleet_vehicles(public.fleet_vehicle_status) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.upsert_fleet_vehicle(
  TEXT, TEXT, public.fleet_vehicle_status, UUID, TEXT, UUID
) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.set_fleet_vehicle_status(
  UUID, public.fleet_vehicle_status
) FROM PUBLIC;

GRANT EXECUTE ON FUNCTION public.list_fleet_vehicles(public.fleet_vehicle_status)
  TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.upsert_fleet_vehicle(
  TEXT, TEXT, public.fleet_vehicle_status, UUID, TEXT, UUID
) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.set_fleet_vehicle_status(
  UUID, public.fleet_vehicle_status
) TO authenticated, service_role;
