-- Exported from the hosted project's supabase_migrations.schema_migrations (20260908045516 catalog_seed_vehicle_master_bootstrap).
-- Source of record for what production ran; see supabase/live-history/README.md.

CREATE OR REPLACE FUNCTION public.catalog_seed_vehicle_master(p_build_token text)
RETURNS SETOF public.catalog_r2_vehicle_master
LANGUAGE plpgsql SECURITY DEFINER SET search_path=public,extensions AS $$
BEGIN
  IF encode(extensions.digest(COALESCE(p_build_token,''),'sha256'),'hex') <> 'd8338aceaf5fcf41c4bfd0eb968c14e614344ebe590f6ab4cd5183c1dc1f4a4c' THEN RAISE EXCEPTION 'invalid build token'; END IF;
  RETURN QUERY SELECT * FROM public.catalog_r2_vehicle_master WHERE maker_slug='nissan' ORDER BY model,year_start,chassis_code,engine_code;
END; $$;
REVOKE ALL ON FUNCTION public.catalog_seed_vehicle_master(text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.catalog_seed_vehicle_master(text) TO anon,service_role;
