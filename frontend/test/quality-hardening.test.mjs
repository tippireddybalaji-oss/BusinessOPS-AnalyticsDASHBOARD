/**
 * Automated Quality Hardening, Accessibility & Demo Readiness Test Suite
 * Executed via Node 22 built-in test runner (node --test)
 */

import { test, describe, beforeEach } from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

import { ApiError, handleApiError, setupModalAccessibility } from "../assets/js/operations-utils.js";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const FRONTEND_DIR = path.resolve(__dirname, "..");

describe("1. Centralized API Error Handling & Security Sanitization", () => {
  test("handles 0 / network failure with user-friendly connection message", () => {
    const error = new ApiError(0, "Network Error", "Failed to fetch", "/api/v1/workspaces");
    const msg = handleApiError(error, "Default fallback", false);
    assert.equal(msg, "Unable to reach the server. Please check your network connection.");
  });

  test("handles 401 Unauthorized with session expired guidance", () => {
    const error = new ApiError(401, "Unauthorized", "Token expired", "/api/v1/me");
    const msg = handleApiError(error, "Default fallback", false);
    assert.equal(msg, "Your session has expired or is unauthorized. Please sign in again.");
  });

  test("handles 403 Forbidden with workspace permission denial message", () => {
    const error = new ApiError(403, "Forbidden", "Access denied to workspace", "/api/v1/workspaces/ws-1/clients");
    const msg = handleApiError(error, "Default fallback", false);
    assert.equal(msg, "Access denied: You do not have permission to perform this action in this workspace.");
  });

  test("handles 404 Not Found with clear resource missing message", () => {
    const error = new ApiError(404, "Not Found", "Client not found", "/api/v1/workspaces/ws-1/clients/cl-999");
    const msg = handleApiError(error, "Default fallback", false);
    assert.equal(msg, "Client not found");
  });

  test("handles 422 with enforcement tier limit safeguard details", () => {
    const error = new ApiError(
      422,
      "Limit Exceeded",
      "Transaction volume exceeds standard reporting window",
      "/api/v1/workspaces/ws-1/analytics/trends",
      null,
      { enforcementTier: "TIER_1_STREAMING_LIMIT", maxAllowed: 10000 }
    );
    const msg = handleApiError(error, "Default fallback", false);
    assert.match(msg, /Processing limit reached \(TIER_1_STREAMING_LIMIT\)/);
  });

  test("handles 422 with field-level validation error formatting", () => {
    const error = new ApiError(
      422,
      "Validation Failed",
      "Invalid payload",
      "/api/v1/workspaces/ws-1/clients",
      { name: "Name cannot be blank", email: "Invalid email format" }
    );
    const msg = handleApiError(error, "Default fallback", false);
    assert.equal(msg, "Validation failed: name: Name cannot be blank, email: Invalid email format");
  });

  test("strictly sanitizes 500 server error and never leaks stack trace or internals", () => {
    const error = new ApiError(
      500,
      "Internal Server Error",
      "java.lang.NullPointerException at com.businessops.dashboard.FirestoreRepository.query:145",
      "/api/v1/workspaces/ws-1/transactions"
    );
    const msg = handleApiError(error, "Default fallback", false);
    assert.equal(msg, "A server error occurred. Please try again later.");
    assert.equal(msg.includes("NullPointerException"), false);
    assert.equal(msg.includes("FirestoreRepository"), false);
  });
});

describe("2. Modal Accessibility & Keyboard Trap (WCAG 2.1 AA)", () => {
  beforeEach(() => {
    // Minimal DOM stub for Node environment
    globalThis.listeners = {};
    globalThis.document = {
      activeElement: null,
      addEventListener(event, fn) {
        globalThis.listeners[event] = fn;
      },
      removeEventListener(event, fn) {
        delete globalThis.listeners[event];
      }
    };
  });

  test("configures role='dialog' and aria-modal='true' on modal container", () => {
    const dialogAttrs = {};
    const dialogEl = {
      setAttribute(k, v) { dialogAttrs[k] = v; },
      querySelectorAll() { return []; }
    };

    const cleanup = setupModalAccessibility(null, dialogEl, () => {});
    assert.equal(dialogAttrs["role"], "dialog");
    assert.equal(dialogAttrs["aria-modal"], "true");
    cleanup();
  });

  test("dismisses modal on Escape key press", () => {
    let closed = false;
    const dialogEl = {
      setAttribute() {},
      querySelectorAll() { return []; }
    };

    const cleanup = setupModalAccessibility(null, dialogEl, () => {
      closed = true;
    });

    assert.ok(globalThis.listeners["keydown"], "Keydown listener must be registered");
    globalThis.listeners["keydown"]({ key: "Escape", preventDefault() {} });
    assert.equal(closed, true, "Escape key must trigger modal onClose callback");
    cleanup();
  });

  test("traps Tab key navigation within modal focusable elements", () => {
    let focusedEl = null;
    const inputEl = { focus() { focusedEl = inputEl; } };
    const closeBtn = { focus() { focusedEl = closeBtn; } };
    const submitBtn = { focus() { focusedEl = submitBtn; } };

    const focusables = [inputEl, closeBtn, submitBtn];

    const dialogEl = {
      setAttribute() {},
      querySelectorAll() { return focusables; },
      contains(el) { return focusables.includes(el); }
    };

    const cleanup = setupModalAccessibility(null, dialogEl, () => {});

    // 1. Tab forward from last element (submitBtn) -> loops to first (inputEl)
    globalThis.document.activeElement = submitBtn;
    let prevented = false;
    globalThis.listeners["keydown"]({
      key: "Tab",
      shiftKey: false,
      preventDefault() { prevented = true; }
    });
    assert.equal(prevented, true);
    assert.equal(focusedEl, inputEl, "Tab on last element must cycle to first element");

    // 2. Shift+Tab backward from first element (inputEl) -> loops to last (submitBtn)
    globalThis.document.activeElement = inputEl;
    prevented = false;
    globalThis.listeners["keydown"]({
      key: "Tab",
      shiftKey: true,
      preventDefault() { prevented = true; }
    });
    assert.equal(prevented, true);
    assert.equal(focusedEl, submitBtn, "Shift+Tab on first element must cycle to last element");

    cleanup();
  });

  test("restores focus to previous active element upon modal closure", () => {
    let focusRestored = false;
    const triggerBtn = {
      focus() { focusRestored = true; }
    };
    globalThis.document.activeElement = triggerBtn;

    const dialogEl = {
      setAttribute() {},
      querySelectorAll() { return []; }
    };

    const cleanup = setupModalAccessibility(null, dialogEl, () => {});
    cleanup();

    assert.equal(focusRestored, true, "Closing modal must restore focus to trigger element");
  });
});

