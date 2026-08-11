import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { parseOsrmRouteJson } from "./osrm";
import { preferRoutingProvider } from "./types";

describe("preferRoutingProvider", () => {
  it("prefers OSRM over Google", () => {
    assert.equal(
      preferRoutingProvider({
        osrmUrl: "http://127.0.0.1:5000",
        googleMapsKey: "k",
      }),
      "osrm",
    );
    assert.equal(
      preferRoutingProvider({ googleMapsKey: "k" }),
      "google_deprecated",
    );
    assert.equal(preferRoutingProvider({}), "haversine");
  });
});

describe("parseOsrmRouteJson", () => {
  it("parses geojson geometry", () => {
    const json = JSON.stringify({
      code: "Ok",
      routes: [
        {
          distance: 1500.4,
          duration: 320.2,
          geometry: {
            coordinates: [
              [31.05, -17.82],
              [31.06, -17.83],
            ],
          },
        },
      ],
    });
    const r = parseOsrmRouteJson(json);
    assert.equal(r.etaSource, "osrm");
    assert.equal(r.distanceMeters, 1500);
    assert.equal(r.points.length, 2);
    assert.equal(r.points[0].lng, 31.05);
  });
});
