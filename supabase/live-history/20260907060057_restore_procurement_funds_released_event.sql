-- Exported from the hosted project's supabase_migrations.schema_migrations (20260907060057 restore_procurement_funds_released_event).
-- Source of record for what production ran; see supabase/live-history/README.md.

INSERT INTO public.sms_event_catalog (code, description, category, priority)
VALUES ('procurement_funds_released','Procurement fund release on PO approve (under requesting official)','procurement','normal')
ON CONFLICT (code) DO UPDATE SET description=EXCLUDED.description,category=EXCLUDED.category,priority=EXCLUDED.priority,is_active=true;
