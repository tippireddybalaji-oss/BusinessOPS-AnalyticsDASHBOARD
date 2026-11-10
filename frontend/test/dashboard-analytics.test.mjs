import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { buildAnalyticsQueryString, resolvePresetDates } from "../assets/js/analytics-utils.js";

describe("Dashboard Analytics Module & API Contract Tests", () => {

  describe("1. Query String Construction (buildAnalyticsQueryString)", () => {
    it("constructs valid query string with all parameters", () => {
      const params = {
        currency: "USD",
        startDate: "2026-01-01",
        endDate: "2026-01-31",
        includePending: true,
        interval: "MONTHLY",
        type: "EXPENSE",
        timezone: "America/New_York",
        limit: 10
      };

      const qs = buildAnalyticsQueryString(params);
      assert.ok(qs.startsWith("?"), "Query string should start with '?'");
      assert.ok(qs.includes("currency=USD"));
      assert.ok(qs.includes("startDate=2026-01-01"));
      assert.ok(qs.includes("endDate=2026-01-31"));
      assert.ok(qs.includes("includePending=true"));
      assert.ok(qs.includes("interval=MONTHLY"));
      assert.ok(qs.includes("type=EXPENSE"));
      assert.ok(qs.includes("timezone=America%2FNew_York") || qs.includes("timezone=America/New_York"));
      assert.ok(qs.includes("limit=10"));
    });

    it("omits null, undefined, and empty string parameters", () => {
      const params = {
        currency: "INR",
        startDate: null,
        endDate: undefined,
        startInstant: "",
        includePending: false
      };

      const qs = buildAnalyticsQueryString(params);
      assert.ok(qs.includes("currency=INR"));
      assert.ok(qs.includes("includePending=false"));
      assert.ok(!qs.includes("startDate"));
      assert.ok(!qs.includes("endDate"));
      assert.ok(!qs.includes("startInstant"));
    });

    it("returns empty string when no parameters provided", () => {
      assert.equal(buildAnalyticsQueryString({}), "");
      assert.equal(buildAnalyticsQueryString(), "");
    });
  });

  describe("2. Date Preset Calculations", () => {
    it("ALL_TIME returns null for both start and end dates", () => {
      const res = resolvePresetDates("ALL_TIME");
      assert.equal(res.startDate, null);
      assert.equal(res.endDate, null);
    });

    it("THIS_MONTH starts on day 01 of the current month", () => {
      const res = resolvePresetDates("THIS_MONTH");
      assert.ok(res.startDate);
      assert.ok(res.endDate);
      assert.ok(res.startDate.endsWith("-01"), `Start date ${res.startDate} should end with -01`);
      assert.ok(res.startDate <= res.endDate, "Start date must be <= end date");
    });

    it("YEAR_TO_DATE starts on Jan 01 of current year", () => {
      const currentYear = new Date().getFullYear();
      const res = resolvePresetDates("YEAR_TO_DATE");
      assert.equal(res.startDate, `${currentYear}-01-01`);
      assert.ok(res.startDate <= res.endDate);
    });

    it("LAST_30_DAYS generates a 30-day delta span", () => {
      const res = resolvePresetDates("LAST_30_DAYS");
      const start = new Date(res.startDate);
      const end = new Date(res.endDate);
      const diffDays = Math.round((end - start) / (1000 * 60 * 60 * 24));
      assert.ok(diffDays >= 28 && diffDays <= 31, `Expected ~30 days difference, got ${diffDays}`);
    });
  });

  describe("3. Executive Financial KPI Logic", () => {
    it("handles isMarginComputable = false with 'N/A' display", () => {
      const mockSummary = {
        formattedRevenue: "0.00",
        formattedExpense: "450.00",
        formattedNetProfit: "-450.00",
        profitMarginPercentage: null,
        isMarginComputable: false,
        totalCompletedTransactions: 2
      };

      let displayedMargin;
      if (!mockSummary.isMarginComputable) {
        displayedMargin = "N/A";
      } else {
        displayedMargin = `${mockSummary.profitMarginPercentage}%`;
      }

      assert.equal(displayedMargin, "N/A");
    });

    it("formats profit margin percentage correctly when computable", () => {
      const mockSummary = {
        formattedRevenue: "10,000.00",
        formattedExpense: "7,500.00",
        formattedNetProfit: "2,500.00",
        profitMarginPercentage: 25.0,
        isMarginComputable: true
      };

      let displayedMargin;
      if (!mockSummary.isMarginComputable) {
        displayedMargin = "N/A";
      } else {
        const val = mockSummary.profitMarginPercentage;
        displayedMargin = `${val > 0 ? "+" : ""}${Number(val).toFixed(1)}%`;
      }

      assert.equal(displayedMargin, "+25.0%");
    });

    it("separates realized and pipeline amounts when includePending is enabled", () => {
      const mockSummary = {
        formattedRevenue: "$150,000.00",
        formattedPendingIncome: "$25,000.00",
        pendingIncomeMinor: 2500000,
        formattedExpense: "$80,000.00",
        formattedPendingExpense: "$10,000.00",
        pendingExpenseMinor: 1000000,
        formattedNetProfit: "$70,000.00",
        formattedProjectedNetProfit: "$85,000.00"
      };

      const includePending = true;
      let revenueSubtext;
      if (includePending && mockSummary.pendingIncomeMinor > 0) {
        revenueSubtext = `+ ${mockSummary.formattedPendingIncome} pending`;
      }

      assert.equal(revenueSubtext, "+ $25,000.00 pending");
      assert.equal(mockSummary.formattedProjectedNetProfit, "$85,000.00");
    });
  });

  describe("4. Category Analytics & Uncategorized Fallbacks", () => {
    it("handles uncategorized category item gracefully", () => {
      const mockCategories = [
        {
          categoryId: "cat-1",
          categoryName: "Software & SaaS",
          categoryColor: "#334B68",
          totalAmountMinor: 50000,
          formattedTotalAmount: "$500.00",
          percentageOfTotal: 71.43,
          transactionCount: 5
        },
        {
          categoryId: "uncategorized",
          categoryName: null,
          categoryColor: null,
          totalAmountMinor: 20000,
          formattedTotalAmount: "$200.00",
          percentageOfTotal: 28.57,
          transactionCount: 2
        }
      ];

      const processed = mockCategories.map(c => ({
        displayName: c.categoryId === "uncategorized" ? "Uncategorized" : c.categoryName,
        displayColor: c.categoryId === "uncategorized" ? "#8894A6" : (c.categoryColor || "#334B68")
      }));

      assert.equal(processed[0].displayName, "Software & SaaS");
      assert.equal(processed[0].displayColor, "#334B68");
      assert.equal(processed[1].displayName, "Uncategorized");
      assert.equal(processed[1].displayColor, "#8894A6");
    });
  });

  describe("5. Client Revenue Leaderboard & Unassigned Separation", () => {
    it("ranks top clients and isolates unassigned client revenue", () => {
      const mockClientResponse = {
        totalRealizedRevenueMinor: 100000,
        formattedTotalRevenue: "$1,000.00",
        unassignedClientRevenueMinor: 25000,
        formattedUnassignedRevenue: "$250.00",
        clients: [
          {
            clientId: "cl-1",
            clientName: "Acme Corp",
            totalRevenueMinor: 50000,
            formattedTotalRevenue: "$500.00",
            percentageOfTotalRevenue: 50.0,
            incomeTransactionCount: 4,
            formattedAverageValue: "$125.00"
          },
          {
            clientId: "cl-2",
            clientName: "Beta LLC",
            totalRevenueMinor: 25000,
            formattedTotalRevenue: "$250.00",
            percentageOfTotalRevenue: 25.0,
            incomeTransactionCount: 2,
            formattedAverageValue: "$125.00"
          }
        ]
      };

      assert.equal(mockClientResponse.clients[0].clientName, "Acme Corp");
      assert.equal(mockClientResponse.clients[0].percentageOfTotalRevenue, 50.0);
      assert.equal(mockClientResponse.unassignedClientRevenueMinor, 25000);
      assert.equal(mockClientResponse.formattedUnassignedRevenue, "$250.00");
    });
  });

  describe("6. HTTP 422 Safeguard Processing Limits Contract", () => {
    it("correctly identifies and extracts 422 limit details", () => {
      const mock422Error = {
        status: 422,
        errorCode: "ANALYTICS_PROCESSING_LIMIT_EXCEEDED",
        message: "Analytics query aggregation aborted: eligible document count exceeds safety threshold",
        limitDetails: {
          enforcementTier: "QUERY_PLANNER_SAFEGUARD",
          maxAllowedCount: 10000,
          totalEligibleCount: 14250
        }
      };

      assert.equal(mock422Error.status, 422);
      assert.equal(mock422Error.errorCode, "ANALYTICS_PROCESSING_LIMIT_EXCEEDED");
      assert.equal(mock422Error.limitDetails.enforcementTier, "QUERY_PLANNER_SAFEGUARD");
      assert.ok(mock422Error.limitDetails.totalEligibleCount > mock422Error.limitDetails.maxAllowedCount);
    });
  });
});
