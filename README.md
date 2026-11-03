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
- [x] **Phase 2 – Workspace Model, Firestore Architecture & Server-Side Authorization**
  - Cloud Firestore collection structure: `users`, `workspaces`, `workspaces/{id}/members`
  - Atomic workspace creation with automatic `OWNER` role provisioning
  - Server-side authorization verifying workspace membership and ownership
  - REST API v1 endpoints: `POST /api/v1/workspaces`, `GET /api/v1/workspaces`, `GET /api/v1/workspaces/{id}`
  - Standardized `GET /api/v1/me` identity endpoint (and `/api/auth/me` alias)
  - Jakarta Bean Validation and currency normalization (INR default)
- [x] **Phase 3 – Client Management Module** ← *current*
  - Dedicated subcollection `workspaces/{workspaceId}/clients/{clientId}`
  - Full CRUD operations with server-side role-based access control (RBAC)
  - Write permissions restricted to `OWNER` & `ADMIN`; `MEMBER` granted read-only access
  - Filter by `ClientStatus` (`ACTIVE`, `INACTIVE`, `LEAD`) with case-insensitive name/company search
  - Pagination envelope (`PagedResponse`) with `page`, `size`, `totalElements`, `totalPages`, `hasMore`
  - Immutability of creation audit fields (`createdAt`, `createdBy`) during updates
  - Declarative composite Firestore index definition (`firestore.indexes.json`)
  - 80 automated tests (100% passing, zero external credential dependencies)
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
        ├── clients/             (Phase 3 – Implemented)
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

#### Workspace Clients Subcollection: `workspaces/{workspaceId}/clients/{clientId}`
* `id` (String): Auto-generated unique identifier (UUID/Firestore document ID).
* `name` (String, required): Client or contact person name (2–100 characters).
* `email` (String, optional): Valid RFC 5322 email address (up to 150 characters).
* `phone` (String, optional): Contact telephone number (up to 30 characters).
* `company` (String, optional): Organization or trading entity name (up to 120 characters).
* `status` (String, required): Client status enum: `ACTIVE` (default), `INACTIVE`, or `LEAD`.
* `notes` (String, optional): Freeform notes or context (up to 2000 characters).
* `createdAt` (Timestamp): Immutable timestamp when client record was created.
* `updatedAt` (Timestamp): Timestamp when client record was last modified.
* `createdBy` (String): Verified Firebase UID of creator (immutable on updates).
* `updatedBy` (String): Verified Firebase UID of the user who performed the latest update.

---

## Server-Side Authorization Invariants

> ⚠️ **Important Security Rule:** The Firebase Admin SDK operates with privileged server credentials and **bypasses Firestore Security Rules**. Therefore, Spring Boot strictly enforces authorization in the service layer before executing database operations:

1. **Authentication:** The `FirebaseAuthFilter` cryptographically verifies the `Authorization: Bearer <token>` header server-side.
2. **Identity Derivation:** The caller's UID is extracted **only** from `FirebaseToken.getUid()`. Request parameters, query strings, and body fields attempting to specify `ownerUid` or `uid` are ignored.
3. **Owner Assignment:** During workspace creation, the authenticated UID is automatically assigned as `ownerUid`, and an initial member record with role `OWNER` is written atomically in a Firestore `WriteBatch`.
4. **Access Control & RBAC Matrix:**
   * **Workspace Isolation:** Every workspace query verifies that the caller belongs to the target workspace. Non-members receive `403 Forbidden`. Non-existent workspaces yield `404 Not Found`.
   * **Role-Based Permissions for Clients:**
     - `OWNER` / `ADMIN`: Full read and write access (`POST`, `PUT`, `DELETE`).
     - `MEMBER`: Read-only access (`GET` list, `GET` by ID). Any write attempt yields `403 Forbidden` (`Insufficient permissions: Member role cannot perform write operations`).
     - `Non-member`: Immediate `403 Forbidden`.
   * **Audit Immutability:** On client update (`PUT`), `createdAt` and `createdBy` are preserved from the original record; only `updatedAt` and `updatedBy` are refreshed.

---

## REST API Endpoints

