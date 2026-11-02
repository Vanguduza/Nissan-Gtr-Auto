-- Extend finance_requisition_type before seeds/RPCs that reference new labels.
-- Must be its own migration: PG forbids using a new enum value in the same transaction as ADD VALUE.

DO $$
BEGIN
  ALTER TYPE public.finance_requisition_type ADD VALUE IF NOT EXISTS 'salary';
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;
DO $$
BEGIN
  ALTER TYPE public.finance_requisition_type ADD VALUE IF NOT EXISTS 'refund';
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;
DO $$
BEGIN
  ALTER TYPE public.finance_requisition_type ADD VALUE IF NOT EXISTS 'asset_capex';
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;
DO $$
BEGIN
  ALTER TYPE public.finance_requisition_type ADD VALUE IF NOT EXISTS 'vendor';
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;
