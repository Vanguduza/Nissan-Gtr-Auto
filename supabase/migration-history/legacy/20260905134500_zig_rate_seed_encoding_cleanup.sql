-- The replacement project inherited the dev seed note with mojibake.
-- Match the seed semantically instead of depending on the corrupted punctuation bytes.

DELETE FROM public.daily_exchange_rates
WHERE currency = 'ZIG'
  AND rate = 1
  AND set_by IS NULL
  AND notes ILIKE 'Initial seed%replace via Finance%Exchange rate%';
