# BusinessOPS Analytics Dashboard

> An enterprise-grade business operations and financial analytics platform built end-to-end on Spring Boot 3 and Google Cloud Firestore.

---

## Overview

**BusinessOPS Analytics Dashboard** provides multi-tenant financial management, client operations, and real-time business metrics built on modern cloud-native standards:

| Layer | Technology |
|---|---|
| Backend runtime | Java 21 LTS + Spring Boot 3.4.5 |
| Build tool | Apache Maven 3.x |
| Authentication | Firebase Authentication (Server-verified ID tokens) |
| Database | Google Cloud Firestore (Native mode, document subcollections) |
| Server SDK | Firebase Admin SDK 9.4.1 for Java |
| Security & Access Control | Spring Security (stateless, token-based, server-side RBAC) |
| Financial Precision | Minor currency units (`Long amountMinor`), ISO-4217 fraction digits, `BigDecimal` |
| Error Handling | Centralized `@RestControllerAdvice` with uniform `ApiErrorResponse` |
| Frontend *(Future Phase)* | HTML5, CSS3, Vanilla JavaScript (ES6+), Chart.js |

---

## Project Phases

- [x] **Phase 0 – Repository inspection & pre-flight audit**
- [x] **Phase 1 – Spring Boot backend foundation** (scaffolding, health probe, security filter)
- [x] **Phase 2 – Workspace Model, Firestore Architecture & Server-Side Authorization**
  - Collection hierarchy: `users`, `workspaces`, `workspaces/{id}/members`
  - Atomic workspace creation with automatic `OWNER` role provisioning
  - Server-side authorization verifying workspace membership and ownership
  - REST API v1 endpoints: `POST /api/v1/workspaces`, `GET /api/v1/workspaces`, `GET /api/v1/workspaces/{id}`, `GET /api/v1/me`
- [x] **Phase 3 – Client Management Module**
  - Dedicated subcollection `workspaces/{workspaceId}/clients/{clientId}`
  - Full CRUD operations with server-side role-based access control (RBAC)
  - Write permissions restricted to `OWNER` & `ADMIN`; `MEMBER` granted read-only access
  - Filter by `ClientStatus` (`ACTIVE`, `INACTIVE`, `LEAD`) with name/company search
  - Pagination envelope (`PagedResponse`) with `totalElements`, `totalPages`, `hasMore`
- [x] **Phase 4 – Transactions & Categories Module**
  - **Milestone 1:** Financial foundation (`CurrencyUtils`, ISO-4217 fraction digits, zero-decimal JPY, `Long amountMinor`, enums, DTOs)
  - **Milestone 2:** Category engine (`CategoryRepository`, deterministic default categories, idempotent batch seeding, self-healing, transactional archival)
  - **Milestone 3:** Transaction engine (`TransactionRepository`, transactional category/client referential integrity, zero category writes, 501-sentinel bounded search)
  - **Milestone 4:** Declarative composite indexes (`firestore.indexes.json`), final verification (191 tests passing), and documentation
- [x] **Phase 5 – Financial Analytics & Business Intelligence Engine**
  - **Summary API:** `GET /api/v1/workspaces/{id}/analytics/summary` (Realized/projected revenue, expenses, net cash flow, profit margins)
  - **Trends API:** `GET /api/v1/workspaces/{id}/analytics/trends` (Monthly & yearly time-series aggregation)
  - **Categories API:** `GET /api/v1/workspaces/{id}/analytics/categories` (Distribution breakdown, percentage of total, uncategorized grouping)
  - **Clients API:** `GET /api/v1/workspaces/{id}/analytics/clients` (Revenue leaderboard ranking, average transaction size, unassigned revenue isolation)
  - **Production Safeguards:** HTTP 422 processing limit safeguards (`AnalyticsProcessingLimitException`) protecting against denial of wallet / runaway Firestore reads
- [x] **Phase 6 – Production Frontend, Executive Dashboard & Quality Hardening** ← *current*
  - **Milestone 1:** Architecture & integration audit (read-only mapping of endpoints to screens)
  - **Milestone 2 & 2.1:** Frontend foundation, modular Firebase Web SDK v10+, authentication, workspaces, design system token alignment
  - **Milestone 3:** Executive Financial Dashboard & Chart.js v4.4.x integration (KPIs, time-series charts, doughnut breakdown, leaderboard)
  - **Milestone 4:** Client management & transaction ledger operations (CRUD, currency-aware minor unit math, responsive pagination)
  - **Milestone 5:** Quality hardening, WCAG 2.1 AA accessibility (skip links, focus trap, Escape dismissal, table captions), CSS shimmer skeletons, centralized error handling, responsive audit across 6 viewports, demo readiness
