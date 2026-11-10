/**
 * BusinessOPS Analytics Dashboard - Workspace Management Module
 *
 * Handles workspace listing, creation, selection, persistence,
 * and header workspace selector components.
 */

import { fetchWithAuth, showToast, handleApiError } from "./api.js";
import { setupModalAccessibility } from "./operations-utils.js";

const STORAGE_KEY_ACTIVE_WORKSPACE = "businessops_active_workspace_id";
const STORAGE_KEY_CACHED_WORKSPACE = "businessops_cached_workspace";

/**
 * Retrieves all accessible workspaces for the authenticated user.
 * Endpoint: GET /api/v1/workspaces
 * @returns {Promise<Array<{id: string, name: string, ownerUid: string, defaultCurrency: string, role: string, createdAt: string}>>}
 */
export async function fetchWorkspaces() {
  return await fetchWithAuth("/api/v1/workspaces");
}

/**
 * Retrieves workspace details by ID after server authorization verification.
 * Endpoint: GET /api/v1/workspaces/{id}
 * @param {string} workspaceId
 * @returns {Promise<object>}
 */
export async function fetchWorkspaceById(workspaceId) {
  return await fetchWithAuth(`/api/v1/workspaces/${workspaceId}`);
}

/**
 * Creates a new business workspace. The creator automatically receives the OWNER role.
 * Endpoint: POST /api/v1/workspaces
 * @param {string} name 2-100 characters
 * @param {string} [defaultCurrency="INR"] ISO-4217 currency code
 * @returns {Promise<object>} created workspace response
 */
export async function createWorkspace(name, defaultCurrency = "INR") {
  const payload = {
    name: name.trim(),
    defaultCurrency: defaultCurrency.trim().toUpperCase()
  };
  const created = await fetchWithAuth("/api/v1/workspaces", {
    method: "POST",
    body: JSON.stringify(payload)
  });

  // Automatically activate the newly created workspace
  setActiveWorkspaceId(created.id);
  cacheWorkspaceData(created);
  return created;
}

/**
 * Returns the currently active workspace ID from localStorage.
 * @returns {string|null}
 */
export function getActiveWorkspaceId() {
  return localStorage.getItem(STORAGE_KEY_ACTIVE_WORKSPACE);
}

/**
 * Sets and persists the active workspace ID.
 * @param {string} workspaceId
 */
export function setActiveWorkspaceId(workspaceId) {
  if (workspaceId) {
    localStorage.setItem(STORAGE_KEY_ACTIVE_WORKSPACE, workspaceId);
  } else {
    localStorage.removeItem(STORAGE_KEY_ACTIVE_WORKSPACE);
  }
}

/**
 * Caches workspace metadata locally for instant display.
 * @param {object} workspace
 */
export function cacheWorkspaceData(workspace) {
  if (workspace) {
    localStorage.setItem(STORAGE_KEY_CACHED_WORKSPACE, JSON.stringify(workspace));
  } else {
    localStorage.removeItem(STORAGE_KEY_CACHED_WORKSPACE);
  }
}

/**
 * Retrieves cached workspace metadata.
 * @returns {object|null}
 */
export function getCachedWorkspaceData() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY_CACHED_WORKSPACE);
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
}

/**
 * Ensures a valid active workspace is selected.
 * If active workspace is not in user's accessible list, defaults to the first available workspace.
 * @param {Array<object>} [workspaces]
 * @returns {Promise<object|null>} active workspace object, or null if user has 0 workspaces
 */
export async function resolveActiveWorkspace(workspaces = null) {
  const list = workspaces || await fetchWorkspaces();
  if (!list || list.length === 0) {
    setActiveWorkspaceId(null);
    cacheWorkspaceData(null);
    return null;
  }

  const currentId = getActiveWorkspaceId();
  let active = list.find(w => w.id === currentId);

  // If no active workspace or current ID not in accessible list, pick the first
  if (!active) {
    active = list[0];
    setActiveWorkspaceId(active.id);
  }

  cacheWorkspaceData(active);
  return active;
}

/**
 * Renders an accessible workspace switcher dropdown in the navigation bar.
 * @param {HTMLElement} container
 * @param {Array<object>} workspaces
 * @param {object} activeWorkspace
 * @param {function(workspaceId: string)} onWorkspaceChange
 */
