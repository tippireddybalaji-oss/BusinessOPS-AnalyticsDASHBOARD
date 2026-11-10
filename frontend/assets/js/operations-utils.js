/** Shared, dependency-free helpers for the Milestone 4 operational views. */

export function buildQueryString(params = {}) {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== null && value !== undefined && value !== "") search.set(key, value);
  });
  const serialized = search.toString();
  return serialized ? `?${serialized}` : "";
}

export function escapeHtml(value) {
  return String(value ?? "").replace(/[&<>'"]/g, (character) => ({
    "&": "&amp;", "<": "&lt;", ">": "&gt;", "'": "&#39;", '"': "&quot;"
  })[character]);
}

export function toMinorUnits(amount, fractionDigits = 2) {
  const normalized = String(amount ?? "").trim();
  if (!/^\d+(\.\d+)?$/.test(normalized)) return null;
  const [whole, fraction = ""] = normalized.split(".");
  if (fraction.length > fractionDigits) return null;
  const factor = 10 ** fractionDigits;
  const minor = Number(whole) * factor + Number(fraction.padEnd(fractionDigits, "0") || 0);
  return Number.isSafeInteger(minor) && minor > 0 ? minor : null;
}

export function amountFromMinor(amountMinor, fractionDigits = 2) {
  if (!Number.isFinite(Number(amountMinor))) return "";
  return (Number(amountMinor) / (10 ** fractionDigits)).toFixed(fractionDigits);
}

export function formatLocalDate(value) {
  if (!value) return "—";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "—" : new Intl.DateTimeFormat(undefined, {
    year: "numeric", month: "short", day: "numeric"
  }).format(date);
}

export function toIsoStartOfDay(localDate) {
  return localDate ? `${localDate}T00:00:00.000Z` : null;
}

/**
 * Sets up WCAG 2.1 AA accessibility for a modal dialog:
 * - Traps keyboard Tab navigation inside the modal container.
 * - Dismisses dialog on Escape key.
 * - Restores focus to the triggering element upon closure.
 * - Configures ARIA attributes (role="dialog", aria-modal="true").
 *
 * @param {HTMLElement} backdropEl The modal backdrop or dialog wrapper.
 * @param {HTMLElement} dialogEl The inner modal container element.
 * @param {Function} onClose Callback invoked when dialog requests closure (Escape key or backdrop click).
 * @returns {Function} cleanup function to call when the modal closes.
 */
export function setupModalAccessibility(backdropEl, dialogEl, onClose) {
  const previousActiveElement = (typeof document !== "undefined" && document.activeElement) ? document.activeElement : null;

  if (dialogEl && typeof dialogEl.setAttribute === "function") {
    dialogEl.setAttribute("role", "dialog");
    dialogEl.setAttribute("aria-modal", "true");
  }

  const focusableSelectors = [
    'a[href]', 'button:not([disabled])', 'textarea:not([disabled])',
    'input:not([disabled])', 'select:not([disabled])', '[tabindex]:not([tabindex="-1"])'
  ].join(', ');

  const getFocusableElements = () => {
    if (!dialogEl || typeof dialogEl.querySelectorAll !== "function") return [];
    return Array.from(dialogEl.querySelectorAll(focusableSelectors));
  };

  const handleKeyDown = (event) => {
    if (event.key === "Escape" || event.key === "Esc") {
      event.preventDefault();
      if (typeof onClose === "function") onClose();
      return;
    }

    if (event.key === "Tab") {
      const focusables = getFocusableElements();
      if (focusables.length === 0) {
        event.preventDefault();
        return;
      }
      const first = focusables[0];
      const last = focusables[focusables.length - 1];

      if (event.shiftKey) {
        if (document.activeElement === first || !dialogEl.contains(document.activeElement)) {
          event.preventDefault();
          last.focus();
        }
      } else {
        if (document.activeElement === last || !dialogEl.contains(document.activeElement)) {
          event.preventDefault();
          first.focus();
        }
      }
    }
  };

  if (typeof document !== "undefined" && typeof document.addEventListener === "function") {
    document.addEventListener("keydown", handleKeyDown);
  }

  // Teardown closure
  return function teardown() {
    if (typeof document !== "undefined" && typeof document.removeEventListener === "function") {
      document.removeEventListener("keydown", handleKeyDown);
    }
    if (previousActiveElement && typeof previousActiveElement.focus === "function") {
      previousActiveElement.focus();
    }
  };
}

/**
 * Structured API Error representation.
 */
export class ApiError extends Error {
  constructor(status, error, message, path, validationErrors = null, limitDetails = null) {
    super(message || error || `HTTP Error ${status}`);
    this.name = "ApiError";
    this.status = status;
    this.error = error;
    this.path = path;
    this.validationErrors = validationErrors;
    this.limitDetails = limitDetails;
  }
}

/**
 * Sanitizes and formats an API error or exception into a user-friendly message,
 * safeguarding against exposing credentials, database internals, or stack traces.
 *
 * @param {Error|ApiError|any} error
 * @param {string} [fallbackMessage="An unexpected error occurred."]
 * @param {boolean} [showToastNotification=true]
 * @param {Function} [toastFn=null] Optional toast display callback
 * @returns {string} User-safe descriptive message
 */
export function handleApiError(error, fallbackMessage = "An unexpected error occurred.", showToastNotification = true, toastFn = null) {
  let message = fallbackMessage;

  if (error instanceof ApiError || (error && typeof error.status === "number")) {
    const status = error.status;
    if (status === 0) {
      message = "Unable to reach the server. Please check your network connection.";
    } else if (status === 401) {
      message = "Your session has expired or is unauthorized. Please sign in again.";
    } else if (status === 403) {
      message = "Access denied: You do not have permission to perform this action in this workspace.";
    } else if (status === 404) {
      message = error.message && !error.message.includes("HTTP Error") ? error.message : "The requested resource was not found.";
    } else if (status === 422) {
      if (error.limitDetails?.enforcementTier) {
        message = `Processing limit reached (${error.limitDetails.enforcementTier}): ${error.message || "Please narrow your query date range."}`;
      } else if (error.validationErrors && typeof error.validationErrors === "object") {
        const details = Object.entries(error.validationErrors).map(([field, err]) => `${field}: ${err}`).join(", ");
        message = `Validation failed: ${details}`;
      } else {
        message = error.message || "The request could not be processed due to business rule validation.";
      }
    } else if (status >= 500) {
      // Strictly sanitize 500 Internal Server Error to avoid exposing backend stack traces, SQL, or Firestore internals
      message = "A server error occurred. Please try again later.";
    } else {
      if (error.validationErrors && typeof error.validationErrors === "object") {
        const details = Object.values(error.validationErrors).join(" ");
        message = details || error.message || fallbackMessage;
      } else {
        message = error.message || fallbackMessage;
      }
    }
  } else if (error instanceof Error) {
    const errText = error.message || "";
    if (errText.includes("fetch") || errText.includes("network") || errText.includes("Failed to fetch")) {
      message = "Unable to reach the server. Please check your network connection.";
    } else {
      message = error.message || fallbackMessage;
    }
  }

  if (showToastNotification) {
    if (typeof toastFn === "function") {
      toastFn(message, "error");
    } else if (typeof window !== "undefined" && typeof window.showToast === "function") {
      window.showToast(message, "error");
    }
  }

  return message;
}