- [ ] Phase 7 – Cloud deployment & final sign-off

---

## Firestore Database Architecture

All multi-tenant data is strictly partitioned beneath isolated workspace documents:

```text
Firestore Database
│
├── users/
│   └── {uid}
│       ├── displayName: String
│       ├── email: String
│       └── createdAt: Timestamp
│
└── workspaces/
    └── {workspaceId}
        ├── name: String
        ├── ownerUid: String
        ├── defaultCurrency: "INR" | "USD" | "EUR" | ...
        ├── createdAt: Timestamp
        ├── updatedAt: Timestamp
        │
        ├── members/
        │   └── {uid}
        │       ├── uid: String
        │       ├── role: "OWNER" | "ADMIN" | "MEMBER"
        │       └── joinedAt: Timestamp
        │
        ├── clients/             (Phase 3)
        │   └── {clientId}
        │       ├── name: String
        │       ├── email: String (optional)
        │       ├── phone: String (optional)
        │       ├── company: String (optional)
        │       ├── status: "ACTIVE" | "INACTIVE" | "LEAD"
        │       ├── notes: String (optional)
        │       ├── createdAt: Timestamp
        │       ├── updatedAt: Timestamp
        │       ├── createdBy: String (UID)
        │       └── updatedBy: String (UID)
        │
        ├── categories/          (Phase 4)
        │   └── {categoryId}
        │       ├── name: String
        │       ├── type: "INCOME" | "EXPENSE"
        │       ├── isDefault: Boolean
        │       ├── color: String (Hex e.g. #10B981)
        │       ├── icon: String (e.g. briefcase, home)
        │       ├── isArchived: Boolean
        │       ├── archivedAt: Timestamp (nullable)
        │       ├── createdAt: Timestamp
        │       ├── updatedAt: Timestamp
        │       ├── createdBy: String (UID)
        │       └── updatedBy: String (UID)
        │
        └── transactions/        (Phase 4)
            └── {transactionId}
                ├── type: "INCOME" | "EXPENSE"
                ├── amountMinor: Long (paise, cents, yen)
                ├── currency: String (ISO-4217)
                ├── categoryId: String
                ├── categoryName: String (denormalized display value)
                ├── categoryColor: String (denormalized display value)
                ├── clientId: String (optional)
                ├── clientName: String (denormalized display value)
                ├── description: String (max 500 chars)
                ├── transactionDate: Timestamp
                ├── paymentMethod: "CASH" | "BANK_TRANSFER" | "UPI" | "CARD" | "CHEQUE" | "OTHER"
                ├── status: "COMPLETED" | "PENDING" | "CANCELLED"
                ├── createdByUid: String (UID)
                ├── updatedByUid: String (UID)
                ├── createdAt: Timestamp
                └── updatedAt: Timestamp
```

---

## Monetary Precision & ISO-4217 Currency Handling

Financial amounts in BusinessOPS adhere to strict non-floating-point minor currency unit arithmetic:

* **Storage Invariant:** All transaction amounts are stored in 64-bit integer minor currency units (`Long amountMinor`), completely eliminating IEEE 754 binary floating-point rounding errors.
* **Fraction Digits:** Supported fraction digits follow ISO-4217 conventions via [`CurrencyUtils`](src/main/java/com/businessops/dashboard/util/CurrencyUtils.java):
  - Standard Currencies (2 decimal places, factor = 100): `INR` (paise), `USD` (cents), `EUR` (cents), `GBP` (pence), `AUD`, `CAD`, `SGD`, `AED`.
  - Zero-Decimal Currencies (0 decimal places, factor = 1): `JPY` (yen).
* **Workspace Currency Inheritance:** If a transaction creation request omits `currency`, it inherits `workspace.defaultCurrency()` rather than defaulting to INR.
* **Validation & Overflow Protection:** Non-positive amounts (`amountMinor <= 0`) and excess decimal precision (e.g. `10.555` for USD or `10.5` for JPY) are rejected with HTTP 400 Bad Request.

