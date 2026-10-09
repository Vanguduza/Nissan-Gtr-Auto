-- Alerts that reach staff away from the screen.
-- 1. Each person chooses, for themselves, a phone number, SMS or WhatsApp, and which alerts they want:
--    urgent approvals (an urgent item has waited 10 minutes for them) and the 07:00 morning summary.
-- 2. The approvals sweep (every 5 minutes) texts an urgent item only to the people it is waiting on,
--    once per item, when they opted in. The in-app alert is unchanged.
-- 3. At 07:00 Harare, everyone who opted in and may see the daily dashboard gets yesterday's figures,
--    computed as them (get_daily_dashboard runs with their identity, so branch/role limits apply),
--    plus an in-app notice linking to that day on Staff → Today.
-- Messages go through sms_outbox (process-sms-outbox); its new channel column picks WhatsApp, falling
-- back to SMS when WhatsApp is not configured or the message is refused.

INSERT INTO public.sms_event_catalog(code, description, category, priority) VALUES
 ('approval_urgent', 'Urgent approval waiting for you', 'staff', 'high'),
 ('morning_summary', 'Yesterday''s figures at 07:00', 'staff', 'normal')
ON CONFLICT (code) DO NOTHING;

ALTER TYPE public.staff_ops_notification_kind ADD VALUE IF NOT EXISTS 'morning_summary';

ALTER TABLE public.sms_outbox ADD COLUMN IF NOT EXISTS channel text NOT NULL DEFAULT 'sms' CHECK (channel IN ('sms','whatsapp'));

CREATE TABLE public.staff_alert_settings (
  user_id uuid PRIMARY KEY REFERENCES public.profiles(id) ON DELETE CASCADE,
  phone_e164 text CHECK (phone_e164 IS NULL OR phone_e164 ~ '^\+[1-9][0-9]{7,14}$'),
  channel text NOT NULL DEFAULT 'sms' CHECK (channel IN ('sms','whatsapp')),
  urgent_approvals boolean NOT NULL DEFAULT false,
  morning_summary boolean NOT NULL DEFAULT false,
  updated_at timestamptz NOT NULL DEFAULT now(),
  CHECK (phone_e164 IS NOT NULL OR NOT (urgent_approvals OR morning_summary))
);
ALTER TABLE public.staff_alert_settings ENABLE ROW LEVEL SECURITY;
CREATE POLICY staff_alert_settings_own_read ON public.staff_alert_settings FOR SELECT TO authenticated USING (user_id = auth.uid());
REVOKE ALL ON TABLE public.staff_alert_settings FROM anon, authenticated;
GRANT SELECT ON TABLE public.staff_alert_settings TO authenticated;

CREATE OR REPLACE FUNCTION public.get_my_alert_settings()
 RETURNS jsonb LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path TO '' AS $f$
DECLARE s public.staff_alert_settings%ROWTYPE;
BEGIN
 IF NOT public.is_staff() THEN RAISE EXCEPTION 'staff only'; END IF;
 SELECT * INTO s FROM public.staff_alert_settings WHERE user_id = auth.uid();
 RETURN jsonb_build_object(
  'phone_e164', COALESCE(s.phone_e164, (SELECT p.phone_e164 FROM public.profiles p WHERE p.id = auth.uid())),
  'channel', COALESCE(s.channel, 'sms'),
  'urgent_approvals', COALESCE(s.urgent_approvals, false),
  'morning_summary', COALESCE(s.morning_summary, false),
  'can_get_summary', public.has_staff_role(ARRAY['admin','finance']::public.staff_role[]) OR public.is_pos_approver(),
  'saved', s.user_id IS NOT NULL);
END $f$;

CREATE OR REPLACE FUNCTION public.set_my_alert_settings(p_phone_e164 text, p_channel text, p_urgent_approvals boolean, p_morning_summary boolean)
 RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path TO '' AS $f$
DECLARE v_phone text := NULLIF(regexp_replace(COALESCE(p_phone_e164, ''), '[\s()-]', '', 'g'), '');
BEGIN
 IF NOT public.is_staff() THEN RAISE EXCEPTION 'staff only'; END IF;
 IF v_phone IS NOT NULL AND v_phone !~ '^\+[1-9][0-9]{7,14}$' THEN RAISE EXCEPTION 'phone must be in international form, e.g. +263771234567'; END IF;
 IF v_phone IS NULL AND (COALESCE(p_urgent_approvals, false) OR COALESCE(p_morning_summary, false)) THEN
  RAISE EXCEPTION 'a phone number is needed to send alerts'; END IF;
 IF COALESCE(p_channel, 'sms') NOT IN ('sms','whatsapp') THEN RAISE EXCEPTION 'channel must be sms or whatsapp'; END IF;
 INSERT INTO public.staff_alert_settings(user_id, phone_e164, channel, urgent_approvals, morning_summary, updated_at)
 VALUES (auth.uid(), v_phone, COALESCE(p_channel, 'sms'), COALESCE(p_urgent_approvals, false), COALESCE(p_morning_summary, false), now())
 ON CONFLICT (user_id) DO UPDATE SET phone_e164 = EXCLUDED.phone_e164, channel = EXCLUDED.channel,
   urgent_approvals = EXCLUDED.urgent_approvals, morning_summary = EXCLUDED.morning_summary, updated_at = now();
 RETURN public.get_my_alert_settings();
