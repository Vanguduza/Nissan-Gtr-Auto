-- Nissan catalog runtime split:
-- Supabase = control/commerce plane; Cloudflare R2 = immutable EPC technical data plane.
-- Normal customer/staff browsing never requires the complete encrypted catalog bundle.

create table if not exists public.catalog_r2_serving_objects (
  id uuid primary key default gen_random_uuid(),
  release_id uuid not null references public.catalog_releases(id) on delete cascade,
  maker_slug text not null,
  object_kind text not null check (object_kind in (
    'manifest','vehicle_fitment','vehicle_search','section_parts','diagram_parts',
    'diagram_image','oem_lookup','taxonomy'
  )),
  scope_key text not null,
  object_key text not null,
  sha256 text null,
  row_count integer not null default 0,
  bytes bigint not null default 0,
  content_type text not null default 'application/json',
  content_encoding text null,
  metadata jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (release_id, object_kind, scope_key)
);

create index if not exists catalog_r2_serving_objects_lookup_idx
  on public.catalog_r2_serving_objects (maker_slug, object_kind, scope_key, release_id);

alter table public.catalog_r2_serving_objects enable row level security;
revoke all on public.catalog_r2_serving_objects from anon, authenticated;

create or replace function public.catalog_v2_ingest_r2_serving_objects_batch(p_rows jsonb)
returns bigint
language plpgsql
security definer
set search_path = public
as $$
declare
  v_role text := coalesce(current_setting('request.jwt.claim.role', true), '');
  v_count bigint;
begin
  if v_role <> 'service_role' then raise exception 'service_role required'; end if;
  if jsonb_typeof(p_rows) <> 'array' then raise exception 'p_rows must be a JSON array'; end if;

  insert into public.catalog_r2_serving_objects (
    release_id, maker_slug, object_kind, scope_key, object_key,
    sha256, row_count, bytes, content_type, content_encoding, metadata, updated_at
  )
  select
    (r->>'release_id')::uuid,
    lower(coalesce(nullif(trim(r->>'maker_slug'), ''), 'nissan')),
    trim(r->>'object_kind'),
    trim(r->>'scope_key'),
    trim(r->>'object_key'),
    nullif(lower(trim(r->>'sha256')), ''),
    coalesce((r->>'row_count')::integer, 0),
    coalesce((r->>'bytes')::bigint, 0),
    coalesce(nullif(trim(r->>'content_type'), ''), 'application/json'),
    nullif(trim(r->>'content_encoding'), ''),
    coalesce(r->'metadata', '{}'::jsonb),
    now()
  from jsonb_array_elements(p_rows) as x(r)
  where nullif(trim(r->>'release_id'), '') is not null
    and nullif(trim(r->>'object_kind'), '') is not null
    and nullif(trim(r->>'scope_key'), '') is not null
    and nullif(trim(r->>'object_key'), '') is not null
  on conflict (release_id, object_kind, scope_key) do update set
    maker_slug = excluded.maker_slug,
    object_key = excluded.object_key,
    sha256 = excluded.sha256,
    row_count = excluded.row_count,
    bytes = excluded.bytes,
    content_type = excluded.content_type,
    content_encoding = excluded.content_encoding,
    metadata = excluded.metadata,
    updated_at = now();

  get diagnostics v_count = row_count;
  return v_count;
end;
$$;
revoke all on function public.catalog_v2_ingest_r2_serving_objects_batch(jsonb) from public, anon, authenticated;
grant execute on function public.catalog_v2_ingest_r2_serving_objects_batch(jsonb) to service_role;

create or replace function public.catalog_commerce_stock_for_oems(p_oems text[])
returns table (
  normalized_oem text,
  stock_item_id uuid,
  oem_part_number text,
  description text,
  saleable_qty numeric,
  reorder_point numeric,
  unit_price numeric,
  currency text
)
language sql
security definer
set search_path = public
stable
as $$
  with wanted as (
    select distinct upper(regexp_replace(x, '[^A-Z0-9]', '', 'g')) as normalized_oem
    from unnest(coalesce(p_oems, array[]::text[])) as u(x)
    where coalesce(trim(x), '') <> ''
  ), stock as (
    select
      si.id,
      si.oem_part_number::text,
      si.description,
      si.reorder_point,
      upper(regexp_replace(si.oem_part_number::text, '[^A-Z0-9]', '', 'g')) as normalized_oem,
      coalesce(sum(case when w.is_active and not w.is_quarantine then sl.quantity else 0 end), 0) as saleable_qty
    from public.stock_items si
    left join public.stock_levels sl on sl.stock_item_id = si.id
    left join public.warehouses w on w.id = sl.warehouse_id
    join wanted x on x.normalized_oem = upper(regexp_replace(si.oem_part_number::text, '[^A-Z0-9]', '', 'g'))
    group by si.id, si.oem_part_number, si.description, si.reorder_point
  )
  select
    s.normalized_oem,
    s.id,
    s.oem_part_number,
    s.description,
    s.saleable_qty,
    s.reorder_point,
    pli.unit_price,
    pl.currency::text
  from stock s
  left join lateral (
    select pli0.unit_price, pli0.price_list_id
    from public.price_list_items pli0
    join public.price_lists pl0 on pl0.id = pli0.price_list_id
    where pli0.stock_item_id = s.id and pl0.is_active = true
    order by pl0.is_default desc, (pl0.code = 'RETAIL') desc, pl0.created_at asc
    limit 1
  ) pli on true
  left join public.price_lists pl on pl.id = pli.price_list_id;
$$;
grant execute on function public.catalog_commerce_stock_for_oems(text[]) to authenticated, service_role;

create or replace function public.customer_catalog_stock_for_vehicle_id(
  p_vehicle_id text,
  p_limit integer default 100
)
returns table (
  stock_item_id uuid,
  oem_part_number text,
  name text,
  fitment_evidence text,
  catalog_release_id text
)
language plpgsql
security definer
set search_path = public
stable
as $$
declare
  v_chassis text;
  v_engine text;
begin
  select vm.chassis_code, vm.engine_code
    into v_chassis, v_engine
  from public.list_customer_vehicle_master('nissan', 10000, 0) vm
  where vm.id = p_vehicle_id
  limit 1;
  if v_chassis is null then return; end if;
  return query
  select * from public.customer_catalog_stock_for_vehicle(
    v_chassis,
    v_engine,
    greatest(1, least(coalesce(p_limit, 100), 100))
  );
end;
$$;
grant execute on function public.customer_catalog_stock_for_vehicle_id(text, integer) to authenticated, service_role;

alter table public.catalog_delivery_config
  drop constraint if exists catalog_delivery_config_delivery_mode_check;
alter table public.catalog_delivery_config
  add constraint catalog_delivery_config_delivery_mode_check
  check (delivery_mode in ('offline_bundle','postgres_full','live_r2_index'));
update public.catalog_delivery_config
set delivery_mode='live_r2_index', default_storage_backend='r2', updated_at=now()
where id=1;
