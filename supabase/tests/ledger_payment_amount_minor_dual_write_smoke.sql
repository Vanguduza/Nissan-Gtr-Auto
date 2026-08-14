-- H4 slice 5: ledger JE + payment_entry amount_minor dual-write smoke (postgres).
-- Prove insert fills minors; draft re-update re-syncs; posted major rewrite blocked;
-- null→minor fill on posted JE allowed without inventing wrong money.

DO $$
DECLARE
  v_draft UUID;
  v_posted UUID;
  v_line UUID;
  v_debit_m BIGINT;
  v_credit_m BIGINT;
  v_pe UUID;
  v_cust UUID;
  v_amt_m BIGINT;
  v_settle_m BIGINT;
  v_blocked BOOLEAN := false;
BEGIN
  -- Draft JE: create lines then re-update majors → minors must re-sync (12.50→1250, then 20.00→2000).
  v_draft := public.create_journal_draft(
    CURRENT_DATE,
    'H4 ledger minor dual-write draft',
    'USD',
    1,
    '[
      {"account_code":"1200","debit":12.50,"credit":0,"currency":"USD"},
      {"account_code":"4100","debit":0,"credit":12.50,"currency":"USD"}
    ]'::jsonb
  );

  SELECT l.id, l.debit_minor, l.credit_minor
  INTO v_line, v_debit_m, v_credit_m
  FROM public.journal_entry_lines l
  WHERE l.journal_entry_id = v_draft AND l.debit > 0
  LIMIT 1;

  IF v_debit_m IS DISTINCT FROM 1250 OR v_credit_m IS DISTINCT FROM 0 THEN
    RAISE EXCEPTION
      'H4 smoke fail: draft debit line minors want 1250/0 got %/%',
      v_debit_m, v_credit_m;
  END IF;

  SELECT credit_minor INTO v_credit_m
  FROM public.journal_entry_lines
  WHERE journal_entry_id = v_draft AND credit > 0
  LIMIT 1;

  IF v_credit_m IS DISTINCT FROM 1250 THEN
    RAISE EXCEPTION 'H4 smoke fail: draft credit_minor want 1250 got %', v_credit_m;
  END IF;

  -- Re-update majors on draft — must recompute minors (not leave stale 1250).
  UPDATE public.journal_entry_lines
  SET debit = 20.00, credit = 0
  WHERE id = v_line;

  UPDATE public.journal_entry_lines
  SET debit = 0, credit = 20.00
  WHERE journal_entry_id = v_draft AND credit > 0;

  SELECT debit_minor INTO v_debit_m FROM public.journal_entry_lines WHERE id = v_line;
  IF v_debit_m IS DISTINCT FROM 2000 THEN
    RAISE EXCEPTION 'H4 smoke fail: re-update debit_minor want 2000 got %', v_debit_m;
  END IF;

  -- Post a fresh balanced entry; minors filled on insert.
  v_posted := public.post_journal_entry(
    CURRENT_DATE,
    'H4 ledger minor dual-write posted',
    'USD',
    1,
    '[
      {"account_code":"1200","debit":30.00,"credit":0,"currency":"USD"},
      {"account_code":"4100","debit":0,"credit":30.00,"currency":"USD"}
    ]'::jsonb
  );

  IF EXISTS (
    SELECT 1
    FROM public.journal_entry_lines
    WHERE journal_entry_id = v_posted
      AND (debit_minor IS NULL OR credit_minor IS NULL
           OR debit_minor IS DISTINCT FROM public._major_to_minor(debit)
           OR credit_minor IS DISTINCT FROM public._major_to_minor(credit))
  ) THEN
    RAISE EXCEPTION 'H4 smoke fail: posted JE minors missing or mismatched';
  END IF;

  -- Posted major rewrite must fail.
  BEGIN
    UPDATE public.journal_entry_lines
    SET debit = 999.00
    WHERE journal_entry_id = v_posted AND debit > 0;
    RAISE EXCEPTION 'H4 smoke fail: posted debit rewrite allowed';
  EXCEPTION
    WHEN OTHERS THEN
      IF SQLERRM LIKE 'H4 smoke fail:%' THEN RAISE; END IF;
      v_blocked := true;
  END;
  IF NOT v_blocked THEN
    RAISE EXCEPTION 'H4 smoke fail: expected posted immutability exception';
  END IF;

  -- Nulling already-set minors on posted must be blocked.
  v_blocked := false;
  BEGIN
    UPDATE public.journal_entry_lines
    SET debit_minor = NULL, credit_minor = NULL
    WHERE journal_entry_id = v_posted;
    RAISE EXCEPTION 'H4 smoke fail: posted minor nulling allowed';
  EXCEPTION
    WHEN OTHERS THEN
      IF SQLERRM LIKE 'H4 smoke fail:%' THEN RAISE; END IF;
      v_blocked := true;
  END;
  IF NOT v_blocked THEN
    RAISE EXCEPTION 'H4 smoke fail: expected posted minor-null block';
  END IF;

  -- Simulate missing minors via trigger-disabled path, then fill.
  ALTER TABLE public.journal_entry_lines DISABLE TRIGGER journal_entry_lines_protect_posted;
  UPDATE public.journal_entry_lines
  SET debit_minor = NULL, credit_minor = NULL
  WHERE journal_entry_id = v_posted;
  ALTER TABLE public.journal_entry_lines ENABLE TRIGGER journal_entry_lines_protect_posted;

  UPDATE public.journal_entry_lines
  SET
    debit_minor = public._major_to_minor(debit),
    credit_minor = public._major_to_minor(credit)
  WHERE journal_entry_id = v_posted
    AND (debit_minor IS NULL OR credit_minor IS NULL);

  SELECT debit_minor INTO v_debit_m
  FROM public.journal_entry_lines
  WHERE journal_entry_id = v_posted AND debit > 0
  LIMIT 1;

  IF v_debit_m IS DISTINCT FROM 3000 THEN
    RAISE EXCEPTION 'H4 smoke fail: null-fill debit_minor want 3000 got %', v_debit_m;
  END IF;

  -- Payment entry dual-write (RPC path).
  INSERT INTO public.customers (display_name, currency)
  VALUES ('H4 payment minor smoke', 'USD')
  RETURNING id INTO v_cust;

  PERFORM set_config('app.payments_rpc', '1', true);
  INSERT INTO public.payment_entries (
    document_number, customer_id, tender, currency, exchange_rate_applied, amount,
    settlement_currency, settlement_amount, settlement_exchange_rate, status
  )
  VALUES (
    'PE-H4-MINOR-' || substr(gen_random_uuid()::text, 1, 8),
    v_cust,
    'cash',
    'USD',
    1,
    12.50,
    'ZIG',
    162.50,
    13,
    'draft'
  )
  RETURNING id INTO v_pe;

  SELECT amount_minor, settlement_amount_minor
  INTO v_amt_m, v_settle_m
  FROM public.payment_entries
  WHERE id = v_pe;

  IF v_amt_m IS DISTINCT FROM 1250 OR v_settle_m IS DISTINCT FROM 16250 THEN
    RAISE EXCEPTION
      'H4 smoke fail: payment minors want 1250/16250 got %/%',
      v_amt_m, v_settle_m;
  END IF;

  -- Re-update majors under RPC — minors must follow (not invent stale/wrong).
  UPDATE public.payment_entries
  SET amount = 20.00, settlement_amount = 260.00
  WHERE id = v_pe;

  SELECT amount_minor, settlement_amount_minor
  INTO v_amt_m, v_settle_m
  FROM public.payment_entries
  WHERE id = v_pe;

  IF v_amt_m IS DISTINCT FROM 2000 OR v_settle_m IS DISTINCT FROM 26000 THEN
    RAISE EXCEPTION
      'H4 smoke fail: payment re-update minors want 2000/26000 got %/%',
      v_amt_m, v_settle_m;
  END IF;

  RAISE NOTICE 'H4 ledger/payment amount_minor dual-write smoke OK je=% pe=%', v_posted, v_pe;
END;
$$;