---

## Server-Side Authorization & RBAC Invariants

The Firebase Admin SDK operates with root administrative privileges and bypasses security rules; therefore, the Spring Boot service layer strictly enforces RBAC invariants:

1. **Identity Derivation:** The authenticated UID is extracted exclusively from `FirebaseToken.getUid()` in the verified Spring Security context. Client-supplied UIDs in bodies or query strings are ignored.
2. **Workspace Isolation:** Every resource request verifies that the authenticated user is an active member or owner of `workspaceId`. Cross-workspace requests are rejected with `403 Forbidden`. Non-existent workspaces return `404 Not Found`.
3. **Role-Based Access Control (RBAC):**
   - `OWNER` / `ADMIN`: Granted full read and write access across Workspaces, Clients, Categories, and Transactions.
   - `MEMBER`: Granted **strictly read-only** access (`GET` endpoints). Any attempted write (`POST`, `PUT`, `DELETE`) is rejected with `403 Forbidden` (`Insufficient permissions: Member role cannot perform write operations`).
   - `Non-member`: Immediate `403 Forbidden`.
4. **Audit Immutability:** Creation timestamps and authors (`createdAt`, `createdByUid`) are permanently preserved during updates; only `updatedAt` and `updatedByUid` are refreshed.

---

## Category Concurrency & Referential Integrity

Category archival and transaction creation coordinate via Cloud Firestore's **Serializable Isolation**:

* **Transactional Category & Client Reads:** When creating or updating a transaction, [`FirestoreTransactionRepository`](src/main/java/com/businessops/dashboard/repository/FirestoreTransactionRepository.java) executes inside `firestore.runTransaction()`. It verifies that the category exists, belongs to the workspace, matches the transaction type (`INCOME` vs `EXPENSE`), and has `isArchived == false`. If a client ID is provided, it verifies the client exists, belongs to the workspace, and has `status == ACTIVE`.
* **Zero Category Mutation (Protocol B):** Transaction creation **never** writes to or updates the category document (no `lastTransactionAt` or counter mutation). This eliminates document hotspot write throttling ($\approx 1\text{ write/sec}$).
* **Conflict Coordination:** If Category Archival commits `isArchived = true` while a transaction creation transaction is in-flight, Firestore's server detects the read-set version divergence, signaling `ABORTED`. The Google Cloud Firestore Java SDK automatically retries the transaction function. On retry, the function observes `isArchived == true` and immediately throws `IllegalArgumentException("Cannot create transaction with archived category")` (HTTP 400), ensuring no orphaned or invalid transactions are persisted.

---

## Bounded Text Search & 501 Sentinel Strategy

Cloud Firestore structured queries natively support equality filters and single-field ranges, but do not support unindexed substring search across multiple fields. To provide fast text search across `description`, `categoryName`, and `clientName` without unbounded memory consumption:

1. **501-Document Sentinel Fetch:** When `search` is provided, the query fetches up to 501 documents ordered by `transactionDate DESC`.
2. **Window Truncation Detection:**
   - If **$\le 500$ documents** are returned: `isWindowTruncated = false`. The search is guaranteed to be exhaustive for that filtered candidate set.
   - If **$501$ documents** are returned: `isWindowTruncated = true`. The 501st sentinel document is discarded, and only the 500 most recent candidates are evaluated.
3. **Response Envelope Metadata:** When `isWindowTruncated == true`, [`PagedResponse`](src/main/java/com/businessops/dashboard/dto/PagedResponse.java) includes:
   ```json
   {
     "isWindowTruncated": true,
     "searchWarning": "Search results are bounded to the 500 most recent transactions. Matches from older transactions are omitted. To perform an exhaustive search, please supply a narrower date range (startDate / endDate)."
   }
   ```
4. **Post-Filter Pagination:** Pagination (`page` and `size`) is applied **after** in-memory text filtering, ensuring pages are never underfilled.

---

## REST API Endpoints

### Public Endpoints
| Method | Path | Description |
|---|---|---|
| `GET` | `/api/health` | Liveness health probe |
| `OPTIONS` | `/**` | CORS preflight handler |

### Protected Endpoints (Bearer Firebase ID token required)