export function renderWorkspaceSwitcher(container, workspaces, activeWorkspace, onWorkspaceChange) {
  if (!container) return;

  const roleClass = activeWorkspace?.role === "OWNER"
    ? "badge-primary"
    : activeWorkspace?.role === "ADMIN"
    ? "badge-info"
    : "badge-neutral";

  container.innerHTML = `
    <div class="workspace-switcher-wrapper" style="position:relative;display:inline-block;">
      <button id="ws-switcher-btn" class="btn btn-secondary btn-sm" style="display:flex;align-items:center;gap:8px;" aria-haspopup="true" aria-expanded="false">
        <span class="status-dot"></span>
        <strong id="active-ws-name">${activeWorkspace ? activeWorkspace.name : "Select Workspace"}</strong>
        <span class="badge ${roleClass}">${activeWorkspace ? activeWorkspace.role : "None"}</span>
        <i data-lucide="chevron-down" class="icon icon-xs" style="color:var(--text-secondary);"></i>
      </button>

      <div id="ws-dropdown-menu" class="card shadow-md" style="display:none;position:absolute;top:calc(100% + 6px);left:0;min-width:260px;z-index:60;padding:6px;border-radius:var(--radius-md);">
        <div style="padding:6px 10px;font-size:11px;font-weight:600;color:var(--text-secondary);text-transform:uppercase;letter-spacing:0.5px;">
          Your Workspaces
        </div>
        <div id="ws-list-items" style="max-height:220px;overflow-y:auto;display:flex;flex-direction:column;gap:2px;">
          ${workspaces.map(w => `
            <button class="ws-option-item btn btn-ghost btn-sm" data-ws-id="${w.id}" style="width:100%;justify-content:space-between;padding:8px 10px;${w.id === activeWorkspace?.id ? 'background-color:var(--color-primary-light);font-weight:600;' : ''}">
              <div style="display:flex;align-items:center;gap:6px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;">
                <span>${w.name}</span>
                <span class="badge badge-neutral" style="font-size:10px;">${w.defaultCurrency || 'INR'}</span>
              </div>
              <span class="badge ${w.role === 'OWNER' ? 'badge-primary' : 'badge-neutral'}" style="font-size:10px;">${w.role}</span>
            </button>
          `).join("")}
        </div>
        <div style="border-top:1px solid var(--border-color);margin-top:6px;padding-top:6px;">
          <button id="ws-create-new-btn" class="btn btn-ghost btn-sm btn-block" style="color:var(--color-primary);font-weight:600;justify-content:flex-start;gap:6px;">
            <i data-lucide="plus" class="icon icon-xs"></i>
            <span>Create New Workspace</span>
          </button>
        </div>
      </div>
    </div>
  `;

  if (window.lucide && typeof window.lucide.createIcons === "function") {
    window.lucide.createIcons({ root: container });
  }

  const btn = container.querySelector("#ws-switcher-btn");
  const menu = container.querySelector("#ws-dropdown-menu");

  const toggleDropdown = () => {
    const isHidden = menu.style.display === "none";
    menu.style.display = isHidden ? "block" : "none";
    btn.setAttribute("aria-expanded", isHidden ? "true" : "false");
  };

  btn.addEventListener("click", (e) => {
    e.stopPropagation();
    toggleDropdown();
  });

  // Close when clicking outside
  document.addEventListener("click", () => {
    menu.style.display = "none";
    btn.setAttribute("aria-expanded", "false");
  });

  // Workspace selection handler
  container.querySelectorAll(".ws-option-item").forEach(item => {
    item.addEventListener("click", (e) => {
      e.stopPropagation();
      const newWsId = item.getAttribute("data-ws-id");
      if (newWsId && newWsId !== activeWorkspace?.id) {
        setActiveWorkspaceId(newWsId);
        const selected = workspaces.find(w => w.id === newWsId);
        cacheWorkspaceData(selected);
        menu.style.display = "none";
        if (typeof onWorkspaceChange === "function") {
          onWorkspaceChange(newWsId);
        } else {
          window.location.reload();
        }
      } else {
        menu.style.display = "none";
      }
    });
  });

  // Create new workspace modal trigger
  const createBtn = container.querySelector("#ws-create-new-btn");
  if (createBtn) {
    createBtn.addEventListener("click", (e) => {
      e.stopPropagation();
      menu.style.display = "none";
      openCreateWorkspaceModal(onWorkspaceChange);
    });
  }
}

/**
 * Opens a modal to create a new workspace.
 * @param {function(newWorkspaceId: string)} [onCreated]
 */
