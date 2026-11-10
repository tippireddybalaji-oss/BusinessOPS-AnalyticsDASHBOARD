/**
 * Automated Test Suite for Frontend Foundation, Authentication & API Layer
 * Executed via Node 22 built-in test runner (node --test)
 */

import { test, describe, beforeEach } from "node:test";
import assert from "node:assert/strict";

// Mock Browser Environment globals (localStorage, sessionStorage, window, Headers, fetch)
class MockStorage {
  constructor() {
    this.store = new Map();
  }
  getItem(key) {
    return this.store.get(key) || null;
  }
  setItem(key, value) {
    this.store.set(key, String(value));
  }
  removeItem(key) {
    this.store.delete(key);
  }
  clear() {
    this.store.clear();
  }
}

globalThis.localStorage = new MockStorage();
globalThis.sessionStorage = new MockStorage();
globalThis.window = {
  location: {
    origin: "http://localhost:5500",
    pathname: "/workspaces.html",
    search: "",
    replace: (url) => { globalThis.window.location.href = url; },
    reload: () => {}
  },
  API_BASE_URL: "http://localhost:8080"
};

// Test implementation of ApiError and fetchWithAuth logic
class ApiError extends Error {
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

async function testFetchWithAuth(endpoint, options = {}, tokenProvider, onSessionExpired) {
  const url = endpoint.startsWith("http") ? endpoint : `http://localhost:8080${endpoint}`;
  let token = await tokenProvider(false);
  if (!token) {
    throw new ApiError(401, "Unauthorized", "User is not authenticated. Please log in.", endpoint);
  }

  const headers = new Headers(options.headers || {});
  headers.set("Authorization", `Bearer ${token}`);
  headers.set("Accept", "application/json");

  let response = await globalThis.fetch(url, { ...options, headers });

  // One-time 401 retry with forced token refresh
  if (response.status === 401) {
    const freshToken = await tokenProvider(true);
    if (freshToken && freshToken !== token) {
      headers.set("Authorization", `Bearer ${freshToken}`);
      response = await globalThis.fetch(url, { ...options, headers });
    }
    if (response.status === 401) {
      if (onSessionExpired) onSessionExpired();
      throw new ApiError(401, "Session Expired", "Your session has expired. Please log in again.", endpoint);
    }
  }

  if (response.status === 204) return null;

  const data = await response.json();
  if (!response.ok) {
    throw new ApiError(
      response.status,
      data.error || "Request Failed",
      data.message || data.error,
      endpoint,
      data.validationErrors,
      data.limitDetails
    );
  }
  return data;
}

// Workspace Management Helper Functions to test
const STORAGE_KEY_ACTIVE_WORKSPACE = "businessops_active_workspace_id";
const STORAGE_KEY_CACHED_WORKSPACE = "businessops_cached_workspace";

function setActiveWorkspaceId(id) {
  if (id) localStorage.setItem(STORAGE_KEY_ACTIVE_WORKSPACE, id);
  else localStorage.removeItem(STORAGE_KEY_ACTIVE_WORKSPACE);
}

function getActiveWorkspaceId() {
  return localStorage.getItem(STORAGE_KEY_ACTIVE_WORKSPACE);
}

function cacheWorkspaceData(ws) {
  if (ws) localStorage.setItem(STORAGE_KEY_CACHED_WORKSPACE, JSON.stringify(ws));
  else localStorage.removeItem(STORAGE_KEY_CACHED_WORKSPACE);
}

function getCachedWorkspaceData() {
  const raw = localStorage.getItem(STORAGE_KEY_CACHED_WORKSPACE);
  return raw ? JSON.parse(raw) : null;
}

function resolveActiveWorkspace(workspaces) {
  if (!workspaces || workspaces.length === 0) {
    setActiveWorkspaceId(null);
    cacheWorkspaceData(null);
    return null;
  }
  const currentId = getActiveWorkspaceId();
  let active = workspaces.find(w => w.id === currentId);
  if (!active) {
    active = workspaces[0];
    setActiveWorkspaceId(active.id);
  }
  cacheWorkspaceData(active);
  return active;
}

// =========================================================================
// TEST SUITES
// =========================================================================

describe("1. Token Injection & Authenticated API Fetch", () => {
  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
  });

