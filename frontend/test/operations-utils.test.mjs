import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { amountFromMinor, buildQueryString, escapeHtml, toMinorUnits } from "../assets/js/operations-utils.js";

describe("Milestone 4 operation helpers", () => {
  it("builds paginated filter queries and omits empty filters", () => {
    const query = buildQueryString({ page: 0, size: 10, status: "ACTIVE", search: "", type: null });
    assert.equal(query, "?page=0&size=10&status=ACTIVE");
  });

  it("escapes untrusted API fields before table rendering", () => {
    assert.equal(escapeHtml('<img src=x onerror="alert(1)">'), "&lt;img src=x onerror=&quot;alert(1)&quot;&gt;");
  });

  it("converts standard-currency values precisely into minor units", () => {
    assert.equal(toMinorUnits("1250.50", 2), 125050);
    assert.equal(toMinorUnits("10.555", 2), null);
    assert.equal(toMinorUnits("0", 2), null);
    assert.equal(amountFromMinor(125050, 2), "1250.50");
  });

  it("supports zero-decimal currency input without accepting fractions", () => {
    assert.equal(toMinorUnits("3500", 0), 3500);
    assert.equal(toMinorUnits("3500.5", 0), null);
  });
});
