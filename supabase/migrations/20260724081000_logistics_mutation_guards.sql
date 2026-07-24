-- Phase 10 follow-up: block direct mutation of logistics docs bypassing RPCs.
-- SECURITY DEFINER RPCs set app.logistics_rpc=1 (transaction-local) before writes.

CREATE OR REPLACE FUNCTION public.guard_pick_list_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  IF public._logistics_rpc_active() THEN
    IF TG_OP = 'DELETE' THEN
      RETURN OLD;
    END IF;
    RETURN NEW;
  END IF;

  IF TG_OP = 'INSERT' THEN
    RAISE EXCEPTION 'pick_lists: use create_pick_list RPC';
  ELSIF TG_OP = 'DELETE' THEN
    RAISE EXCEPTION 'pick_lists: direct delete not allowed';
  ELSIF TG_OP = 'UPDATE' THEN
    IF OLD.status IN ('done', 'cancelled') AND NEW.status IS DISTINCT FROM OLD.status THEN
      RAISE EXCEPTION 'pick_lists: terminal pick lists are immutable';
    END IF;
    RAISE EXCEPTION 'pick_lists: use pick list RPCs for updates';
  END IF;

  RETURN NULL;
END;
$$;

CREATE OR REPLACE FUNCTION public.guard_pick_list_line_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
  v_status public.pick_list_status;
BEGIN
  IF public._logistics_rpc_active() THEN
    IF TG_OP = 'DELETE' THEN
      RETURN OLD;
    END IF;
    RETURN NEW;
  END IF;

  SELECT pl.status INTO v_status
  FROM public.pick_lists pl
  WHERE pl.id = COALESCE(NEW.pick_list_id, OLD.pick_list_id);

  IF v_status IS DISTINCT FROM 'draft' THEN
    RAISE EXCEPTION 'pick_list_lines: parent must be draft (status=%)', v_status;
  END IF;

  RAISE EXCEPTION 'pick_list_lines: use pick list RPCs';
END;
$$;

CREATE OR REPLACE FUNCTION public.guard_delivery_note_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  IF public._logistics_rpc_active() THEN
    IF TG_OP = 'DELETE' THEN
      RETURN OLD;
    END IF;
    RETURN NEW;
  END IF;

  IF TG_OP = 'INSERT' THEN
    RAISE EXCEPTION 'delivery_notes: use create_delivery_note RPC';
  ELSIF TG_OP = 'DELETE' THEN
    RAISE EXCEPTION 'delivery_notes: direct delete not allowed';
  ELSIF TG_OP = 'UPDATE' THEN
    IF OLD.status IN ('submitted', 'cancelled') THEN
      RAISE EXCEPTION 'delivery_notes: submitted/cancelled DNs are immutable (use cancel_delivery_note)';
    END IF;
    RAISE EXCEPTION 'delivery_notes: use delivery note RPCs for updates';
  END IF;

  RETURN NULL;
END;
$$;

CREATE OR REPLACE FUNCTION public.guard_delivery_note_line_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
  v_status public.delivery_note_status;
BEGIN
  IF public._logistics_rpc_active() THEN
    IF TG_OP = 'DELETE' THEN
      RETURN OLD;
    END IF;
    RETURN NEW;
  END IF;

  SELECT dn.status INTO v_status
  FROM public.delivery_notes dn
  WHERE dn.id = COALESCE(NEW.delivery_note_id, OLD.delivery_note_id);

  IF v_status IS DISTINCT FROM 'draft' THEN
    RAISE EXCEPTION 'delivery_note_lines: parent must be draft (status=%)', v_status;
  END IF;

  RAISE EXCEPTION 'delivery_note_lines: use delivery note RPCs';
END;
$$;

CREATE OR REPLACE FUNCTION public.guard_delivery_job_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  IF public._logistics_rpc_active() THEN
    IF TG_OP = 'DELETE' THEN
      RETURN OLD;
    END IF;
    RETURN NEW;
  END IF;

  IF TG_OP = 'INSERT' THEN
    RAISE EXCEPTION 'delivery_jobs: use create_delivery_job RPC';
  ELSIF TG_OP = 'DELETE' THEN
    RAISE EXCEPTION 'delivery_jobs: direct delete not allowed';
  ELSIF TG_OP = 'UPDATE' THEN
    IF OLD.status IN ('completed', 'failed') THEN
      RAISE EXCEPTION 'delivery_jobs: terminal jobs are immutable';
    END IF;
    RAISE EXCEPTION 'delivery_jobs: use update_delivery_job_status RPC';
  END IF;

  RETURN NULL;
