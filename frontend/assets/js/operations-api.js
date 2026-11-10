/** API functions for client, category, and transaction operations. */

import { fetchWithAuth } from "./api.js";
import { buildQueryString } from "./operations-utils.js";

const workspacePath = (workspaceId) => `/api/v1/workspaces/${encodeURIComponent(workspaceId)}`;

export async function fetchClients(workspaceId, filters = {}) {
  return fetchWithAuth(`${workspacePath(workspaceId)}/clients${buildQueryString(filters)}`);
}

export async function createClient(workspaceId, payload) {
  return fetchWithAuth(`${workspacePath(workspaceId)}/clients`, { method: "POST", body: JSON.stringify(payload) });
}

export async function updateClient(workspaceId, clientId, payload) {
  return fetchWithAuth(`${workspacePath(workspaceId)}/clients/${encodeURIComponent(clientId)}`, { method: "PUT", body: JSON.stringify(payload) });
}

export async function fetchCategories(workspaceId, type) {
  return fetchWithAuth(`${workspacePath(workspaceId)}/categories${buildQueryString({ type })}`);
}

export async function fetchTransactions(workspaceId, filters = {}) {
  return fetchWithAuth(`${workspacePath(workspaceId)}/transactions${buildQueryString(filters)}`);
}

export async function createTransaction(workspaceId, payload) {
  return fetchWithAuth(`${workspacePath(workspaceId)}/transactions`, { method: "POST", body: JSON.stringify(payload) });
}

export async function updateTransaction(workspaceId, transactionId, payload) {
  return fetchWithAuth(`${workspacePath(workspaceId)}/transactions/${encodeURIComponent(transactionId)}`, { method: "PUT", body: JSON.stringify(payload) });
}
