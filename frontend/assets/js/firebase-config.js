/**
 * BusinessOPS Analytics Dashboard - Firebase Configuration & Initialization
 *
 * Uses official Firebase Web JS SDK (modular v10+).
 * Project: businessops-analyticsdashboard
 */

import { initializeApp } from "https://www.gstatic.com/firebasejs/10.13.0/firebase-app.js";
import { getAuth } from "https://www.gstatic.com/firebasejs/10.13.0/firebase-auth.js";

// Public Web Client Configuration (safe to expose on client; service account keys are NEVER included)
export const firebaseConfig = window.__FIREBASE_CONFIG__ || {
  projectId: "businessops-analyticsdashboard",
  appId: "1:44382270795:web:f1e2e18c26fbd36d5f64ce",
  storageBucket: "businessops-analyticsdashboard.firebasestorage.app",
  apiKey: "AIzaSyDmcQQnXxvXMidwC-HgWFtWjg53kUIBHF4",
  authDomain: "businessops-analyticsdashboard.firebaseapp.com",
  messagingSenderId: "44382270795"
};

// Initialize Firebase App & Auth
export const app = initializeApp(firebaseConfig);
export const auth = getAuth(app);

