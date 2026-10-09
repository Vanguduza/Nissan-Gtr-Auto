-- Exported from the hosted project's supabase_migrations.schema_migrations (20260905131747 capture_sales_fulfillment_cost_basis).
-- Source of record for what production ran; see supabase/live-history/README.md.

ALTER TABLE public.sales_invoice_lines
  ADD COLUMN IF NOT EXISTS unit_cost_basis numeric,
  ADD COLUMN IF NOT EXISTS cost_total_basis numeric NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS return_against_line_id uuid REFERENCES public.sales_invoice_lines(id);

DO $$ BEGIN
  ALTER TABLE public.sales_invoice_lines
    ADD CONSTRAINT sales_invoice_lines_cost_basis_nonnegative
    CHECK (cost_total_basis >= 0 AND (unit_cost_basis IS NULL OR unit_cost_basis >= 0));
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

CREATE INDEX IF NOT EXISTS sales_invoice_lines_return_against_idx
  ON public.sales_invoice_lines(return_against_line_id)
  WHERE return_against_line_id IS NOT NULL;

CREATE OR REPLACE FUNCTION public.capture_sales_invoice_line_cost_basis()
RETURNS trigger
LANGUAGE plpgsql
SET search_path TO ''
AS $$
DECLARE
  v_doc_type public.sales_doc_type;
  v_wh uuid;
  v_cost numeric;
BEGIN
  IF NEW.is_core_charge OR NOT COALESCE(NEW.issues_stock,true) OR COALESCE(NEW.qty_fulfilled,0)<=0 THEN
    RETURN NEW;
  END IF;
  IF NEW.unit_cost_basis IS NOT NULL AND NEW.cost_total_basis>0 THEN
    RETURN NEW;
  END IF;

  SELECT si.doc_type,si.warehouse_id INTO v_doc_type,v_wh
  FROM public.sales_invoices si WHERE si.id=NEW.invoice_id;
  IF v_doc_type IS DISTINCT FROM 'invoice'::public.sales_doc_type THEN
    RETURN NEW;
  END IF;

  SELECT sl.unit_cost INTO v_cost
  FROM public.stock_levels sl
  WHERE sl.stock_item_id=NEW.stock_item_id AND sl.warehouse_id=v_wh;

  NEW.unit_cost_basis:=COALESCE(v_cost,0);
  NEW.cost_total_basis:=round(COALESCE(v_cost,0)*COALESCE(NEW.qty_fulfilled,0),4);
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS sales_invoice_lines_capture_cost_basis ON public.sales_invoice_lines;
CREATE TRIGGER sales_invoice_lines_capture_cost_basis
BEFORE INSERT ON public.sales_invoice_lines
FOR EACH ROW EXECUTE FUNCTION public.capture_sales_invoice_line_cost_basis();

CREATE OR REPLACE FUNCTION public.capture_delivery_line_cost_basis()
RETURNS trigger
LANGUAGE plpgsql
SET search_path TO ''
AS $$
DECLARE
  v_old_cost numeric:=COALESCE(OLD.unit_cost,0);
  v_new_cost numeric:=COALESCE(NEW.unit_cost,0);
  v_delta numeric;
  v_existing numeric;
  v_fulfilled numeric;
  v_new_total numeric;
BEGIN
  IF NEW.sales_invoice_line_id IS NULL OR COALESCE(NEW.qty_base,0)<=0 THEN
    RETURN NEW;
  END IF;
  IF v_old_cost IS NOT DISTINCT FROM v_new_cost THEN
    RETURN NEW;
  END IF;

  SELECT sil.cost_total_basis,sil.qty_fulfilled
    INTO v_existing,v_fulfilled
  FROM public.sales_invoice_lines sil
  WHERE sil.id=NEW.sales_invoice_line_id
  FOR UPDATE;
  IF NOT FOUND THEN RETURN NEW; END IF;

  v_delta:=round((v_new_cost-v_old_cost)*NEW.qty_base,4);
  v_new_total:=GREATEST(0,COALESCE(v_existing,0)+v_delta);

  UPDATE public.sales_invoice_lines
  SET cost_total_basis=v_new_total,
      unit_cost_basis=CASE
        WHEN COALESCE(v_fulfilled,0)+NEW.qty_base>0
          THEN round(v_new_total/(COALESCE(v_fulfilled,0)+NEW.qty_base),6)
        ELSE NULL
      END
  WHERE id=NEW.sales_invoice_line_id;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS delivery_note_lines_capture_cost_basis ON public.delivery_note_lines;
CREATE TRIGGER delivery_note_lines_capture_cost_basis
AFTER UPDATE OF unit_cost ON public.delivery_note_lines
FOR EACH ROW EXECUTE FUNCTION public.capture_delivery_line_cost_basis();

CREATE OR REPLACE FUNCTION public.rollback_delivery_cost_basis_on_cancel()
RETURNS trigger
LANGUAGE plpgsql
SET search_path TO ''
AS $$
DECLARE
  r record;
  v_remaining numeric;
  v_fulfilled numeric;
BEGIN
  IF OLD.status IS DISTINCT FROM 'submitted'::public.delivery_note_status
     OR NEW.status IS DISTINCT FROM 'cancelled'::public.delivery_note_status THEN
    RETURN NEW;
  END IF;

  FOR r IN
    SELECT dnl.sales_invoice_line_id,dnl.qty_base,COALESCE(dnl.unit_cost,0) AS unit_cost
    FROM public.delivery_note_lines dnl
    WHERE dnl.delivery_note_id=NEW.id AND dnl.sales_invoice_line_id IS NOT NULL
  LOOP
    SELECT GREATEST(0,COALESCE(sil.cost_total_basis,0)-round(r.unit_cost*r.qty_base,4)),
           COALESCE(sil.qty_fulfilled,0)
      INTO v_remaining,v_fulfilled
    FROM public.sales_invoice_lines sil
    WHERE sil.id=r.sales_invoice_line_id
    FOR UPDATE;

    UPDATE public.sales_invoice_lines
    SET cost_total_basis=v_remaining,
        unit_cost_basis=CASE WHEN v_fulfilled>0 THEN round(v_remaining/v_fulfilled,6) ELSE NULL END
    WHERE id=r.sales_invoice_line_id;
  END LOOP;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS delivery_notes_rollback_cost_basis ON public.delivery_notes;
CREATE TRIGGER delivery_notes_rollback_cost_basis
AFTER UPDATE OF status ON public.delivery_notes
FOR EACH ROW EXECUTE FUNCTION public.rollback_delivery_cost_basis_on_cancel();
