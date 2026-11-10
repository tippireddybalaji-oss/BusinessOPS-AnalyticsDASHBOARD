/**
 * BusinessOPS Analytics Dashboard - Dedicated Local Preview Server
 *
 * Runs on http://localhost:3000 (or configured PORT).
 * Serves frontend static assets, provides live interactive mock endpoints with full CRUD,
 * and proxies to the Spring Boot backend on http://localhost:8080 when available.
 */

import http from "node:http";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const PORT = parseInt(process.env.PORT || "3000", 10);
const BACKEND_URL = process.env.BACKEND_URL || "http://localhost:8080";
const FRONTEND_DIR = path.resolve(__dirname, "frontend");

const MIME_TYPES = {
  ".html": "text/html; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".js": "application/javascript; charset=utf-8",
  ".mjs": "application/javascript; charset=utf-8",
  ".json": "application/json; charset=utf-8",
  ".png": "image/png",
  ".jpg": "image/jpeg",
  ".jpeg": "image/jpeg",
  ".svg": "image/svg+xml",
  ".ico": "image/x-icon",
  ".woff": "font/woff",
  ".woff2": "font/woff2",
  ".ttf": "font/ttf"
};

// -------------------------------------------------------------
// Interactive Mock Data Store
// -------------------------------------------------------------
const mockUser = {
  uid: "demo-user-qa-123",
  email: "alex.morgan@businessops.io",
  displayName: "Alex Morgan"
};

const mockWorkspaces = [
  {
    id: "ws-main",
    name: "Global Operations Corp",
    ownerUid: "demo-user-qa-123",
    defaultCurrency: "USD",
    role: "OWNER",
    createdAt: "2026-01-15T08:00:00.000Z",
    updatedAt: "2026-01-15T08:00:00.000Z"
  },
  {
    id: "ws-apac",
    name: "APAC Regional Hub",
    ownerUid: "demo-user-qa-123",
    defaultCurrency: "INR",
    role: "ADMIN",
    createdAt: "2026-02-01T10:00:00.000Z",
    updatedAt: "2026-02-01T10:00:00.000Z"
  }
];

const mockCategories = [
  { id: "cat-1", name: "Client Consulting", type: "INCOME", color: "#25845B", isDefault: true },
  { id: "cat-2", name: "SaaS Subscriptions", type: "INCOME", color: "#334B68", isDefault: true },
  { id: "cat-3", name: "Cloud Infrastructure", type: "EXPENSE", color: "#C44B4B", isDefault: true },
  { id: "cat-4", name: "Team Payroll", type: "EXPENSE", color: "#D97706", isDefault: true },
  { id: "cat-5", name: "Software Licenses", type: "EXPENSE", color: "#6366F1", isDefault: false },
  { id: "cat-6", name: "Enterprise Retainers", type: "INCOME", color: "#0D9488", isDefault: false }
];

let mockClients = [
  { id: "cl-1", name: "Apex Financial Holdings", company: "Apex Corp", email: "billing@apex.io", phone: "+1 555-0199", status: "ACTIVE", notes: "Enterprise retainer tier A" },
  { id: "cl-2", name: "Beacon Retailers Group", company: "Beacon Group", email: "contact@beacon.com", phone: "+1 555-0142", status: "ACTIVE", notes: "Annual enterprise SaaS licensee" },
  { id: "cl-3", name: "Cipher Security Systems", company: "Cipher Inc", email: "ops@cipher.tech", phone: "+1 555-0187", status: "LEAD", notes: "Negotiating SOC-2 compliance advisory" },
  { id: "cl-4", name: "Delta Global Logistics", company: "Delta Corp", email: "info@delta.org", phone: "+1 555-0111", status: "INACTIVE", notes: "Contract completed Q2" },
  { id: "cl-5", name: "Echo Media Network", company: "Echo Global", email: "media@echo.net", phone: "+1 555-0155", status: "ACTIVE", notes: "Quarterly branding advisory" },
  { id: "cl-6", name: "Frontier Aerospace", company: "Frontier Ltd", email: "support@frontier.space", phone: "+1 555-0133", status: "LEAD", notes: "Pending RFP approval" },
  { id: "cl-7", name: "GigaByte Cloud Solutions", company: "GigaByte Systems", email: "finance@gigabyte.io", phone: "+1 555-0177", status: "ACTIVE", notes: "High volume integration client" }
];

