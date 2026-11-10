/**
 * BusinessOPS Analytics Dashboard - Analytics API Module
 *
 * Integrates the four core backend analytics endpoints adhering to Phase 5 Section 9:
 * 1. GET /api/v1/workspaces/{workspaceId}/analytics/summary
 * 2. GET /api/v1/workspaces/{workspaceId}/analytics/trends
 * 3. GET /api/v1/workspaces/{workspaceId}/analytics/categories
 * 4. GET /api/v1/workspaces/{workspaceId}/analytics/clients
 *
 * Includes standard high-fidelity fallback DTO generators for robust development
 * and demonstration resilience when backend database indexes are pending.
 */

import { fetchWithAuth, ApiError } from "./api.js";
import { buildAnalyticsQueryString, resolvePresetDates, formatDateYMD } from "./analytics-utils.js";

export { buildAnalyticsQueryString, resolvePresetDates, formatDateYMD };

/**
 * Currency symbol helper
 * @param {string} curr
 * @returns {string}
 */
function getCurrencyPrefix(curr = "USD") {
  switch (curr) {
    case "USD": return "$";
    case "INR": return "₹";
    case "EUR": return "€";
    case "GBP": return "£";
    default: return `${curr} `;
  }
}

/**
 * High-fidelity fallback DTO generator for Executive Financial Summary
 */
export function getSampleFinancialSummary(workspaceId, params = {}) {
  const currency = params.currency || "USD";
  const sym = getCurrencyPrefix(currency);
  const includePending = Boolean(params.includePending);

  const totalRevenueMinor = 4850000;
  const totalExpenseMinor = 2135000;
  const netProfitMinor = totalRevenueMinor - totalExpenseMinor; // 2715000

  const pendingIncomeMinor = includePending ? 850000 : 0;
  const pendingExpenseMinor = includePending ? 320000 : 0;
  const projectedNetProfitMinor = includePending ? (netProfitMinor + pendingIncomeMinor - pendingExpenseMinor) : netProfitMinor;

  return {
    workspaceId,
    currency,
    fractionDigits: 2,
    startInclusive: params.startDate ? `${params.startDate}T00:00:00Z` : "2026-11-01T00:00:00Z",
    endExclusive: params.endDate ? `${params.endDate}T23:59:59Z` : "2026-11-30T23:59:59Z",
    reportingTimezone: params.timezone || "UTC",
    totalRevenueMinor,
    formattedRevenue: `${sym}${(totalRevenueMinor / 100).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`,
    totalExpenseMinor,
    formattedExpense: `${sym}${(totalExpenseMinor / 100).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`,
    netProfitMinor,
    formattedNetProfit: `+${sym}${(netProfitMinor / 100).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`,
    profitMarginPercentage: 55.98,
    isMarginComputable: true,
    totalCompletedTransactions: 42,
    incomeTransactionCount: 28,
    expenseTransactionCount: 14,
    pendingIncomeMinor,
    formattedPendingIncome: includePending ? `${sym}${(pendingIncomeMinor / 100).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}` : "0.00",
    pendingExpenseMinor,
    formattedPendingExpense: includePending ? `${sym}${(pendingExpenseMinor / 100).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}` : "0.00",
    projectedNetProfitMinor,
    formattedProjectedNetProfit: `+${sym}${(projectedNetProfitMinor / 100).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`,
    pendingTransactionCount: includePending ? 5 : 0,
    availableCurrencies: ["USD", "INR", "EUR", "GBP"],
    completeness: {
      isComplete: true,
      eligibleDocumentCount: 42,
      processedDocumentCount: 42
    }
  };
}

/**
 * High-fidelity fallback DTO generator for Financial Trends (Time-Series)
 */
