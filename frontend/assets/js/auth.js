/**
 * BusinessOPS Analytics Dashboard - Authentication Module
 *
 * Handles Firebase Authentication, ID Token lifecycle, automatic refresh,
 * login, registration, sign-out, and client-side route protection.
 */

import { auth } from "./firebase-config.js";
import {
  signInWithEmailAndPassword,
  createUserWithEmailAndPassword,
  signOut,
  onAuthStateChanged,
  updateProfile
} from "https://www.gstatic.com/firebasejs/10.13.0/firebase-auth.js";

let currentUser = null;
let authInitialized = false;
const authSubscribers = [];

export function isDemoMode() {
  try {
    return localStorage.getItem("businessops_demo_mode") === "true" ||
           sessionStorage.getItem("businessops_demo_mode") === "true";
  } catch {
    return false;
  }
}

const DEMO_USER = {
  uid: "demo-user-qa-123",
  email: "alex.morgan@businessops.io",
  displayName: "Alex Morgan",
  getIdToken: async () => "mock-demo-token"
};

/**
 * Initializes the auth observer and invokes callback when auth state changes.
 * @param {function(user: object|null)} callback
 * @returns {function()} unsubscribe function
 */
export function initAuth(callback) {
  if (isDemoMode()) {
    currentUser = DEMO_USER;
    authInitialized = true;
    if (typeof callback === "function") {
      setTimeout(() => callback(DEMO_USER), 0);
    }
    return () => {};
  }

  if (typeof callback === "function") {
    authSubscribers.push(callback);
    if (authInitialized) {
      callback(currentUser);
    }
  }

  // Subscribe to Firebase Auth state
  return onAuthStateChanged(auth, async (user) => {
    currentUser = user;
    authInitialized = true;
    for (const sub of authSubscribers) {
      try {
        sub(user);
      } catch (err) {
        console.error("Error in auth subscriber callback:", err);
      }
    }
  });
}

/**
 * Returns the currently signed-in Firebase user, or null.
 * @returns {object|null}
 */
export function getCurrentUser() {
  if (isDemoMode()) return DEMO_USER;
  return currentUser || auth.currentUser;
}

/**
 * Retrieves the current user's Firebase ID token.
 * @param {boolean} forceRefresh whether to force a token refresh
 * @returns {Promise<string|null>}
 */
export async function getIdToken(forceRefresh = false) {
  if (isDemoMode()) return "mock-demo-token";
  const user = getCurrentUser();
  if (!user) {
    return null;
  }
  return await user.getIdToken(forceRefresh);
}

/**
 * Signs in user with email and password.
 * @param {string} email
 * @param {string} password
 * @returns {Promise<object>} Firebase UserCredential
 */
export async function loginWithEmail(email, password) {
  const cred = await signInWithEmailAndPassword(auth, email.trim(), password);
  currentUser = cred.user;
  return cred;
}

/**
 * Registers a new user with email, password, and optional display name.
 * @param {string} email
 * @param {string} password
 * @param {string} [displayName]
 * @returns {Promise<object>} Firebase UserCredential
 */
export async function registerWithEmail(email, password, displayName = "") {
  const cred = await createUserWithEmailAndPassword(auth, email.trim(), password);
  currentUser = cred.user;
  if (displayName && displayName.trim()) {
    try {
      await updateProfile(cred.user, { displayName: displayName.trim() });
    } catch (e) {
      console.warn("Could not update user display name:", e);
    }
  }
  return cred;
}

/**
 * Signs out the current user and clears session storage.
 * @returns {Promise<void>}
 */
export async function logout() {
  try {
    const wasDemo = isDemoMode();
    localStorage.removeItem("businessops_demo_mode");
    sessionStorage.removeItem("businessops_demo_mode");
    // Clear active workspace and local session items
    localStorage.removeItem("businessops_active_workspace_id");
    localStorage.removeItem("businessops_cached_workspace");
    sessionStorage.clear();
    if (!wasDemo) {
      await signOut(auth);
    }
    currentUser = null;
  } catch (err) {
    console.error("Error during signOut:", err);
    throw err;
  }
}

/**
 * Route Guard: Requires authentication to view current page.
 * If user is not signed in after auth initialization, redirects to login.html.
 * @param {string} [redirectUrl="login.html"]
 */
export function requireAuth(redirectUrl = "login.html") {
  return new Promise((resolve) => {
    initAuth((user) => {
      if (!user) {
        // Save current location for return after login
        const currentPath = window.location.pathname.split("/").pop() || "index.html";
        if (currentPath !== "login.html") {
          sessionStorage.setItem("businessops_auth_redirect", window.location.href);
        }
        window.location.replace(redirectUrl);
      } else {
        resolve(user);
      }
    });
  });
}

/**
 * Route Guard: For guest-only pages like login.html.
 * If user is already signed in, redirects to destination.
 * @param {string} [destinationUrl="workspaces.html"]
 */
export function requireGuest(destinationUrl = "workspaces.html") {
  return new Promise((resolve) => {
    initAuth((user) => {
      if (user) {
        const savedRedirect = sessionStorage.getItem("businessops_auth_redirect");
        if (savedRedirect) {
          sessionStorage.removeItem("businessops_auth_redirect");
          window.location.replace(savedRedirect);
        } else {
          window.location.replace(destinationUrl);
        }
      } else {
        resolve();
      }
    });
  });
}