END $f$;

-- One message to one person (not the manager_sms_preferences fan-out of emit_domain_event).
CREATE OR REPLACE FUNCTION private.queue_staff_message(p_user_id uuid, p_event_code text, p_dedupe_key text, p_body text, p_payload jsonb DEFAULT '{}'::jsonb)
 RETURNS boolean LANGUAGE plpgsql SECURITY DEFINER SET search_path TO '' AS $f$
DECLARE s public.staff_alert_settings%ROWTYPE; v_event uuid;
BEGIN
 SELECT * INTO s FROM public.staff_alert_settings WHERE user_id = p_user_id AND phone_e164 IS NOT NULL;
 IF NOT FOUND THEN RETURN false; END IF;
 INSERT INTO public.domain_events(event_code, dedupe_key, payload, actor_user_id)
 VALUES (p_event_code, p_dedupe_key, COALESCE(p_payload, '{}'::jsonb), NULL)
 ON CONFLICT (event_code, dedupe_key) DO NOTHING RETURNING id INTO v_event;
 IF v_event IS NULL THEN RETURN false; END IF;  -- already sent
 INSERT INTO public.sms_outbox(domain_event_id, event_code, recipient_user_id, phone_e164, body, channel)
 VALUES (v_event, p_event_code, p_user_id, s.phone_e164, left(p_body, 640), s.channel);
 RETURN true;
END $f$;

CREATE OR REPLACE FUNCTION private.sweep_approval_alerts()
 RETURNS integer LANGUAGE plpgsql SECURITY DEFINER SET search_path TO '' AS $f$
DECLARE u record; it jsonb; v_n integer := 0; v_limit interval; v_urgent boolean;
BEGIN
 PERFORM set_config('app.staff_ops_notify','1',true);
 FOR u IN SELECT DISTINCT sr.user_id, COALESCE(a.urgent_approvals, false) AS wants_text FROM public.staff_roles sr
          LEFT JOIN public.staff_alert_settings a ON a.user_id = sr.user_id
          WHERE sr.role IN('admin','finance','sales','dispatcher','warehouse') LOOP
  FOR it IN SELECT * FROM jsonb_array_elements(private.approvals_for(u.user_id)) LOOP
   v_urgent := (it->>'urgent')::boolean;
   v_limit := CASE WHEN v_urgent THEN interval '10 minutes' ELSE interval '4 hours' END;
   CONTINUE WHEN (it->>'waiting_since')::timestamptz > now() - v_limit;
   INSERT INTO public.staff_ops_notifications(kind, recipient_user_id, title, body, ref_key, href)
   VALUES ('approval_waiting', u.user_id,
           CASE WHEN v_urgent THEN 'Urgent: ' ELSE 'Waiting: ' END || (it->>'title'),
           COALESCE(it->>'detail','') || ' · waiting since ' || to_char((it->>'waiting_since')::timestamptz AT TIME ZONE 'Africa/Harare','DD Mon HH24:MI'),
           (it->>'kind')||':'||(it->>'ref'), it->>'href')
   ON CONFLICT (recipient_user_id, ref_key) WHERE ref_key IS NOT NULL DO NOTHING;
   IF FOUND THEN
    v_n := v_n + 1;
    IF v_urgent AND u.wants_text THEN
     PERFORM private.queue_staff_message(u.user_id, 'approval_urgent', 'approval:'||(it->>'kind')||':'||(it->>'ref')||':'||u.user_id,
       format('GTR Auto URGENT: %s. %s Waiting since %s. Open Staff > Approvals.', it->>'title', COALESCE(it->>'detail',''),
              to_char((it->>'waiting_since')::timestamptz AT TIME ZONE 'Africa/Harare','HH24:MI')),
       jsonb_build_object('kind', it->>'kind', 'ref', it->>'ref'));
    END IF;
   END IF;
  END LOOP;
 END LOOP;
 PERFORM set_config('app.staff_ops_notify','',true);
 RETURN v_n;
END $f$;

