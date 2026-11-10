/**
 * BusinessOPS Analytics Dashboard - Authenticated API Client
 *
 * Centralized HTTP utility with Firebase Bearer token injection,
 * one-time HTTP 401 token refresh and retry, uniform error handling,
 * and standard DTO deserialization.
 */

import { getIdToken, logout } from "./auth.js";
import { ApiError, handleApiError as formatApiError } from "./operations-utils.js";

export { ApiError };

// Determine API base URL (defaults to relative origin if served on 8080, 3000, 5500, or explicitly configured, else http://localhost:8080)
export const API_BASE_URL = typeof window !== "undefined" && window.API_BASE_URL !== undefined
  ? window.API_BASE_URL
  : (typeof window !== "undefined" && window.location && (window.location.port === "8080" || window.location.port === "3000" || window.location.port === "5500") ? "" : "http://localhost:8080");

/**
 * Performs an authenticated HTTP fetch against the backend REST API.
 * Automatically injects the Firebase Bearer token and executes a one-time
 * token refresh retry if the server responds with HTTP 401 Unauthorized.
 *
 * @param {string} endpoint e.g. "/api/v1/me" or "/api/v1/workspaces"
 * @param {RequestInit} [options={}]
 * @returns {Promise<any>} parsed JSON body, or null for 204 No Content
 */
export async function fetchWithAuth(endpoint, options = {}) {
  const url = endpoint.startsWith("http") ? endpoint : `${API_BASE_URL}${endpoint}`;
  
  // 1. Acquire current ID token
  let token = await getIdToken(false);
  if (!token) {
    throw new ApiError(401, "Unauthorized", "User is not authenticated. Please log in.", endpoint);
  }

  // 2. Prepare request headers
  const headers = new Headers(options.headers || {});
  headers.set("Authorization", `Bearer ${token}`);
  if (!headers.has("Accept")) {
    headers.set("Accept", "application/json");
  }
  if (options.body && typeof options.body === "string" && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }

  // 3. Initial fetch attempt
  let response;
  try {
    response = await fetch(url, { ...options, headers });
  } catch (networkError) {
    throw new ApiError(0, "Network Error", networkError.message || "Unable to reach server", endpoint);
  }

  // 4. One-time 401 token refresh & retry safeguard
  if (response.status === 401) {
    console.warn(`[API] Received 401 on ${endpoint}. Attempting forced token refresh...`);
    const freshToken = await getIdToken(true);
    
    if (freshToken && freshToken !== token) {
      headers.set("Authorization", `Bearer ${freshToken}`);
      try {
        response = await fetch(url, { ...options, headers });
      } catch (retryError) {
        throw new ApiError(0, "Network Error", retryError.message, endpoint);
      }
    }

    // If still 401 after forced refresh, session is invalid -> trigger logout and redirect
    if (response.status === 401) {
      console.error(`[API] 401 persisted after token refresh on ${endpoint}. Redirecting to login.`);
      await logout();
      if (!window.location.pathname.endsWith("login.html")) {
        window.location.replace("login.html?session_expired=true");
      }
      throw new ApiError(401, "Session Expired", "Your session has expired. Please log in again.", endpoint);
    }
  }

  // 5. Handle HTTP 204 No Content
  if (response.status === 204) {
    return null;
  }

  // 6. Parse response payload
  let data;
  const contentType = response.headers.get("content-type");
  if (contentType && contentType.includes("application/json")) {
    data = await response.json();
  } else {
    data = await response.text();
  }

  // 7. Check for errors
  if (!response.ok) {
    const status = response.status;
    const errorTitle = typeof data === "object" ? (data.error || "Request Failed") : "Request Failed";
    const errorMessage = typeof data === "object" ? (data.message || errorTitle) : data;
    const validationErrors = typeof data === "object" ? data.validationErrors : null;
    const limitDetails = typeof data === "object" ? data.limitDetails : null;

    throw new ApiError(status, errorTitle, errorMessage, endpoint, validationErrors, limitDetails);
  }

  return data;
}

/**
 * Retrieves the authenticated caller's verified profile basics.
 * Endpoint: GET /api/v1/me
 * @returns {Promise<{uid: string, email: string}>}
 */
export async function fetchUserProfile() {
  return await fetchWithAuth("/api/v1/me");
}

/**
 * Toast Notification Utility
 * @param {string} message
 * @param {"success"|"error"|"warning"|"info"} [type="info"]
 * @param {number} [duration=4000]
 */
export function showToast(message, type = "info", duration = 4000) {
  let container = document.getElementById("toast-container");
  if (!container) {
    container = document.createElement("div");
    container.id = "toast-container";
    document.body.appendChild(container);
  }

  const iconName = type === "success" ? "check-circle-2" : type === "error" ? "alert-circle" : "info";
  const toast = document.createElement("div");
  toast.className = `toast toast-${type}`;
  toast.innerHTML = `
    <div style="display:flex;align-items:center;gap:8px;">
      <i data-lucide="${iconName}" class="icon icon-sm"></i>
      <span>${message}</span>
    </div>
    <button class="toast-close-btn" style="background:none;border:none;color:inherit;cursor:pointer;padding:2px;display:flex;align-items:center;" aria-label="Close">
      <i data-lucide="x" class="icon icon-xs"></i>
    </button>
  `;

  if (window.lucide && typeof window.lucide.createIcons === "function") {
    window.lucide.createIcons({ root: toast });
  }

  const closeBtn = toast.querySelector(".toast-close-btn");
  const removeToast = () => {
    toast.style.opacity = "0";
    toast.style.transform = "translateY(8px)";
    setTimeout(() => toast.remove(), 200);
  };

  closeBtn.addEventListener("click", removeToast);
  container.appendChild(toast);

  if (duration > 0) {
    setTimeout(removeToast, duration);
  }
}

/**
 * Sanitizes and formats an API error or exception into a user-friendly message,
 * safeguarding against exposing credentials, database internals, or stack traces.
 *
 * @param {Error|ApiError|any} error
 * @param {string} [fallbackMessage="An unexpected error occurred."]
 * @param {boolean} [showToastNotification=true]
 * @returns {string} User-safe descriptive message
 */
export function handleApiError(error, fallbackMessage = "An unexpected error occurred.", showToastNotification = true) {
  return formatApiError(error, fallbackMessage, showToastNotification, showToast);
}