#### Identity & Workspaces
| Method | Path | Role Required | Description |
|---|---|---|---|
| `GET` | `/api/v1/me` | Authenticated | Returns caller identity (`uid`, `email`) |
| `POST` | `/api/v1/workspaces` | Authenticated | Creates new workspace; caller becomes `OWNER` |
| `GET` | `/api/v1/workspaces` | Authenticated | Lists workspaces accessible to caller |
| `GET` | `/api/v1/workspaces/{workspaceId}` | `OWNER`, `ADMIN`, `MEMBER` | Retrieves workspace details |

#### Clients Module (Phase 3)
| Method | Path | Role Required | Description |
|---|---|---|---|
| `POST` | `/api/v1/workspaces/{workspaceId}/clients` | `OWNER`, `ADMIN` | Creates new client |
| `GET` | `/api/v1/workspaces/{workspaceId}/clients` | `OWNER`, `ADMIN`, `MEMBER` | Lists clients (supports `status`, `search`, `page`, `size`) |
| `GET` | `/api/v1/workspaces/{workspaceId}/clients/{clientId}` | `OWNER`, `ADMIN`, `MEMBER` | Retrieves client details |
| `PUT` | `/api/v1/workspaces/{workspaceId}/clients/{clientId}` | `OWNER`, `ADMIN` | Updates client details |
| `DELETE` | `/api/v1/workspaces/{workspaceId}/clients/{clientId}` | `OWNER`, `ADMIN` | Deletes client record |

#### Categories Module (Phase 4)
| Method | Path | Role Required | Description |
|---|---|---|---|
| `POST` | `/api/v1/workspaces/{workspaceId}/categories` | `OWNER`, `ADMIN` | Creates custom category |
| `GET` | `/api/v1/workspaces/{workspaceId}/categories` | `OWNER`, `ADMIN`, `MEMBER` | Lists categories (supports `type`, `includeArchived`) |
| `GET` | `/api/v1/workspaces/{workspaceId}/categories/{categoryId}` | `OWNER`, `ADMIN`, `MEMBER` | Retrieves category details |
| `PUT` | `/api/v1/workspaces/{workspaceId}/categories/{categoryId}` | `OWNER`, `ADMIN` | Updates category (defaults cannot be renamed) |
| `DELETE` | `/api/v1/workspaces/{workspaceId}/categories/{categoryId}` | `OWNER`, `ADMIN` | Idempotent archival (defaults cannot be archived) |

#### Transactions Module (Phase 4)
| Method | Path | Role Required | Description |
|---|---|---|---|
| `POST` | `/api/v1/workspaces/{workspaceId}/transactions` | `OWNER`, `ADMIN` | Creates transaction with verified category/client |
| `GET` | `/api/v1/workspaces/{workspaceId}/transactions` | `OWNER`, `ADMIN`, `MEMBER` | Lists transactions (supports `type`, `categoryId`, `clientId`, `startDate`, `endDate`, `search`, `page`, `size`) |
| `GET` | `/api/v1/workspaces/{workspaceId}/transactions/{transactionId}` | `OWNER`, `ADMIN`, `MEMBER` | Retrieves single transaction |
| `PUT` | `/api/v1/workspaces/{workspaceId}/transactions/{transactionId}` | `OWNER`, `ADMIN` | Updates transaction |
| `DELETE` | `/api/v1/workspaces/{workspaceId}/transactions/{transactionId}` | `OWNER`, `ADMIN` | Hard deletes transaction |

---

## API Request & Response Examples

### 1. Create Transaction

**Request (`POST /api/v1/workspaces/{id}/transactions`):**
```json
{
  "type": "INCOME",
  "amountMinor": 2500000,
  "currency": "INR",
  "categoryId": "def_inc_sales",
  "clientId": "cl-acme-corp",
  "description": "Enterprise consulting retainer - Q1",
  "transactionDate": "2026-11-04T10:00:00Z",
  "paymentMethod": "BANK_TRANSFER",
  "status": "COMPLETED"
}
```

**Response (`201 Created`):**
```json
{
  "id": "tx-9a8b7c6d",
  "workspaceId": "ws-abc-123",
  "type": "INCOME",
  "amountMinor": 2500000,
  "formattedAmount": "25000.00",
  "currency": "INR",
  "fractionDigits": 2,
  "categoryId": "def_inc_sales",
  "categoryName": "Client Invoicing / Sales",
  "categoryColor": "#10B981",
  "clientId": "cl-acme-corp",
  "clientName": "Acme Corporation",
  "description": "Enterprise consulting retainer - Q1",
  "transactionDate": "2026-11-04T10:00:00Z",
  "paymentMethod": "BANK_TRANSFER",
  "status": "COMPLETED",
  "createdByUid": "firebaseUid123",
  "updatedByUid": "firebaseUid123",
  "createdAt": "2026-11-04T10:00:00Z",
  "updatedAt": "2026-11-04T10:00:00Z"
}
```

