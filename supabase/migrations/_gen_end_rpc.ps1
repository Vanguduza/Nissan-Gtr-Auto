$ErrorActionPreference = 'Stop'
$dir = 'c:\Users\j\Desktop\nissan gtr\supabase\migrations'
$src610 = Get-Content -Raw (Join-Path $dir '20260724061000_procurement_mutation_guards.sql')
$src620 = Get-Content -Raw (Join-Path $dir '20260724062000_rfq_quotation_mutation_guards.sql')

$funcs610 = @(
  'create_material_request','submit_material_request','cancel_material_request',
  'create_purchase_order','submit_purchase_order','cancel_purchase_order',
  'convert_material_request_to_po','create_goods_receipt','cancel_goods_receipt',
  'submit_goods_receipt','create_landed_cost_voucher','submit_landed_cost_voucher',
  'cancel_landed_cost_voucher','create_blanket_purchase_order','create_blanket_release'
)
$funcs620 = @(
  'create_rfq','submit_rfq','cancel_rfq','upsert_supplier_quotation',
  'submit_supplier_quotation','cancel_supplier_quotation','award_quotation_to_po'
)

function Extract-Function([string]$src, [string]$name) {
  $pat = "(?ms)(CREATE OR REPLACE FUNCTION public\.$([regex]::Escape($name))\([\s\S]*?^\$\$;)"
  $m = [regex]::Match($src, $pat)
  if (-not $m.Success) { throw "not found: $name" }
  return $m.Groups[1].Value
}

function Wrap-Function([string]$fnSql) {
  $m = [regex]::Match($fnSql, '(?ms)(AS \$\$\n)(.*)^(\$\$;)')
  if (-not $m.Success) { throw 'no body' }
  $header = $fnSql.Substring(0, $m.Groups[1].Index)
  $asMarker = $m.Groups[1].Value
  $body = $m.Groups[2].Value
  $endMarker = $m.Groups[3].Value

  $bm = [regex]::Match($body, '\bBEGIN\n')
  if (-not $bm.Success) { throw 'no BEGIN' }
  $decl = $body.Substring(0, $bm.Index)
  $rest = $body.Substring($bm.Index + $bm.Length)
  if (-not $rest.TrimEnd().EndsWith('END;')) { throw "unexpected end" }

  $inner = [regex]::Replace($rest, '\nEND;\s*$', "`n")
  $inner2 = [regex]::Replace($inner, '^\s*PERFORM public\._procurement_begin_rpc\(\);\n', '', 1)
  $inner3 = [regex]::Replace($inner2, '(?m)^([ \t]*)RETURN ', '${1}PERFORM public._procurement_end_rpc();`n${1}RETURN ')

  $newBody = $decl + "BEGIN`n" + "  PERFORM public._procurement_begin_rpc();`n" + $inner3 +
    "EXCEPTION`n" + "  WHEN OTHERS THEN`n" + "    PERFORM public._procurement_end_rpc();`n" +
    "    RAISE;`n" + "END;`n"
  return $header + $asMarker + $newBody + $endMarker
}

$sb = New-Object System.Text.StringBuilder
[void]$sb.Append(@"
-- Phase 8c follow-up: clear app.procurement_rpc after SECURITY DEFINER RPCs.
-- Transaction-local GUC leaked across RPC returns inside long smoke DO blocks.
-- Nested RPC→RPC (convert_material_request_to_po / award_quotation_to_po → create_purchase_order)
-- uses app.procurement_rpc_depth so only the outermost end clears the flag.

CREATE OR REPLACE FUNCTION public._procurement_begin_rpc()
RETURNS void
LANGUAGE plpgsql
AS `$`$
DECLARE
  v_depth integer;
BEGIN
  v_depth := COALESCE(NULLIF(current_setting('app.procurement_rpc_depth', true), '')::integer, 0) + 1;
  PERFORM set_config('app.procurement_rpc_depth', v_depth::text, true);
  PERFORM set_config('app.procurement_rpc', '1', true);
END;
`$`$;

CREATE OR REPLACE FUNCTION public._procurement_end_rpc()
RETURNS void
LANGUAGE plpgsql
AS `$`$
DECLARE
  v_depth integer;
BEGIN
  v_depth := COALESCE(NULLIF(current_setting('app.procurement_rpc_depth', true), '')::integer, 0);
  IF v_depth <= 1 THEN
    PERFORM set_config('app.procurement_rpc_depth', '0', true);
    PERFORM set_config('app.procurement_rpc', '', true);
  ELSE
    PERFORM set_config('app.procurement_rpc_depth', (v_depth - 1)::text, true);
  END IF;
END;
`$`$;

REVOKE ALL ON FUNCTION public._procurement_begin_rpc() FROM PUBLIC;
REVOKE ALL ON FUNCTION public._procurement_end_rpc() FROM PUBLIC;

"@)

foreach ($name in $funcs610) {
  [void]$sb.Append((Wrap-Function (Extract-Function $src610 $name)))
  [void]$sb.Append("`n`n")
}
foreach ($name in $funcs620) {
  [void]$sb.Append((Wrap-Function (Extract-Function $src620 $name)))
  [void]$sb.Append("`n`n")
}

$outPath = Join-Path $dir '20260724063000_procurement_end_rpc.sql'
$text = $sb.ToString().TrimEnd() + "`n"
[System.IO.File]::WriteAllText($outPath, $text)
Write-Output "wrote $outPath ($($text.Length) chars)"
Write-Output "begin=$([regex]::Matches($text, '_procurement_begin_rpc').Count) end=$([regex]::Matches($text, '_procurement_end_rpc').Count) others=$([regex]::Matches($text, 'WHEN OTHERS THEN').Count)"
