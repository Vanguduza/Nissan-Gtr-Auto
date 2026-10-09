-- Exported from the hosted project's supabase_migrations.schema_migrations (20260908072425 catalog_r2_presign_list_page_tool).
-- Source of record for what production ran; see supabase/live-history/README.md.

CREATE OR REPLACE FUNCTION public.catalog_r2_presign_list_page(
  p_prefix text,
  p_continuation_token text DEFAULT NULL,
  p_max_keys integer DEFAULT 1000,
  p_expires integer DEFAULT 600
)
RETURNS text
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, extensions, vault
AS $$
DECLARE
  account_id text;
  access_key text;
  secret_key text;
  bucket text;
  host text;
  canonical_uri text;
  amz_date text;
  date_stamp text;
  scope text;
  credential text;
  service_query text;
  canonical_query text;
  canonical_headers text;
  canonical_request text;
  string_to_sign text;
BEGIN
  IF p_prefix IS NULL THEN p_prefix := ''; END IF;
  p_max_keys := GREATEST(1, LEAST(COALESCE(p_max_keys,1000),1000));
  p_expires := GREATEST(60, LEAST(COALESCE(p_expires,600),3600));
  SELECT decrypted_secret INTO account_id FROM vault.decrypted_secrets WHERE name='catalog_r2_account_id';
  SELECT decrypted_secret INTO access_key FROM vault.decrypted_secrets WHERE name='catalog_r2_access_key_id';
  SELECT decrypted_secret INTO secret_key FROM vault.decrypted_secrets WHERE name='catalog_r2_secret_access_key';
  SELECT decrypted_secret INTO bucket FROM vault.decrypted_secrets WHERE name='catalog_r2_bucket';
  IF account_id IS NULL OR access_key IS NULL OR secret_key IS NULL OR bucket IS NULL THEN
    RAISE EXCEPTION 'R2 Vault configuration incomplete';
  END IF;
  host := account_id || '.r2.cloudflarestorage.com';
  canonical_uri := '/' || public._aws_uri_encode(bucket, true) || '/';
  amz_date := to_char(clock_timestamp() AT TIME ZONE 'UTC', 'YYYYMMDD"T"HH24MISS"Z"');
  date_stamp := substring(amz_date from 1 for 8);
  scope := date_stamp || '/auto/s3/aws4_request';
  credential := access_key || '/' || scope;
  service_query := CASE WHEN NULLIF(p_continuation_token,'') IS NOT NULL
      THEN 'continuation-token=' || public._aws_uri_encode(p_continuation_token, true) || '&'
      ELSE '' END
    || 'list-type=2'
    || '&max-keys=' || p_max_keys::text
    || '&prefix=' || public._aws_uri_encode(p_prefix, true);
  canonical_query := 'X-Amz-Algorithm=AWS4-HMAC-SHA256'
    || '&X-Amz-Credential=' || public._aws_uri_encode(credential, true)
    || '&X-Amz-Date=' || amz_date
    || '&X-Amz-Expires=' || p_expires::text
    || '&X-Amz-SignedHeaders=host'
    || '&' || service_query;
  canonical_headers := 'host:' || host || E'\n';
  canonical_request := 'GET' || E'\n' || canonical_uri || E'\n' || canonical_query || E'\n'
    || canonical_headers || E'\n' || 'host' || E'\n' || 'UNSIGNED-PAYLOAD';
  string_to_sign := 'AWS4-HMAC-SHA256' || E'\n' || amz_date || E'\n' || scope || E'\n'
    || encode(extensions.digest(canonical_request, 'sha256'), 'hex');
  RETURN 'https://' || host || canonical_uri || '?' || canonical_query || '&X-Amz-Signature=' || encode(
    extensions.hmac(
      convert_to(string_to_sign,'UTF8'),
      extensions.hmac(
        convert_to('aws4_request','UTF8'),
        extensions.hmac(
          convert_to('s3','UTF8'),
          extensions.hmac(
            convert_to('auto','UTF8'),
            extensions.hmac(convert_to(date_stamp,'UTF8'),convert_to('AWS4'||secret_key,'UTF8'),'sha256'),
            'sha256'
          ),'sha256'
        ),'sha256'
      ),'sha256'
    ),'hex'
  );
END;
$$;
REVOKE ALL ON FUNCTION public.catalog_r2_presign_list_page(text,text,integer,integer) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.catalog_r2_presign_list_page(text,text,integer,integer) TO service_role;
COMMENT ON FUNCTION public.catalog_r2_presign_list_page(text,text,integer,integer) IS
  'Release tooling only: short-lived R2 ListObjectsV2 URL generated from Vault without exporting credentials.';
