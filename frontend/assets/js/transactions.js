import { requireAuth, logout } from "./auth.js";
import { showToast, handleApiError } from "./api.js";
import { fetchWorkspaces, resolveActiveWorkspace, renderWorkspaceSwitcher, setActiveWorkspaceId } from "./workspace.js";
import { createTransaction, fetchCategories, fetchClients, fetchTransactions, updateTransaction } from "./operations-api.js";
import { amountFromMinor, escapeHtml, formatLocalDate, toIsoStartOfDay, toMinorUnits, setupModalAccessibility } from "./operations-utils.js";

const state = { user: null, workspaces: [], workspace: null, page: 0, size: 10, search: "", type: "", categoryId: "", response: null, categories: [], clients: [] };
const editable = () => ["OWNER", "ADMIN"].includes(state.workspace?.role);
const fractionDigits = () => state.workspace?.defaultCurrency === "JPY" ? 0 : 2;

function renderSkeletonRows(tbody, count = 5) {
  tbody.innerHTML = Array.from({ length: count }, () => `
    <tr class="skeleton-row" aria-hidden="true">
      <td><div class="skeleton skeleton-row-cell" style="width:65%;"></div><div class="skeleton skeleton-row-cell" style="width:40%;height:10px;margin-top:4px;"></div></td>
      <td><div class="skeleton skeleton-row-cell" style="width:50%;"></div></td>
      <td><div class="skeleton skeleton-row-cell" style="width:75%;"></div><div class="skeleton skeleton-row-cell" style="width:45%;height:10px;margin-top:4px;"></div></td>
      <td><div class="skeleton skeleton-row-cell" style="width:60%;margin-left:auto;"></div></td>
      <td><div class="skeleton skeleton-row-cell" style="width:55%;"></div></td>
      <td><div class="skeleton skeleton-row-cell" style="width:40%;margin-left:auto;"></div></td>
    </tr>
  `).join("");
}

function setBusy(isBusy) {
  const loading = document.getElementById("transactions-loading");
  if (loading) loading.classList.toggle("d-none", !isBusy);
  if (isBusy && (!state.response || state.response.content.length === 0)) {
    const body = document.getElementById("transactions-table-body");
    document.getElementById("transactions-table-wrap")?.classList.remove("d-none");
    document.getElementById("transactions-empty")?.classList.add("d-none");
    if (body) renderSkeletonRows(body);
  }
}

function setUserInfo() {
  const user = state.user;
  document.getElementById("user-display-email").textContent = user.email || "";
  document.getElementById("user-display-name").textContent = user.displayName || user.email?.split("@")[0] || "User";
  document.getElementById("user-avatar").textContent = (user.displayName?.[0] || user.email?.[0] || "U").toUpperCase();
}

function categoryOptions(type, selected = "") {
  return state.categories.filter((category) => !type || category.type === type).map((category) => `<option value="${escapeHtml(category.id)}" ${category.id === selected ? "selected" : ""}>${escapeHtml(category.name)}</option>`).join("");
}

function clientOptions(selected = "") {
  return state.clients.map((client) => `<option value="${escapeHtml(client.id)}" ${client.id === selected ? "selected" : ""}>${escapeHtml(client.name)}</option>`).join("");
}