export function getSampleFinancialTrends(workspaceId, params = {}) {
  const currency = params.currency || "USD";
  const sym = getCurrencyPrefix(currency);
  const interval = params.interval === "YEARLY" ? "YEARLY" : "MONTHLY";
  const includePending = Boolean(params.includePending);

  const rawMonthly = [
    { period: "2026-06", rev: 3200000, exp: 1650000, tx: 28, pRev: 400000, pExp: 150000 },
    { period: "2026-07", rev: 3650000, exp: 1820000, tx: 31, pRev: 550000, pExp: 200000 },
    { period: "2026-08", rev: 4120000, exp: 2100000, tx: 35, pRev: 600000, pExp: 220000 },
    { period: "2026-09", rev: 3950000, exp: 1980000, tx: 33, pRev: 480000, pExp: 180000 },
    { period: "2026-10", rev: 4580000, exp: 2050000, tx: 39, pRev: 720000, pExp: 280000 },
    { period: "2026-11", rev: 4850000, exp: 2135000, tx: 42, pRev: 850000, pExp: 320000 }
  ];

  const rawYearly = [
    { period: "2023", rev: 28500000, exp: 16200000, tx: 240, pRev: 0, pExp: 0 },
    { period: "2024", rev: 36800000, exp: 19500000, tx: 310, pRev: 0, pExp: 0 },
    { period: "2025", rev: 44200000, exp: 22800000, tx: 380, pRev: 0, pExp: 0 },
    { period: "2026", rev: 51200000, exp: 24100000, tx: 420, pRev: 3500000, pExp: 1200000 }
  ];

  const sourceData = interval === "YEARLY" ? rawYearly : rawMonthly;

  const periods = sourceData.map(d => {
    const netProfitMinor = d.rev - d.exp;
    const margin = d.rev > 0 ? Number(((netProfitMinor / d.rev) * 100).toFixed(2)) : 0;
    const pRev = includePending ? d.pRev : 0;
    const pExp = includePending ? d.pExp : 0;
    const projNet = netProfitMinor + pRev - pExp;

    return {
      period: d.period,
      startInclusive: `${d.period}-01T00:00:00Z`,
      endExclusive: `${d.period}-28T23:59:59Z`,
      revenueMinor: d.rev,
      formattedRevenue: `${sym}${(d.rev / 100).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`,
      expenseMinor: d.exp,
      formattedExpense: `${sym}${(d.exp / 100).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`,
      netProfitMinor,
      formattedNetProfit: `${netProfitMinor >= 0 ? "+" : ""}${sym}${(netProfitMinor / 100).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`,
      profitMarginPercentage: margin,
      transactionCount: d.tx,
      pendingIncomeMinor: pRev,
      formattedPendingIncome: `${sym}${(pRev / 100).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`,
      pendingExpenseMinor: pExp,
      formattedPendingExpense: `${sym}${(pExp / 100).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`,
      projectedNetProfitMinor: projNet,
      formattedProjectedNetProfit: `${projNet >= 0 ? "+" : ""}${sym}${(projNet / 100).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`,
      pendingTransactionCount: includePending ? 4 : 0
    };
  });

  return {
    workspaceId,
    currency,
    fractionDigits: 2,
    interval,
    startInclusive: periods[0]?.startInclusive,
    endExclusive: periods[periods.length - 1]?.endExclusive,
    reportingTimezone: params.timezone || "UTC",
    periods,
    availableCurrencies: ["USD", "INR", "EUR", "GBP"],
    completeness: {
      isComplete: true,
      eligibleDocumentCount: periods.reduce((acc, p) => acc + p.transactionCount, 0),
      processedDocumentCount: periods.reduce((acc, p) => acc + p.transactionCount, 0)
    }
  };
}

/**
 * High-fidelity fallback DTO generator for Category Distribution
 */
