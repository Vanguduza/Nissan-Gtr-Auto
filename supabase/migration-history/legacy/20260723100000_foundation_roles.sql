-- Phase 1 foundation: enums, profiles, staff roles
-- Exclusions: no ZIMRA, no payroll-tax fields

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TYPE public.currency_code AS ENUM ('USD', 'ZIG');
CREATE TYPE public.account_type AS ENUM ('asset', 'liability', 'equity', 'income', 'expense');
CREATE TYPE public.valuation_method AS ENUM ('FIFO', 'AVG');
CREATE TYPE public.staff_role AS ENUM (
  'admin',
  'finance',
  'warehouse',
  'sales',
  'dispatcher',
  'hr'
);

-- App profile linked to auth.users
CREATE TABLE public.profiles (
  id UUID PRIMARY KEY REFERENCES auth.users (id) ON DELETE CASCADE,
  full_name TEXT,
  is_staff BOOLEAN NOT NULL DEFAULT false,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE public.staff_roles (
  user_id UUID NOT NULL REFERENCES public.profiles (id) ON DELETE CASCADE,
  role public.staff_role NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, role)
);

CREATE OR REPLACE FUNCTION public.is_staff()
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT COALESCE(
    (SELECT is_staff FROM public.profiles WHERE id = auth.uid()),
    false
  );
$$;

CREATE OR REPLACE FUNCTION public.has_staff_role(roles public.staff_role[])
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT EXISTS (
    SELECT 1
    FROM public.staff_roles sr
    WHERE sr.user_id = auth.uid()
      AND sr.role = ANY (roles)
  );
$$;

ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.staff_roles ENABLE ROW LEVEL SECURITY;

CREATE POLICY profiles_select_own_or_admin
  ON public.profiles FOR SELECT TO authenticated
  USING (id = auth.uid() OR public.has_staff_role(ARRAY['admin']::public.staff_role[]));

CREATE POLICY profiles_update_own
  ON public.profiles FOR UPDATE TO authenticated
  USING (id = auth.uid())
  WITH CHECK (id = auth.uid());

CREATE POLICY profiles_insert_own
  ON public.profiles FOR INSERT TO authenticated
  WITH CHECK (id = auth.uid());

CREATE POLICY staff_roles_select_own_or_admin
  ON public.staff_roles FOR SELECT TO authenticated
  USING (user_id = auth.uid() OR public.has_staff_role(ARRAY['admin']::public.staff_role[]));

CREATE POLICY staff_roles_admin_write
  ON public.staff_roles FOR ALL TO authenticated
  USING (public.has_staff_role(ARRAY['admin']::public.staff_role[]))
  WITH CHECK (public.has_staff_role(ARRAY['admin']::public.staff_role[]));

