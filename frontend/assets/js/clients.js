import { requireAuth, logout } from "./auth.js";
import { showToast, handleApiError } from "./api.js";
import { fetchWorkspaces, resolveActiveWorkspace, renderWorkspaceSwitcher, setActiveWorkspaceId } from "./workspace.js";
import { createClient, fetchClients, updateClient } from "./operations-api.js";
import { escapeHtml, setupModalAccessibility } from "./operations-utils.js";

const state = { user: null, workspaces: [], workspace: null, page: 0, size: 10, search: "", status: "", response: null };
const editable = () => ["OWNER", "ADMIN"].includes(state.workspace?.role);

function setUserInfo() {
  const { user } = state;
  document.getElementById("user-display-email").textContent = user.email || "";
  document.getElementById("user-display-name").textContent = user.displayName || user.email?.split("@")[0] || "User";
  document.getElementById("user-avatar").textContent = (user.displayName?.[0] || user.email?.[0] || "U").toUpperCase();
}

function renderSkeletonRows(tbody, count = 5) {
  tbody.innerHTML = Array.from({ length: count }, () => `
    <tr class="skeleton-row" aria-hidden="true">
      <td><div class="skeleton skeleton-row-cell" style="width:70%;"></div><div class="skeleton skeleton-row-cell" style="width:40%;height:10px;margin-top:4px;"></div></td>
      <td><div class="skeleton skeleton-row-cell" style="width:80%;"></div></td>
      <td><div class="skeleton skeleton-row-cell" style="width:50%;"></div></td>
      <td><div class="skeleton skeleton-row-cell" style="width:40%;margin-left:auto;"></div></td>
    </tr>
  `).join("");
}

function setBusy(isBusy) {
  const loading = document.getElementById("clients-loading");
  if (loading) loading.classList.toggle("d-none", !isBusy);
  if (isBusy && (!state.response || state.response.content.length === 0)) {
    const body = document.getElementById("clients-table-body");
    document.getElementById("clients-table-wrap")?.classList.remove("d-none");
    document.getElementById("clients-empty")?.classList.add("d-none");
    if (body) renderSkeletonRows(body);
  }
}

function renderClients() {
  const body = document.getElementById("clients-table-body");
  const empty = document.getElementById("clients-empty");
  const response = state.response || { content: [], totalElements: 0, totalPages: 0 };
  document.getElementById("clients-count").textContent = `${response.totalElements || 0} client${response.totalElements === 1 ? "" : "s"}`;
  document.getElementById("clients-page-status").textContent = response.totalPages ? `Page ${response.page + 1} of ${response.totalPages}` : "No pages";
  document.getElementById("clients-previous").disabled = state.page === 0;
  document.getElementById("clients-next").disabled = !response.hasMore;

  if (response.content.length === 0) {
    body.innerHTML = "";
    document.getElementById("clients-table-wrap").classList.add("d-none");
    empty.classList.remove("d-none");

    const hasFilters = Boolean(state.search || state.status);
    if (hasFilters) {
      empty.innerHTML = `
        <i data-lucide="filter-x" class="icon icon-lg" style="color:var(--text-secondary);margin-bottom:8px;"></i>
        <h3>No Matching Clients</h3>
        <p>No client records match "${escapeHtml(state.search || state.status)}". Try adjusting your search query or status filter.</p>
        <button id="reset-client-filters-btn" class="btn btn-secondary btn-sm empty-action">Reset Filters</button>
      `;
      document.getElementById("reset-client-filters-btn")?.addEventListener("click", () => {
        document.getElementById("client-search").value = "";
        document.getElementById("client-status-filter").value = "";
        state.search = "";
        state.status = "";
        state.page = 0;
        loadClients();
      });
    } else {
      empty.innerHTML = `
        <i data-lucide="users" class="icon icon-lg" style="color:var(--text-secondary);margin-bottom:8px;"></i>
        <h3>No Clients in Workspace</h3>
        <p>You haven't added any clients to this workspace yet. Add your first client to manage contracts and customer details.</p>
        ${editable() ? `<button id="empty-add-client-btn" class="btn btn-primary btn-sm empty-action"><i data-lucide="user-plus" class="icon icon-xs"></i><span>Add Client</span></button>` : ""}
      `;
      document.getElementById("empty-add-client-btn")?.addEventListener("click", () => openClientModal());
    }
  } else {
    empty.classList.add("d-none");
    document.getElementById("clients-table-wrap").classList.remove("d-none");
    body.innerHTML = response.content.map((client) => `
      <tr>
        <td><strong>${escapeHtml(client.name)}</strong><div class="table-secondary">${escapeHtml(client.company || "—")}</div></td>
        <td>${escapeHtml(client.email || "—")}<div class="table-secondary">${escapeHtml(client.phone || "")}</div></td>
        <td><span class="badge ${client.status === "ACTIVE" ? "badge-success" : client.status === "LEAD" ? "badge-info" : "badge-neutral"}">${escapeHtml(client.status)}</span></td>
        <td><div class="operations-table-actions">${editable() ? `<button class="btn btn-ghost btn-sm client-edit" data-id="${escapeHtml(client.id)}" aria-label="Edit ${escapeHtml(client.name)}"><i data-lucide="pencil" class="icon icon-xs"></i><span>Edit</span></button>` : "<span class=\"table-secondary\">Read-only</span>"}</div></td>
      </tr>`).join("");
    body.querySelectorAll(".client-edit").forEach((button) => button.addEventListener("click", () => {
      openClientModal(response.content.find((client) => client.id === button.dataset.id));
    }));
  }
  window.lucide?.createIcons();
}

