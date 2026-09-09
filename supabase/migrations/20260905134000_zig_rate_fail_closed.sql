-- Production hardening: ZiG must never silently use the historical 1:1 dev seed.
-- USD remains the storefront/accounting base currency when no verified ZiG rate exists.

DELETE FROM public.daily_exchange_rates
WHERE currency = 'ZIG'
  AND rate = 1
  AND set_by IS NULL
  AND notes = 'Initial seed — replace via Finance → Exchange rate';

-- get_zig_exchange_rate already returns NULL when no positive row exists.
-- Clients must treat NULL as "ZiG settlement unavailable" until Finance publishes a rate.