### Public Endpoints (no auth required)

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/health` | Service liveness probe |
| `OPTIONS` | `/**` | CORS preflight checks |

### Protected Endpoints (Firebase ID token required)

| Method | Path | Role Required | Description |
|--------|------|---------------|-------------|
| `GET` | `/api/v1/me` | Authenticated | Returns verified caller identity (`uid`, `email`) |
| `POST` | `/api/v1/workspaces` | Authenticated | Creates a new workspace; caller becomes `OWNER` |
| `GET` | `/api/v1/workspaces` | Authenticated | Lists all workspaces accessible to the caller |
| `GET` | `/api/v1/workspaces/{workspaceId}` | `OWNER`, `ADMIN`, `MEMBER` | Retrieves workspace details |
| `POST` | `/api/v1/workspaces/{workspaceId}/clients` | `OWNER`, `ADMIN` | Creates a new client in the workspace |
| `GET` | `/api/v1/workspaces/{workspaceId}/clients` | `OWNER`, `ADMIN`, `MEMBER` | Lists workspace clients (supports `status`, `search`, `page`, `size`) |
| `GET` | `/api/v1/workspaces/{workspaceId}/clients/{clientId}` | `OWNER`, `ADMIN`, `MEMBER` | Retrieves single client details |
| `PUT` | `/api/v1/workspaces/{workspaceId}/clients/{clientId}` | `OWNER`, `ADMIN` | Updates an existing client |
| `DELETE` | `/api/v1/workspaces/{workspaceId}/clients/{clientId}` | `OWNER`, `ADMIN` | Deletes a client |

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

### 4. Create Client in Workspace

**Request:**
```bash
POST /api/v1/workspaces/7d9e1f2a-8c3b-4e5d-9f0a-1b2c3d4e5f6a/clients
Authorization: Bearer <Firebase_ID_Token>
Content-Type: application/json

{
  "name": "Tata Consultancy Services",
  "email": "procurement@tcs.example.com",
  "phone": "+91 98765 43210",
  "company": "Tata Group",
  "status": "ACTIVE",
  "notes": "Key enterprise accounts tier 1"
}
```

**Response (`201 Created`):**
```json
{
  "id": "e9b1c2d3-4f5a-6b7c-8d9e-0f1a2b3c4d5e",
  "workspaceId": "7d9e1f2a-8c3b-4e5d-9f0a-1b2c3d4e5f6a",
  "name": "Tata Consultancy Services",
  "email": "procurement@tcs.example.com",
  "phone": "+91 98765 43210",
  "company": "Tata Group",
  "status": "ACTIVE",
  "notes": "Key enterprise accounts tier 1",
  "createdAt": "2026-11-03T14:15:00Z",
  "updatedAt": "2026-11-03T14:15:00Z",
  "createdBy": "firebaseUid12345",
  "updatedBy": "firebaseUid12345"
}
```

### 5. List Clients with Status Filter & Search

**Request:**
```bash
GET /api/v1/workspaces/7d9e1f2a-8c3b-4e5d-9f0a-1b2c3d4e5f6a/clients?status=ACTIVE&search=Tata&page=0&size=20
Authorization: Bearer <Firebase_ID_Token>
```

**Response (`200 OK`):**
```json
{
  "content": [
    {
      "id": "e9b1c2d3-4f5a-6b7c-8d9e-0f1a2b3c4d5e",
      "workspaceId": "7d9e1f2a-8c3b-4e5d-9f0a-1b2c3d4e5f6a",
      "name": "Tata Consultancy Services",
      "email": "procurement@tcs.example.com",
      "phone": "+91 98765 43210",
      "company": "Tata Group",
      "status": "ACTIVE",
      "notes": "Key enterprise accounts tier 1",
      "createdAt": "2026-11-03T14:15:00Z",
      "updatedAt": "2026-11-03T14:15:00Z",
      "createdBy": "firebaseUid12345",
      "updatedBy": "firebaseUid12345"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1,
  "hasMore": false
}
```

### 6. Update Client

**Request:**
```bash
PUT /api/v1/workspaces/7d9e1f2a-8c3b-4e5d-9f0a-1b2c3d4e5f6a/clients/e9b1c2d3-4f5a-6b7c-8d9e-0f1a2b3c4d5e
Authorization: Bearer <Firebase_ID_Token>
Content-Type: application/json

{
  "name": "Tata Consultancy Services Ltd",
  "email": "procurement@tcs.com",
  "phone": "+91 98765 43210",
  "company": "Tata Sons",
  "status": "ACTIVE",
  "notes": "Updated contact person and corporate entity"
}
```

**Response (`200 OK`):**
```json
{
  "id": "e9b1c2d3-4f5a-6b7c-8d9e-0f1a2b3c4d5e",
  "workspaceId": "7d9e1f2a-8c3b-4e5d-9f0a-1b2c3d4e5f6a",
  "name": "Tata Consultancy Services Ltd",
  "email": "procurement@tcs.com",
  "phone": "+91 98765 43210",
  "company": "Tata Sons",
  "status": "ACTIVE",
  "notes": "Updated contact person and corporate entity",
  "createdAt": "2026-11-03T14:15:00Z",
  "updatedAt": "2026-11-03T14:20:00Z",
  "createdBy": "firebaseUid12345",
  "updatedBy": "firebaseUid12345"
}
```

### 7. Delete Client

**Request:**
```bash
DELETE /api/v1/workspaces/7d9e1f2a-8c3b-4e5d-9f0a-1b2c3d4e5f6a/clients/e9b1c2d3-4f5a-6b7c-8d9e-0f1a2b3c4d5e
Authorization: Bearer <Firebase_ID_Token>
```

**Response (`204 No Content`)**

---

### 8. Error Responses

* **Client not found (`404 Not Found`):**
  ```json
  {
    "error": "Not Found",
    "status": 404,
    "message": "Client not found with id: missing-client-id in workspace: ws-123",
    "path": "/api/v1/workspaces/ws-123/clients/missing-client-id",
    "timestamp": "2026-11-03T14:20:05Z"
  }
  ```
* **Member role write forbidden (`403 Forbidden`):**
  ```json
  {
    "error": "Forbidden",
    "status": 403,
    "message": "Insufficient permissions: Member role cannot perform write operations in workspace: ws-123",
    "path": "/api/v1/workspaces/ws-123/clients",
    "timestamp": "2026-11-03T14:20:10Z"
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
    "path": "/api/v1/workspaces/ws-123/clients",
    "timestamp": "2026-11-03T14:20:15Z",
    "validationErrors": {
      "email": "Client email must be a valid email address",
      "name": "Client name cannot be blank"
    }
  }
  ```

---

## Required Firestore Indexes

The project includes declarative composite index definitions in `firestore.indexes.json`:

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

### Index Deployment
Deploy indexes to Firebase with the Firebase CLI:
```bash
firebase deploy --only firestore:indexes
```

---

## Running Automated Tests & Build

```bash
# Set Java 21 LTS
export JAVA_HOME=$(/usr/libexec/java_home -v 21)

# Run complete test suite (80 tests, zero real Firebase credentials needed)
mvn test

# Clean compile and package executable Spring Boot JAR
mvn clean test package
```

### Test Suite Breakdown (80 tests passing)

| Test Class | Tests | Focus Area |
|---|---|---|
| `ClientControllerTest` | 15 | Client REST endpoints, validation, 201/200/204, MEMBER 403 write guard, 404 |
| `ClientServiceTest` | 8 | Client business logic, RBAC matrix, audit field preservation, pagination |
| `FirestoreClientRepositoryTest` | 8 | Client document mapping, search/filter, update, delete, DB error translation |
| `WorkspaceControllerTest` | 13 | Endpoint contracts, validation, 201/200/400/401/403/404, spoofing prevention |
| `WorkspaceServiceTest` | 9 | Business logic, default currency, owner assignment, access-control rules |
| `FirestoreWorkspaceRepositoryTest` | 5 | Atomic batch writes, document mapping, database exception handling |
| `AuthControllerTest` | 10 | Token extraction, expiry, invalid tokens, CORS preflight |
| `GlobalExceptionHandlerTest` | 6 | Validation mapping, 400, 401, 403, 404, 500 error sanitization |
| `HealthControllerTest` | 4 | Public health probe and JSON structure |
| `BusinessOpsApplicationTests` | 1 | Spring application context startup smoke test |
| **Total** | **80** | **100% Passing, zero test failures, zero skipped** |

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