### 2. List Transactions with Bounded Search

**Request:**
```bash
GET /api/v1/workspaces/{id}/transactions?type=INCOME&search=Acme&page=0&size=20
Authorization: Bearer <Firebase_ID_Token>
```

**Response (`200 OK`):**
```json
{
  "content": [
    {
      "id": "tx-9a8b7c6d",
      "type": "INCOME",
      "amountMinor": 2500000,
      "formattedAmount": "25000.00",
      "currency": "INR",
      "fractionDigits": 2,
      "categoryName": "Client Invoicing / Sales",
      "clientName": "Acme Corporation",
      "transactionDate": "2026-11-04T10:00:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1,
  "hasMore": false,
  "isWindowTruncated": false,
  "searchWarning": null
}
```

---

## Required Firestore Indexes

The declarative composite index definitions in `firestore.indexes.json` cover all supported query patterns:

```json
{
  "indexes": [
    {
      "collectionGroup": "clients",
      "queryScope": "COLLECTION",
      "fields": [
        { "fieldPath": "status", "order": "ASCENDING" },
        { "fieldPath": "createdAt", "order": "DESCENDING" }
      ]
    },
    {
      "collectionGroup": "categories",
      "queryScope": "COLLECTION",
      "fields": [
        { "fieldPath": "type", "order": "ASCENDING" },
        { "fieldPath": "isArchived", "order": "ASCENDING" },
        { "fieldPath": "name", "order": "ASCENDING" }
      ]
    },
    {
      "collectionGroup": "transactions",
      "queryScope": "COLLECTION",
      "fields": [
        { "fieldPath": "type", "order": "ASCENDING" },
        { "fieldPath": "transactionDate", "order": "DESCENDING" }
      ]
    },
    {
      "collectionGroup": "transactions",
      "queryScope": "COLLECTION",
      "fields": [
        { "fieldPath": "categoryId", "order": "ASCENDING" },
        { "fieldPath": "transactionDate", "order": "DESCENDING" }
      ]
    },
    {
      "collectionGroup": "transactions",
      "queryScope": "COLLECTION",
      "fields": [
        { "fieldPath": "clientId", "order": "ASCENDING" },
        { "fieldPath": "transactionDate", "order": "DESCENDING" }
      ]
    },
    {
      "collectionGroup": "transactions",
      "queryScope": "COLLECTION",
      "fields": [
        { "fieldPath": "type", "order": "ASCENDING" },
        { "fieldPath": "categoryId", "order": "ASCENDING" },
        { "fieldPath": "transactionDate", "order": "DESCENDING" }
      ]
    },
    {
      "collectionGroup": "transactions",
      "queryScope": "COLLECTION",
      "fields": [
        { "fieldPath": "type", "order": "ASCENDING" },
        { "fieldPath": "clientId", "order": "ASCENDING" },
        { "fieldPath": "transactionDate", "order": "DESCENDING" }
      ]
    },
    {
      "collectionGroup": "transactions",
      "queryScope": "COLLECTION",
      "fields": [
        { "fieldPath": "categoryId", "order": "ASCENDING" },
        { "fieldPath": "clientId", "order": "ASCENDING" },
        { "fieldPath": "transactionDate", "order": "DESCENDING" }
      ]
    },
    {
      "collectionGroup": "transactions",
      "queryScope": "COLLECTION",
      "fields": [
        { "fieldPath": "type", "order": "ASCENDING" },
        { "fieldPath": "categoryId", "order": "ASCENDING" },
        { "fieldPath": "clientId", "order": "ASCENDING" },
        { "fieldPath": "transactionDate", "order": "DESCENDING" }
      ]
    }
  ],
  "fieldOverrides": [
    {
      "collectionGroup": "members",
      "fieldPath": "uid",
      "indexes": [
        { "order": "ASCENDING", "queryScope": "COLLECTION_GROUP" }
      ]
    }
  ]
}
```

