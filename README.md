# BusinessOPS Analytics Dashboard

> The first full-stack application I'm building end-to-end with the goal of
> scaling it into a successful SaaS product.

---

## Overview

**BusinessOPS Analytics Dashboard** is a full-stack business operations and
analytics platform built on:

| Layer | Technology |
|---|---|
| Backend runtime | Java 21 + Spring Boot 3.4.5 |
| Build tool | Apache Maven 3.x |
| Authentication | Firebase Authentication (ID token verification) |
| Database | Cloud Firestore |
| Firebase SDK | Firebase Admin SDK 9.4.1 for Java |
| Security | Spring Security (stateless, token-based, CORS-configured) |
| Error Handling | Centralized `@RestControllerAdvice` (`ApiErrorResponse`) |
| Frontend *(Phase 3+)* | HTML5, CSS3, Vanilla JavaScript (ES6+), Chart.js |

---

## Project Phases

- [x] **Phase 1 – Backend foundation** — Maven / Spring Boot scaffold, health endpoint, tests
- [x] **Phase 2 – Firebase Auth + Firestore integration**
  - Firebase Admin SDK initialised via Application Default Credentials
  - Spring Security filter verifying Firebase ID tokens server-side
  - Standardized `GET /api/v1/me` (and `GET /api/auth/me`) identity endpoints
  - Centralized exception handling with `GlobalExceptionHandler` and `ApiErrorResponse`
  - Configurable CORS support (development origins + production override)
  - Reusable `FirebaseAuth` and `Firestore` Spring beans
  - Comprehensive automated test suite with zero external credential dependencies
- [ ] Phase 3 – Frontend (HTML5 / CSS3 / Vanilla JS / Chart.js)
- [ ] Phase 4 – Workspace model & access control
- [ ] Phase 5 – Client management & transactions API
- [ ] Phase 6 – Analytics engine & dashboard charts
- [ ] Phase 7 – SaaS multi-tenancy & billing

---

## Repository Structure

```
BusinessOPS-AnalyticsDASHBOARD/
├── pom.xml
├── .gitignore
├── README.md
└── src/
    ├── main/
    │   ├── java/com/businessops/dashboard/
    │   │   ├── BusinessOpsApplication.java       ← entry point
    │   │   ├── config/
    │   │   │   ├── FirebaseConfig.java            ← FirebaseApp + FirebaseAuth + Firestore beans
    │   │   │   └── SecurityConfig.java            ← Spring Security + CORS rules
    │   │   ├── controller/
    │   │   │   ├── AuthController.java            ← GET /api/v1/me (and legacy /api/auth/me)
    │   │   │   └── HealthController.java          ← GET /api/health (public)
    │   │   ├── exception/
    │   │   │   ├── FirebaseAuthenticationException.java
    │   │   │   └── GlobalExceptionHandler.java    ← Centralized @RestControllerAdvice
    │   │   ├── model/
    │   │   │   ├── ApiErrorResponse.java          ← Standardized REST error payload
    │   │   │   ├── AuthUserResponse.java          ← {uid, email} record
    │   │   │   └── HealthResponse.java            ← {status, application} record
    │   │   ├── repository/                        ← (reserved for Firestore repos)
    │   │   ├── security/
    │   │   │   └── FirebaseAuthFilter.java        ← Bearer token extraction & verification
    │   │   └── service/                           ← (reserved for business logic)
    │   └── resources/
    │       └── application.properties
    └── test/
        ├── java/com/businessops/dashboard/
        │   ├── BusinessOpsApplicationTests.java   ← full context smoke test
        │   ├── controller/
        │   │   ├── AuthControllerTest.java        ← 10 tests (auth, expiration, spoofing, CORS)
        │   │   └── HealthControllerTest.java      ← 4 tests (public health probe)
        │   └── exception/
        │       └── GlobalExceptionHandlerTest.java← 6 tests (validation, 401, 403, 500)
        └── resources/
            └── application-test.properties
```

---

## Prerequisites

| Tool | Required version |
|---|---|
| Java (JDK) | **21** (LTS) |
| Apache Maven | 3.8 or newer |
| Firebase project | Any plan (Spark / Blaze) |

> **macOS with Homebrew:**
> ```bash
> brew install openjdk@21 maven
> export JAVA_HOME=$(/usr/libexec/java_home -v 21)
> ```

---

## Firebase Admin SDK Setup

### 1. Create a Firebase project

