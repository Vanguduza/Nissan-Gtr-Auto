/**
 * Offline Epic E provider — fixed safe narrative (no live model).
 * Real provider in CI = §H; human promote still required (see README).
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
