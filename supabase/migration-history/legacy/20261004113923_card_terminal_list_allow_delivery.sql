-- Card machines admin: show whether a machine may be used by drivers for card on delivery
-- (`allow_delivery`, set with `set_pos_card_terminal_delivery_enabled`). The column is appended,
-- so callers reading the other columns by name are unaffected.
DROP FUNCTION IF EXISTS public.list_pos_card_terminals(uuid, text);

CREATE FUNCTION public.list_pos_card_terminals(p_warehouse_id uuid DEFAULT NULL::uuid, p_device_id text DEFAULT NULL::text)
 RETURNS TABLE(id uuid, code text, label text, acquirer_name text, external_terminal_id text, adapter_key text, adapter_config jsonb, warehouse_id uuid, device_id text, is_active boolean, allow_delivery boolean)
 LANGUAGE sql
 STABLE SECURITY DEFINER
 SET search_path TO ''
AS $function$
 SELECT t.id,t.code,t.label,t.acquirer_name,t.external_terminal_id,t.adapter_key,t.adapter_config,t.warehouse_id,t.device_id,t.is_active,t.allow_delivery
 FROM public.pos_card_terminals t
 WHERE public.is_staff() AND t.is_active
  AND (p_warehouse_id IS NULL OR t.warehouse_id IS NULL OR t.warehouse_id=p_warehouse_id)
  AND (p_device_id IS NULL OR t.device_id IS NULL OR t.device_id=p_device_id)
 ORDER BY t.label,t.code;
$function$;

REVOKE ALL ON FUNCTION public.list_pos_card_terminals(uuid, text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.list_pos_card_terminals(uuid, text) TO authenticated, service_role;