function renderTransactions() {
  const body = document.getElementById("transactions-table-body");
  const empty = document.getElementById("transactions-empty");
  const response = state.response || { content: [], totalElements: 0, totalPages: 0 };
  document.getElementById("transactions-count").textContent = `${response.totalElements || 0} transaction${response.totalElements === 1 ? "" : "s"}`;
  document.getElementById("transactions-page-status").textContent = response.totalPages ? `Page ${response.page + 1} of ${response.totalPages}` : "No pages";
  document.getElementById("transactions-previous").disabled = state.page === 0;
  document.getElementById("transactions-next").disabled = !response.hasMore;

  if (response.content.length === 0) {
    body.innerHTML = "";
    document.getElementById("transactions-table-wrap").classList.add("d-none");
    empty.classList.remove("d-none");

    const hasFilters = Boolean(state.search || state.type || state.categoryId);
    if (hasFilters) {
      empty.innerHTML = `
        <i data-lucide="filter-x" class="icon icon-lg" style="color:var(--text-secondary);margin-bottom:8px;"></i>
        <h3>No Matching Transactions</h3>
        <p>No transaction records match your active search or type/category filters.</p>
        <button id="reset-tx-filters-btn" class="btn btn-secondary btn-sm empty-action">Reset Filters</button>
      `;
      document.getElementById("reset-tx-filters-btn")?.addEventListener("click", () => {
        document.getElementById("transaction-search").value = "";
        document.getElementById("transaction-type-filter").value = "";
        document.getElementById("transaction-category-filter").value = "";
        state.search = "";
        state.type = "";
        state.categoryId = "";
        state.page = 0;
        loadTransactions();
      });
    } else {
      empty.innerHTML = `
        <i data-lucide="receipt-text" class="icon icon-lg" style="color:var(--text-secondary);margin-bottom:8px;"></i>
        <h3>No Transactions in Workspace</h3>
        <p>No financial ledger entries exist in this workspace. Record your first transaction to initiate accounting tracking.</p>
        ${editable() ? `<button id="empty-add-tx-btn" class="btn btn-primary btn-sm empty-action"><i data-lucide="plus" class="icon icon-xs"></i><span>Add Transaction</span></button>` : ""}
      `;
      document.getElementById("empty-add-tx-btn")?.addEventListener("click", () => openTransactionModal());
    }
  } else {
    empty.classList.add("d-none");
    document.getElementById("transactions-table-wrap").classList.remove("d-none");
    body.innerHTML = response.content.map((transaction) => `
      <tr>
        <td>${formatLocalDate(transaction.transactionDate)}<div class="table-secondary">${escapeHtml(transaction.paymentMethod?.replaceAll("_", " ") || "")}</div></td>
        <td><span class="badge ${transaction.type === "INCOME" ? "badge-success" : "badge-danger"}">${escapeHtml(transaction.type)}</span></td>
        <td><strong>${escapeHtml(transaction.description || transaction.categoryName || "—")}</strong><div class="table-secondary">${escapeHtml(transaction.categoryName || "Uncategorized")}${transaction.clientName ? ` · ${escapeHtml(transaction.clientName)}` : ""}</div></td>
        <td class="text-right"><span class="${transaction.type === "INCOME" ? "amount-income" : "amount-expense"}">${transaction.type === "INCOME" ? "+" : "−"}${escapeHtml(transaction.formattedAmount || amountFromMinor(transaction.amountMinor, transaction.fractionDigits || fractionDigits()))}</span></td>
        <td><span class="badge ${transaction.status === "COMPLETED" ? "badge-success" : transaction.status === "PENDING" ? "badge-warning" : "badge-neutral"}">${escapeHtml(transaction.status)}</span></td>
        <td><div class="operations-table-actions">${editable() ? `<button class="btn btn-ghost btn-sm transaction-edit" data-id="${escapeHtml(transaction.id)}" aria-label="Edit transaction"><i data-lucide="pencil" class="icon icon-xs"></i><span>Edit</span></button>` : "<span class=\"table-secondary\">Read-only</span>"}</div></td>
      </tr>`).join("");
    body.querySelectorAll(".transaction-edit").forEach((button) => button.addEventListener("click", () => openTransactionModal(response.content.find((item) => item.id === button.dataset.id))));
  }
  window.lucide?.createIcons();
}

async function loadReferenceData() {
  const [categories, clients] = await Promise.all([fetchCategories(state.workspace.id), fetchClients(state.workspace.id, { page: 0, size: 100, status: "ACTIVE" })]);
  state.categories = categories || [];
  state.clients = clients.content || [];
  const select = document.getElementById("transaction-category-filter");
  select.innerHTML = `<option value="">All categories</option>${state.categories.map((category) => `<option value="${escapeHtml(category.id)}">${escapeHtml(category.name)}</option>`).join("")}`;
}

async function loadTransactions() {
  setBusy(true);
  try {
    state.response = await fetchTransactions(state.workspace.id, { page: state.page, size: state.size, search: state.search, type: state.type, categoryId: state.categoryId });
    if (state.page > 0 && state.response.content.length === 0 && state.response.totalElements > 0) {
      state.page -= 1;
      return loadTransactions();
    }
    renderTransactions();
    if (state.response.isWindowTruncated) {
      document.getElementById("transactions-search-warning").textContent = state.response.searchWarning;
      document.getElementById("transactions-search-warning").classList.remove("d-none");
    } else {
      document.getElementById("transactions-search-warning").classList.add("d-none");
    }
  } catch (error) {
    handleApiError(error, "Could not load transactions.");
  } finally {
    setBusy(false);
  }
}

