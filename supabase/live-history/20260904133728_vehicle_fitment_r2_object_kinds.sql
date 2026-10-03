-- Exported from the hosted project's supabase_migrations.schema_migrations (20260904133728 vehicle_fitment_r2_object_kinds).
-- Source of record for what production ran; see supabase/live-history/README.md.

alter table public.catalog_r2_serving_objects drop constraint if exists catalog_r2_serving_objects_object_kind_check;
alter table public.catalog_r2_serving_objects add constraint catalog_r2_serving_objects_object_kind_check check (object_kind in ('manifest','vehicle_fitment','vehicle_search','section_parts','diagram_parts','diagram_image','oem_lookup','taxonomy','vehicle_index','vehicle_cascade'));
