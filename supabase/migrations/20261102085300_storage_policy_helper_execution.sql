-- Authenticated Storage policies execute their predicates as the requesting
-- role. The hosted petty-cash migration revoked this helper's PUBLIC execution
-- without granting the authenticated role used by its two Storage policies.
-- Once native bootstrap default grants are removed, PostgreSQL may evaluate
-- that policy even for another bucket and reject an otherwise valid upload.
-- Retain the existing requester/staff checks and grant only their caller roles.
REVOKE ALL ON FUNCTION public._can_access_petty_cash_receipt_object(text)
  FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public._can_access_petty_cash_receipt_object(text)
  TO authenticated, service_role;
