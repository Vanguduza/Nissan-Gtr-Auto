-- Batch 1 §1.3 smoke: split-bill tenders post to distinct GL accounts.
-- Run after migrations 20260803100000 + 20260803110000 (local).

BEGIN;
SELECT plan(4);

-- Helpers exist
SELECT has_function('public', 'checkout_pos_cart_with_tenders', ARRAY['uuid', 'jsonb', 'text', 'text', 'text']);
SELECT has_function('public', 'settle_invoice_tenders', ARRAY['uuid', 'jsonb']);
SELECT has_function('public', 'gl_account_for_payment_tender', ARRAY['public.payment_tender']);

SELECT is(
  public.gl_account_for_payment_tender('ecocash'::public.payment_tender),
  '1160',
  'EcoCash tender maps to EcoCash GL 1160'
);

SELECT * FROM finish();
ROLLBACK;
