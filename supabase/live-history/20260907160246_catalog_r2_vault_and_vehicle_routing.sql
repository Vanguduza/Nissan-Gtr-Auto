-- Exported from the hosted project's supabase_migrations.schema_migrations (20260907160246 catalog_r2_vault_and_vehicle_routing).
-- Source of record for what production ran; see supabase/live-history/README.md.

create table if not exists public.catalog_r2_vehicle_master (
  r2_scope_key text primary key check (r2_scope_key ~ '^VM-[0-9a-f]{28}$'),
  maker_slug text not null default 'nissan' check (maker_slug = 'nissan'),
  model text not null,
  chassis_code text not null,
  engine_code text not null,
  year_start integer,
  year_end integer,
  sales_region text,
  source_release_version text not null default 'v2-storage-2026-09',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  check (year_start is null or year_end is null or year_end >= year_start),
  unique (r2_scope_key, source_release_version)
);

alter table public.catalog_r2_vehicle_master enable row level security;
revoke all on table public.catalog_r2_vehicle_master from public, anon, authenticated;
grant select, insert, update, delete on table public.catalog_r2_vehicle_master to service_role;

insert into public.catalog_r2_vehicle_master (r2_scope_key,model,chassis_code,engine_code,year_start,year_end,sales_region) values
('VM-133189aac171f1928d3a83ff8965','280ZX','S130','L28E',1978,2011,'U.S.A.'),
('VM-17968c936746a2c459b6c50b7e71','180SX','KRPS13','SR20DE',1996,2012,'Japan'),
('VM-1840b508044b3269c328f24b55aa','180SX','KRPS13','SR20DET',1994,2012,'Japan'),
('VM-1a627259f294137631ca52a83010','200SX','S12','CA20E',1984,2011,'U.S.A.'),
('VM-319b94b36aca37a4109556e8fec2','240SX','S13','KA24D',1991,2011,'U.S.A.'),
('VM-5199df72dfbd8db9b6b34e858c49','240SX','S14','KA24DE',1994,2011,'U.S.A.'),
('VM-68eb9532421c2aa35608d2162a5d','180SX','RPS13','SR20DE',1996,2012,'Japan'),
('VM-92ad99a84a8b085643bf4b28e170','180SX','RS13','SR20DE',1996,2012,'Japan'),
('VM-956eff92552105b0318ac00505bf','240SX','S13','KA24E',1988,2011,'U.S.A.'),
('VM-b14e305c4dd32a01e2fd3b669313','200SX','S12','CA18ET',1983,1986,'U.S.A.'),
('VM-b5253d4082d395ed8494b6c14cfb','280ZX','S130','L28ET',1978,2011,'U.S.A.'),
('VM-c574c805d493f167184f73bd5d4c','300ZX','Z31','VG30',1983,2011,'U.S.A.'),
('VM-ef9501a8837beb1f3a6364796c71','200SX','S110','Z22E',1981,2011,'U.S.A.'),
('VM-f7c339c794c47bfffc0ea751cebf','180SX','RPS13','SR20DET',1994,2012,'Japan'),
('VM-f88b3028a9ad872a49f24506011d','180SX','RS13','CA18DT',1989,1991,'Japan'),
('VM-fbc25fe4219f0f38346f5e42df1b','200SX','S12','VG30E',1986,2011,'U.S.A.')
on conflict (r2_scope_key) do update set
  model=excluded.model,
  chassis_code=excluded.chassis_code,
  engine_code=excluded.engine_code,
  year_start=excluded.year_start,
  year_end=excluded.year_end,
  sales_region=excluded.sales_region,
  source_release_version=excluded.source_release_version,
  updated_at=now();

