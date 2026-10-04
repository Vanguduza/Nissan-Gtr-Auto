-- Back office queue updates while a driver waits at the door (RLS still decides who sees a row).
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_publication WHERE pubname = 'supabase_realtime')
     AND NOT EXISTS (SELECT 1 FROM pg_publication_tables WHERE pubname = 'supabase_realtime' AND schemaname = 'public' AND tablename = 'delivery_balance_approvals') THEN
    ALTER PUBLICATION supabase_realtime ADD TABLE public.delivery_balance_approvals;
  END IF;
END $$;