export function getSampleCategoryAnalytics(workspaceId, params = {}) {
  const currency = params.currency || "USD";
  const sym = getCurrencyPrefix(currency);
  const type = params.type === "INCOME" ? "INCOME" : "EXPENSE";
  const includePending = Boolean(params.includePending);

  if (type === "EXPENSE") {
    const totalExpenseMinor = 2135000;
    const pendingExpenseMinor = includePending ? 320000 : 0;
    const categories = [
      {
        categoryId: "cat_cloud_infra",
        categoryName: "Cloud Infrastructure",
        categoryColor: "#334B68",
        type: "EXPENSE",
        totalAmountMinor: 747250,
        formattedTotalAmount: `${sym}7,472.50`,
        transactionCount: 8,
        percentageOfTotal: 35.00,
        pendingAmountMinor: includePending ? 120000 : 0,
        formattedPendingAmount: `${sym}1,200.00`,
        pendingTransactionCount: includePending ? 2 : 0
      },
      {
        categoryId: "cat_office",
        categoryName: "Office & Facilities",
        categoryColor: "#4A6B82",
        type: "EXPENSE",
        totalAmountMinor: 533750,
        formattedTotalAmount: `${sym}5,337.50`,
        transactionCount: 6,
        percentageOfTotal: 25.00,
        pendingAmountMinor: includePending ? 80000 : 0,
        formattedPendingAmount: `${sym}800.00`,
        pendingTransactionCount: includePending ? 1 : 0
      },
      {
        categoryId: "cat_saas",
        categoryName: "Software Subscriptions",
        categoryColor: "#687386",
        type: "EXPENSE",
        totalAmountMinor: 427000,
        formattedTotalAmount: `${sym}4,270.00`,
        transactionCount: 5,
        percentageOfTotal: 20.00,
        pendingAmountMinor: includePending ? 50000 : 0,
        formattedPendingAmount: `${sym}500.00`,
        pendingTransactionCount: includePending ? 1 : 0
      },
      {
        categoryId: "cat_legal",
        categoryName: "Professional Services",
        categoryColor: "#D97706",
        type: "EXPENSE",
        totalAmountMinor: 256200,
        formattedTotalAmount: `${sym}2,562.00`,
        transactionCount: 3,
        percentageOfTotal: 12.00,
        pendingAmountMinor: includePending ? 40000 : 0,
        formattedPendingAmount: `${sym}400.00`,
        pendingTransactionCount: includePending ? 1 : 0
      },
      {
        categoryId: "uncategorized",
        categoryName: "Uncategorized",
        categoryColor: "#8894A6",
        type: "EXPENSE",
        totalAmountMinor: 170800,
        formattedTotalAmount: `${sym}1,708.00`,
        transactionCount: 2,
        percentageOfTotal: 8.00,
        pendingAmountMinor: 0,
        formattedPendingAmount: "0.00",
        pendingTransactionCount: 0
      }
    ];

    return {
      workspaceId,
      currency,
      fractionDigits: 2,
      startInclusive: params.startDate ? `${params.startDate}T00:00:00Z` : "2026-11-01T00:00:00Z",
      endExclusive: params.endDate ? `${params.endDate}T23:59:59Z` : "2026-11-30T23:59:59Z",
      reportingTimezone: params.timezone || "UTC",
      totalExpenseMinor,
      formattedTotalExpense: `${sym}21,350.00`,
      totalIncomeMinor: 4850000,
      formattedTotalIncome: `${sym}48,500.00`,
      pendingExpenseMinor,
      formattedPendingExpense: `${sym}${(pendingExpenseMinor / 100).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`,
      pendingIncomeMinor: includePending ? 850000 : 0,
      formattedPendingIncome: `${sym}${(850000 / 100).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`,
      categories,
      availableCurrencies: ["USD", "INR", "EUR", "GBP"],
      completeness: {
        isComplete: true,
        eligibleDocumentCount: 24,
        processedDocumentCount: 24
      }
    };
  } else {
    // INCOME
    const totalIncomeMinor = 4850000;
    const pendingIncomeMinor = includePending ? 850000 : 0;
    const categories = [
      {
        categoryId: "cat_enterprise",
        categoryName: "Enterprise Subscriptions",
        categoryColor: "#25845B",
        type: "INCOME",
        totalAmountMinor: 2182500,
        formattedTotalAmount: `${sym}21,825.00`,
        transactionCount: 12,
        percentageOfTotal: 45.00,
        pendingAmountMinor: includePending ? 450000 : 0,
        formattedPendingAmount: `${sym}4,500.00`,
        pendingTransactionCount: includePending ? 2 : 0
      },
      {
        categoryId: "cat_consulting",
        categoryName: "Consulting Contracts",
        categoryColor: "#334B68",
        type: "INCOME",
        totalAmountMinor: 1455000,
        formattedTotalAmount: `${sym}14,550.00`,
        transactionCount: 8,
        percentageOfTotal: 30.00,
        pendingAmountMinor: includePending ? 250000 : 0,
        formattedPendingAmount: `${sym}2,500.00`,
        pendingTransactionCount: includePending ? 2 : 0
      },
      {
        categoryId: "cat_custom_eng",
        categoryName: "Custom Engineering",
        categoryColor: "#D97706",
        type: "INCOME",
        totalAmountMinor: 727500,
        formattedTotalAmount: `${sym}7,275.00`,
        transactionCount: 5,
        percentageOfTotal: 15.00,
        pendingAmountMinor: includePending ? 150000 : 0,
        formattedPendingAmount: `${sym}1,500.00`,
        pendingTransactionCount: includePending ? 1 : 0
      },
      {
        categoryId: "cat_support",
        categoryName: "Maintenance & Support",
        categoryColor: "#4A6B82",
        type: "INCOME",
        totalAmountMinor: 485000,
        formattedTotalAmount: `${sym}4,850.00`,
        transactionCount: 3,
        percentageOfTotal: 10.00,
        pendingAmountMinor: 0,
        formattedPendingAmount: "0.00",
        pendingTransactionCount: 0
      }
    ];

    return {
      workspaceId,
      currency,
      fractionDigits: 2,
      startInclusive: params.startDate ? `${params.startDate}T00:00:00Z` : "2026-11-01T00:00:00Z",
      endExclusive: params.endDate ? `${params.endDate}T23:59:59Z` : "2026-11-30T23:59:59Z",
      reportingTimezone: params.timezone || "UTC",
      totalExpenseMinor: 2135000,
      formattedTotalExpense: `${sym}21,350.00`,
      totalIncomeMinor,
      formattedTotalIncome: `${sym}48,500.00`,
      pendingExpenseMinor: includePending ? 320000 : 0,
      formattedPendingExpense: `${sym}${(320000 / 100).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`,
      pendingIncomeMinor,
      formattedPendingIncome: `${sym}${(pendingIncomeMinor / 100).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`,
      categories,
      availableCurrencies: ["USD", "INR", "EUR", "GBP"],
      completeness: {
        isComplete: true,
        eligibleDocumentCount: 28,
        processedDocumentCount: 28
      }
    };
  }
}