export function openCreateWorkspaceModal(onCreated) {
  let modalBackdrop = document.getElementById("create-ws-modal-backdrop");
  if (!modalBackdrop) {
    modalBackdrop = document.createElement("div");
    modalBackdrop.id = "create-ws-modal-backdrop";
    modalBackdrop.className = "modal-backdrop";
    document.body.appendChild(modalBackdrop);
  }

  modalBackdrop.innerHTML = `
    <div class="modal" role="dialog" aria-modal="true" aria-labelledby="create-ws-modal-title">
      <div class="card-header">
        <h2 id="create-ws-modal-title" class="card-title">Create Workspace</h2>
        <button id="close-ws-modal-btn" class="btn btn-ghost btn-sm" aria-label="Close dialog" style="padding:4px;">
          <i data-lucide="x" class="icon icon-sm"></i>
        </button>
      </div>
      <form id="create-ws-form" novalidate>
        <div class="card-body">
          <div id="ws-modal-alert" class="alert alert-danger d-none" role="alert"></div>

          <div class="form-group">
            <label class="form-label" for="ws-name-input">Workspace Name *</label>
            <input type="text" id="ws-name-input" class="form-input" placeholder="e.g. Acme Operations" minlength="2" maxlength="100" required autofocus />
            <span class="form-hint">2 to 100 characters. Identifies your business entity.</span>
          </div>

          <div class="form-group">
            <label class="form-label" for="ws-currency-select">Default Currency *</label>
            <select id="ws-currency-select" class="form-select" required>
              <option value="INR" selected>INR (₹) - Indian Rupee (Default)</option>
              <option value="USD">USD ($) - US Dollar</option>
              <option value="EUR">EUR (€) - Euro</option>
              <option value="GBP">GBP (£) - British Pound</option>
              <option value="JPY">JPY (¥) - Japanese Yen (Zero-decimal)</option>
              <option value="AUD">AUD ($) - Australian Dollar</option>
              <option value="CAD">CAD ($) - Canadian Dollar</option>
              <option value="SGD">SGD ($) - Singapore Dollar</option>
            </select>
            <span class="form-hint">Determines the accounting base currency for financial analytics.</span>
          </div>
        </div>

        <div class="card-footer">
          <button type="button" id="cancel-ws-modal-btn" class="btn btn-secondary">Cancel</button>
          <button type="submit" id="submit-ws-btn" class="btn btn-primary">
            <span id="submit-ws-text" style="display:flex;align-items:center;gap:6px;">
              <i data-lucide="plus" class="icon icon-xs"></i>
              <span>Create Workspace</span>
            </span>
            <span id="submit-ws-spinner" class="spinner d-none" aria-hidden="true"></span>
          </button>
        </div>
      </form>
    </div>
  `;

  if (window.lucide && typeof window.lucide.createIcons === "function") {
    window.lucide.createIcons({ root: modalBackdrop });
  }

  modalBackdrop.style.display = "flex";
  const modalEl = modalBackdrop.querySelector(".modal");

  let teardownAccessibility = null;
  const close = () => {
    modalBackdrop.style.display = "none";
    if (typeof teardownAccessibility === "function") teardownAccessibility();
  };

  teardownAccessibility = setupModalAccessibility(modalBackdrop, modalEl, close);

  modalBackdrop.querySelector("#close-ws-modal-btn").addEventListener("click", close);
  modalBackdrop.querySelector("#cancel-ws-modal-btn").addEventListener("click", close);
  modalBackdrop.addEventListener("click", (e) => {
    if (e.target === modalBackdrop) close();
  });

  const form = modalBackdrop.querySelector("#create-ws-form");
  const alertEl = modalBackdrop.querySelector("#ws-modal-alert");
  const submitBtn = modalBackdrop.querySelector("#submit-ws-btn");
  const submitText = modalBackdrop.querySelector("#submit-ws-text");
  const submitSpinner = modalBackdrop.querySelector("#submit-ws-spinner");

  form.addEventListener("submit", async (e) => {
    e.preventDefault();
    alertEl.classList.add("d-none");
    const name = modalBackdrop.querySelector("#ws-name-input").value.trim();
    const currency = modalBackdrop.querySelector("#ws-currency-select").value;

    if (name.length < 2 || name.length > 100) {
      alertEl.textContent = "Workspace name must be between 2 and 100 characters.";
      alertEl.classList.remove("d-none");
      return;
    }

    submitBtn.disabled = true;
    submitText.classList.add("d-none");
    submitSpinner.classList.remove("d-none");

    try {
      const created = await createWorkspace(name, currency);
      showToast(`Workspace "${created.name}" created successfully!`, "success");
      close();
      if (typeof onCreated === "function") {
        onCreated(created.id);
      } else {
        window.location.reload();
      }
    } catch (err) {
      const message = handleApiError(err, "Failed to create workspace.", false);
      alertEl.textContent = message;
      alertEl.classList.remove("d-none");
    } finally {
      submitBtn.disabled = false;
      submitText.classList.remove("d-none");
      submitSpinner.classList.add("d-none");
    }
  });
}