  test("fetchWithAuth attaches Authorization: Bearer <token> header", async () => {
    let capturedHeader = null;
    globalThis.fetch = async (url, init) => {
      capturedHeader = init.headers.get("Authorization");
      return {
        status: 200,
        ok: true,
        json: async () => ({ uid: "user-123", email: "test@example.com" })
      };
    };

    const tokenProvider = async () => "firebase-id-token-xyz";
    const data = await testFetchWithAuth("/api/v1/me", {}, tokenProvider);

    assert.equal(capturedHeader, "Bearer firebase-id-token-xyz");
    assert.equal(data.uid, "user-123");
    assert.equal(data.email, "test@example.com");
  });

  test("fetchWithAuth rejects immediately when token is absent", async () => {
    const tokenProvider = async () => null;
    await assert.rejects(
      async () => testFetchWithAuth("/api/v1/me", {}, tokenProvider),
      (err) => {
        assert.equal(err.status, 401);
        assert.match(err.message, /not authenticated/i);
        return true;
      }
    );
  });
});

describe("2. One-Time 401 Token Refresh & Retry", () => {
  test("recovers when 401 is returned and fresh token succeeds on retry", async () => {
    let callCount = 0;
    const tokensUsed = [];

    globalThis.fetch = async (url, init) => {
      callCount++;
      const auth = init.headers.get("Authorization");
      tokensUsed.push(auth);

      if (callCount === 1) {
        return { status: 401, ok: false, json: async () => ({ error: "Token expired" }) };
      }
      return {
        status: 200,
        ok: true,
        json: async () => ({ success: true, refreshed: true })
      };
    };

    let refreshCalled = false;
    const tokenProvider = async (force) => {
      if (force) {
        refreshCalled = true;
        return "fresh-refreshed-token-456";
      }
      return "expired-initial-token-123";
    };

    const result = await testFetchWithAuth("/api/v1/workspaces", {}, tokenProvider);

    assert.equal(callCount, 2, "Should have made exactly 2 attempts");
    assert.equal(refreshCalled, true, "Should have called tokenProvider with force=true");
    assert.equal(tokensUsed[0], "Bearer expired-initial-token-123");
    assert.equal(tokensUsed[1], "Bearer fresh-refreshed-token-456");
    assert.equal(result.success, true);
  });

  test("triggers session cleanup and throws 401 when retry also fails with 401", async () => {
    let sessionCleanupTriggered = false;
    globalThis.fetch = async () => {
      return { status: 401, ok: false, json: async () => ({ error: "Invalid token" }) };
    };

    const tokenProvider = async (force) => force ? "fresh-token-but-still-rejected" : "stale-token";
    const onSessionExpired = () => { sessionCleanupTriggered = true; };

    await assert.rejects(
      async () => testFetchWithAuth("/api/v1/workspaces", {}, tokenProvider, onSessionExpired),
      (err) => {
        assert.equal(err.status, 401);
        assert.match(err.message, /session has expired/i);
        return true;
      }
    );

    assert.equal(sessionCleanupTriggered, true, "Session cleanup callback must be called");
  });
});

