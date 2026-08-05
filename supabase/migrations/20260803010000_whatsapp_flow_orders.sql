-- WhatsApp Flow orders (Meta Flow cart → Paynow CTA → receipt PDF)
-- Tax-agnostic; no ZIMRA. Service-role FastAPI satellite writes via service key.

CREATE TABLE IF NOT EXISTS public.whatsapp_flow_orders (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  status TEXT NOT NULL DEFAULT 'PENDING'
    CHECK (status IN (
      'PENDING',
      'PAID',
      'FAILED',
      'CANCELLED',
      'FULFILLING',
      'COMPLETED'
    )),
  currency public.currency_code NOT NULL DEFAULT 'USD',
  subtotal NUMERIC(18, 4) NOT NULL DEFAULT 0 CHECK (subtotal >= 0),
  delivery_fee NUMERIC(18, 4) NOT NULL DEFAULT 0 CHECK (delivery_fee >= 0),
  total NUMERIC(18, 4) NOT NULL DEFAULT 0 CHECK (total >= 0),
  delivery_method TEXT NOT NULL DEFAULT 'counter_collect'
    CHECK (delivery_method IN ('counter_collect', 'harare', 'nationwide')),
  delivery_notes TEXT,
  wa_id TEXT,
  lines JSONB NOT NULL DEFAULT '[]'::jsonb,
  payment_link TEXT,
  payment_reference TEXT,
  channel TEXT NOT NULL DEFAULT 'whatsapp_flow',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS whatsapp_flow_orders_status_idx
  ON public.whatsapp_flow_orders (status, created_at DESC);
CREATE INDEX IF NOT EXISTS whatsapp_flow_orders_wa_idx
  ON public.whatsapp_flow_orders (wa_id)
  WHERE wa_id IS NOT NULL;

COMMENT ON TABLE public.whatsapp_flow_orders IS
  'Orders created from WhatsApp Flows cart/checkout; settled via Paynow/ContiPay webhook.';

ALTER TABLE public.whatsapp_flow_orders ENABLE ROW LEVEL SECURITY;

-- Staff can read; mutations via service_role / SECURITY DEFINER only from FastAPI
DROP POLICY IF EXISTS whatsapp_flow_orders_staff_select ON public.whatsapp_flow_orders;
CREATE POLICY whatsapp_flow_orders_staff_select ON public.whatsapp_flow_orders
  FOR SELECT TO authenticated
  USING (
    EXISTS (
      SELECT 1 FROM public.staff_roles sr
      WHERE sr.user_id = auth.uid()
        AND sr.role IN ('admin', 'finance', 'sales', 'warehouse', 'dispatcher')
    )
  );

-- No direct INSERT/UPDATE/DELETE for authenticated — service_role bypasses RLS
GRANT SELECT ON TABLE public.whatsapp_flow_orders TO authenticated;
GRANT ALL ON TABLE public.whatsapp_flow_orders TO service_role;

CREATE OR REPLACE FUNCTION public.touch_whatsapp_flow_orders_updated_at()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
  NEW.updated_at := now();
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS whatsapp_flow_orders_updated_at ON public.whatsapp_flow_orders;
CREATE TRIGGER whatsapp_flow_orders_updated_at
  BEFORE UPDATE ON public.whatsapp_flow_orders
  FOR EACH ROW EXECUTE FUNCTION public.touch_whatsapp_flow_orders_updated_at();