END;
$$;

CREATE OR REPLACE FUNCTION public.guard_delivery_location_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  IF public._logistics_rpc_active() THEN
    IF TG_OP = 'DELETE' THEN
      RETURN OLD;
    END IF;
    RETURN NEW;
  END IF;

  IF TG_OP = 'INSERT' THEN
    RAISE EXCEPTION 'delivery_locations: use ingest_delivery_location RPC (bridge only)';
  ELSIF TG_OP = 'UPDATE' THEN
    RAISE EXCEPTION 'delivery_locations: trail points are immutable';
  ELSIF TG_OP = 'DELETE' THEN
    RAISE EXCEPTION 'delivery_locations: use purge_delivery_locations RPC';
  END IF;

  RETURN NULL;
END;
$$;

DROP TRIGGER IF EXISTS pick_lists_mutation_guard ON public.pick_lists;
CREATE TRIGGER pick_lists_mutation_guard
  BEFORE INSERT OR UPDATE OR DELETE ON public.pick_lists
  FOR EACH ROW
  EXECUTE PROCEDURE public.guard_pick_list_mutation();

DROP TRIGGER IF EXISTS pick_list_lines_mutation_guard ON public.pick_list_lines;
CREATE TRIGGER pick_list_lines_mutation_guard
  BEFORE INSERT OR UPDATE OR DELETE ON public.pick_list_lines
  FOR EACH ROW
  EXECUTE PROCEDURE public.guard_pick_list_line_mutation();

DROP TRIGGER IF EXISTS delivery_notes_mutation_guard ON public.delivery_notes;
CREATE TRIGGER delivery_notes_mutation_guard
  BEFORE INSERT OR UPDATE OR DELETE ON public.delivery_notes
  FOR EACH ROW
  EXECUTE PROCEDURE public.guard_delivery_note_mutation();

DROP TRIGGER IF EXISTS delivery_note_lines_mutation_guard ON public.delivery_note_lines;
CREATE TRIGGER delivery_note_lines_mutation_guard
  BEFORE INSERT OR UPDATE OR DELETE ON public.delivery_note_lines
  FOR EACH ROW
  EXECUTE PROCEDURE public.guard_delivery_note_line_mutation();

DROP TRIGGER IF EXISTS delivery_jobs_mutation_guard ON public.delivery_jobs;
CREATE TRIGGER delivery_jobs_mutation_guard
  BEFORE INSERT OR UPDATE OR DELETE ON public.delivery_jobs
  FOR EACH ROW
  EXECUTE PROCEDURE public.guard_delivery_job_mutation();

DROP TRIGGER IF EXISTS delivery_locations_mutation_guard ON public.delivery_locations;
CREATE TRIGGER delivery_locations_mutation_guard
  BEFORE INSERT OR UPDATE OR DELETE ON public.delivery_locations
  FOR EACH ROW
  EXECUTE PROCEDURE public.guard_delivery_location_mutation();

-- Direct table writes via RLS still blocked for clients; drop broad write policies
-- so only SECURITY DEFINER RPCs (with GUC) mutate submitted-path tables.
DROP POLICY IF EXISTS pick_lists_staff_write ON public.pick_lists;
DROP POLICY IF EXISTS pick_list_lines_staff_write ON public.pick_list_lines;
DROP POLICY IF EXISTS delivery_notes_staff_write ON public.delivery_notes;
DROP POLICY IF EXISTS delivery_note_lines_staff_write ON public.delivery_note_lines;
DROP POLICY IF EXISTS delivery_jobs_staff_write ON public.delivery_jobs;
DROP POLICY IF EXISTS delivery_locations_staff_insert ON public.delivery_locations;

REVOKE ALL ON FUNCTION public.guard_pick_list_mutation() FROM PUBLIC;
REVOKE ALL ON FUNCTION public.guard_pick_list_line_mutation() FROM PUBLIC;
REVOKE ALL ON FUNCTION public.guard_delivery_note_mutation() FROM PUBLIC;
REVOKE ALL ON FUNCTION public.guard_delivery_note_line_mutation() FROM PUBLIC;
REVOKE ALL ON FUNCTION public.guard_delivery_job_mutation() FROM PUBLIC;
REVOKE ALL ON FUNCTION public.guard_delivery_location_mutation() FROM PUBLIC;
