-- Exported from the hosted project's supabase_migrations.schema_migrations (20260908050627 catalog_seed_bootstrap_batch_order_fix).
-- Source of record for what production ran; see supabase/live-history/README.md.

CREATE OR REPLACE FUNCTION public.catalog_r2_presign_batch(p_build_token text,p_object_kind text,p_offset integer DEFAULT 0,p_limit integer DEFAULT 200,p_expires integer DEFAULT 900)
RETURNS TABLE(scope_key text,object_key text,sha256 text,row_count integer,bytes bigint,content_encoding text,url text)
LANGUAGE plpgsql SECURITY DEFINER SET search_path=public,extensions AS $$
DECLARE v_release uuid;
BEGIN
  IF encode(extensions.digest(COALESCE(p_build_token,''),'sha256'),'hex') <> 'd8338aceaf5fcf41c4bfd0eb968c14e614344ebe590f6ab4cd5183c1dc1f4a4c' THEN RAISE EXCEPTION 'invalid build token'; END IF;
  IF p_object_kind NOT IN ('vehicle_search','vehicle_fitment','section_parts','diagram_parts','diagram_image') THEN RAISE EXCEPTION 'unsupported kind'; END IF;
  SELECT id INTO v_release FROM public.catalog_releases WHERE maker_slug='nissan' AND is_current AND published_at IS NOT NULL ORDER BY published_at DESC LIMIT 1;
  IF v_release IS NULL THEN RAISE EXCEPTION 'current Nissan release missing'; END IF;
  RETURN QUERY
  SELECT o.scope_key,o.object_key,o.sha256,o.row_count,o.bytes,o.content_encoding,public.catalog_r2_presign_get(o.object_key,p_expires)
  FROM public.catalog_r2_serving_objects o
  WHERE o.release_id=v_release AND o.maker_slug='nissan' AND o.object_kind=p_object_kind
  ORDER BY o.scope_key,o.object_key
  OFFSET GREATEST(COALESCE(p_offset,0),0)
  LIMIT GREATEST(1,LEAST(COALESCE(p_limit,200),500));
END; $$;