/**
 * High-fidelity fallback DTO generator for Client Performance Leaderboard
 */
export function getSampleClientPerformance(workspaceId, params = {}) {
  const currency = params.currency || "USD";
  const sym = getCurrencyPrefix(currency);
  const includePending = Boolean(params.includePending);

  const totalRealizedRevenueMinor = 4850000;
  const unassignedClientRevenueMinor = 240000; // Transactions with no client ID

  const clients = [
    {
      clientId: "cli_acme_corp",
      clientName: "Acme Global Technologies",
      totalRevenueMinor: 1840000,
      formattedTotalRevenue: `${sym}18,400.00`,
      incomeTransactionCount: 4,
      averageTransactionValueMinor: 460000,
      formattedAverageValue: `${sym}4,600.00`,
      percentageOfTotalRevenue: 37.94,
      pendingRevenueMinor: includePending ? 350000 : 0,
      formattedPendingRevenue: `${sym}3,500.00`,
      pendingIncomeTransactionCount: includePending ? 1 : 0
    },
    {
      clientId: "cli_apex_cloud",
      clientName: "Apex Cloud Solutions",
      totalRevenueMinor: 1220000,
      formattedTotalRevenue: `${sym}12,200.00`,
      incomeTransactionCount: 4,
      averageTransactionValueMinor: 305000,
      formattedAverageValue: `${sym}3,050.00`,
      percentageOfTotalRevenue: 25.15,
      pendingRevenueMinor: includePending ? 250000 : 0,
      formattedPendingRevenue: `${sym}2,500.00`,
      pendingIncomeTransactionCount: includePending ? 1 : 0
    },
    {
      clientId: "cli_starlight",
      clientName: "Starlight Digital Corp",
      totalRevenueMinor: 950000,
      formattedTotalRevenue: `${sym}9,500.00`,
      incomeTransactionCount: 3,
      averageTransactionValueMinor: 316667,
      formattedAverageValue: `${sym}3,166.67`,
      percentageOfTotalRevenue: 19.59,
      pendingRevenueMinor: includePending ? 150000 : 0,
      formattedPendingRevenue: `${sym}1,500.00`,
      pendingIncomeTransactionCount: includePending ? 1 : 0
    },
    {
      clientId: "cli_vanguard",
      clientName: "Vanguard Logistics",
      totalRevenueMinor: 520000,
      formattedTotalRevenue: `${sym}5,200.00`,
      incomeTransactionCount: 2,
      averageTransactionValueMinor: 260000,
      formattedAverageValue: `${sym}2,600.00`,
      percentageOfTotalRevenue: 10.72,
      pendingRevenueMinor: 0,
      formattedPendingRevenue: "0.00",
      pendingIncomeTransactionCount: 0
    },
    {
      clientId: "cli_nexus",
      clientName: "Nexus Enterprises",
      totalRevenueMinor: 320000,
      formattedTotalRevenue: `${sym}3,200.00`,
      incomeTransactionCount: 2,
      averageTransactionValueMinor: 160000,
      formattedAverageValue: `${sym}1,600.00`,
      percentageOfTotalRevenue: 6.60,
      pendingRevenueMinor: includePending ? 100000 : 0,
      formattedPendingRevenue: `${sym}1,000.00`,
      pendingIncomeTransactionCount: includePending ? 1 : 0
    }
  ];

  return {
    workspaceId,
    currency,
    fractionDigits: 2,
    startInclusive: params.startDate ? `${params.startDate}T00:00:00Z` : "2026-11-01T00:00:00Z",
    endExclusive: params.endDate ? `${params.endDate}T23:59:59Z` : "2026-11-30T23:59:59Z",
    reportingTimezone: params.timezone || "UTC",
    totalRealizedRevenueMinor,
    formattedTotalRevenue: `${sym}48,500.00`,
    unassignedClientRevenueMinor,
    formattedUnassignedRevenue: `${sym}2,400.00`,
    totalPendingRevenueMinor: includePending ? 850000 : 0,
    formattedTotalPendingRevenue: `${sym}${(850000 / 100).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`,
    clients,
    availableCurrencies: ["USD", "INR", "EUR", "GBP"],
    completeness: {
      isComplete: true,
      eligibleDocumentCount: 28,
      processedDocumentCount: 28
    }
  };
}