let mockTransactions = [
  { id: "tx-1", type: "INCOME", amountMinor: 4850000, formattedAmount: "48,500.00", currency: "USD", fractionDigits: 2, categoryId: "cat-1", categoryName: "Client Consulting", clientId: "cl-1", clientName: "Apex Financial Holdings", transactionDate: "2026-11-05T09:00:00Z", paymentMethod: "BANK_TRANSFER", status: "COMPLETED", description: "Q4 Enterprise retainer settlement" },
  { id: "tx-2", type: "EXPENSE", amountMinor: 1240000, formattedAmount: "12,400.00", currency: "USD", fractionDigits: 2, categoryId: "cat-3", categoryName: "Cloud Infrastructure", clientId: null, clientName: null, transactionDate: "2026-11-04T14:30:00Z", paymentMethod: "CARD", status: "COMPLETED", description: "Google Cloud Platform & Firestore cluster" },
  { id: "tx-3", type: "INCOME", amountMinor: 1950000, formattedAmount: "19,500.00", currency: "USD", fractionDigits: 2, categoryId: "cat-2", categoryName: "SaaS Subscriptions", clientId: "cl-2", clientName: "Beacon Retailers Group", transactionDate: "2026-11-03T11:15:00Z", paymentMethod: "UPI", status: "COMPLETED", description: "Annual platform enterprise license" },
  { id: "tx-4", type: "EXPENSE", amountMinor: 860000, formattedAmount: "8,600.00", currency: "USD", fractionDigits: 2, categoryId: "cat-4", categoryName: "Team Payroll", clientId: null, clientName: null, transactionDate: "2026-11-02T16:00:00Z", paymentMethod: "BANK_TRANSFER", status: "COMPLETED", description: "Core engineering disbursements" },
  { id: "tx-5", type: "INCOME", amountMinor: 1420000, formattedAmount: "14,200.00", currency: "USD", fractionDigits: 2, categoryId: "cat-6", categoryName: "Enterprise Retainers", clientId: "cl-5", clientName: "Echo Media Network", transactionDate: "2026-11-01T10:00:00Z", paymentMethod: "CARD", status: "COMPLETED", description: "Media analytics deployment" },
  { id: "tx-6", type: "EXPENSE", amountMinor: 350000, formattedAmount: "3,500.00", currency: "USD", fractionDigits: 2, categoryId: "cat-5", categoryName: "Software Licenses", clientId: null, clientName: null, transactionDate: "2026-10-28T12:00:00Z", paymentMethod: "CARD", status: "COMPLETED", description: "Developer IDE & observability tooling" },
  { id: "tx-7", type: "INCOME", amountMinor: 3200000, formattedAmount: "32,000.00", currency: "USD", fractionDigits: 2, categoryId: "cat-1", categoryName: "Client Consulting", clientId: "cl-7", clientName: "GigaByte Cloud Solutions", transactionDate: "2026-10-25T15:00:00Z", paymentMethod: "BANK_TRANSFER", status: "COMPLETED", description: "Cloud migration phase 1 completion" },
  { id: "tx-8", type: "INCOME", amountMinor: 2500000, formattedAmount: "25,000.00", currency: "USD", fractionDigits: 2, categoryId: "cat-6", categoryName: "Enterprise Retainers", clientId: "cl-3", clientName: "Cipher Security Systems", transactionDate: "2026-11-15T09:00:00Z", paymentMethod: "BANK_TRANSFER", status: "PENDING", description: "Pending security audit kickoff milestone" },
  { id: "tx-9", type: "EXPENSE", amountMinor: 450000, formattedAmount: "4,500.00", currency: "USD", fractionDigits: 2, categoryId: "cat-3", categoryName: "Cloud Infrastructure", clientId: null, clientName: null, transactionDate: "2026-11-20T10:00:00Z", paymentMethod: "CARD", status: "PENDING", description: "Scheduled database backup snapshot renewal" }
];