### Index Deployment Command
To deploy index definitions to a live Firebase project:
```bash
firebase deploy --only firestore:indexes
```

---

## Audit Observations & Known Architectural Trade-offs

1. **Structured Query Pagination Scalability:**  
   When text search is not provided (`search == null`), the backend executes the structured query across the filtered collection and pages the items in memory via `PagedResponse.of(...)`. This accurately provides aggregate `totalElements` counts for small-to-medium datasets. For high-volume production deployments ($> 10,000$ transactions per workspace), introducing cursor-based pagination (`startAfter`) or `Query.count()` aggregation is recommended as a future enhancement.
2. **Timestamp Generation:**  
   Entity timestamps currently utilize `Timestamp.now()` (host system clock) rather than Firestore server commit sentinels (`FieldValue.serverTimestamp()`). This preserves POJO mapping compatibility across all existing repositories (`Client`, `Category`, `Transaction`), but relies on host clock synchronization. Transitioning to server-side sentinels will be evaluated in a future infrastructure refinement.
3. **Live Distributed Concurrency Verification:**  
   Unit tests execute hermetically using Mockito, validating serializable isolation logic via mock transaction callbacks. True multi-threaded commit-time conflict retry loops (`ABORTED` contention) should be benchmarked in a staging environment against a live Cloud Firestore instance or the local Firebase Emulator Suite.
4. **Transaction Deletion Policy:**  
   `DELETE /api/v1/workspaces/{workspaceId}/transactions/{transactionId}` currently performs a physical hard delete. While compliant with the Phase 4 specification, financial accounting standards often require immutable ledgers. Soft-deletion (`status: CANCELLED` or `isDeleted: true`) can be separately scoped if regulatory compliance mandates preserving deleted entries.

---

## Running Automated Tests & Build

### 1. Backend Maven Test Suite (292 tests)
```bash
# Set Java 21 LTS
export JAVA_HOME=$(/usr/libexec/java_home -v 21)

# Run complete backend test suite (292 tests, zero real Firebase credentials required)
mvn test

# Clean compile, execute tests, and package executable fat JAR
mvn clean test package
```

### 2. Frontend Automated Test Suite (49 tests)
```bash
# Execute pure vanilla ES6+ Node.js test runner
node --test frontend/test/*.test.mjs
```

### 3. Responsive Design Audit & Browser Verification
```bash
# Audits all pages across 6 target breakpoints (375x667, 390x844, 768x1024, 820x1180, 1280x800, 1440x900)
node ../scratch/milestone-5-responsive-audit.mjs
```

### Complete Test Suite Breakdown (341 tests passing)

