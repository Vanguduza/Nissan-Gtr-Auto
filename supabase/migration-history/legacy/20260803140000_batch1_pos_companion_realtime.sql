-- Batch 1 §1.5 — POS companion Realtime (pos_cart_lines + pos_scan_sessions).
-- Web tablet subscribes; no browser camera.

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_publication_tables
    WHERE pubname = 'supabase_realtime'
      AND schemaname = 'public'
      AND tablename = 'pos_cart_lines'
  ) THEN
    ALTER PUBLICATION supabase_realtime ADD TABLE public.pos_cart_lines;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_publication_tables
    WHERE pubname = 'supabase_realtime'
      AND schemaname = 'public'
      AND tablename = 'pos_scan_sessions'
  ) THEN
    ALTER PUBLICATION supabase_realtime ADD TABLE public.pos_scan_sessions;
  END IF;
END $$;

ALTER TABLE public.pos_cart_lines REPLICA IDENTITY FULL;
ALTER TABLE public.pos_scan_sessions REPLICA IDENTITY FULL;