describe("3. Static HTML & Semantic Accessibility Audit across all views", () => {
  const pages = ["login.html", "workspaces.html", "dashboard.html", "clients.html", "transactions.html"];

  for (const page of pages) {
    test(`${page} satisfies WCAG 2.1 AA core document requirements`, () => {
      const htmlPath = path.join(FRONTEND_DIR, page);
      assert.ok(fs.existsSync(htmlPath), `File ${page} must exist`);
      const content = fs.readFileSync(htmlPath, "utf-8");

      // DOCTYPE and lang attribute
      assert.match(content, /<!DOCTYPE html>/i, `${page} must declare DOCTYPE`);
      assert.match(content, /<html\s+lang="en">/i, `${page} must specify lang="en"`);

      // Viewport meta
      assert.match(content, /<meta\s+name="viewport"\s+content="width=device-width,\s*initial-scale=1\.0"/i, `${page} must have responsive viewport`);

      // Skip link
      assert.match(content, /class="skip-link"/i, `${page} must provide a skip-link for keyboard users`);
    });
  }

  test("all data tables contain captions and scoped headers", () => {
    const tablePages = ["clients.html", "transactions.html", "dashboard.html"];
    for (const page of tablePages) {
      const content = fs.readFileSync(path.join(FRONTEND_DIR, page), "utf-8");
      assert.match(content, /<table\s+class="data-table">/i, `${page} must contain data-table`);
      assert.match(content, /<caption\s+class="visually-hidden">/i, `${page} table must have visually-hidden caption`);
      assert.match(content, /<th\s+scope="col">/i, `${page} table headers must declare scope="col"`);
    }
  });

  test("dashboard chart canvases have accessible role and labels", () => {
    const content = fs.readFileSync(path.join(FRONTEND_DIR, "dashboard.html"), "utf-8");
    assert.match(content, /<canvas[^>]*id="trends-canvas"[^>]*role="img"/i, "Trends canvas must have role='img'");
    assert.match(content, /<canvas[^>]*id="trends-canvas"[^>]*aria-label=/i, "Trends canvas must have aria-label");
    assert.match(content, /<canvas[^>]*id="category-canvas"[^>]*role="img"/i, "Category canvas must have role='img'");
    assert.match(content, /<canvas[^>]*id="category-canvas"[^>]*aria-label=/i, "Category canvas must have aria-label");
  });
});

describe("4. CSS Design System & Responsive Tokens Conformance", () => {
  test("variables.css contains mandatory enterprise color palette", () => {
    const varsCss = fs.readFileSync(path.join(FRONTEND_DIR, "assets", "css", "variables.css"), "utf-8");
    assert.match(varsCss, /--color-primary:\s*#334B68/i, "Primary color must be #334B68");
    assert.match(varsCss, /--bg-page:\s*#F5F6F8/i, "Background page color must be #F5F6F8");
    assert.match(varsCss, /--bg-surface:\s*#FFFFFF/i, "Surface color must be #FFFFFF");
    assert.match(varsCss, /--text-main:\s*#202938/i, "Main text color must be #202938");
    assert.match(varsCss, /--text-secondary:\s*#687386/i, "Secondary text color must be #687386");
    assert.match(varsCss, /--border-color:\s*#E2E6EC/i, "Border color must be #E2E6EC");
    assert.match(varsCss, /--color-success:\s*#25845B/i, "Success color must be #25845B");
    assert.match(varsCss, /--color-danger:\s*#C44B4B/i, "Danger color must be #C44B4B");
  });

  test("base.css contains skip-link and visually-hidden accessibility utilities", () => {
    const baseCss = fs.readFileSync(path.join(FRONTEND_DIR, "assets", "css", "base.css"), "utf-8");
    assert.match(baseCss, /\.skip-link\s*\{/i, "base.css must define .skip-link");
    assert.match(baseCss, /\.visually-hidden\s*\{/i, "base.css must define .visually-hidden");
    assert.match(baseCss, /:focus-visible\s*\{/i, "base.css must define accessible :focus-visible");
  });

  test("components.css provides shimmer skeleton classes and mobile touch target rules", () => {
    const compCss = fs.readFileSync(path.join(FRONTEND_DIR, "assets", "css", "components.css"), "utf-8");
    assert.match(compCss, /\.skeleton\s*\{/i, "components.css must define .skeleton");
    assert.match(compCss, /@keyframes skeletonPulse/i, "components.css must define skeletonPulse animation");
    assert.match(compCss, /min-height:\s*44px/i, "components.css must enforce >= 44px touch targets on mobile");
  });
});
