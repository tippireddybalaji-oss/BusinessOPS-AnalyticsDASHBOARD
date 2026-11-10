/**
 * BusinessOPS Analytics Dashboard - Main Dashboard Controller
 *
 * Implements Phase 6 Milestone 3:
 * - Executive KPI Summary Cards
 * - Financial Trends Time-Series Chart (Chart.js v4.4.x)
 * - Category Distribution Doughnut Chart & Detail Breakdown Table
 * - Client Revenue Leaderboard & Unassigned Revenue Separation
 * - Global Filter Bar (Presets, Custom Dates, Currency, Include Pending)
 * - Error Handling (HTTP 422 processing limits, 401 recovery, empty/loading states)
 */

import { requireAuth, logout } from "./auth.js";
import { showToast, handleApiError, ApiError } from "./api.js";
import {
  fetchWorkspaces,
  getActiveWorkspaceId,
  setActiveWorkspaceId,
  resolveActiveWorkspace,
  renderWorkspaceSwitcher
} from "./workspace.js";
import {
  fetchFinancialSummary,
  fetchFinancialTrends,
  fetchCategoryAnalytics,
  fetchClientPerformance
} from "./analytics-api.js";

// Chart.js Instances
let trendsChartInstance = null;
let categoryChartInstance = null;

// Dashboard Filter State
const state = {
  user: null,
  workspaces: [],
  activeWorkspace: null,
  preset: "THIS_MONTH",
  startDate: null,
  endDate: null,
  currency: null,
  includePending: false,
  trendsInterval: "MONTHLY",
  trendsChartType: "bar",
  categoryType: "EXPENSE",
  isLoading: false
};

/**
 * Format a Date object to YYYY-MM-DD in local timezone
 * @param {Date} date
 * @returns {string}
 */
