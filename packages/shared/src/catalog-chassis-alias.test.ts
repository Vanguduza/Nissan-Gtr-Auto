import assert from "node:assert/strict";
import { describe, it } from "node:test";
import {
  EPC_CHASSIS_ALIASES,
  epcChassisLookupCodes,
} from "./catalog-chassis-alias.ts";

describe("epcChassisLookupCodes", () => {
  it("returns primary only when no alias", () => {
    assert.deepEqual(epcChassisLookupCodes("T31"), ["T31"]);
    assert.deepEqual(epcChassisLookupCodes(" d22 "), ["D22"]);
  });

  it("appends D40 → D22 and T32 → T31", () => {
    assert.deepEqual(epcChassisLookupCodes("D40"), ["D40", "D22"]);
    assert.deepEqual(epcChassisLookupCodes("t32"), ["T32", "T31"]);
    assert.equal(EPC_CHASSIS_ALIASES.D40, "D22");
    assert.equal(EPC_CHASSIS_ALIASES.T32, "T31");
  });

  it("returns empty for blank", () => {
    assert.deepEqual(epcChassisLookupCodes("  "), []);
  });
});
