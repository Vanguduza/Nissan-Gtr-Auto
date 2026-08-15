-- Petty cash float requisition type (fund box from 1100).
-- Own migration: PG forbids using a new enum value in the same txn as ADD VALUE.

DO $$
BEGIN
  ALTER TYPE public.finance_requisition_type ADD VALUE IF NOT EXISTS 'petty_float';
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;
