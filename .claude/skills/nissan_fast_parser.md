# Nissan FAST EPC Parser

> **Trigger:** Load only when the task touches Nissan FAST EPC data parsing, VIN decoding, PNC/OEM part number mapping, or the `data-pipeline/` Nissan ingestion scripts.

## Overview

Nissan FAST (Factory Automotive Service Tool) EPC data provides the definitive relational mapping between VINs, chassis codes, PNC codes, and OEM part numbers. This skill guides reverse-engineering and parsing that data into our relational hierarchy.

## Relational Hierarchy

```
Model Variant (e.g., "GT-R R35 2024")
  └── Engine/Chassis Code (e.g., VR38DETT / R35)
        └── Sub-Assembly (e.g., "Cooling System" / PNC 21010)
              └── Diagram (exploded view with bounding boxes)
                    └── OEM Part Number (10-digit, e.g., 21410-JF00A)
```

## VIN Decoding

| VIN Position | Field | Example |
|-------------|-------|---------|
| 1-3 | WMI (World Manufacturer ID) | JN1 |
| 4-8 | Vehicle attributes | — |
| 9 | Check digit | — |
| 10 | Model year | R = 2024 |
| 11 | Plant code | — |
| 12-17 | Serial number | — |

Map VIN prefix → `vehicle_master` row:
- `vin_prefix`, `chassis_code`, `engine_code`, `production_year`, `model_variant`

## PNC (Part Name Code)

5-digit codes identifying sub-assemblies:
- `21010` = Water Pump
- `21410` = Radiator
- `41010` = Brake Pad

Store in `pnc_categories`: `pnc_code`, `category_name`, `subcategory_name`.

## OEM Part Numbers

10-digit Nissan format: `XXXXX-XXXXX` (e.g., `21410-JF00A`).
- Supersession chain: `part_fitment.superseded_by` → newer OEM number.
- Search must resolve superseded parts to current replacements.

## Parsing Approach

1. Extract raw FAST data via `nissan-epc`-style scripts or reverse-engineered exports.
2. Parse into intermediate JSON conforming to `data-pipeline/schemas/`.
3. Validate against JSON schema.
4. Batch import into `vehicle_master`, `pnc_categories`, `part_fitment` tables.
5. Index in Meilisearch for 4-way search.

## Key Tables

```sql
-- vehicle_master: VIN decoder
vin_prefix, chassis_code, engine_code, production_year, model_variant

-- pnc_categories: visual navigation
pnc_code, category_name, subcategory_name

-- part_fitment: the fitment ledger
oem_part_number, pnc_code, chassis_code, engine_code, superseded_by
```
