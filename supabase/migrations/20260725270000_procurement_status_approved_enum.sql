-- Phase 1 (professional finance workflows): extend procurement_doc_status.
-- Enum ADD VALUE must commit before RPCs can reference the new labels
-- (Postgres same-transaction restriction). Companion: …271000_procurement_approve.sql
-- Exclusions: no ZIMRA / payroll tax.

DO $$
BEGIN
  ALTER TYPE public.procurement_doc_status ADD VALUE IF NOT EXISTS 'approved';
EXCEPTION
  WHEN duplicate_object THEN NULL;
END;
$$;

DO $$
BEGIN
  ALTER TYPE public.procurement_doc_status ADD VALUE IF NOT EXISTS 'rejected';
EXCEPTION
  WHEN duplicate_object THEN NULL;
END;
$$;