async function loadClients() {
  setBusy(true);
  try {
    state.response = await fetchClients(state.workspace.id, { page: state.page, size: state.size, search: state.search, status: state.status });
    if (state.page > 0 && state.response.content.length === 0 && state.response.totalElements > 0) {
      state.page -= 1;
      return loadClients();
    }
    renderClients();
  } catch (error) {
    handleApiError(error, "Could not load clients.");
  } finally { setBusy(false); }
}

function openClientModal(client = null) {
  if (!editable()) return;
  const isEditing = Boolean(client);
  const backdrop = document.getElementById("client-modal-backdrop");
  backdrop.innerHTML = `
    <div class="modal" role="dialog" aria-modal="true" aria-labelledby="client-modal-title">
      <div class="card-header">
        <h2 id="client-modal-title" class="card-title">${isEditing ? "Edit Client" : "Add Client"}</h2>
        <button class="btn btn-ghost btn-sm modal-close" aria-label="Close dialog"><i data-lucide="x" class="icon icon-sm"></i></button>
      </div>
      <form id="client-form" novalidate>
        <div class="card-body">
          <div id="client-form-alert" class="alert alert-danger d-none" role="alert"></div>
          <div class="form-grid">
            <div class="form-group">
              <label class="form-label" for="client-name">Client Name *</label>
              <input class="form-input" id="client-name" maxlength="150" required value="${escapeHtml(client?.name)}">
            </div>
            <div class="form-group">
              <label class="form-label" for="client-company">Company</label>
              <input class="form-input" id="client-company" maxlength="150" value="${escapeHtml(client?.company)}">
            </div>
            <div class="form-group">
              <label class="form-label" for="client-email">Email</label>
              <input class="form-input" id="client-email" type="email" maxlength="254" value="${escapeHtml(client?.email)}">
            </div>
            <div class="form-group">
              <label class="form-label" for="client-phone">Phone</label>
              <input class="form-input" id="client-phone" maxlength="30" value="${escapeHtml(client?.phone)}">
            </div>
            <div class="form-group">
              <label class="form-label" for="client-status">Status *</label>
              <select class="form-select" id="client-status">
                <option value="ACTIVE">Active</option>
                <option value="LEAD">Lead</option>
                <option value="INACTIVE">Inactive</option>
              </select>
            </div>
            <div class="form-group full-width">
              <label class="form-label" for="client-notes">Notes</label>
              <textarea class="form-textarea" id="client-notes" maxlength="2000">${escapeHtml(client?.notes)}</textarea>
            </div>
          </div>
        </div>
        <div class="card-footer">
          <button type="button" class="btn btn-secondary modal-close">Cancel</button>
          <button id="client-submit-btn" class="btn btn-primary" type="submit">
            <span class="btn-text">${isEditing ? "Save Changes" : "Create Client"}</span>
            <span class="spinner d-none" aria-hidden="true"></span>
          </button>
        </div>
      </form>
    </div>
  `;
  backdrop.classList.remove("d-none");
  const modalEl = backdrop.querySelector(".modal");
  document.getElementById("client-status").value = client?.status || "ACTIVE";

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

  const form = document.getElementById("client-form");
  const submitBtn = document.getElementById("client-submit-btn");

  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    const name = document.getElementById("client-name").value.trim();
    const email = document.getElementById("client-email").value.trim();
    const alert = document.getElementById("client-form-alert");
    const fail = (message) => { alert.textContent = message; alert.classList.remove("d-none"); };
    alert.classList.add("d-none");

    if (!name) return fail("Client name is required.");
    if (email && !document.getElementById("client-email").checkValidity()) return fail("Enter a valid email address.");

    const payload = {
      name,
      company: document.getElementById("client-company").value.trim() || null,
      email: email || null,
      phone: document.getElementById("client-phone").value.trim() || null,
      status: document.getElementById("client-status").value,
      notes: document.getElementById("client-notes").value.trim() || null
    };

    // Duplicate submission prevention
    submitBtn.disabled = true;
    const btnText = submitBtn.querySelector(".btn-text");
    const spinner = submitBtn.querySelector(".spinner");
    if (btnText) btnText.classList.add("d-none");
    if (spinner) spinner.classList.remove("d-none");

    try {
      if (isEditing) await updateClient(state.workspace.id, client.id, payload);
      else await createClient(state.workspace.id, payload);
      closeModal();
      showToast(isEditing ? "Client updated." : "Client created.", "success");
      await loadClients();
    } catch (error) {
      const msg = error.validationErrors
        ? Object.values(error.validationErrors).join(" ")
        : (error.message || "Failed to save client.");
      fail(msg);
    } finally {
      submitBtn.disabled = false;
      if (btnText) btnText.classList.remove("d-none");
      if (spinner) spinner.classList.add("d-none");
    }
  });

  window.lucide?.createIcons({ root: backdrop });
  document.getElementById("client-name").focus();
}

