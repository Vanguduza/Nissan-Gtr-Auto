from pathlib import Path
import re

src610 = Path(__file__).with_name("20260724061000_procurement_mutation_guards.sql").read_text(encoding="utf-8")
src620 = Path(__file__).with_name("20260724062000_rfq_quotation_mutation_guards.sql").read_text(encoding="utf-8")

funcs_610 = [
    "create_material_request",
    "submit_material_request",
    "cancel_material_request",
    "create_purchase_order",
    "submit_purchase_order",
    "cancel_purchase_order",
    "convert_material_request_to_po",
    "create_goods_receipt",
    "cancel_goods_receipt",
    "submit_goods_receipt",
    "create_landed_cost_voucher",
    "submit_landed_cost_voucher",
    "cancel_landed_cost_voucher",
    "create_blanket_purchase_order",
    "create_blanket_release",
]
funcs_620 = [
    "create_rfq",
    "submit_rfq",
    "cancel_rfq",
    "upsert_supplier_quotation",
    "submit_supplier_quotation",
    "cancel_supplier_quotation",
    "award_quotation_to_po",
]


def extract_function(src: str, name: str) -> str:
    pat = rf"(CREATE OR REPLACE FUNCTION public\.{re.escape(name)}\([\s\S]*?^\$\$;)"
    m = re.search(pat, src, re.MULTILINE)
    if not m:
        raise SystemExit(f"not found: {name}")
    return m.group(1)


def wrap_function(fn_sql: str) -> str:
    m = re.search(r"(AS \$\$\n)(.*)^(\$\$;)", fn_sql, re.MULTILINE | re.DOTALL)
    if not m:
        raise SystemExit("no body")
    header = fn_sql[: m.start(1)]
    as_marker = m.group(1)
    body = m.group(2)
    end_marker = m.group(3)

    bm = re.search(r"\bBEGIN\n", body)
    if not bm:
        raise SystemExit("no BEGIN")
    decl = body[: bm.start()]
    rest = body[bm.end() :]
    if not rest.rstrip().endswith("END;"):
        raise SystemExit(f"unexpected end: {rest[-40]!r}")

    inner = re.sub(r"\nEND;\s*$", "\n", rest)
    inner2 = re.sub(
        r"^\s*PERFORM public\._procurement_begin_rpc\(\);\n", "", inner, count=1
    )
    inner3 = re.sub(
        r"^([ \t]*)RETURN ",
        r"\1PERFORM public._procurement_end_rpc();\n\1RETURN ",
        inner2,
        flags=re.MULTILINE,
    )

    new_body = (
        decl
        + "BEGIN\n"
        + "  PERFORM public._procurement_begin_rpc();\n"
        + inner3
        + "EXCEPTION\n"
        + "  WHEN OTHERS THEN\n"
        + "    PERFORM public._procurement_end_rpc();\n"
        + "    RAISE;\n"
        + "END;\n"
    )
    return header + as_marker + new_body + end_marker


out: list[str] = []
out.append(
    """-- Phase 8c follow-up: clear app.procurement_rpc after SECURITY DEFINER RPCs.
-- Transaction-local GUC leaked across RPC returns inside long smoke DO blocks.
-- Nested RPC→RPC (convert_material_request_to_po / award_quotation_to_po → create_purchase_order)
-- uses app.procurement_rpc_depth so only the outermost end clears the flag.

CREATE OR REPLACE FUNCTION public._procurement_begin_rpc()
RETURNS void
LANGUAGE plpgsql
AS $$
DECLARE
  v_depth integer;
BEGIN
  v_depth := COALESCE(NULLIF(current_setting('app.procurement_rpc_depth', true), '')::integer, 0) + 1;
  PERFORM set_config('app.procurement_rpc_depth', v_depth::text, true);
  PERFORM set_config('app.procurement_rpc', '1', true);
END;
$$;

CREATE OR REPLACE FUNCTION public._procurement_end_rpc()
RETURNS void
LANGUAGE plpgsql
AS $$
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
$$;

REVOKE ALL ON FUNCTION public._procurement_begin_rpc() FROM PUBLIC;
REVOKE ALL ON FUNCTION public._procurement_end_rpc() FROM PUBLIC;

"""
)

for name in funcs_610:
    out.append(wrap_function(extract_function(src610, name)))
    out.append("\n\n")

for name in funcs_620:
    out.append(wrap_function(extract_function(src620, name)))
    out.append("\n\n")

path = Path(__file__).with_name("20260724063000_procurement_end_rpc.sql")
path.write_text("".join(out).rstrip() + "\n", encoding="utf-8")
print(f"wrote {path} ({path.stat().st_size} bytes)")
text = path.read_text(encoding="utf-8")
print("begin calls", text.count("_procurement_begin_rpc"))
print("end calls", text.count("_procurement_end_rpc"))
print("EXCEPTION WHEN OTHERS", text.count("WHEN OTHERS THEN"))