export function formatDateYMD(date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

/**
 * Resolves start and end YYYY-MM-DD dates based on selected preset
 * @param {string} preset
 * @returns {{ startDate: string|null, endDate: string|null }}
 */
export function resolvePresetDates(preset) {
  const now = new Date();
  const today = formatDateYMD(now);

  switch (preset) {
    case "THIS_MONTH": {
      const firstDay = new Date(now.getFullYear(), now.getMonth(), 1);
      return { startDate: formatDateYMD(firstDay), endDate: today };
    }
    case "LAST_30_DAYS": {
      const past30 = new Date(now);
      past30.setDate(past30.getDate() - 30);
      return { startDate: formatDateYMD(past30), endDate: today };
    }
    case "QUARTER_TO_DATE": {
      const quarterStartMonth = Math.floor(now.getMonth() / 3) * 3;
      const firstQuarterDay = new Date(now.getFullYear(), quarterStartMonth, 1);
      return { startDate: formatDateYMD(firstQuarterDay), endDate: today };
    }
    case "YEAR_TO_DATE": {
      const firstDayOfYear = new Date(now.getFullYear(), 0, 1);
      return { startDate: formatDateYMD(firstDayOfYear), endDate: today };
    }
    case "ALL_TIME": {
      return { startDate: null, endDate: null };
    }
    default:
      return { startDate: state.startDate, endDate: state.endDate };
  }
}

/**
 * Main Initialization Flow
 */
export async function initDashboard() {
  // Initialize Lucide icons on static DOM
  if (window.lucide && typeof window.lucide.createIcons === "function") {
    window.lucide.createIcons();
  }

  // 1. Route Guard
  state.user = await requireAuth("login.html");

  // 2. Resolve Accessible Workspaces
  try {
    state.workspaces = await fetchWorkspaces();
    state.activeWorkspace = await resolveActiveWorkspace(state.workspaces);

    if (!state.activeWorkspace) {
      // User has 0 workspaces, redirect to workspace onboarding
      window.location.replace("workspaces.html");
      return;
    }

    state.currency = state.activeWorkspace.defaultCurrency || "INR";
  } catch (err) {
    console.error("Workspace resolution failed:", err);
    showToast("Failed to load workspace details: " + err.message, "error");
    return;
  }

  // 3. Render Header User Info & Workspace Switcher
  setupHeader();

  // 4. Setup Filter Bar Listeners
  setupFilters();

  // 5. Initial Data Load
  const initialDates = resolvePresetDates(state.preset);
  state.startDate = initialDates.startDate;
  state.endDate = initialDates.endDate;

  await refreshDashboard();
}

/**
 * Configure header UI and workspace dropdown
 */
function setupHeader() {
  const userAvatar = document.getElementById("user-avatar");
  const userDisplayName = document.getElementById("user-display-name");
  const userDisplayEmail = document.getElementById("user-display-email");
  const logoutBtn = document.getElementById("logout-btn");
  const navWsSwitcher = document.getElementById("nav-workspace-switcher");

  if (userDisplayEmail) userDisplayEmail.textContent = state.user.email || "";
  if (userDisplayName) userDisplayName.textContent = state.user.displayName || state.user.email?.split("@")[0] || "User";
  if (userAvatar) userAvatar.textContent = (state.user.displayName?.[0] || state.user.email?.[0] || "U").toUpperCase();

  if (logoutBtn) {
    logoutBtn.addEventListener("click", async () => {
      try {
        await logout();
        window.location.replace("login.html");
      } catch (err) {
        showToast("Error signing out: " + err.message, "error");
      }
    });
  }

  renderWorkspaceSwitcher(navWsSwitcher, state.workspaces, state.activeWorkspace, async (newId) => {
    setActiveWorkspaceId(newId);
    state.activeWorkspace = state.workspaces.find(w => w.id === newId) || state.activeWorkspace;
    state.currency = state.activeWorkspace.defaultCurrency || "INR";
    showToast(`Switched to ${state.activeWorkspace.name}`, "info");
    await refreshDashboard();
  });
}

/**
 * Setup Global Filter Listeners
 */
function setupFilters() {
  const pillBtns = document.querySelectorAll(".filter-pill-btn");
  const customRangeBox = document.getElementById("custom-date-controls");
  const customStartInput = document.getElementById("custom-start-date");
  const customEndInput = document.getElementById("custom-end-date");
  const customApplyBtn = document.getElementById("custom-apply-btn");
  const currencySelect = document.getElementById("filter-currency-select");
  const pendingCheckbox = document.getElementById("filter-include-pending");

  // Date Preset Pill Buttons
  pillBtns.forEach(btn => {
    btn.addEventListener("click", async () => {
      pillBtns.forEach(b => b.classList.remove("active"));
      btn.classList.add("active");

      const preset = btn.dataset.preset;
      state.preset = preset;

      if (preset === "CUSTOM") {
        if (customRangeBox) customRangeBox.classList.remove("d-none");
        return; // Wait for user to input dates and click Apply
      } else {
        if (customRangeBox) customRangeBox.classList.add("d-none");
        const resolved = resolvePresetDates(preset);
        state.startDate = resolved.startDate;
        state.endDate = resolved.endDate;
        await refreshDashboard();
      }
    });
  });

  // Custom Date Range Apply
  if (customApplyBtn) {
    customApplyBtn.addEventListener("click", async () => {
      const s = customStartInput?.value;
      const e = customEndInput?.value;
      if (!s || !e) {
        showToast("Please select both start and end dates.", "warning");
        return;
      }
      if (s > e) {
        showToast("Start date cannot be after end date.", "warning");
        return;
      }
      state.startDate = s;
      state.endDate = e;
      await refreshDashboard();
    });
  }

  // Currency Selector
  if (currencySelect) {
    currencySelect.addEventListener("change", async (e) => {
      state.currency = e.target.value;
      await refreshDashboard();
    });
  }

  // Include Pending Toggle
  if (pendingCheckbox) {
    pendingCheckbox.addEventListener("change", async (e) => {
      state.includePending = e.target.checked;
      await refreshDashboard();
    });
  }

  // Trends Chart Controls
  const intervalBtns = document.querySelectorAll(".trends-interval-btn");
  intervalBtns.forEach(btn => {
    btn.addEventListener("click", async () => {
      intervalBtns.forEach(b => b.classList.remove("active"));
      btn.classList.add("active");
      state.trendsInterval = btn.dataset.interval;
      await loadTrendsSection();
    });
  });

  const chartTypeBtns = document.querySelectorAll(".trends-type-btn");
  chartTypeBtns.forEach(btn => {
    btn.addEventListener("click", () => {
      chartTypeBtns.forEach(b => b.classList.remove("active"));
      btn.classList.add("active");
      state.trendsChartType = btn.dataset.type;
      renderTrendsChart(state.latestTrendsData);
    });
  });

  // Category Income/Expense Toggle
  const categoryTypeBtns = document.querySelectorAll(".category-type-btn");
  categoryTypeBtns.forEach(btn => {
    btn.addEventListener("click", async () => {
      categoryTypeBtns.forEach(b => b.classList.remove("active"));
      btn.classList.add("active");
      state.categoryType = btn.dataset.type;
      await loadCategorySection();
    });
  });

  // Empty State Recovery Actions
  const trendsResetBtn = document.getElementById("trends-reset-filter-btn");
  if (trendsResetBtn) {
    trendsResetBtn.addEventListener("click", async () => {
      document.querySelectorAll(".filter-pill-btn").forEach(b => {
        b.classList.toggle("active", b.dataset.preset === "ALL_TIME");
      });
      state.preset = "ALL_TIME";
      state.startDate = null;
      state.endDate = null;
      document.getElementById("custom-date-controls")?.classList.add("d-none");
      await refreshDashboard();
    });
  }

  const categoryResetBtn = document.getElementById("category-reset-btn");
  if (categoryResetBtn) {
    categoryResetBtn.addEventListener("click", async () => {
      document.querySelectorAll(".filter-pill-btn").forEach(b => {
        b.classList.toggle("active", b.dataset.preset === "ALL_TIME");
      });
      state.preset = "ALL_TIME";
      state.startDate = null;
      state.endDate = null;
      document.getElementById("custom-date-controls")?.classList.add("d-none");
      await refreshDashboard();
    });
  }
}

/**
 * Refreshes all four analytics sections in parallel
 */
export async function refreshDashboard() {
  if (state.isLoading) return;
  state.isLoading = true;

  setDashboardLoading(true);
  hideSafeguardBanner();

  const workspaceId = state.activeWorkspace.id;
  const commonParams = {
    currency: state.currency,
    startDate: state.startDate,
    endDate: state.endDate,
    includePending: state.includePending,
    timezone: Intl.DateTimeFormat().resolvedOptions().timeZone || "UTC"
  };

  try {
    const [summaryResult, trendsResult, categoryResult, clientResult] = await Promise.allSettled([
      fetchFinancialSummary(workspaceId, commonParams),
      fetchFinancialTrends(workspaceId, { ...commonParams, interval: state.trendsInterval }),
      fetchCategoryAnalytics(workspaceId, { ...commonParams, type: state.categoryType }),
      fetchClientPerformance(workspaceId, { ...commonParams, limit: 10 })
    ]);

    // Check for HTTP 422 processing limit safeguards
    const limitError = [summaryResult, trendsResult, categoryResult, clientResult]
      .filter(r => r.status === "rejected" && r.reason instanceof ApiError && r.reason.status === 422)
      .map(r => r.reason)[0];

    if (limitError) {
      showSafeguardBanner(limitError);
    }

    // 1. Render Summary
    if (summaryResult.status === "fulfilled") {
      renderSummaryKPIs(summaryResult.value);
      updateCurrencySelector(summaryResult.value.availableCurrencies, summaryResult.value.currency);
    } else {
      console.error("Summary fetch error:", summaryResult.reason);
      if (summaryResult.reason?.status !== 422) {
        handleApiError(summaryResult.reason, "Failed to load financial summary.");
      }
    }

    // 2. Render Trends
    if (trendsResult.status === "fulfilled") {
      state.latestTrendsData = trendsResult.value;
      renderTrendsChart(trendsResult.value);
    } else {
      console.error("Trends fetch error:", trendsResult.reason);
      renderTrendsEmptyState(trendsResult.reason?.message);
    }

    // 3. Render Categories
    if (categoryResult.status === "fulfilled") {
      renderCategorySection(categoryResult.value);
    } else {
      console.error("Categories fetch error:", categoryResult.reason);
      renderCategoryEmptyState(categoryResult.reason?.message);
    }

    // 4. Render Clients
    if (clientResult.status === "fulfilled") {
      renderClientSection(clientResult.value);
    } else {
      console.error("Clients fetch error:", clientResult.reason);
      renderClientEmptyState(clientResult.reason?.message);
    }
  } catch (err) {
    console.error("Global refresh failure:", err);
    handleApiError(err, "Dashboard refresh failed.");
  } finally {
    state.isLoading = false;
    setDashboardLoading(false);
    if (window.lucide && typeof window.lucide.createIcons === "function") {
      window.lucide.createIcons();
    }
  }
}

/**
 * Load only trends section (e.g. on interval switch)
 */
async function loadTrendsSection() {
  const commonParams = {
    currency: state.currency,
    startDate: state.startDate,
    endDate: state.endDate,
    includePending: state.includePending,
    interval: state.trendsInterval,
    timezone: Intl.DateTimeFormat().resolvedOptions().timeZone || "UTC"
  };
  try {
    const data = await fetchFinancialTrends(state.activeWorkspace.id, commonParams);
    state.latestTrendsData = data;
    renderTrendsChart(data);
  } catch (err) {
    renderTrendsEmptyState(err.message);
  }
}

/**
 * Load only category section (e.g. on income/expense toggle)
 */
async function loadCategorySection() {
  const commonParams = {
    currency: state.currency,
    startDate: state.startDate,
    endDate: state.endDate,
    includePending: state.includePending,
    type: state.categoryType,
    timezone: Intl.DateTimeFormat().resolvedOptions().timeZone || "UTC"
  };
  try {
    const data = await fetchCategoryAnalytics(state.activeWorkspace.id, commonParams);
    renderCategorySection(data);
  } catch (err) {
    renderCategoryEmptyState(err.message);
  }
}

/**
 * Render Executive KPI Cards
 * @param {object} summary
 */
function renderSummaryKPIs(summary) {
  // 1. Total Revenue
  const revEl = document.getElementById("kpi-revenue-val");
  const revSubEl = document.getElementById("kpi-revenue-sub");
  if (revEl) revEl.textContent = summary.formattedRevenue || "0.00";
  if (revSubEl) {
    if (state.includePending && summary.pendingIncomeMinor > 0) {
      revSubEl.innerHTML = `<span class="pipeline-highlight">+ ${summary.formattedPendingIncome} pending</span>`;
    } else {
      revSubEl.textContent = `${summary.incomeTransactionCount || 0} income payments`;
    }
  }

  // 2. Total Expenses
  const expEl = document.getElementById("kpi-expense-val");
  const expSubEl = document.getElementById("kpi-expense-sub");
  if (expEl) expEl.textContent = summary.formattedExpense || "0.00";
  if (expSubEl) {
    if (state.includePending && summary.pendingExpenseMinor > 0) {
      expSubEl.innerHTML = `<span class="pipeline-highlight">+ ${summary.formattedPendingExpense} pending</span>`;
    } else {
      expSubEl.textContent = `${summary.expenseTransactionCount || 0} expense payments`;
    }
  }

  // 3. Net Cash Flow
  const netEl = document.getElementById("kpi-net-val");
  const netSubEl = document.getElementById("kpi-net-sub");
  if (netEl) {
    netEl.textContent = summary.formattedNetProfit || "0.00";
    netEl.classList.remove("positive", "negative");
    if (summary.netProfitMinor > 0) netEl.classList.add("positive");
    else if (summary.netProfitMinor < 0) netEl.classList.add("negative");
  }
  if (netSubEl) {
    if (state.includePending) {
      netSubEl.innerHTML = `Projected: <strong>${summary.formattedProjectedNetProfit}</strong>`;
    } else {
      netSubEl.textContent = "Realized net income";
    }
  }

  // 4. Profit Margin (Handled: isMarginComputable === false -> "N/A")
  const marginEl = document.getElementById("kpi-margin-val");
  const marginSubEl = document.getElementById("kpi-margin-sub");
  if (marginEl) {
    marginEl.classList.remove("positive", "negative");
    if (!summary.isMarginComputable) {
      marginEl.textContent = "N/A";
      marginEl.style.color = "var(--text-secondary)";
      if (marginSubEl) marginSubEl.textContent = "Zero revenue in period";
    } else {
      const margin = summary.profitMarginPercentage ?? 0;
      marginEl.textContent = `${margin > 0 ? "+" : ""}${Number(margin).toFixed(1)}%`;
      marginEl.style.color = margin >= 0 ? "var(--color-success)" : "var(--color-danger)";
      if (marginSubEl) marginSubEl.textContent = "Net margin on revenue";
    }
  }

  // 5. Completed Activity
  const txEl = document.getElementById("kpi-tx-val");
  const txSubEl = document.getElementById("kpi-tx-sub");
  if (txEl) txEl.textContent = summary.totalCompletedTransactions ?? 0;
  if (txSubEl) {
    if (state.includePending && summary.pendingTransactionCount > 0) {
      txSubEl.innerHTML = `<span class="pipeline-highlight">+ ${summary.pendingTransactionCount} pending</span>`;
    } else {
      txSubEl.textContent = `${summary.incomeTransactionCount} in · ${summary.expenseTransactionCount} out`;
    }
  }
}

/**
 * Updates Currency Selector options dynamically based on availableCurrencies
 * @param {Array<string>} [availableCurrencies]
 * @param {string} currentCurrency
 */
function updateCurrencySelector(availableCurrencies = [], currentCurrency) {
  const select = document.getElementById("filter-currency-select");
  if (!select) return;

  const currentOpts = Array.from(select.options).map(o => o.value);
  const currencies = availableCurrencies && availableCurrencies.length > 0
    ? availableCurrencies
    : [currentCurrency || state.currency || "INR"];

  // Ensure current workspace currency is always present
  if (!currencies.includes(state.currency)) {
    currencies.push(state.currency);
  }

  select.innerHTML = currencies.map(c => `
    <option value="${c}" ${c === currentCurrency ? "selected" : ""}>${c}</option>
  `).join("");
}

/**
 * Render Financial Trends Chart via Chart.js v4.4.x
 * @param {object} trends
 */
function renderTrendsChart(trends) {
  const container = document.getElementById("trends-chart-container");
  const emptyBox = document.getElementById("trends-empty-state");
  const canvas = document.getElementById("trends-canvas");
  if (!canvas) return;

  const periods = trends?.periods || [];

  if (periods.length === 0) {
    renderTrendsEmptyState("No trend records found for the selected period.");
    return;
  }

  if (emptyBox) emptyBox.classList.add("d-none");
  canvas.classList.remove("d-none");

  // Destroy previous Chart instance
  if (trendsChartInstance) {
    trendsChartInstance.destroy();
    trendsChartInstance = null;
  }

  const labels = periods.map(p => p.period);
  const revenueData = periods.map(p => (p.revenueMinor || 0) / 100);
  const expenseData = periods.map(p => (p.expenseMinor || 0) / 100);
  const profitData = periods.map(p => (p.netProfitMinor || 0) / 100);

  const isBar = state.trendsChartType === "bar";

  const datasets = [
    {
      label: "Revenue",
      data: revenueData,
      backgroundColor: isBar ? "rgba(51, 75, 104, 0.85)" : "rgba(51, 75, 104, 0.1)",
      borderColor: "#334B68",
      borderWidth: 1.5,
      borderRadius: isBar ? 4 : 0,
      fill: !isBar,
      tension: 0.25
    },
    {
      label: "Expense",
      data: expenseData,
      backgroundColor: isBar ? "rgba(196, 75, 75, 0.85)" : "rgba(196, 75, 75, 0.1)",
      borderColor: "#C44B4B",
      borderWidth: 1.5,
      borderRadius: isBar ? 4 : 0,
      fill: !isBar,
      tension: 0.25
    },
    {
      label: "Net Cash Flow",
      data: profitData,
      type: "line",
      borderColor: "#25845B",
      backgroundColor: "#25845B",
      borderWidth: 2,
      pointRadius: 3,
      pointHoverRadius: 5,
      pointBackgroundColor: "#25845B",
      tension: 0.25,
      yAxisID: "y"
    }
  ];

  // If includePending enabled, add projected line
  if (state.includePending) {
    const projectedData = periods.map(p => (p.projectedNetProfitMinor || p.netProfitMinor || 0) / 100);
    datasets.push({
      label: "Projected Net",
      data: projectedData,
      type: "line",
      borderColor: "#687386",
      borderDash: [4, 4],
      borderWidth: 1.5,
      pointRadius: 2,
      fill: false,
      tension: 0.25
    });
  }

  const ctx = canvas.getContext("2d");
  trendsChartInstance = new window.Chart(ctx, {
    type: isBar ? "bar" : "line",
    data: { labels, datasets },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      interaction: {
        mode: "index",
        intersect: false
      },
      plugins: {
        legend: {
          position: "top",
          align: "end",
          labels: {
            boxWidth: 12,
            boxHeight: 12,
            usePointStyle: true,
            pointStyle: "circle",
            color: "#687386",
            font: { family: "-apple-system, system-ui, sans-serif", size: 12 }
          }
        },
        tooltip: {
          backgroundColor: "#202938",
          titleColor: "#FFFFFF",
          bodyColor: "#F5F6F8",
          borderColor: "#E2E6EC",
          borderWidth: 1,
          padding: 10,
          boxPadding: 4,
          callbacks: {
            label: (context) => {
              const val = Number(context.raw).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
              return ` ${context.dataset.label}: ${trends.currency || ""} ${val}`;
            }
          }
        }
      },
      scales: {
        x: {
          grid: { display: false },
          ticks: { color: "#687386", font: { size: 11 } }
        },
        y: {
          grid: { color: "#E2E6EC", drawBorder: false },
          ticks: {
            color: "#687386",
            font: { size: 11 },
            callback: (v) => `${Number(v).toLocaleString()}`
          }
        }
      }
    }
  });
}