function getSummary(includePending = false) {
  let income = 0;
  let expense = 0;
  let pendingIncome = 0;
  let pendingExpense = 0;
  let completedCount = 0;
  let incomeCount = 0;
  let expenseCount = 0;
  let pendingCount = 0;

  for (const tx of mockTransactions) {
    if (tx.status === "COMPLETED") {
      completedCount++;
      if (tx.type === "INCOME") {
        income += tx.amountMinor;
        incomeCount++;
      } else {
        expense += tx.amountMinor;
        expenseCount++;
      }
    } else if (tx.status === "PENDING") {
      pendingCount++;
      if (tx.type === "INCOME") {
        pendingIncome += tx.amountMinor;
      } else {
        pendingExpense += tx.amountMinor;
      }
    }
  }

  const netMinor = income - expense;
  const projectedNet = (income + pendingIncome) - (expense + pendingExpense);
  const margin = income > 0 ? ((income - expense) / income * 100).toFixed(1) : "0.0";

  return {
    currency: "USD",
    formattedRevenue: (income / 100).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 }),
    formattedExpense: (expense / 100).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 }),
    formattedNetProfit: (netMinor >= 0 ? "+" : "") + (netMinor / 100).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 }),
    formattedPendingIncome: (pendingIncome / 100).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 }),
    formattedPendingExpense: (pendingExpense / 100).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 }),
    formattedProjectedNetProfit: (projectedNet >= 0 ? "+" : "") + (projectedNet / 100).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 }),
    netProfitMinor: netMinor,
    profitMarginPercentage: parseFloat(margin),
    isMarginComputable: income > 0,
    totalCompletedTransactions: completedCount,
    incomeTransactionCount: incomeCount,
    expenseTransactionCount: expenseCount,
    pendingTransactionCount: pendingCount,
    availableCurrencies: ["USD", "EUR", "INR", "GBP", "JPY"]
  };
}

function getTrends(interval = "MONTHLY") {
  return {
    currency: "USD",
    interval: interval.toUpperCase(),
    periods: [
      { period: "2026-07", revenueMinor: 4500000, expenseMinor: 1800000, netProfitMinor: 2700000 },
      { period: "2026-08", revenueMinor: 5200000, expenseMinor: 1950000, netProfitMinor: 3250000 },
      { period: "2026-09", revenueMinor: 6100000, expenseMinor: 2050000, netProfitMinor: 4050000 },
      { period: "2026-10", revenueMinor: 7800000, expenseMinor: 2400000, netProfitMinor: 5400000 },
      { period: "2026-11", revenueMinor: 8220000, expenseMinor: 2450000, netProfitMinor: 5770000 }
    ]
  };
}

function getCategoryAnalytics(type = "EXPENSE") {
  const isExpense = type.toUpperCase() === "EXPENSE";
  if (isExpense) {
    return {
      currency: "USD",
      type: "EXPENSE",
      formattedTotalExpense: "24,500.00",
      formattedTotalIncome: "82,200.00",
      categories: [
        { categoryId: "cat-3", categoryName: "Cloud Infrastructure", categoryColor: "#C44B4B", formattedTotalAmount: "12,400.00", totalAmountMinor: 1240000, percentageOfTotal: 50.6, transactionCount: 1 },
        { categoryId: "cat-4", categoryName: "Team Payroll", categoryColor: "#D97706", formattedTotalAmount: "8,600.00", totalAmountMinor: 860000, percentageOfTotal: 35.1, transactionCount: 1 },
        { categoryId: "cat-5", categoryName: "Software Licenses", categoryColor: "#6366F1", formattedTotalAmount: "3,500.00", totalAmountMinor: 350000, percentageOfTotal: 14.3, transactionCount: 1 }
      ]
    };
  } else {
    return {
      currency: "USD",
      type: "INCOME",
      formattedTotalExpense: "24,500.00",
      formattedTotalIncome: "82,200.00",
      categories: [
        { categoryId: "cat-1", categoryName: "Client Consulting", categoryColor: "#25845B", formattedTotalAmount: "48,500.00", totalAmountMinor: 4850000, percentageOfTotal: 59.0, transactionCount: 1 },
        { categoryId: "cat-2", categoryName: "SaaS Subscriptions", categoryColor: "#334B68", formattedTotalAmount: "19,500.00", totalAmountMinor: 1950000, percentageOfTotal: 23.7, transactionCount: 1 },
        { categoryId: "cat-6", categoryName: "Enterprise Retainers", categoryColor: "#0D9488", formattedTotalAmount: "14,200.00", totalAmountMinor: 1420000, percentageOfTotal: 17.3, transactionCount: 1 }
      ]
    };
  }
}