function openTransactionModal(transaction = null) {
  if (!editable()) return;
  const editing = Boolean(transaction);
  const backdrop = document.getElementById("transaction-modal-backdrop");
  const currentType = transaction?.type || "INCOME";
  const date = transaction?.transactionDate ? new Date(transaction.transactionDate).toISOString().slice(0, 10) : new Date().toISOString().slice(0, 10);

  backdrop.innerHTML = `
    <div class="modal modal-wide" role="dialog" aria-modal="true" aria-labelledby="transaction-modal-title">
      <div class="card-header">
        <h2 id="transaction-modal-title" class="card-title">${editing ? "Edit Transaction" : "Add Transaction"}</h2>
        <button class="btn btn-ghost btn-sm modal-close" aria-label="Close dialog"><i data-lucide="x" class="icon icon-sm"></i></button>
      </div>
      <form id="transaction-form" novalidate>
        <div class="card-body">
          <div id="transaction-form-alert" class="alert alert-danger d-none" role="alert"></div>
          <div class="form-grid">
            <div class="form-group">
              <label class="form-label" for="transaction-type">Type *</label>
              <select class="form-select" id="transaction-type">
                <option value="INCOME">Income</option>
                <option value="EXPENSE">Expense</option>
              </select>
            </div>
            <div class="form-group">
              <label class="form-label" for="transaction-amount">Amount (${escapeHtml(state.workspace.defaultCurrency || "INR")}) *</label>
              <input class="form-input" id="transaction-amount" type="text" inputmode="decimal" required value="${editing ? amountFromMinor(transaction.amountMinor, transaction.fractionDigits ?? fractionDigits()) : ""}" placeholder="0.00">
              <span class="form-hint">Use up to ${fractionDigits()} decimal place${fractionDigits() === 1 ? "" : "s"}.</span>
            </div>
            <div class="form-group">
              <label class="form-label" for="transaction-category">Category *</label>
              <select class="form-select" id="transaction-category" required>${categoryOptions(currentType, transaction?.categoryId)}</select>
            </div>
            <div class="form-group">
              <label class="form-label" for="transaction-client">Client</label>
              <select class="form-select" id="transaction-client"><option value="">No client</option>${clientOptions(transaction?.clientId)}</select>
            </div>
            <div class="form-group">
              <label class="form-label" for="transaction-date">Transaction Date *</label>
              <input class="form-input" id="transaction-date" type="date" required value="${date}">
            </div>
            <div class="form-group">
              <label class="form-label" for="transaction-payment">Payment Method *</label>
              <select class="form-select" id="transaction-payment">${["BANK_TRANSFER", "CASH", "UPI", "CARD", "CHEQUE", "OTHER"].map((method) => `<option value="${method}" ${transaction?.paymentMethod === method ? "selected" : ""}>${method.replaceAll("_", " ")}</option>`).join("")}</select>
            </div>
            <div class="form-group">
              <label class="form-label" for="transaction-status">Status *</label>
              <select class="form-select" id="transaction-status">${["COMPLETED", "PENDING", "CANCELLED"].map((status) => `<option value="${status}" ${transaction?.status === status ? "selected" : ""}>${status[0] + status.slice(1).toLowerCase()}</option>`).join("")}</select>
            </div>
            <div class="form-group full-width">
              <label class="form-label" for="transaction-description">Description</label>
              <textarea class="form-textarea" id="transaction-description" maxlength="500" placeholder="Optional supporting detail">${escapeHtml(transaction?.description)}</textarea>
            </div>
          </div>
        </div>
        <div class="card-footer">
          <button type="button" class="btn btn-secondary modal-close">Cancel</button>
          <button id="transaction-submit-btn" class="btn btn-primary" type="submit">
            <span class="btn-text">${editing ? "Save Changes" : "Create Transaction"}</span>
            <span class="spinner d-none" aria-hidden="true"></span>
          </button>
        </div>
      </form>
    </div>
  `;
  backdrop.classList.remove("d-none");
  const modalEl = backdrop.querySelector(".modal");
  document.getElementById("transaction-type").value = currentType;

  let teardownAccessibility = null;
  const closeModal = () => {
    backdrop.classList.add("d-none");
    if (typeof teardownAccessibility === "function") teardownAccessibility();
  };

  teardownAccessibility = setupModalAccessibility(backdrop, modalEl, closeModal);

  backdrop.querySelectorAll(".modal-close").forEach((button) => button.addEventListener("click", closeModal));
  backdrop.addEventListener("click", (e) => {
    if (e.target === backdrop) closeModal();
  });

  document.getElementById("transaction-type").addEventListener("change", (event) => {
    document.getElementById("transaction-category").innerHTML = categoryOptions(event.target.value);
  });

  const form = document.getElementById("transaction-form");
  const submitBtn = document.getElementById("transaction-submit-btn");

  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    const alert = document.getElementById("transaction-form-alert");
    const fail = (message) => { alert.textContent = message; alert.classList.remove("d-none"); };
    alert.classList.add("d-none");

    const amountMinor = toMinorUnits(document.getElementById("transaction-amount").value, fractionDigits());
    if (!amountMinor) return fail(`Enter an amount greater than zero with no more than ${fractionDigits()} decimal places.`);
    const categoryId = document.getElementById("transaction-category").value;
    if (!categoryId) return fail("Select a category that matches the transaction type.");

    const payload = {
      type: document.getElementById("transaction-type").value,
      amountMinor,
      currency: state.workspace.defaultCurrency || "INR",
      categoryId,
      clientId: document.getElementById("transaction-client").value || null,
      description: document.getElementById("transaction-description").value.trim() || null,
      transactionDate: toIsoStartOfDay(document.getElementById("transaction-date").value),
      paymentMethod: document.getElementById("transaction-payment").value,
      status: document.getElementById("transaction-status").value
    };

    // Duplicate submission prevention
    submitBtn.disabled = true;
    const btnText = submitBtn.querySelector(".btn-text");
    const spinner = submitBtn.querySelector(".spinner");
    if (btnText) btnText.classList.add("d-none");
    if (spinner) spinner.classList.remove("d-none");

    try {
      if (editing) await updateTransaction(state.workspace.id, transaction.id, payload);
      else await createTransaction(state.workspace.id, payload);
      closeModal();
      showToast(editing ? "Transaction updated." : "Transaction created.", "success");
      await loadTransactions();
    } catch (error) {
      const msg = error.validationErrors
        ? Object.values(error.validationErrors).join(" ")
        : (error.message || "Failed to record transaction.");
      fail(msg);
    } finally {
      submitBtn.disabled = false;
      if (btnText) btnText.classList.remove("d-none");
      if (spinner) spinner.classList.add("d-none");
    }
  });

  window.lucide?.createIcons({ root: backdrop });
  document.getElementById("transaction-amount").focus();
}

