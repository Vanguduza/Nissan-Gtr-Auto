-- EcoCash direct C2B fields on WhatsApp Flow orders (alongside Paynow/ContiPay)

ALTER TABLE public.whatsapp_flow_orders
  ADD COLUMN IF NOT EXISTS payment_provider TEXT NOT NULL DEFAULT 'paynow'
    CHECK (payment_provider IN ('paynow', 'contipay', 'ecocash', 'stub'));

ALTER TABLE public.whatsapp_flow_orders
  ADD COLUMN IF NOT EXISTS payment_source_reference TEXT;

COMMENT ON COLUMN public.whatsapp_flow_orders.payment_provider IS
  'Checkout rail: paynow|contipay aggregators, or ecocash direct C2B merchant API.';
COMMENT ON COLUMN public.whatsapp_flow_orders.payment_source_reference IS
  'Merchant sourceReference sent to EcoCash (or PSP merchant ref).';
