# BusinessOPS Analytics Dashboard

> The first full-stack application I'm building end-to-end with the goal of
> scaling it into a successful SaaS product.

---

## Overview

**BusinessOPS Analytics Dashboard** is an enterprise-grade business operations and
analytics platform built on:

| Layer | Technology |
|---|---|
| Backend runtime | Java 21 + Spring Boot 3.4.5 |
| Build tool | Apache Maven 3.x |
| Authentication | Firebase Authentication (Server-verified ID tokens) |
| Database | Cloud Firestore |
| Server integration | Firebase Admin SDK 9.4.1 for Java |
| Security & Access Control | Spring Security (stateless, token-based, server-side authorization) |
| Error Handling | Centralized `@RestControllerAdvice` (`ApiErrorResponse`) |
| Frontend *(Future Phase)* | HTML5, CSS3, Vanilla JavaScript (ES6+), Chart.js |

---

## Project Phases

- [x] **Phase 0 – Repository inspection & pre-flight audit**
- [x] **Phase 1 – Spring Boot backend foundation** (scaffold, health probe, tests)
- [x] **Phase 2 – Workspace Model, Firestore Architecture & Server-Side Authorization** ← *current*
  - Cloud Firestore collection structure: `users`, `workspaces`, `workspaces/{id}/members`
  - Atomic workspace creation with automatic `OWNER` role provisioning
  - Server-side authorization verifying workspace membership and ownership
  - REST API v1 endpoints: `POST /api/v1/workspaces`, `GET /api/v1/workspaces`, `GET /api/v1/workspaces/{id}`
  - Standardized `GET /api/v1/me` identity endpoint (and `/api/auth/me` alias)
  - Jakarta Bean Validation and currency normalization (INR default)
  - 48 automated tests (100% passing, zero external credential dependencies)
- [ ] Phase 3 – Client management (CRUD, status filter, pagination)
- [ ] Phase 4 – Income and expense transactions (minor units / paise, categories)
- [ ] Phase 5 – Analytics engine (Net cash flow, monthly trends, category charts)
- [ ] Phase 6 – Static frontend & Chart.js integration
- [ ] Phase 7 – Quality hardening, deployment & handoff

---

## Firestore Database Architecture

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
        ├── defaultCurrency: "INR"
        ├── createdAt: Timestamp
        ├── updatedAt: Timestamp
        │
        ├── members/
        │   └── {uid}
        │       ├── uid: String
        │       ├── role: "OWNER" | "ADMIN" | "MEMBER"
        │       └── joinedAt: Timestamp
        │
        ├── clients/             (Reserved for Phase 3)
        ├── transactions/        (Reserved for Phase 4)
        └── categories/          (Reserved for Phase 4)
```

### Document Fields Specification

#### Workspaces Collection: `workspaces/{workspaceId}`
* `name` (String, required): Business or company name (2–100 characters).
* `ownerUid` (String, required): Firebase UID of the creator, derived exclusively from verified ID token.
* `defaultCurrency` (String, required): Defaults to `"INR"`. Supported: `INR`, `USD`, `EUR`, `GBP`, `AUD`, `CAD`, `SGD`, `AED`, `JPY`.
* `createdAt` (Timestamp): Server timestamp of creation.
* `updatedAt` (Timestamp): Server timestamp of latest modification.

#### Workspace Members Subcollection: `workspaces/{workspaceId}/members/{uid}`
* `uid` (String): Member's Firebase UID (also used as document ID).
* `role` (String): Workspace role: `OWNER`, `ADMIN`, or `MEMBER`.
* `joinedAt` (Timestamp): Server timestamp when the user became a member.

---

## Server-Side Authorization Invariants

> ⚠️ **Important Security Rule:** The Firebase Admin SDK operates with privileged server credentials and **bypasses Firestore Security Rules**. Therefore, Spring Boot strictly enforces authorization in the service layer before executing database operations:

1. **Authentication:** The `FirebaseAuthFilter` cryptographically verifies the `Authorization: Bearer <token>` header server-side.
2. **Identity Derivation:** The caller's UID is extracted **only** from `FirebaseToken.getUid()`. Request parameters, query strings, and body fields attempting to specify `ownerUid` or `uid` are ignored.
3. **Owner Assignment:** During workspace creation, the authenticated UID is automatically assigned as `ownerUid`, and an initial member record with role `OWNER` is written atomically in a Firestore `WriteBatch`.
4. **Access Control:**
   * Retrieval of `workspaces/{id}` verifies that the caller is either the `ownerUid` or exists in the `members/{uid}` subcollection.
   * If a non-existent workspace ID is requested: returns `404 Not Found`.
   * If an unauthorized user attempts access (cross-workspace isolation breach): returns `403 Forbidden`.
   * Listing `GET /api/v1/workspaces` returns only workspaces owned by or shared with the caller.

---

## REST API Endpoints

### Public Endpoints (no auth required)

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/health` | Service liveness probe |
| `OPTIONS` | `/**` | CORS preflight checks |

### Protected Endpoints (Firebase ID token required)

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/v1/me` | Returns verified caller identity (`uid`, `email`) |
| `POST` | `/api/v1/workspaces` | Creates a new workspace; creator becomes `OWNER` |
| `GET` | `/api/v1/workspaces` | Lists all workspaces accessible to the caller |
| `GET` | `/api/v1/workspaces/{id}` | Retrieves workspace details (enforces membership) |

---

## API Request & Response Examples

### 1. Create Workspace

**Request:**
```bash
POST /api/v1/workspaces
Authorization: Bearer <Firebase_ID_Token>
Content-Type: application/json

