-- D-57 settle fields on WhatsApp Flow orders (browse stays USD; ZiG at EcoCash pay).
-- No ZIMRA. Existing RLS on whatsapp_flow_orders unchanged (staff SELECT; service_role writes).

ALTER TABLE public.whatsapp_flow_orders
  ADD COLUMN IF NOT EXISTS fx_rate_id UUID
    REFERENCES public.daily_exchange_rates (id);

ALTER TABLE public.whatsapp_flow_orders
  ADD COLUMN IF NOT EXISTS settle_currency public.currency_code;

ALTER TABLE public.whatsapp_flow_orders
  ADD COLUMN IF NOT EXISTS settle_total NUMERIC(18, 4)
    CHECK (settle_total IS NULL OR settle_total >= 0);

ALTER TABLE public.whatsapp_flow_orders
  ADD COLUMN IF NOT EXISTS settle_amount_minor BIGINT
    CHECK (settle_amount_minor IS NULL OR settle_amount_minor >= 0);

COMMENT ON COLUMN public.whatsapp_flow_orders.fx_rate_id IS
  'D-57: daily_exchange_rates.id applied when settling EcoCash/ZiG; null for USD rails.';
COMMENT ON COLUMN public.whatsapp_flow_orders.settle_currency IS
  'D-57: payable currency at pay step (ZIG for EcoCash); browse currency column stays USD.';
COMMENT ON COLUMN public.whatsapp_flow_orders.settle_total IS
  'D-57: payable major units for EcoCash C2B (from MoneyMinor); null when USD rail.';
COMMENT ON COLUMN public.whatsapp_flow_orders.settle_amount_minor IS
  'D-57: payable amount_minor (ZiG or USD settle); AI must never invent this.';
