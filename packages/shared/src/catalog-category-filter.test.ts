import assert from "node:assert/strict";
import { describe, it } from "node:test";
import {
  categoryFilterNeedles,
  categoryMatchesFilter,
  effectiveCategoryFilter,
  resolveMerchandisingNode,
  shopCategoryFacetOptions,
  stripEpcVehicleSuffix,
} from "./catalog-category-filter.ts";

describe("categoryFilterNeedles", () => {
  it("expands merchandising plurals to EPC stems", () => {
    const needles = categoryFilterNeedles("brakes");
    assert.ok(needles.includes("brakes"));
    assert.ok(needles.includes("brake"));
  });

  it("maps Android-style Braking tile to brake", () => {
    assert.ok(categoryFilterNeedles("Braking").includes("brake"));
  });

  it("expands suspension leaf to ball-joint stems", () => {
    const needles = categoryFilterNeedles("ball-joints");
    assert.ok(needles.some((n) => n.includes("ball")));
    assert.ok(needles.some((n) => n.includes("joint")));
  });
});

describe("stripEpcVehicleSuffix", () => {
  it("strips Megazip for-vehicle suffixes", () => {
    assert.equal(
      stripEpcVehicleSuffix(
        "Accelerator linkage for 1997–2000 Nissan FRONTIER (D22)",
      ),
      "Accelerator linkage",
    );
    assert.equal(
      stripEpcVehicleSuffix("WIRING FOR 2004 - 2011 NISSAN ALTIMA (L31)"),
      "WIRING",
    );
  });

  it("leaves clean assembly names alone", () => {
    assert.equal(
      stripEpcVehicleSuffix("BRAKE PIPING & CONTROL"),
      "BRAKE PIPING & CONTROL",
    );
  });
});

describe("resolveMerchandisingNode / shopCategoryFacetOptions", () => {
  it("resolves top nav suspension to parent with curated leaves", () => {
    const node = resolveMerchandisingNode("suspension");
    assert.ok(node);
    assert.equal(node!.parent.slug, "suspension");
    assert.equal(node!.sub, undefined);
    const facets = shopCategoryFacetOptions("suspension");
    const labels = facets.map((f) => f.label.toLowerCase());
    assert.ok(labels.some((l) => l.includes("ball joint")));
    assert.ok(labels.some((l) => l.includes("tie rod")));
    assert.ok(labels.some((l) => l.includes("control arm")));
    assert.ok(!labels.some((l) => l.includes("accelerator")));
  });

  it("returns parent tiles when no filter is active", () => {
    const facets = shopCategoryFacetOptions(null);
    assert.ok(facets.some((f) => f.slug === "brakes"));
    assert.ok(facets.some((f) => f.slug === "suspension"));
  });

  it("returns sibling leaves when a subcategory is selected", () => {
    const facets = shopCategoryFacetOptions("ball-joints");
    assert.ok(facets.some((f) => f.slug === "control-arms"));
    assert.ok(facets.every((f) => !f.label.toLowerCase().includes("accelerator")));
  });

  it("uses cat parent for facets when sub is also set", () => {
    const facets = shopCategoryFacetOptions("suspension", "ball-joints");
    assert.ok(facets.some((f) => f.slug === "shock-absorbers"));
  });

  it("returns empty facets for unknown filters (no EPC dump)", () => {
    assert.deepEqual(shopCategoryFacetOptions("not-a-real-category"), []);
  });

  it("maps drivetrain synonym to transmission parent", () => {
    const node = resolveMerchandisingNode("drivetrain");
    assert.equal(node?.parent.slug, "transmission");
  });
});

describe("effectiveCategoryFilter", () => {
  it("prefers subcategory over parent", () => {
    assert.equal(
      effectiveCategoryFilter("suspension", "ball-joints"),
      "ball-joints",
    );
    assert.equal(effectiveCategoryFilter("suspension", null), "suspension");
  });
});

describe("categoryMatchesFilter", () => {
  it("matches shop slug against EPC assembly group", () => {
    assert.equal(
      categoryMatchesFilter("brakes", "BRAKE PIPING & CONTROL"),
      true,
    );
    assert.equal(categoryMatchesFilter("filters", "AIR CLEANER"), true);
    assert.equal(categoryMatchesFilter("engine", "ENGINE ASSEMBLY"), true);
  });

  it("matches exact fixture labels case-insensitively", () => {
    assert.equal(categoryMatchesFilter("brakes", "Brakes"), true);
    assert.equal(categoryMatchesFilter("Cooling", "Cooling"), true);
  });

  it("matches subcategory when category is generic", () => {
    assert.equal(
      categoryMatchesFilter("filters", "Uncategorized", "Oil filter element"),
      true,
    );
  });

  it("rejects unrelated categories", () => {
    assert.equal(
      categoryMatchesFilter("brakes", "ENGINE ASSEMBLY", "Timing chain"),
      false,
    );
  });

  it("matches full facet labels from the PLP", () => {
    assert.equal(
      categoryMatchesFilter(
        "brake piping & control",
        "BRAKE PIPING & CONTROL",
      ),
      true,
    );
  });

  it("matches suspension leaves against stripped Megazip names", () => {
    assert.equal(
      categoryMatchesFilter(
        "ball-joints",
        "Ball joint for 1997–2000 Nissan FRONTIER (D22)",
      ),
      true,
    );
    assert.equal(
      categoryMatchesFilter(
        "suspension",
        "Accelerator linkage for 1997–2000 Nissan FRONTIER (D22)",
      ),
      false,
    );
  });

  it("matches control arms stem under suspension parent browse", () => {
    assert.equal(
      categoryMatchesFilter("control-arms", "FRONT SUSPENSION", "CONTROL ARM"),
      true,
    );
  });
});
