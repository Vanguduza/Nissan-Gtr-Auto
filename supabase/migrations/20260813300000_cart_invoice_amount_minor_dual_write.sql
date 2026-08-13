-- H4 / B-MONEY-1 slice 3: dual-write unit_price_minor / line_total_minor on
-- pos_cart_lines + sales_invoice_lines (mirror PO-line pattern from 20260812030000).
-- Nullable columns; triggers keep minors in sync; no RLS change (existing table policies).
-- AI never invents payable amounts — values derive from major NUMERIC already written.

ALTER TABLE public.pos_cart_lines
  ADD COLUMN IF NOT EXISTS unit_price_minor BIGINT,
  ADD COLUMN IF NOT EXISTS line_total_minor BIGINT;

ALTER TABLE public.sales_invoice_lines
  ADD COLUMN IF NOT EXISTS unit_price_minor BIGINT,
  ADD COLUMN IF NOT EXISTS line_total_minor BIGINT;

COMMENT ON COLUMN public.pos_cart_lines.unit_price_minor IS
  'Dual-write minor units; prefer with unit_price until cutover (H4).';
COMMENT ON COLUMN public.pos_cart_lines.line_total_minor IS
  'Dual-write minor units; prefer with line_total until cutover (H4).';
COMMENT ON COLUMN public.sales_invoice_lines.unit_price_minor IS
  'Dual-write minor units; prefer with unit_price until cutover (H4).';
COMMENT ON COLUMN public.sales_invoice_lines.line_total_minor IS
  'Dual-write minor units; prefer with line_total until cutover (H4).';

-- Reuse public._major_to_minor(NUMERIC) from 20260812030000 when present.
CREATE OR REPLACE FUNCTION public._major_to_minor(p_amount NUMERIC)
RETURNS BIGINT
LANGUAGE sql
IMMUTABLE
AS $$
  SELECT CASE
    WHEN p_amount IS NULL THEN NULL
    ELSE ROUND(p_amount * 100)::BIGINT
  END;
$$;

CREATE OR REPLACE FUNCTION public._cart_line_dual_write_minor()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  IF NEW.unit_price IS NOT NULL THEN
    NEW.unit_price_minor := public._major_to_minor(NEW.unit_price);
  END IF;
  IF NEW.line_total IS NOT NULL THEN
    NEW.line_total_minor := public._major_to_minor(NEW.line_total);
  END IF;
  RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION public._invoice_line_dual_write_minor()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  IF NEW.unit_price IS NOT NULL THEN
    NEW.unit_price_minor := public._major_to_minor(NEW.unit_price);
  END IF;
  IF NEW.line_total IS NOT NULL THEN
    NEW.line_total_minor := public._major_to_minor(NEW.line_total);
  END IF;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS pos_cart_lines_dual_write_minor ON public.pos_cart_lines;
CREATE TRIGGER pos_cart_lines_dual_write_minor
  BEFORE INSERT OR UPDATE OF unit_price, line_total ON public.pos_cart_lines
  FOR EACH ROW
  EXECUTE FUNCTION public._cart_line_dual_write_minor();

DROP TRIGGER IF EXISTS sales_invoice_lines_dual_write_minor ON public.sales_invoice_lines;
CREATE TRIGGER sales_invoice_lines_dual_write_minor
  BEFORE INSERT OR UPDATE OF unit_price, line_total ON public.sales_invoice_lines
  FOR EACH ROW
  EXECUTE FUNCTION public._invoice_line_dual_write_minor();

-- Backfill existing rows (null minors only)
UPDATE public.pos_cart_lines
SET
  unit_price_minor = public._major_to_minor(unit_price),
  line_total_minor = public._major_to_minor(line_total)
WHERE unit_price_minor IS NULL OR line_total_minor IS NULL;

UPDATE public.sales_invoice_lines
SET
  unit_price_minor = public._major_to_minor(unit_price),
  line_total_minor = public._major_to_minor(line_total)
WHERE unit_price_minor IS NULL OR line_total_minor IS NULL;