create or replace function public.catalog_r2_vehicle_master_list()
returns jsonb
language sql
stable
security definer
set search_path = public
as $$
  select coalesce(jsonb_agg(jsonb_build_object(
    'id', r2_scope_key,
    'r2_scope_key', r2_scope_key,
    'maker_slug', maker_slug,
    'model', model,
    'chassis_code', chassis_code,
    'engine_code', engine_code,
    'year_start', year_start,
    'year_end', year_end,
    'sales_region', sales_region,
    'display_name', trim(concat_ws(' · ', 'Nissan ' || model, chassis_code, engine_code, case when year_start is not null or year_end is not null then concat(coalesce(year_start::text,''),'–',coalesce(year_end::text,'')) else null end)),
    'source_release_version', source_release_version
  ) order by model, year_start, chassis_code, engine_code, r2_scope_key), '[]'::jsonb)
  from public.catalog_r2_vehicle_master;
$$;
revoke all on function public.catalog_r2_vehicle_master_list() from public, anon, authenticated;
grant execute on function public.catalog_r2_vehicle_master_list() to service_role;

create or replace function public.store_catalog_r2_credentials_once(
  p_account_id text,
  p_access_key_id text,
  p_secret_access_key text,
  p_bucket text,
  p_endpoint text default null
)
returns void
language plpgsql
security definer
set search_path = public, vault
as $$
declare
  v_name text;
  v_value text;
  v_existing uuid;
begin
  if auth.role() <> 'service_role' then raise exception 'service_role required'; end if;
  if nullif(trim(p_account_id),'') is null or nullif(trim(p_access_key_id),'') is null or nullif(trim(p_secret_access_key),'') is null or nullif(trim(p_bucket),'') is null then
    raise exception 'complete R2 credential set required';
  end if;
  for v_name, v_value in
    select * from (values
      ('catalog_r2_account_id', trim(p_account_id)),
      ('catalog_r2_access_key_id', trim(p_access_key_id)),
      ('catalog_r2_secret_access_key', trim(p_secret_access_key)),
      ('catalog_r2_bucket', trim(p_bucket)),
      ('catalog_r2_endpoint', nullif(trim(coalesce(p_endpoint,'')),''))
    ) x(name,value)
  loop
    if v_value is null then continue; end if;
    select id into v_existing from vault.secrets where name=v_name limit 1;
    if v_existing is null then
      perform vault.create_secret(v_value, v_name, 'Nissan GTR Auto live R2 runtime credential migrated server-to-server', null);
    else
      perform vault.update_secret(v_existing, v_value, v_name, 'Nissan GTR Auto live R2 runtime credential migrated server-to-server', null);
    end if;
  end loop;
end;
$$;
revoke all on function public.store_catalog_r2_credentials_once(text,text,text,text,text) from public, anon, authenticated;
grant execute on function public.store_catalog_r2_credentials_once(text,text,text,text,text) to service_role;

create or replace function public.catalog_r2_runtime_config()
returns jsonb
language plpgsql
stable
security definer
set search_path = public, vault
as $$
declare v jsonb;
begin
  if auth.role() <> 'service_role' then raise exception 'service_role required'; end if;
  select jsonb_build_object(
    'account_id', max(decrypted_secret) filter (where name='catalog_r2_account_id'),
    'access_key_id', max(decrypted_secret) filter (where name='catalog_r2_access_key_id'),
    'secret_access_key', max(decrypted_secret) filter (where name='catalog_r2_secret_access_key'),
    'bucket', max(decrypted_secret) filter (where name='catalog_r2_bucket'),
    'endpoint', max(decrypted_secret) filter (where name='catalog_r2_endpoint')
  ) into v from vault.decrypted_secrets
  where name in ('catalog_r2_account_id','catalog_r2_access_key_id','catalog_r2_secret_access_key','catalog_r2_bucket','catalog_r2_endpoint');
  if coalesce(v->>'account_id','')='' or coalesce(v->>'access_key_id','')='' or coalesce(v->>'secret_access_key','')='' or coalesce(v->>'bucket','')='' then
    raise exception 'R2 runtime credentials are not configured';
  end if;
  return v;
end;
$$;
revoke all on function public.catalog_r2_runtime_config() from public, anon, authenticated;
grant execute on function public.catalog_r2_runtime_config() to service_role;