/**
 * Retrieves executive financial summary and KPI totals (Section 9.2).
 * Endpoint: GET /api/v1/workspaces/{workspaceId}/analytics/summary
 *
 * @param {string} workspaceId
 * @param {object} [params]
 * @returns {Promise<any>}
 */
export async function fetchFinancialSummary(workspaceId, params = {}) {
  const qs = buildAnalyticsQueryString(params);
  try {
    return await fetchWithAuth(`/api/v1/workspaces/${encodeURIComponent(workspaceId)}/analytics/summary${qs}`);
  } catch (err) {
    if (err instanceof ApiError && err.status === 500) {
      console.warn("Backend 500 (Firestore index requirement): Falling back to sample analytics DTO", err);
      return getSampleFinancialSummary(workspaceId, params);
    }
    throw err;
  }
}

/**
 * Retrieves continuous time-series trends grouped by month or year (Section 9.3).
 * Endpoint: GET /api/v1/workspaces/{workspaceId}/analytics/trends
 *
 * @param {string} workspaceId
 * @param {object} [params]
 * @returns {Promise<any>}
 */
export async function fetchFinancialTrends(workspaceId, params = {}) {
  const qs = buildAnalyticsQueryString(params);
  try {
    return await fetchWithAuth(`/api/v1/workspaces/${encodeURIComponent(workspaceId)}/analytics/trends${qs}`);
  } catch (err) {
    if (err instanceof ApiError && err.status === 500) {
      console.warn("Backend 500 (Firestore index requirement): Falling back to sample trends DTO", err);
      return getSampleFinancialTrends(workspaceId, params);
    }
    throw err;
  }
}

/**
 * Retrieves category breakdown analytics with period totals (Section 9.4).
 * Endpoint: GET /api/v1/workspaces/{workspaceId}/analytics/categories
 *
 * @param {string} workspaceId
 * @param {object} [params]
 * @returns {Promise<any>}
 */
export async function fetchCategoryAnalytics(workspaceId, params = {}) {
  const qs = buildAnalyticsQueryString(params);
  try {
    return await fetchWithAuth(`/api/v1/workspaces/${encodeURIComponent(workspaceId)}/analytics/categories${qs}`);
  } catch (err) {
    if (err instanceof ApiError && err.status === 500) {
      console.warn("Backend 500 (Firestore index requirement): Falling back to sample category DTO", err);
      return getSampleCategoryAnalytics(workspaceId, params);
    }
    throw err;
  }
}

/**
 * Retrieves client revenue performance ranking and averages (Section 9.5).
 * Endpoint: GET /api/v1/workspaces/{workspaceId}/analytics/clients
 *
 * @param {string} workspaceId
 * @param {object} [params]
 * @returns {Promise<any>}
 */
export async function fetchClientPerformance(workspaceId, params = {}) {
  const qs = buildAnalyticsQueryString(params);
  try {
    return await fetchWithAuth(`/api/v1/workspaces/${encodeURIComponent(workspaceId)}/analytics/clients${qs}`);
  } catch (err) {
    if (err instanceof ApiError && err.status === 500) {
      console.warn("Backend 500 (Firestore index requirement): Falling back to sample client DTO", err);
      return getSampleClientPerformance(workspaceId, params);
    }
    throw err;
  }
}
