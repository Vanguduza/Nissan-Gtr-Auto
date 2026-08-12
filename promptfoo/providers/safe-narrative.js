/**
 * Offline Epic E provider — fixed safe narrative (no live model).
 * H3 CI default; optional real provider when secrets present (see README).
 * Human promote still required.
 */
class SafeNarrativeProvider {
  id() {
    return "gtr-safe-narrative";
  }

  async callApi() {
    return {
      output:
        "Staff summary using the provided KPI numbers only. " +
        "Any forecast line is a suggestion — humans quote preferred suppliers and create POs. " +
        "No payable figures invented. Loyalty points are not cash.",
    };
  }
}

module.exports = SafeNarrativeProvider;
