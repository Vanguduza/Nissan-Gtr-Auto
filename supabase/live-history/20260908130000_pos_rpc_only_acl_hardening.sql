-- Exported from the hosted project's supabase_migrations.schema_migrations (20260908130000 pos_rpc_only_acl_hardening).
-- Source of record for what production ran; see supabase/live-history/README.md.

-- P0/P1 POS tables are RPC-only from client roles. RLS remains defense in depth.
REVOKE ALL ON TABLE public.pos_till_sessions FROM anon, authenticated;

REVOKE ALL ON TABLE public.pos_till_cash_movements FROM anon, authenticated;

REVOKE ALL ON TABLE public.pos_till_count_lines FROM anon, authenticated;

REVOKE ALL ON TABLE public.pos_till_session_events FROM anon, authenticated;

REVOKE ALL ON TABLE public.pos_fulfillment_requests FROM anon, authenticated;

REVOKE ALL ON TABLE public.pos_return_cases FROM anon, authenticated;

REVOKE ALL ON TABLE public.pos_return_case_lines FROM anon, authenticated;

REVOKE ALL ON TABLE public.pos_core_returns FROM anon, authenticated;

REVOKE ALL ON TABLE public.pos_commerce_tender_settlements FROM anon, authenticated;

REVOKE ALL ON TABLE public.pos_approval_policies FROM anon, authenticated;

REVOKE ALL ON TABLE public.pos_action_audit FROM anon, authenticated;