| Suite | Component | Tests | Focus Area | Status |
|---|---|---|---|---|
| **Backend** | `AnalyticsControllerTest` | 14 | REST contracts for summary, trends, categories, clients | PASS |
| **Backend** | `AnalyticsServiceTest` | 24 | Financial aggregations, profit margins, 422 processing limit safeguards | PASS |
| **Backend** | `FirestoreTransactionAggregationTest` | 18 | Aggregation pipelines, grouping, uncategorized fallbacks | PASS |
| **Backend** | `FirestoreTransactionRepositoryTest` | 12 | Isolation, category/client validations, zero category writes | PASS |
| **Backend** | `TransactionServiceTest` | 14 | RBAC write rejection, workspace currency inheritance, 501 sentinel search | PASS |
| **Backend** | `TransactionControllerTest` | 11 | Transaction REST endpoints, validation errors, 201/200/204, 403, 404 | PASS |
| **Backend** | `FirestoreCategoryRepositoryTest` | 10 | Category CRUD, transactional archival, idempotent batch seeding | PASS |
| **Backend** | `CategoryServiceTest` | 13 | Category business rules, default category protection, self-healing | PASS |
| **Backend** | `CategoryControllerTest` | 15 | Category REST endpoints, validation, RBAC, 201/200/204/400/403/404 | PASS |
| **Backend** | `CurrencyUtilsTest` | 18 | ISO-4217 fraction digits, JPY zero-decimal, BigDecimal parsing & overflow | PASS |
| **Backend** | `TransactionModelTest` | 18 | Domain entity models, enums (`TransactionType`, `PaymentMethod`, `TransactionStatus`) | PASS |
| **Backend** | `ClientControllerTest` | 15 | Client REST endpoints, validation, 201/200/204, MEMBER 403 write guard, 404 | PASS |
| **Backend** | `ClientServiceTest` | 8 | Client business logic, RBAC matrix, audit field preservation, pagination | PASS |
| **Backend** | `FirestoreClientRepositoryTest` | 8 | Client document mapping, search/filter, update, delete, DB error translation | PASS |
| **Backend** | `WorkspaceControllerTest` | 13 | Endpoint contracts, validation, 201/200/400/401/403/404, spoofing prevention | PASS |
| **Backend** | `WorkspaceServiceTest` | 9 | Business logic, default currency, owner assignment, access-control rules | PASS |
| **Backend** | `FirestoreWorkspaceRepositoryTest` | 5 | Atomic batch writes, document mapping, database exception handling | PASS |
| **Backend** | `AuthControllerTest` | 10 | Token extraction, expiry, invalid tokens, CORS preflight | PASS |
| **Backend** | `GlobalExceptionHandlerTest` | 7 | Validation mapping, 400, 401, 403, 404, 500 error sanitization | PASS |
| **Backend** | `SupportedCurrenciesTest` | 15 | ISO currency validation, currency precision support | PASS |
| **Backend** | `AnalyticsDtoContractTest` | 7 | DTO serialization, completeness metadata, limit details | PASS |
| **Backend** | `FirestoreIndexConfigurationTest` | 5 | Declarative index parity, collection group index verification | PASS |
| **Backend** | `HealthControllerTest` | 4 | Public health probe and JSON structure | PASS |
| **Backend** | `BusinessOpsApplicationTests` | 1 | Spring application context startup smoke test | PASS |
| **Frontend** | `frontend-auth-api.test.mjs` | 11 | Token injection, 401 token refresh & retry, workspace state resolution | PASS |
| **Frontend** | `dashboard-analytics.test.mjs` | 13 | Query string builder, date presets, KPI calculations, 422 safeguards | PASS |
| **Frontend** | `operations-utils.test.mjs` | 4 | Filter query builder, HTML escaping, minor unit currency arithmetic | PASS |
| **Frontend** | `quality-hardening.test.mjs` | 21 | Centralized error sanitization (500/422/403/401/0), modal focus trapping, WCAG 2.1 AA audits | PASS |
| **Total** | **Combined Full Suite** | **341** | **100% Passing, zero test failures, zero errors, zero regressions** | **PASS** |

---

## Frontend Architecture & Technology Stack

The frontend is built with pure, framework-free web standards complying strictly with the Global UI/UX Design System:

* **HTML5:** Semantic document structure (`<main>`, `<nav>`, `<header>`, `<table>`, `<caption>`, `<dialog>`)
* **CSS3:** Custom design tokens (`variables.css`), responsive layouts (`base.css`), reusable components (`components.css`), shimmer skeletons (`.skeleton`), enterprise color system (`#334B68`, `#F5F6F8`, `#202938`, `#687386`)
* **Vanilla JavaScript ES6+:** Native modular JS without React, Next.js, Vue, or build-step compilers
* **Chart.js v4.4.x:** Interactive responsive visualizations (financial trends time-series, category distribution doughnut)
* **Lucide Icons:** Consistent SVG iconography replacing emojis or decorative graphics
* **Accessibility (WCAG 2.1 AA):** Skip navigation links on all pages, visible `:focus-visible` rings, modal keyboard focus trapping and Escape-key dismissal, table captions, scoped headers, ARIA live regions, minimum 44x44px touch targets on mobile
* **Error Resilience:** Centralized API error handling with sanitized 500 server messages (zero backend stack trace leakage), automatic 401 token refresh and retry, contextual empty states with one-click recovery actions, and CSS shimmer loading skeletons.

---

## Running the Application Locally

```bash
# 1. Set Firebase Admin credentials (path to service account JSON)
export GOOGLE_APPLICATION_CREDENTIALS="/path/to/serviceAccountKey.json"

# 2. Run Spring Boot
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
mvn spring-boot:run
```

The application runs on **`http://localhost:8080`**.

---

## Contributing & Security Guidelines

* Never commit service-account JSON keys, `.env` files, or private credentials.
* Never trust client-supplied identity parameters (`uid`, `ownerUid`, `role`).
* Keep all business records strictly partitioned by workspace document IDs.
* Never execute unbounded Firestore collection queries.

---

## License

MIT
