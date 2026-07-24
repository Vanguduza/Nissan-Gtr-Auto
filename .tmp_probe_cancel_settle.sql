BEGIN;

SELECT set_config('request.jwt.claim.role', 'service' || '_role', true);
SELECT set_config('request.jwt.claims', '{"role":"service' || '_role"}', true);

INSERT INTO public.paynow_payment_intents (external_ref, method, status, amount, currency)
VALUES ('probe-cancel-paynow-20260724', 'ecocash', 'cancelled', 1.00, 'USD')
ON CONFLICT (external_ref) DO UPDATE SET status = 'cancelled';

INSERT INTO public.contipay_payment_intents (external_ref, method, status, amount, currency)
VALUES ('probe-cancel-contipay-20260724', 'ecocash', 'cancelled', 1.00, 'USD')
ON CONFLICT (external_ref) DO UPDATE SET status = 'cancelled';

DO $$
DECLARE
  paynow_ok BOOLEAN := false;
  contipay_ok BOOLEAN := false;
  err TEXT;
BEGIN
  BEGIN
    PERFORM public.mark_paynow_settled(
      'probe-cancel-paynow-20260724',
      'probe-hash-paynow-cancel-guard-001'
    );
  EXCEPTION WHEN OTHERS THEN
    GET STACKED DIAGNOSTICS err = MESSAGE_TEXT;
    IF err LIKE 'cannot settle cancelled Paynow intent%' THEN
      paynow_ok := true;
      RAISE NOTICE 'paynow_refused: %', err;
    ELSE
      RAISE EXCEPTION 'Paynow probe unexpected error: %', err;
    END IF;
  END;

  IF NOT paynow_ok THEN
    RAISE EXCEPTION 'Paynow settle unexpectedly succeeded on cancelled intent';
  END IF;

  BEGIN
    PERFORM public.mark_contipay_settled(
      'probe-cancel-contipay-20260724',
      'probe-hash-contipay-cancel-guard-001'
    );
  EXCEPTION WHEN OTHERS THEN
    GET STACKED DIAGNOSTICS err = MESSAGE_TEXT;
    IF err LIKE 'cannot settle cancelled ContiPay intent%' THEN
      contipay_ok := true;
      RAISE NOTICE 'contipay_refused: %', err;
    ELSE
      RAISE EXCEPTION 'ContiPay probe unexpected error: %', err;
    END IF;
  END;

  IF NOT contipay_ok THEN
    RAISE EXCEPTION 'ContiPay settle unexpectedly succeeded on cancelled intent';
  END IF;

  RAISE NOTICE 'VERIFY_OK paynow=% contipay=%', paynow_ok, contipay_ok;
END $$;

SELECT 'paynow' AS provider, external_ref, result_note, processed
FROM public.paynow_webhook_events
WHERE payload_hash = 'probe-hash-paynow-cancel-guard-001'
UNION ALL
SELECT 'contipay', external_ref, result_note, processed
FROM public.contipay_webhook_events
WHERE payload_hash = 'probe-hash-contipay-cancel-guard-001';

DELETE FROM public.paynow_webhook_events WHERE payload_hash = 'probe-hash-paynow-cancel-guard-001';
DELETE FROM public.contipay_webhook_events WHERE payload_hash = 'probe-hash-contipay-cancel-guard-001';
DELETE FROM public.paynow_payment_intents WHERE external_ref = 'probe-cancel-paynow-20260724';
DELETE FROM public.contipay_payment_intents WHERE external_ref = 'probe-cancel-contipay-20260724';

COMMIT;
