-- Finance bank recon: restore INSERT/UPDATE for authenticated.
-- RLS policies bank_stmt_* already restrict to admin/finance; grants were
-- SELECT-only after 20260724110000, blocking staff web import/match.

GRANT INSERT, UPDATE, DELETE ON TABLE public.bank_statements TO authenticated;
GRANT INSERT, UPDATE, DELETE ON TABLE public.bank_statement_lines TO authenticated;
GRANT INSERT, UPDATE, DELETE ON TABLE public.bank_recon_matches TO authenticated;

COMMENT ON TABLE public.bank_statements IS
  'Bank statement headers. DML via RLS for admin/finance; SELECT for same roles.';
