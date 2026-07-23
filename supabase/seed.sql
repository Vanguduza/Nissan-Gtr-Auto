-- Dev-only seed (local `supabase db reset`). Never use these passwords in production.
-- Requires extensions used by Auth (pgcrypto already in Phase 1 foundation).

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Fixed UUIDs for local smoke tests / docs
-- admin:    a0000000-0000-4000-8000-000000000001
-- finance:  a0000000-0000-4000-8000-000000000002
-- warehouse:a0000000-0000-4000-8000-000000000003

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
  ('a0000000-0000-4000-8000-000000000003', 'Local Warehouse', false)
ON CONFLICT (id) DO UPDATE
SET full_name = EXCLUDED.full_name;

-- Role grants (sync trigger sets is_staff)
INSERT INTO public.staff_roles (user_id, role) VALUES
  ('a0000000-0000-4000-8000-000000000001', 'admin'),
  ('a0000000-0000-4000-8000-000000000002', 'finance'),
  ('a0000000-0000-4000-8000-000000000003', 'warehouse')
ON CONFLICT DO NOTHING;
