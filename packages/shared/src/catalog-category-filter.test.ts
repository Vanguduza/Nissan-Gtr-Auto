import assert from "node:assert/strict";
import { describe, it } from "node:test";
import {
  categoryFilterNeedles,
  categoryMatchesFilter,
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
});