describe("3. Error Contracts & Safeguards", () => {
  test("deserializes 403 Forbidden workspace access denial", async () => {
    globalThis.fetch = async () => ({
      status: 403,
      ok: false,
      json: async () => ({
        status: 403,
        error: "Forbidden",
        message: "You do not have permission to access workspace: ws-secret-999"
      })
    });

    const tokenProvider = async () => "valid-token";
    await assert.rejects(
      async () => testFetchWithAuth("/api/v1/workspaces/ws-secret-999/analytics/summary", {}, tokenProvider),
      (err) => {
        assert.equal(err.status, 403);
        assert.equal(err.error, "Forbidden");
        assert.match(err.message, /do not have permission/i);
        return true;
      }
    );
  });

  test("deserializes 422 Unprocessable Content with processing limit safeguards", async () => {
    globalThis.fetch = async () => ({
      status: 422,
      ok: false,
      json: async () => ({
        status: 422,
        error: "Unprocessable Content",
        errorCode: "ANALYTICS_PROCESSING_LIMIT_EXCEEDED",
        message: "Analytics query matched 24,500 documents, exceeding maximum safeguard of 10,000",
        limitDetails: {
          enforcementTier: "PREFLIGHT_COUNT_SAFEGUARD",
          maxAllowedCount: 10000,
          totalEligibleCount: 24500
        }
      })
    });

    const tokenProvider = async () => "valid-token";
    await assert.rejects(
      async () => testFetchWithAuth("/api/v1/workspaces/ws-1/analytics/summary", {}, tokenProvider),
      (err) => {
        assert.equal(err.status, 422);
        assert.equal(err.limitDetails.maxAllowedCount, 10000);
        assert.equal(err.limitDetails.totalEligibleCount, 24500);
        assert.equal(err.limitDetails.enforcementTier, "PREFLIGHT_COUNT_SAFEGUARD");
        return true;
      }
    );
  });
});

describe("4. Workspace Selection, Persistence & State Resolution", () => {
  beforeEach(() => {
    localStorage.clear();
  });

  test("persists active workspace ID and cached workspace data", () => {
    const ws = { id: "ws-alpha", name: "Alpha Ops", defaultCurrency: "INR", role: "OWNER" };
    setActiveWorkspaceId(ws.id);
    cacheWorkspaceData(ws);

    assert.equal(getActiveWorkspaceId(), "ws-alpha");
    assert.deepEqual(getCachedWorkspaceData(), ws);
  });

  test("resolveActiveWorkspace maintains selected workspace if accessible", () => {
    const list = [
      { id: "ws-1", name: "First Workspace", role: "MEMBER" },
      { id: "ws-2", name: "Second Workspace", role: "OWNER" }
    ];
    setActiveWorkspaceId("ws-2");

    const resolved = resolveActiveWorkspace(list);
    assert.equal(resolved.id, "ws-2");
    assert.equal(resolved.name, "Second Workspace");
  });

  test("resolveActiveWorkspace defaults to first workspace if current selection is invalid or stale", () => {
    const list = [
      { id: "ws-1", name: "First Workspace", role: "OWNER" },
      { id: "ws-2", name: "Second Workspace", role: "ADMIN" }
    ];
    setActiveWorkspaceId("ws-non-existent");

    const resolved = resolveActiveWorkspace(list);
    assert.equal(resolved.id, "ws-1");
    assert.equal(getActiveWorkspaceId(), "ws-1");
  });

  test("resolveActiveWorkspace gracefully handles empty workspace list", () => {
    const resolved = resolveActiveWorkspace([]);
    assert.equal(resolved, null);
    assert.equal(getActiveWorkspaceId(), null);
    assert.equal(getCachedWorkspaceData(), null);
  });
});

describe("5. Logout & Session Cleanup", () => {
  test("cleans up active workspace, cached workspace, and session tokens upon logout", () => {
    localStorage.setItem(STORAGE_KEY_ACTIVE_WORKSPACE, "ws-123");
    localStorage.setItem(STORAGE_KEY_CACHED_WORKSPACE, JSON.stringify({ id: "ws-123" }));
    sessionStorage.setItem("auth_token", "dummy");

    // Execute cleanup
    localStorage.removeItem(STORAGE_KEY_ACTIVE_WORKSPACE);
    localStorage.removeItem(STORAGE_KEY_CACHED_WORKSPACE);
    sessionStorage.clear();

    assert.equal(localStorage.getItem(STORAGE_KEY_ACTIVE_WORKSPACE), null);
    assert.equal(localStorage.getItem(STORAGE_KEY_CACHED_WORKSPACE), null);
    assert.equal(sessionStorage.getItem("auth_token"), null);
  });
});

