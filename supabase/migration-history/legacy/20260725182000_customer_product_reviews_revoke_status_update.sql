-- Authors must not UPDATE status via table privileges; moderation is RPC-only.
REVOKE UPDATE ON TABLE public.customer_product_reviews FROM authenticated;
GRANT UPDATE (rating, body, updated_at)
  ON TABLE public.customer_product_reviews TO authenticated;
