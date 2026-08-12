-- Register procurement_funds_released for emit_domain_event (Epic A approve → fund release).
-- approve_purchase_order emits this code; catalog row was missing from 20260812* landings.

INSERT INTO public.sms_event_catalog (code, description, category, severity)
VALUES (
  'procurement_funds_released',
  'Procurement fund release on PO approve (under requesting official)',
  'procurement',
  'normal'
)
ON CONFLICT (code) DO UPDATE
SET
  description = EXCLUDED.description,
  category = EXCLUDED.category,
  severity = EXCLUDED.severity,
  is_active = true;
