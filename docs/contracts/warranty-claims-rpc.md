# Warranty claims RPC contract (Phase 5b)

Management app (Phase 12) calls these Supabase RPCs. No fiscal / warranty-authority payloads.

## `open_warranty_claim`

| Param | Type | Notes |
|-------|------|--------|
| `p_stock_serial_id` | UUID? | At least one of serial or invoice required |
| `p_sales_invoice_id` | UUID? | Posted `invoice` doc only |
| `p_stock_batch_id` | UUID? | Optional batch hint |
| `p_notes` | text? | |

**Returns:** claim UUID. **Errors:** missing linkage; serial `quarantine`/`scrapped`; duplicate open claim on serial.

## `approve_warranty_claim`

| Param | Type | Notes |
|-------|------|--------|
| `p_claim_id` | UUID | Must be `open` |
| `p_resolution` | enum | `replacement` \| `credit_note` \| `return_only` |
| `p_lines` | JSONB? | Return/CN lines: `{stock_item_id, uom_id, qty, unit_price?, valuation_method?}` |
| `p_replacement_lines` | JSONB? | Required for `replacement`; `{stock_item_id, uom_id, qty, valuation_method?, replacement_serial_id?}` |

**Returns:** claim UUID. **Side effects:**

- `return_only` / `replacement`: `post_return_to_quarantine` → pending QUAR transfer (never MAIN saleable return).
- `credit_note`: `post_return_credit_note` only (CN quarantine stock path).
- `replacement`: also `post_stock_issue` from saleable warehouse.

## `reject_warranty_claim`

| Param | Type |
|-------|------|
| `p_claim_id` | UUID |
| `p_reason` | text? |

**Returns:** claim UUID. Status `open` → `rejected` (`reject_only`).

## `close_warranty_claim`

| Param | Type |
|-------|------|
| `p_claim_id` | UUID |

**Returns:** claim UUID. Idempotent if already `closed`. From `approved` \| `rejected` only.

## Related

- `post_stock_issue(from_wh, notes, lines)` — replacement outbound (ISS-).
- Domain events: `warranty_claim_*`; stock side reuses `quarantine_received`, `serial_moved`, `return_*`.
