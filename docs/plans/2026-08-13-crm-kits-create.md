# CRM kits create / list (staff)

- Status: P0 implemented (2026-08-13)
- Date: 2026-08-13
- Lane(s): `@backend_agent` (migration/RPCs) → `@web_agent` (`/staff/crm/kits`)
- Extends: kits SoR `supabase/migrations/20260724121000_kits_bom_sell.sql`; CRM pattern `/staff/crm/product-pages`

## Locked decisions

| Decision | Choice |
|----------|--------|
| Roles | **admin \| sales \| warehouse** may create/update/add/remove kits |
| Kit OEM | **Manual** staff entry (e.g. `KIT-…`) → `stock_items.oem_part_number` |
| Title | → `stock_items.description` |
| Fitment | Optional **chassis picker** → `part_fitment` row for kit OEM (not free-text SoR) |
| sell_mode | Default **`explode`** |
| Component qty | Default **1**, UOM from component `base_uom_id` / **EA** |
| After create | **List + create/edit** on CRM kits surface |

## RPCs

| RPC | Notes |
|-----|--------|
| `create_kit_with_components(oem, title, components jsonb, chassis?, sell_mode?)` | Composite create; enforces ≥2 unique components |
| `create_item_kit` / `update_item_kit` / `add_kit_component` / `remove_kit_component` | Widened via `_require_kit_staff()`; `update_item_kit` accepts optional `p_title`; remove enforces ≥2 remaining |
| `_require_kit_staff()` | admin \| sales \| warehouse |

Migration: `supabase/migrations/20260813120000_crm_kits_staff_create.sql` (includes RLS WITH CHECK widen for `item_kits` / `item_kit_components`).

## P0 done checklist

- [x] Migration widens kit mutation roles + composite create + min-2 components
- [x] Optional chassis → `part_fitment`
- [x] Nav CRM → Kits → `/staff/crm/kits` (admin/sales/warehouse)
- [x] Staff UI: list + create + edit (title/active/components)
- [x] No ZIMRA / payroll tax / HTML5 QR

## Smoke test

1. Apply migration (`supabase db reset` or migrate).
2. Sign in as sales or admin → **CRM → Kits**.
3. Create kit: title, OEM `KIT-TEST-1`, pick 2 catalog parts, optional chassis → Create.
4. Confirm row in list; Edit title / toggle active / add third part / try remove below 2 (blocked).
5. Customer `/kits` shows active kit when storefront list is wired.