{
  "name": "Bharat Retail Operations",
  "defaultCurrency": "INR"
}
```

**Response (`201 Created`):**
```json
{
  "id": "7d9e1f2a-8c3b-4e5d-9f0a-1b2c3d4e5f6a",
  "name": "Bharat Retail Operations",
  "ownerUid": "firebaseUid12345",
  "defaultCurrency": "INR",
  "createdAt": "2026-11-03T13:40:00Z",
  "updatedAt": "2026-11-03T13:40:00Z",
  "role": "OWNER"
}
```

### 2. List Workspaces

**Request:**
```bash
GET /api/v1/workspaces
Authorization: Bearer <Firebase_ID_Token>
```

**Response (`200 OK`):**
```json
[
  {
    "id": "7d9e1f2a-8c3b-4e5d-9f0a-1b2c3d4e5f6a",
    "name": "Bharat Retail Operations",
    "ownerUid": "firebaseUid12345",
    "defaultCurrency": "INR",
    "createdAt": "2026-11-03T13:40:00Z",
    "updatedAt": "2026-11-03T13:40:00Z",
    "role": "OWNER"
  }
]
```

### 3. Retrieve Workspace Details

**Request:**
```bash
GET /api/v1/workspaces/7d9e1f2a-8c3b-4e5d-9f0a-1b2c3d4e5f6a
Authorization: Bearer <Firebase_ID_Token>
```

**Response (`200 OK`):**
```json
{
  "id": "7d9e1f2a-8c3b-4e5d-9f0a-1b2c3d4e5f6a",
  "name": "Bharat Retail Operations",
  "ownerUid": "firebaseUid12345",
  "defaultCurrency": "INR",
  "createdAt": "2026-11-03T13:40:00Z",
  "updatedAt": "2026-11-03T13:40:00Z",
  "role": "OWNER"
}
```

### 4. Error Responses

* **Workspace not found (`404 Not Found`):**
  ```json
  {
    "error": "Not Found",
    "status": 404,
    "message": "Workspace not found with id: missing-id",
    "path": "/api/v1/workspaces/missing-id",
    "timestamp": "2026-11-03T13:40:05Z"
  }
  ```
* **Cross-workspace access denied (`403 Forbidden`):**
  ```json
  {
    "error": "Forbidden",
    "status": 403,
    "message": "You do not have permission to access workspace: other-user-workspace",
    "path": "/api/v1/workspaces/other-user-workspace",
    "timestamp": "2026-11-03T13:40:10Z"
  }
  ```
* **Validation error (`400 Bad Request`):**
  ```json
  {
    "error": "Validation Failed",
    "status": 400,
    "message": "Input validation failed. Please check field errors.",
    "path": "/api/v1/workspaces",
    "timestamp": "2026-11-03T13:40:15Z",
    "validationErrors": {
      "name": "Workspace name cannot be blank"
    }
  }
  ```

---

## Required Firestore Indexes

For listing workspaces across team memberships, Cloud Firestore supports single-field collection-group queries:
* **Collection Group:** `members`
* **Field:** `uid` (Ascending)
* **Scope:** Collection Group

*Note:* If the collection group index is pending, the backend automatically falls back to direct `ownerUid` queries on the `workspaces` collection without crashing.

---

## Running Automated Tests & Build

```bash
# Set Java 21 LTS
export JAVA_HOME=$(/usr/libexec/java_home -v 21)

# Run complete test suite (48 tests, zero real Firebase credentials needed)
mvn test

# Clean compile and package executable Spring Boot JAR
mvn clean test package
```

### Test Suite Breakdown (48 tests passing)

| Test Class | Tests | Focus Area |
|---|---|---|
| `WorkspaceControllerTest` | 13 | Endpoint contracts, validation, 201/200/400/401/403/404, spoofing prevention |
| `WorkspaceServiceTest` | 9 | Business logic, default currency, owner assignment, access-control rules |
| `FirestoreWorkspaceRepositoryTest` | 5 | Atomic batch writes, document mapping, database exception handling |
| `AuthControllerTest` | 10 | Token extraction, expiry, invalid tokens, CORS preflight |
| `GlobalExceptionHandlerTest` | 6 | Validation mapping, 400, 401, 403, 404, 500 error sanitization |
| `HealthControllerTest` | 4 | Public health probe and JSON structure |
| `BusinessOpsApplicationTests` | 1 | Spring application context startup smoke test |

---

## Running the Application Locally

```bash
# 1. Set Firebase Admin credentials (outside repo)
export GOOGLE_APPLICATION_CREDENTIALS="/path/to/serviceAccountKey.json"

# 2. Run Spring Boot
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
mvn spring-boot:run
```

Server runs on: **`http://localhost:8080`**.

---

## Contributing & Security Guidelines

* Never commit service-account JSON keys, `.env` files, or private keys.
* Never trust client-supplied identity parameters (`uid`, `ownerUid`, `role`).
* Keep all business records strictly partitioned by workspace document IDs.

---

## License

MIT – see [LICENSE](LICENSE) *(to be added)*.