function wireEvents() {
  document.getElementById("transaction-search").addEventListener("input", (event) => {
    state.search = event.target.value.trim();
    state.page = 0;
    clearTimeout(window.transactionSearchTimer);
    window.transactionSearchTimer = setTimeout(loadTransactions, 300);
  });
  document.getElementById("transaction-type-filter").addEventListener("change", (event) => {
    state.type = event.target.value;
    state.page = 0;
    loadTransactions();
  });
  document.getElementById("transaction-category-filter").addEventListener("change", (event) => {
    state.categoryId = event.target.value;
    state.page = 0;
    loadTransactions();
  });
  document.getElementById("transactions-previous").addEventListener("click", () => {
    state.page -= 1;
    loadTransactions();
  });
  document.getElementById("transactions-next").addEventListener("click", () => {
    state.page += 1;
    loadTransactions();
  });
  document.getElementById("create-transaction-btn").addEventListener("click", () => openTransactionModal());
  document.getElementById("logout-btn").addEventListener("click", async () => {
    await logout();
    window.location.replace("login.html");
  });
}

async function init() {
  window.lucide?.createIcons();
  state.user = await requireAuth("login.html");
  setUserInfo();
  state.workspaces = await fetchWorkspaces();
  state.workspace = await resolveActiveWorkspace(state.workspaces);
  if (!state.workspace) return window.location.replace("workspaces.html");
  renderWorkspaceSwitcher(document.getElementById("nav-workspace-switcher"), state.workspaces, state.workspace, async (id) => {
    setActiveWorkspaceId(id);
    state.workspace = state.workspaces.find((workspace) => workspace.id === id);
    state.page = 0;
    await loadReferenceData();
    await loadTransactions();
  });
  if (!editable()) {
    document.getElementById("create-transaction-btn").classList.add("d-none");
    document.getElementById("transactions-readonly-note").classList.remove("d-none");
  }
  await loadReferenceData();
  wireEvents();
  await loadTransactions();
}

init().catch((error) => handleApiError(error, "Could not open transaction operations."));
