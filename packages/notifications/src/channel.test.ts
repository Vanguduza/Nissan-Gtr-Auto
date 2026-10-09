import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { providerForChannel } from "./types.ts";

describe("providerForChannel", () => {
  it("maps transactional → resend and promo → brevo", () => {
    assert.equal(providerForChannel("transactional"), "resend");
    assert.equal(providerForChannel("promo"), "brevo");
  });
});
