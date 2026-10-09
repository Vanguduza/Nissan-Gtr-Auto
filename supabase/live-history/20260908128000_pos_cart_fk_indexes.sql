-- Exported from the hosted project's supabase_migrations.schema_migrations (20260908128000 pos_cart_fk_indexes).
-- Source of record for what production ran; see supabase/live-history/README.md.

-- Close the final unindexed foreign-key paths on public.pos_carts.
CREATE INDEX IF NOT EXISTS pos_carts_created_by_idx
  ON public.pos_carts (created_by);

CREATE INDEX IF NOT EXISTS pos_carts_warehouse_id_idx
  ON public.pos_carts (warehouse_id);