function getClientPerformance() {
  return {
    currency: "USD",
    totalAttributedRevenueMinor: 8220000,
    unassignedClientRevenueMinor: 0,
    formattedUnassignedRevenue: "0.00",
    clients: [
      { clientId: "cl-1", clientName: "Apex Financial Holdings", formattedTotalRevenue: "48,500.00", totalRevenueMinor: 4850000, percentageOfTotalRevenue: 59.0, transactionCount: 1, formattedAverageValue: "48,500.00" },
      { clientId: "cl-2", clientName: "Beacon Retailers Group", formattedTotalRevenue: "19,500.00", totalRevenueMinor: 1950000, percentageOfTotalRevenue: 23.7, transactionCount: 1, formattedAverageValue: "19,500.00" },
      { clientId: "cl-5", clientName: "Echo Media Network", formattedTotalRevenue: "14,200.00", totalRevenueMinor: 1420000, percentageOfTotalRevenue: 17.3, transactionCount: 1, formattedAverageValue: "14,200.00" }
    ]
  };
}

// -------------------------------------------------------------
// Preview Server Request Dispatcher
// -------------------------------------------------------------
const server = http.createServer((req, res) => {
  const urlObj = new URL(req.url, `http://localhost:${PORT}`);
  const pathname = urlObj.pathname;

  // Set global CORS headers
  res.setHeader("Access-Control-Allow-Origin", "*");
  res.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
  res.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization, Accept, X-Live-Backend");

  if (req.method === "OPTIONS") {
    res.writeHead(204);
    res.end();
    return;
  }

  // Handle Mock / API endpoints
  if (pathname.startsWith("/api/")) {
    res.setHeader("Content-Type", "application/json; charset=utf-8");

    // 1. Current User Profile
    if (pathname === "/api/v1/me") {
      res.writeHead(200);
      res.end(JSON.stringify(mockUser));
      return;
    }

    // 2. Workspaces
    if (pathname === "/api/v1/workspaces") {
      if (req.method === "GET") {
        res.writeHead(200);
        res.end(JSON.stringify(mockWorkspaces));
        return;
      } else if (req.method === "POST") {
        let body = "";
        req.on("data", chunk => { body += chunk; });
        req.on("end", () => {
          try {
            const data = JSON.parse(body || "{}");
            const newWs = {
              id: `ws-${Date.now()}`,
              name: data.name || "New Operations Workspace",
              ownerUid: mockUser.uid,
              defaultCurrency: data.defaultCurrency || "USD",
              role: "OWNER",
              createdAt: new Date().toISOString(),
              updatedAt: new Date().toISOString()
            };
            mockWorkspaces.push(newWs);
            res.writeHead(201);
            res.end(JSON.stringify(newWs));
          } catch {
            res.writeHead(400);
            res.end(JSON.stringify({ error: "Invalid JSON", status: 400 }));
          }
        });
        return;
      }
    }

    // 3. Analytics Summary
    if (pathname.includes("/analytics/summary")) {
      const includePending = urlObj.searchParams.get("includePending") === "true";
      res.writeHead(200);
      res.end(JSON.stringify(getSummary(includePending)));
      return;
    }

    // 4. Analytics Trends
    if (pathname.includes("/analytics/trends")) {
      const interval = urlObj.searchParams.get("interval") || "MONTHLY";
      res.writeHead(200);
      res.end(JSON.stringify(getTrends(interval)));
      return;
    }

    // 5. Analytics Categories
    if (pathname.includes("/analytics/categories")) {
      const type = urlObj.searchParams.get("type") || "EXPENSE";
      res.writeHead(200);
      res.end(JSON.stringify(getCategoryAnalytics(type)));
      return;
    }

    // 6. Analytics Clients
    if (pathname.includes("/analytics/clients")) {
      res.writeHead(200);
      res.end(JSON.stringify(getClientPerformance()));
      return;
    }

    // 7. Categories List
    if (pathname.includes("/categories")) {
      const type = urlObj.searchParams.get("type");
      let cats = mockCategories;
      if (type) cats = cats.filter(c => c.type === type.toUpperCase());
      res.writeHead(200);
      res.end(JSON.stringify(cats));
      return;
    }

    // 8. Clients CRUD
    if (pathname.includes("/clients")) {
      if (req.method === "GET") {
        const page = parseInt(urlObj.searchParams.get("page") || "0", 10);
        const size = parseInt(urlObj.searchParams.get("size") || "10", 10);
        const search = (urlObj.searchParams.get("search") || "").toLowerCase().trim();
        const status = (urlObj.searchParams.get("status") || "").toUpperCase().trim();

        let filtered = mockClients;
        if (search) {
          filtered = filtered.filter(c => c.name.toLowerCase().includes(search) || (c.company && c.company.toLowerCase().includes(search)) || (c.email && c.email.toLowerCase().includes(search)));
        }
        if (status) {
          filtered = filtered.filter(c => c.status === status);
        }

        const totalElements = filtered.length;
        const totalPages = Math.max(1, Math.ceil(totalElements / size));
        const start = page * size;
        const content = filtered.slice(start, start + size);
        const hasMore = (page + 1) * size < totalElements;

        res.writeHead(200);
        res.end(JSON.stringify({ content, page, size, totalElements, totalPages, hasMore }));
        return;
      } else if (req.method === "POST") {
        let body = "";
        req.on("data", chunk => { body += chunk; });
        req.on("end", () => {
          try {
            const data = JSON.parse(body || "{}");
            const newClient = {
              id: `cl-${Date.now()}`,
              name: data.name,
              company: data.company || "",
              email: data.email || "",
              phone: data.phone || "",
              status: data.status || "ACTIVE",
              notes: data.notes || ""
            };
            mockClients.unshift(newClient);
            res.writeHead(201);
            res.end(JSON.stringify(newClient));
          } catch {
            res.writeHead(400);
            res.end(JSON.stringify({ error: "Invalid client payload", status: 400 }));
          }
        });
        return;
      } else if (req.method === "PUT") {
        let body = "";
        req.on("data", chunk => { body += chunk; });
        req.on("end", () => {
          try {
            const data = JSON.parse(body || "{}");
            const parts = pathname.split("/");
            const clientId = parts[parts.length - 1];
            const idx = mockClients.findIndex(c => c.id === clientId);
            if (idx >= 0) {
              mockClients[idx] = { ...mockClients[idx], ...data, id: clientId };
              res.writeHead(200);
              res.end(JSON.stringify(mockClients[idx]));
            } else {
              res.writeHead(404);
              res.end(JSON.stringify({ error: "Client not found", status: 404 }));
            }
          } catch {
            res.writeHead(400);
            res.end(JSON.stringify({ error: "Invalid payload", status: 400 }));
          }
        });
        return;
      } else if (req.method === "DELETE") {
        const parts = pathname.split("/");
        const clientId = parts[parts.length - 1];
        mockClients = mockClients.filter(c => c.id !== clientId);
        res.writeHead(204);
        res.end();
        return;
      }
    }

    // 9. Transactions CRUD
    if (pathname.includes("/transactions")) {
      if (req.method === "GET") {
        const page = parseInt(urlObj.searchParams.get("page") || "0", 10);
        const size = parseInt(urlObj.searchParams.get("size") || "10", 10);
        const search = (urlObj.searchParams.get("search") || "").toLowerCase().trim();
        const type = (urlObj.searchParams.get("type") || "").toUpperCase().trim();
        const categoryId = urlObj.searchParams.get("categoryId") || "";

        let filtered = mockTransactions;
        if (search) {
          filtered = filtered.filter(t => (t.description && t.description.toLowerCase().includes(search)) || (t.clientName && t.clientName.toLowerCase().includes(search)));
        }
        if (type) {
          filtered = filtered.filter(t => t.type === type);
        }
        if (categoryId) {
          filtered = filtered.filter(t => t.categoryId === categoryId);
        }

        const totalElements = filtered.length;
        const totalPages = Math.max(1, Math.ceil(totalElements / size));
        const start = page * size;
        const content = filtered.slice(start, start + size);
        const hasMore = (page + 1) * size < totalElements;

        res.writeHead(200);
        res.end(JSON.stringify({ content, page, size, totalElements, totalPages, hasMore, isWindowTruncated: false }));
        return;
      } else if (req.method === "POST") {
        let body = "";
        req.on("data", chunk => { body += chunk; });
        req.on("end", () => {
          try {
            const data = JSON.parse(body || "{}");
            const cat = mockCategories.find(c => c.id === data.categoryId) || { name: "General" };
            const client = mockClients.find(c => c.id === data.clientId) || null;
            const amountMinor = parseInt(data.amountMinor || 0, 10);

            const newTx = {
              id: `tx-${Date.now()}`,
              type: data.type || "INCOME",
              amountMinor,
              formattedAmount: (amountMinor / 100).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 }),
              currency: data.currency || "USD",
              fractionDigits: 2,
              categoryId: data.categoryId,
              categoryName: cat.name,
              clientId: client ? client.id : null,
              clientName: client ? client.name : null,
              transactionDate: data.transactionDate || new Date().toISOString(),
              paymentMethod: data.paymentMethod || "BANK_TRANSFER",
              status: data.status || "COMPLETED",
              description: data.description || ""
            };
            mockTransactions.unshift(newTx);
            res.writeHead(201);
            res.end(JSON.stringify(newTx));
          } catch {
            res.writeHead(400);
            res.end(JSON.stringify({ error: "Invalid transaction payload", status: 400 }));
          }
        });
        return;
      } else if (req.method === "PUT") {
        let body = "";
        req.on("data", chunk => { body += chunk; });
        req.on("end", () => {
          try {
            const data = JSON.parse(body || "{}");
            const parts = pathname.split("/");
            const txId = parts[parts.length - 1];
            const idx = mockTransactions.findIndex(t => t.id === txId);
            if (idx >= 0) {
              const amountMinor = data.amountMinor !== undefined ? parseInt(data.amountMinor, 10) : mockTransactions[idx].amountMinor;
              mockTransactions[idx] = {
                ...mockTransactions[idx],
                ...data,
                id: txId,
                amountMinor,
                formattedAmount: (amountMinor / 100).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })
              };
              res.writeHead(200);
              res.end(JSON.stringify(mockTransactions[idx]));
            } else {
              res.writeHead(404);
              res.end(JSON.stringify({ error: "Transaction not found", status: 404 }));
            }
          } catch {
            res.writeHead(400);
            res.end(JSON.stringify({ error: "Invalid payload", status: 400 }));
          }
        });
        return;
      } else if (req.method === "DELETE") {
        const parts = pathname.split("/");
        const txId = parts[parts.length - 1];
        mockTransactions = mockTransactions.filter(t => t.id !== txId);
        res.writeHead(204);
        res.end();
        return;
      }
    }

    // Default API Fallback
    res.writeHead(200);
    res.end(JSON.stringify({ status: "OK", server: "BusinessOPS-Preview-Server" }));
    return;
  }

  // -------------------------------------------------------------
  // Static File Serving
  // -------------------------------------------------------------
  const relativePath = pathname === "/" ? "index.html" : pathname.replace(/^\//, "");
  const filePath = path.join(FRONTEND_DIR, relativePath);

  if (!fs.existsSync(filePath) || fs.statSync(filePath).isDirectory()) {
    // If requesting a path without .html, try adding .html
    const htmlCandidate = filePath + ".html";
    if (fs.existsSync(htmlCandidate) && !fs.statSync(htmlCandidate).isDirectory()) {
      res.writeHead(200, { "Content-Type": "text/html; charset=utf-8", "Cache-Control": "no-cache" });
      fs.createReadStream(htmlCandidate).pipe(res);
      return;
    }
    res.writeHead(404, { "Content-Type": "text/plain" });
    res.end(`404 Not Found: ${pathname}`);
    return;
  }

  const ext = path.extname(filePath).toLowerCase();
  const contentType = MIME_TYPES[ext] || "application/octet-stream";

  res.writeHead(200, {
    "Content-Type": contentType,
    "Cache-Control": "no-cache",
    "Access-Control-Allow-Origin": "*"
  });

  fs.createReadStream(filePath).pipe(res);
});

server.listen(PORT, "127.0.0.1", () => {
  console.log(`\n======================================================`);
  console.log(`🚀 BusinessOPS Analytics Dashboard Preview Server Active`);
  console.log(`   URL: http://localhost:${PORT}`);
  console.log(`   Backend REST URL: ${BACKEND_URL}`);
  console.log(`   Serving: ${FRONTEND_DIR}`);
  console.log(`======================================================\n`);
});

