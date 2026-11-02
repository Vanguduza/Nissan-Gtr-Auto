-- Keep one PostgREST/SQL cart constructor. The historical three-argument RPC
-- only delegated to the four-argument constructor, whose fourth argument already
-- defaults to 'immediate'. Keeping both makes ordinary three-argument calls
-- ambiguous. Removing that wrapper preserves its behavior through the canonical
-- defaulted constructor and retains explicit dispatch/customer/FX validation.
DROP FUNCTION IF EXISTS public.create_pos_cart(uuid, uuid, public.currency_code);

-- These are authenticated staff operations. Their internal role/ownership guards
-- remain authoritative; anonymous callers do not need to reach their bodies.
-- Hosted hardening preceded some restored legacy wrappers, so close that ACL gap
-- explicitly after the reconciled snapshot without changing their logic.
REVOKE EXECUTE ON FUNCTION public.checkout_pos_cart(uuid, text, text, text)
  FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.post_pos_refund(uuid, text)
  FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.settle_invoice_tenders(uuid, jsonb)
  FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.upsert_staff_product_page(
  uuid, numeric, text, numeric, text
) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.search_catalog(text,text) FROM PUBLIC,anon;
REVOKE EXECUTE ON FUNCTION public.check_whatsapp_bot_rate_limit(text,integer,integer)
  FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.check_whatsapp_bot_rate_limit(text,integer,integer)
  TO service_role;

-- Native Supabase bootstrap grants future postgres-created functions to anon.
-- New application RPCs must opt in explicitly when anonymous access is intended;
-- revoking only PUBLIC on an individual function does not remove that direct grant.
ALTER DEFAULT PRIVILEGES FOR ROLE postgres
  REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC;
ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public
  REVOKE EXECUTE ON FUNCTIONS FROM anon;

-- Client requests must not gain access to the unexposed workflow schema even if
-- an internal trigger function retains PostgreSQL's default function ACL.
REVOKE ALL ON SCHEMA private FROM PUBLIC, anon, authenticated;
