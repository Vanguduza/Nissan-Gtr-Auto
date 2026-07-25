-- Delivery out-for-delivery SMS: absolute /track/{token} when public site URL is set.
-- Env mirror: PUBLIC_SITE_URL (ops) → Postgres GUC app.public_site_url
--   e.g. ALTER DATABASE postgres SET app.public_site_url = 'https://nissangtrauto.co.zw';
-- Optional fallback: app_settings key public.site_url, value_json {"url":"https://..."}.
-- When unset: relative /track/{token} (fail closed / relative OK). Never raises to callers.
-- No whatsapp_outbox table — WA Cloud is receipt/bot-only; skip WA enqueue.
-- Exclusions: no ZIMRA / payroll tax; RLS unchanged.

CREATE OR REPLACE FUNCTION public._notify_out_for_delivery(
  p_delivery_job_id UUID,
  p_track_token TEXT
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_base TEXT;
  v_track_url TEXT;
  v_body TEXT;
BEGIN
  IF p_track_token IS NULL OR length(trim(p_track_token)) = 0 THEN
    RETURN;
  END IF;

  -- PUBLIC_SITE_URL → GUC app.public_site_url; else app_settings public.site_url.
  v_base := NULLIF(
    trim(both FROM COALESCE(
      NULLIF(current_setting('app.public_site_url', true), ''),
      (
        SELECT NULLIF(trim(both FROM (s.value_json ->> 'url')), '')
        FROM public.app_settings s
        WHERE s.key = 'public.site_url'
      )
    )),
    ''
  );

  IF v_base IS NOT NULL THEN
    v_base := rtrim(v_base, '/');
    v_track_url := v_base || '/track/' || trim(p_track_token);
  ELSE
    v_track_url := '/track/' || trim(p_track_token);
  END IF;

  v_body := format(
    'GTR Auto: Your order is out for delivery. Track: %s',
    v_track_url
  );

  PERFORM public._enqueue_delivery_customer_sms(
    p_delivery_job_id,
    'delivery_out_for_delivery',
    'delivery_out_for_delivery:' || p_delivery_job_id::text,
    v_body
  );
EXCEPTION
  WHEN OTHERS THEN
    -- Fail closed: never surface notify errors to update_delivery_job_status.
    NULL;
END;
$$;

COMMENT ON FUNCTION public._notify_out_for_delivery(UUID, TEXT) IS
  'Best-effort SMS for dispatched jobs. Track URL absolute when PUBLIC_SITE_URL '
  'is mirrored as GUC app.public_site_url (or app_settings public.site_url.url); '
  'else relative /track/{token}. Fail closed. No WA outbox — skip WhatsApp.';

REVOKE ALL ON FUNCTION public._notify_out_for_delivery(UUID, TEXT) FROM PUBLIC;
