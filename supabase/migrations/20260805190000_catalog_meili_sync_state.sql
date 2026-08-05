-- Meilisearch catalog sync state (derived index metadata; Supabase remains SoR)
-- See docs/decisions/2026-08-05-meilisearch-catalog-search.md

CREATE TABLE public.catalog_meili_sync_state (
  id SMALLINT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
  last_full_sync_at TIMESTAMPTZ,
  document_count INTEGER NOT NULL DEFAULT 0,
  index_uid TEXT NOT NULL DEFAULT 'parts',
  meili_task_uid BIGINT,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by UUID REFERENCES auth.users (id)
);

INSERT INTO public.catalog_meili_sync_state (id)
VALUES (1)
ON CONFLICT (id) DO NOTHING;

ALTER TABLE public.catalog_meili_sync_state ENABLE ROW LEVEL SECURITY;

CREATE POLICY catalog_meili_sync_state_staff_select
  ON public.catalog_meili_sync_state FOR SELECT
  TO authenticated
  USING (
    public.has_staff_role(ARRAY['admin', 'warehouse', 'sales']::public.staff_role[])
  );

CREATE POLICY catalog_meili_sync_state_service_role_all
  ON public.catalog_meili_sync_state FOR ALL
  TO service_role
  USING (true)
  WITH CHECK (true);

COMMENT ON TABLE public.catalog_meili_sync_state IS
  'Singleton row tracking last Meilisearch full sync from catalog tables.';
