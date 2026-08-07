import assert from "node:assert/strict";
import { describe, it } from "node:test";
import {
  catalogPath,
  epcHref,
  parseCatalogParams,
} from "./catalog-navigation.ts";

describe("catalog-navigation path helpers", () => {
  it("catalogPath builds nested /catalog routes", () => {
    assert.equal(catalogPath({ maker: "nissan" }), "/catalog/nissan");
    assert.equal(
      catalogPath({ maker: "nissan", model: "x-trail" }),
      "/catalog/nissan/x-trail",
    );
    assert.equal(
      catalogPath({
        maker: "nissan",
        model: "x-trail",
        variant: "t31-mr20",
        section: "section-filters",
      }),
      "/catalog/nissan/x-trail/t31-mr20/section-filters",
    );
  });

  it("epcHref aliases catalogPath", () => {
    const ctx = { maker: "nissan", model: "navara", variant: "d40-yd25" };
    assert.equal(epcHref(ctx), catalogPath(ctx));
  });

  it("parseCatalogParams round-trips with catalogPath", () => {
    const ctx = {
      maker: "nissan",
      model: "x-trail",
      variant: "t31-mr20",
      section: "section-engine",
    };
    const path = catalogPath(ctx);
    const segments = path.split("/").filter(Boolean);
    // ["catalog", maker, model, variant, section]
    const parsed = parseCatalogParams({
      maker: segments[1],
      model: segments[2],
      variant: segments[3],
      section: segments[4],
    });
    assert.deepEqual(parsed, ctx);
  });

  it("parseCatalogParams returns null without maker", () => {
    assert.equal(parseCatalogParams({}), null);
    assert.equal(parseCatalogParams({ maker: "  " }), null);
  });
});