function wireEvents() {
  document.getElementById("client-search").addEventListener("input", (event) => { state.search = event.target.value.trim(); state.page = 0; clearTimeout(window.clientSearchTimer); window.clientSearchTimer = setTimeout(loadClients, 300); });
  document.getElementById("client-status-filter").addEventListener("change", (event) => { state.status = event.target.value; state.page = 0; loadClients(); });
  document.getElementById("clients-previous").addEventListener("click", () => { state.page -= 1; loadClients(); });
  document.getElementById("clients-next").addEventListener("click", () => { state.page += 1; loadClients(); });
  document.getElementById("create-client-btn").addEventListener("click", () => openClientModal());
  document.getElementById("logout-btn").addEventListener("click", async () => { await logout(); window.location.replace("login.html"); });
}

async function init() {
  window.lucide?.createIcons();
  state.user = await requireAuth("login.html");
  setUserInfo();
  state.workspaces = await fetchWorkspaces(); state.workspace = await resolveActiveWorkspace(state.workspaces);
  if (!state.workspace) return window.location.replace("workspaces.html");
  renderWorkspaceSwitcher(document.getElementById("nav-workspace-switcher"), state.workspaces, state.workspace, async (id) => { setActiveWorkspaceId(id); state.workspace = state.workspaces.find((workspace) => workspace.id === id); state.page = 0; await loadClients(); });
  if (!editable()) { document.getElementById("create-client-btn").classList.add("d-none"); document.getElementById("clients-readonly-note").classList.remove("d-none"); }
  wireEvents(); await loadClients();
}

init().catch((error) => showToast(`Could not open client operations: ${error.message}`, "error"));