-- Yesterday in one text, as the recipient is allowed to see it (all branches they can see).
CREATE OR REPLACE FUNCTION private.morning_summary_text(p_day date, d jsonb)
 RETURNS text LANGUAGE sql IMMUTABLE SET search_path TO '' AS $f$
 SELECT concat_ws(E'\n',
  'GTR Auto ' || to_char(p_day, 'Dy DD Mon') || ':',
  COALESCE((SELECT string_agg(format('Sales %s %s net (%s invoices, margin %s%%)', s->>'currency',
        to_char((s->>'net')::numeric, 'FM999,999,990.00'), s->>'invoices', COALESCE(round((s->>'margin_pct')::numeric)::text, '-')), E'\n')
     FROM jsonb_array_elements(COALESCE(d->'sales','[]'::jsonb)) s), 'No sales'),
  (SELECT 'Unpaid from the day: ' || string_agg(format('%s %s', u->>'currency', to_char((u->>'amount')::numeric, 'FM999,999,990.00')), ' + ')
     FROM jsonb_array_elements(COALESCE(d->'unpaid_today','[]'::jsonb)) u),
  (SELECT 'Till differences: ' || string_agg(format('%s %s %s', COALESCE(t->>'cashier','till'), t->>'currency', to_char((t->>'variance')::numeric, 'FM999,990.00')), ', ')
     FROM jsonb_array_elements(COALESCE(d->'tills','[]'::jsonb)) t WHERE abs(COALESCE((t->>'variance')::numeric, 0)) > 0.009),
  (SELECT 'Drivers still hold: ' || string_agg(format('%s %s', c->>'currency', to_char((c->>'amount')::numeric, 'FM999,999,990.00')), ' + ')
     FROM jsonb_array_elements(COALESCE(d->'driver_cash_held','[]'::jsonb)) c),
  CASE WHEN COALESCE((d->'deliveries'->>'failed')::int, 0) > 0 THEN 'Failed deliveries: ' || (d->'deliveries'->>'failed') END,
  CASE WHEN COALESCE((d->>'low_stock_count')::int, 0) > 0 THEN 'Parts at or under reorder point: ' || (d->>'low_stock_count') END,
  CASE WHEN COALESCE((d->>'approvals_waiting')::int, 0) > 0 THEN 'Waiting for approval now: ' || (d->>'approvals_waiting') END,
  'Details: Staff > Today')
$f$;

CREATE OR REPLACE FUNCTION private.send_morning_summaries(p_day date DEFAULT NULL)
 RETURNS integer LANGUAGE plpgsql SECURITY DEFINER SET search_path TO '' AS $f$
DECLARE v_day date := COALESCE(p_day, (now() AT TIME ZONE 'Africa/Harare')::date - 1); s record; d jsonb; v_text text; v_n integer := 0;
BEGIN
 FOR s IN SELECT a.user_id FROM public.staff_alert_settings a WHERE a.morning_summary AND a.phone_e164 IS NOT NULL LOOP
  BEGIN
   -- Run the dashboard as this person: its own role and branch checks decide what they get.
   PERFORM set_config('request.jwt.claims', json_build_object('sub', s.user_id, 'role', 'authenticated')::text, true);
   d := public.get_daily_dashboard(v_day, NULL);
  EXCEPTION WHEN OTHERS THEN
   d := NULL;  -- no longer a manager / finance / admin: nothing to send
  END;
  PERFORM set_config('request.jwt.claims', '', true);
  CONTINUE WHEN d IS NULL;
  v_text := private.morning_summary_text(v_day, d);
  IF private.queue_staff_message(s.user_id, 'morning_summary', 'morning:' || v_day || ':' || s.user_id, v_text, jsonb_build_object('day', v_day)) THEN
   v_n := v_n + 1;
  END IF;
  PERFORM set_config('app.staff_ops_notify','1',true);
  INSERT INTO public.staff_ops_notifications(kind, recipient_user_id, title, body, ref_key, href)
  VALUES ('morning_summary', s.user_id, 'Yesterday at a glance', v_text, 'morning:' || v_day, '/staff/dashboard?date=' || v_day)
  ON CONFLICT (recipient_user_id, ref_key) WHERE ref_key IS NOT NULL DO NOTHING;
  PERFORM set_config('app.staff_ops_notify','',true);
 END LOOP;
 RETURN v_n;
END $f$;

REVOKE ALL ON FUNCTION private.queue_staff_message(uuid, text, text, text, jsonb) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION private.morning_summary_text(date, jsonb) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION private.send_morning_summaries(date) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.get_my_alert_settings() FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.set_my_alert_settings(text, text, boolean, boolean) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.get_my_alert_settings() TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.set_my_alert_settings(text, text, boolean, boolean) TO authenticated, service_role;

-- 05:00 UTC = 07:00 Africa/Harare (no daylight saving).
SELECT cron.unschedule(jobid) FROM cron.job WHERE jobname = 'morning-summary-v1';
SELECT cron.schedule('morning-summary-v1', '0 5 * * *', 'SELECT private.send_morning_summaries()');