function renderTrendsEmptyState(msg) {
  const canvas = document.getElementById("trends-canvas");
  const emptyBox = document.getElementById("trends-empty-state");
  if (canvas) canvas.classList.add("d-none");
  if (emptyBox) {
    emptyBox.classList.remove("d-none");
    const p = emptyBox.querySelector("p");
    if (p) p.textContent = msg || "No financial activity recorded in the selected period.";
  }
}

/**
 * Render Category Analytics (Doughnut Chart + Breakdown Table)
 * @param {object} catData
 */
function renderCategorySection(catData) {
  const categories = catData?.categories || [];
  const canvas = document.getElementById("category-canvas");
  const emptyBox = document.getElementById("category-empty-state");
  const tableBody = document.getElementById("category-table-body");
  const headerTotal = document.getElementById("category-period-total");

  if (headerTotal) {
    const isExpense = state.categoryType === "EXPENSE";
    const total = isExpense ? catData.formattedTotalExpense : catData.formattedTotalIncome;
    headerTotal.textContent = `Total: ${catData.currency || ""} ${total || "0.00"}`;
  }

  if (categories.length === 0) {
    renderCategoryEmptyState("No categorized transactions found for this selection.");
    return;
  }

  if (canvas) canvas.classList.remove("d-none");
  if (emptyBox) emptyBox.classList.add("d-none");

  // Destroy previous Doughnut instance
  if (categoryChartInstance) {
    categoryChartInstance.destroy();
    categoryChartInstance = null;
  }

  // Fallback Enterprise Palette
  const defaultColors = [
    "#334B68", "#25845B", "#C44B4B", "#D97706", "#4A6B82",
    "#687386", "#1D2B3D", "#8894A6", "#3E5C76", "#52796F"
  ];

  const labels = categories.map(c => c.categoryName || (c.categoryId === "uncategorized" ? "Uncategorized" : "Other"));
  const dataVals = categories.map(c => (c.totalAmountMinor || 0) / 100);
  const bgColors = categories.map((c, i) => {
    if (c.categoryId === "uncategorized") return "#8894A6";
    return c.categoryColor || defaultColors[i % defaultColors.length];
  });

  if (canvas) {
    const ctx = canvas.getContext("2d");
    categoryChartInstance = new window.Chart(ctx, {
      type: "doughnut",
      data: {
        labels,
        datasets: [{
          data: dataVals,
          backgroundColor: bgColors,
          borderWidth: 2,
          borderColor: "#FFFFFF"
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: {
            display: false // Handled cleanly by the adjacent detail table
          },
          tooltip: {
            backgroundColor: "#202938",
            padding: 8,
            callbacks: {
              label: (context) => {
                const item = categories[context.dataIndex];
                const pct = item?.percentageOfTotal ?? 0;
                const amt = Number(context.raw).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
                return ` ${context.label}: ${catData.currency || ""} ${amt} (${pct}%)`;
              }
            }
          }
        },
        cutout: "68%"
      }
    });
  }

  // Populate Table
  if (tableBody) {
    tableBody.innerHTML = categories.map((c, idx) => {
      const color = bgColors[idx];
      const isUncat = c.categoryId === "uncategorized";
      const name = isUncat ? "Uncategorized" : (c.categoryName || "Category");
      return `
        <tr>
          <td>
            <div style="display:flex;align-items:center;">
              <span class="color-indicator-dot" style="background-color:${color};"></span>
              <strong style="${isUncat ? 'color:var(--text-secondary);font-style:italic;' : ''}">${name}</strong>
            </div>
          </td>
          <td style="font-weight:600;text-align:right;">${c.formattedTotalAmount}</td>
          <td style="text-align:right;color:var(--text-secondary);">${c.percentageOfTotal}%</td>
          <td style="text-align:right;color:var(--text-secondary);">${c.transactionCount}</td>
        </tr>
      `;
    }).join("");
  }
}

function renderCategoryEmptyState(msg) {
  const canvas = document.getElementById("category-canvas");
  const emptyBox = document.getElementById("category-empty-state");
  const tableBody = document.getElementById("category-table-body");
  if (canvas) canvas.classList.add("d-none");
  if (emptyBox) {
    emptyBox.classList.remove("d-none");
    const p = emptyBox.querySelector("p");
    if (p) p.textContent = msg || "No transactions recorded for this category filter.";
  }
  if (tableBody) tableBody.innerHTML = "";
}

/**
 * Render Client Revenue Performance Leaderboard
 * @param {object} clientData
 */
function renderClientSection(clientData) {
  const listEl = document.getElementById("client-leaderboard-list");
  const emptyBox = document.getElementById("client-empty-state");
  const unassignedBox = document.getElementById("client-unassigned-callout");
  const unassignedAmt = document.getElementById("client-unassigned-amount");

  const clients = clientData?.clients || [];

  // Render Unassigned Revenue Callout
  if (unassignedBox && unassignedAmt) {
    if (clientData.unassignedClientRevenueMinor && clientData.unassignedClientRevenueMinor > 0) {
      unassignedAmt.textContent = clientData.formattedUnassignedRevenue;
      unassignedBox.classList.remove("d-none");
    } else {
      unassignedBox.classList.add("d-none");
    }
  }

  if (clients.length === 0) {
    renderClientEmptyState("No client revenue recorded in the selected period.");
    return;
  }

  if (listEl) listEl.classList.remove("d-none");
  if (emptyBox) emptyBox.classList.add("d-none");

  if (listEl) {
    listEl.innerHTML = clients.map((c, i) => {
      const pct = Math.min(100, Math.max(0, Number(c.percentageOfTotalRevenue || 0)));
      const isTop1 = i === 0;
      return `
        <div class="leaderboard-item">
          <div class="rank-badge ${isTop1 ? 'top-1' : ''}">${i + 1}</div>
          <div class="client-meta-info">
            <div class="client-name-text" title="${c.clientName || 'Client'}">${c.clientName || 'Client'}</div>
            <div class="client-contribution-bar-bg" aria-hidden="true">
              <div class="client-contribution-bar-fill" style="width: ${pct}%;"></div>
            </div>
          </div>
          <div class="client-financial-stats">
            <div class="client-revenue-stat">${c.formattedTotalRevenue}</div>
            <div class="client-substat">${c.percentageOfTotalRevenue}% · Avg: ${c.formattedAverageValue}</div>
          </div>
        </div>
      `;
    }).join("");
  }
}

function renderClientEmptyState(msg) {
  const listEl = document.getElementById("client-leaderboard-list");
  const emptyBox = document.getElementById("client-empty-state");
  if (listEl) listEl.classList.add("d-none");
  if (emptyBox) {
    emptyBox.classList.remove("d-none");
    const p = emptyBox.querySelector("p");
    if (p) p.textContent = msg || "No revenue-generating client payments recorded in this period.";
  }
}

/**
 * UI Loading State Controller
 * @param {boolean} loading
 */
function setDashboardLoading(loading) {
  const spinner = document.getElementById("dashboard-refresh-spinner");
  if (spinner) {
    if (loading) spinner.classList.remove("d-none");
    else spinner.classList.add("d-none");
  }
  const kpiValueIds = ["kpi-revenue-val", "kpi-expense-val", "kpi-net-val", "kpi-margin-val", "kpi-tx-val"];
  kpiValueIds.forEach(id => {
    const el = document.getElementById(id);
    if (el) {
      if (loading && (!el.textContent || el.textContent.trim() === "0.00" || el.textContent.trim() === "0")) {
        el.classList.add("skeleton");
      } else if (!loading) {
        el.classList.remove("skeleton");
      }
    }
  });
}

/**
 * Displays HTTP 422 Processing Limit Exceeded Safeguard Banner
 * @param {ApiError} error
 */
function showSafeguardBanner(error) {
  let banner = document.getElementById("safeguard-alert-banner");
  if (!banner) return;

  const msgEl = banner.querySelector(".safeguard-message");
  if (msgEl) {
    const tier = error.limitDetails?.enforcementTier ? ` (${error.limitDetails.enforcementTier})` : "";
    msgEl.textContent = `${error.message}${tier}. Please choose a narrower timeframe (e.g. This Month or Last 30 Days).`;
  }
  banner.classList.remove("d-none");
}

function hideSafeguardBanner() {
  const banner = document.getElementById("safeguard-alert-banner");
  if (banner) banner.classList.add("d-none");
}

// Auto-run when loaded directly in browser
if (typeof window !== "undefined" && window.document) {
  const runInit = () => {
    if (window.location.pathname.endsWith("dashboard.html")) {
      initDashboard().catch(err => console.error("Dashboard init error:", err));
    }
  };
  if (document.readyState === "loading") {
    window.addEventListener("DOMContentLoaded", runInit);
  } else {
    runInit();
  }
}

