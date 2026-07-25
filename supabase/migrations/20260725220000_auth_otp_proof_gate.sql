-- Auth OTP server-side gate: one-time proofs + attempt counter;
-- revoke direct EXECUTE on resolve_customer_for_receipt_contacts from authenticated
-- (DEFINER checkout path still calls it internally).

-- ---------------------------------------------------------------------------
-- Challenge attempt counter (brute-force soft limit)
-- ---------------------------------------------------------------------------
ALTER TABLE public.auth_otp_challenges
  ADD COLUMN IF NOT EXISTS attempt_count INTEGER NOT NULL DEFAULT 0
    CONSTRAINT auth_otp_challenges_attempt_nonneg CHECK (attempt_count >= 0);

COMMENT ON COLUMN public.auth_otp_challenges.attempt_count IS
  'Failed verify attempts against this challenge; Edge refuses after AUTH_OTP_MAX_ATTEMPTS.';

-- ---------------------------------------------------------------------------
-- One-time proofs minted after successful OTP verify (Edge service_role only)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.auth_otp_proofs (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  email TEXT,
  phone_e164 TEXT,
  expires_at TIMESTAMPTZ NOT NULL,
  consumed_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT auth_otp_proofs_identifier_present CHECK (
    email IS NOT NULL OR phone_e164 IS NOT NULL
  )
);

CREATE INDEX IF NOT EXISTS auth_otp_proofs_lookup_idx
  ON public.auth_otp_proofs (expires_at DESC)
  WHERE consumed_at IS NULL;

ALTER TABLE public.auth_otp_proofs ENABLE ROW LEVEL SECURITY;
-- No anon/authenticated policies — service_role bypasses RLS for Edge.
GRANT ALL ON TABLE public.auth_otp_proofs TO service_role;

COMMENT ON TABLE public.auth_otp_proofs IS
  'Short-lived OTP verification proofs; Edge mints HMAC token over id; consumed by complete_signup/login.';

-- ---------------------------------------------------------------------------
-- Contact bind helper: keep DEFINER internal use; drop direct client EXECUTE
-- ---------------------------------------------------------------------------
REVOKE ALL ON FUNCTION public.resolve_customer_for_receipt_contacts(TEXT, TEXT, TEXT)
  FROM PUBLIC;
REVOKE ALL ON FUNCTION public.resolve_customer_for_receipt_contacts(TEXT, TEXT, TEXT)
  FROM authenticated;
REVOKE ALL ON FUNCTION public.resolve_customer_for_receipt_contacts(TEXT, TEXT, TEXT)
  FROM anon;
GRANT EXECUTE ON FUNCTION public.resolve_customer_for_receipt_contacts(TEXT, TEXT, TEXT)
  TO service_role;
