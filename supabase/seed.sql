-- LOCAL DEVELOPMENT ONLY. Never execute this seed against a hosted/production Supabase project.
-- Dev-only seed (local `supabase db reset`). Never use these passwords in production.
-- Requires extensions used by Auth (pgcrypto already in Phase 1 foundation).

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Fixed UUIDs for local smoke tests / docs
-- admin:       a0000000-0000-4000-8000-000000000001
-- finance:     a0000000-0000-4000-8000-000000000002
-- warehouse:   a0000000-0000-4000-8000-000000000003
-- storefront-a:c0000000-0000-4000-8000-0000000000a1  (customer row c100…a1)
-- storefront-b:c0000000-0000-4000-8000-0000000000b2  (customer row c100…b2)

DO $$
DECLARE
  v_instance_id UUID := '00000000-0000-0000-0000-000000000000';
  r RECORD;
BEGIN
  FOR r IN
    SELECT *
    FROM (
      VALUES
        (
          'a0000000-0000-4000-8000-000000000001'::uuid,
          'admin@gtr.local',
          'Local Admin',
          'local-dev-admin'
        ),
        (
          'a0000000-0000-4000-8000-000000000002'::uuid,
          'finance@gtr.local',
          'Local Finance',
          'local-dev-finance'
        ),
        (
          'a0000000-0000-4000-8000-000000000003'::uuid,
          'warehouse@gtr.local',
          'Local Warehouse',
          'local-dev-warehouse'
        ),
        (
          'c0000000-0000-4000-8000-0000000000a1'::uuid,
          'storefront-a@gtr.local',
          'Storefront A',
          'local-dev-customer'
        ),
        (
          'c0000000-0000-4000-8000-0000000000b2'::uuid,
          'storefront-b@gtr.local',
          'Storefront B',
          'local-dev-customer'
        )
    ) AS t(id, email, full_name, plain_password)
  LOOP
    INSERT INTO auth.users (
      instance_id,
      id,
      aud,
      role,
      email,
      encrypted_password,
      email_confirmed_at,
      raw_app_meta_data,
      raw_user_meta_data,
      created_at,
      updated_at,
      confirmation_token,
      recovery_token,
      email_change_token_new,
      email_change
    )
    VALUES (
      v_instance_id,
      r.id,
      'authenticated',
      'authenticated',
      r.email,
      crypt(r.plain_password, gen_salt('bf')),
      now(),
      '{"provider":"email","providers":["email"]}'::jsonb,
      jsonb_build_object('full_name', r.full_name),
      now(),
      now(),
      '',
      '',
      '',
      ''
    )
    ON CONFLICT (id) DO NOTHING;

    INSERT INTO auth.identities (
      id,
      user_id,
      identity_data,
      provider,
      provider_id,
      last_sign_in_at,
      created_at,
      updated_at
    )
    VALUES (
      r.id,
      r.id,
      jsonb_build_object(
        'sub', r.id::text,
        'email', r.email,
        'email_verified', true
      ),
      'email',
      r.id::text,
      now(),
      now(),
      now()
    )
    ON CONFLICT (provider, provider_id) DO NOTHING;
  END LOOP;
END;
$$;

-- Profiles are created by handle_new_user; ensure rows exist if trigger already ran
INSERT INTO public.profiles (id, full_name, is_staff)
VALUES
  ('a0000000-0000-4000-8000-000000000001', 'Local Admin', false),
  ('a0000000-0000-4000-8000-000000000002', 'Local Finance', false),
  ('a0000000-0000-4000-8000-000000000003', 'Local Warehouse', false),
  ('c0000000-0000-4000-8000-0000000000a1', 'Storefront A', false),
  ('c0000000-0000-4000-8000-0000000000b2', 'Storefront B', false)
ON CONFLICT (id) DO UPDATE
SET full_name = EXCLUDED.full_name;

-- Role grants (sync trigger sets is_staff) — staff only; storefront users stay non-staff
INSERT INTO public.staff_roles (user_id, role) VALUES
  ('a0000000-0000-4000-8000-000000000001', 'admin'),
  ('a0000000-0000-4000-8000-000000000002', 'finance'),
  ('a0000000-0000-4000-8000-000000000003', 'warehouse')
ON CONFLICT DO NOTHING;

-- Storefront customers linked to auth profiles (same UUIDs as customer_storefront_authz_smoke)
INSERT INTO public.customers (id, display_name, email, currency, profile_id)
VALUES
  (
    'c1000000-0000-4000-8000-0000000000a1',
    'Storefront Customer A',
    'storefront-a@gtr.local',
    'USD',
    'c0000000-0000-4000-8000-0000000000a1'
  ),
  (
    'c1000000-0000-4000-8000-0000000000b2',
    'Storefront Customer B',
    'storefront-b@gtr.local',
    'USD',
    'c0000000-0000-4000-8000-0000000000b2'
  )
ON CONFLICT (id) DO UPDATE
SET display_name = EXCLUDED.display_name,
    email = EXCLUDED.email,
    profile_id = EXCLUDED.profile_id;

-- After reset: upload Navara diagram PNG bytes into Storage (metadata + diagram_path
-- come from migration 20260725180000). Prefer docker (no secrets):
--   node supabase/seed_catalog_diagrams.mjs --docker
-- Or Storage API with SERVICE_ROLE from `supabase status` (do not commit the key).
-- Fixtures: data-pipeline/fixtures/navara_d40_yd25/diagrams/navara-d40/*.png
