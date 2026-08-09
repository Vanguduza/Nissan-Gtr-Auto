-- Paste into Supabase Dashboard → SQL Editor (project gylrgwqyuiwkyykardwc).
-- One-shot: finish megazip scrub + column renames when REST is too slow.
-- Safe to re-run (idempotent guards).

BEGIN;

UPDATE public.catalog_diagram_parts
SET diagram_path = 'epc/' || substr(diagram_path, length('megazip/') + 1)
WHERE diagram_path ILIKE 'megazip/%';

UPDATE public.part_fitment
SET diagram_path = 'epc/' || substr(diagram_path, length('megazip/') + 1)
WHERE diagram_path ILIKE 'megazip/%';

UPDATE public.catalog_diagrams
SET
  storage_path = CASE
    WHEN storage_path ILIKE 'megazip/%'
      THEN 'epc/' || substr(storage_path, length('megazip/') + 1)
    ELSE storage_path
  END,
  source_url = CASE WHEN source_url ILIKE '%megazip%' THEN NULL ELSE source_url END,
  image_url = CASE WHEN image_url ILIKE '%megazip%' THEN NULL ELSE image_url END
WHERE storage_path ILIKE '%megazip%'
   OR source_url ILIKE '%megazip%'
   OR image_url ILIKE '%megazip%';

UPDATE public.catalog_makers
SET source = 'epc'
WHERE source ILIKE '%megazip%';

UPDATE public.catalog_models SET source_url = NULL WHERE source_url ILIKE '%megazip%';
UPDATE public.catalog_variants SET source_url = NULL WHERE source_url ILIKE '%megazip%';
UPDATE public.catalog_sections SET source_url = NULL WHERE source_url ILIKE '%megazip%';

DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 'catalog_variants'
      AND column_name = 'megazip_data_id'
  ) THEN
    ALTER TABLE public.catalog_variants
      RENAME COLUMN megazip_data_id TO external_data_id;
  END IF;
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 'catalog_diagram_parts'
      AND column_name = 'megazip_item_id'
  ) THEN
    ALTER TABLE public.catalog_diagram_parts
      RENAME COLUMN megazip_item_id TO external_item_id;
  END IF;
END $$;

COMMIT;

SELECT 'catalog_diagram_parts' AS t, count(*) AS megazip_rows
FROM public.catalog_diagram_parts WHERE diagram_path ILIKE '%megazip%'
UNION ALL
SELECT 'part_fitment', count(*) FROM public.part_fitment WHERE diagram_path ILIKE '%megazip%'
UNION ALL
SELECT 'catalog_diagrams', count(*) FROM public.catalog_diagrams
WHERE storage_path ILIKE '%megazip%' OR coalesce(source_url,'') ILIKE '%megazip%'
   OR coalesce(image_url,'') ILIKE '%megazip%';
