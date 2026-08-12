import assert from "node:assert/strict";
import { describe, it } from "node:test";
import {
  FORBIDDEN_SEARCH_INVENTORY_KEYS,
  MEILI_IS_DISCOVERY_INDEX_ONLY,
  parseSearchCatalogResponseForTest,
  searchHitHasInventedAvailability,
  stripInventedAvailabilityFromResults,
} from "./catalog-search.ts";

describe("Meili discovery index — never invents qty (Stock/WMS §8)", () => {
  it("flags discovery-only contract", () => {
    assert.equal(MEILI_IS_DISCOVERY_INDEX_ONLY, true);
    assert.ok(FORBIDDEN_SEARCH_INVENTORY_KEYS.includes("qty"));
    assert.ok(FORBIDDEN_SEARCH_INVENTORY_KEYS.includes("saleable_qty"));
  });

  it("strips invented inventory keys from part hits", () => {
    const cleaned = stripInventedAvailabilityFromResults([
      {
        type: "part",
        oem_part_number: "1613273C10",
        qty: 99,
        saleable_qty: 5,
        in_stock: true,
        availability: "in_stock",
        category_name: "Engine",
      },
    ]);
    assert.equal(cleaned.length, 1);
    const hit = cleaned[0];
    assert.equal(hit.type, "part");
    assert.equal(hit.type === "part" && hit.oem_part_number, "1613273C10");
    assert.equal(searchHitHasInventedAvailability(hit), false);
    assert.equal(
      Object.prototype.hasOwnProperty.call(hit, "qty"),
      false,
    );
  });

  it("strips nested fitment inventory theater", () => {
    const cleaned = stripInventedAvailabilityFromResults([
      {
        type: "vehicle",
        chassis_code: "D40",
        fitments: [
          {
            type: "part",
            oem_part_number: "A",
            qty_on_hand: 3,
            qty_wh1: 1,
            qty_wh2: 2,
          },
        ],
      },
    ]);
    assert.equal(cleaned.length, 1);
    const vehicle = cleaned[0];
    assert.equal(vehicle.type, "vehicle");
    if (vehicle.type !== "vehicle") return;
    assert.equal(vehicle.fitments?.length, 1);
    assert.equal(searchHitHasInventedAvailability(vehicle), false);
  });

  it("parseSearchCatalogResponse sanitizes Meili-shaped payloads", () => {
    const parsed = parseSearchCatalogResponseForTest({
      mode: "part",
      query: "1613",
      backend: "meili",
      results: [
        {
          type: "part",
          oem_part_number: "1613273C10",
          on_hand: 12,
          stock: "in_stock",
        },
      ],
    });
    assert.ok(parsed);
    assert.equal(parsed!.backend, "meili");
    assert.equal(parsed!.results.length, 1);
    assert.equal(searchHitHasInventedAvailability(parsed!.results[0]), false);
  });
});