Go to [console.firebase.google.com](https://console.firebase.google.com) and create or select a project.

### 2. Enable Firebase Authentication

In the console: **Authentication → Sign-in method** → enable the providers you need (e.g. Email/Password, Google).

### 3. Generate a service-account key

**Project Settings → Service Accounts → Generate new private key**

Download the JSON file and store it **outside the repository**.

> ⚠️ **Never commit this file.** It is blocked by `.gitignore`.

### 4. Set `GOOGLE_APPLICATION_CREDENTIALS`

```bash
export GOOGLE_APPLICATION_CREDENTIALS="/absolute/path/to/serviceAccountKey.json"
```

Add this line to `~/.zshrc` (or `~/.bashrc`) to make it permanent.

The Firebase Admin SDK reads this environment variable automatically — no path needs to be written into `application.properties`.

---

## Running the Application

```bash
# 1. Clone
git clone https://github.com/tippireddybalaji-oss/BusinessOPS-AnalyticsDASHBOARD.git
cd BusinessOPS-AnalyticsDASHBOARD

# 2. Set Firebase credentials (required for Firebase-backed endpoints)
export GOOGLE_APPLICATION_CREDENTIALS="/path/to/serviceAccountKey.json"

# 3. Set Java 21 (macOS)
export JAVA_HOME=$(/usr/libexec/java_home -v 21)

# 4. Run
mvn spring-boot:run
```

The server starts on **http://localhost:8080**.

---

## API Endpoints

### Public Endpoints (no authentication required)

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/health` | Service liveness probe |
| `OPTIONS` | `/**` | CORS preflight checks |

```bash
curl -s http://localhost:8080/api/health
# {"status":"UP","application":"BusinessOPS and analyticsDASHBOARD"}
```

### Protected Endpoints (Firebase ID token required)

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/v1/me` | Standardized identity endpoint; returns verified caller's UID and email |
| `GET` | `/api/auth/me` | Legacy alias for `/api/v1/me` |

#### Test the auth endpoint

```bash
ID_TOKEN="<paste-firebase-id-token-here>"

curl -s \
  -H "Authorization: Bearer $ID_TOKEN" \
  http://localhost:8080/api/v1/me | python3 -m json.tool
# {
#   "uid": "abc123firebaseUid",
#   "email": "user@example.com"
# }
```

#### Missing, invalid, or expired token → 401

```bash
curl -s http://localhost:8080/api/v1/me
# {"error":"Authentication required.","status":401}
```

---

## Centralized Error Handling

All REST exceptions are caught by `GlobalExceptionHandler` and returned as a standardized `ApiErrorResponse` payload:

```json
{
  "error": "Validation Failed",
  "status": 400,
  "message": "Input validation failed. Please check field errors.",
  "path": "/api/v1/workspaces",
  "timestamp": "2026-11-03T13:20:00Z",
  "validationErrors": {
    "name": "Workspace name cannot be blank"
  }
}
```

Stack traces and sensitive internal details are never leaked in error messages.

---

## CORS Configuration

Configured in `SecurityConfig` via `app.cors.allowed-origins` in `application.properties`:
* **Default development origins:** `http://localhost:3000`, `http://127.0.0.1:3000`, `http://localhost:5500`, `http://127.0.0.1:5500`, `http://localhost:8080`, `http://127.0.0.1:8080`
* **Production override:** Set environment variable `APP_CORS_ALLOWED_ORIGINS="https://dashboard.example.com"`
* **Allowed HTTP methods:** `GET`, `POST`, `PUT`, `DELETE`, `PATCH`, `OPTIONS`
* **Allowed headers:** `Authorization`, `Content-Type`, `Accept`, `X-Requested-With`, `Origin`
* **Pre-flight caching:** 1 hour (`maxAge = 3600`)

---

## Running Tests & Build

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)

# Run automated tests
mvn test

# Package build (creates executable JAR in target/)
mvn clean package
```

Zero real Firebase credentials are required for running tests; all Firebase components are mocked.

---

## Security Invariants

| Invariant | Implementation |
|---|---|
| Token trust | UID extracted **only** from server-verified `FirebaseToken`, never from client input |
| Statelessness | No HTTP sessions or cookies; every request carries its own ID token |
| Secrets hygiene | `GOOGLE_APPLICATION_CREDENTIALS` env var only; no credentials in version control |
| `.gitignore` | Service-account JSON patterns, `.env`, `.pem`, `.p12` blocked |
| Graceful degradation | App boots without Firebase credentials; only Firebase-backed routes fail |
| CORS | Explicitly configured per environment; preflight cached |
| CSRF | Disabled (REST API, stateless Bearer token) |

---

## License

MIT – see [LICENSE](LICENSE) *(to be added)*.
