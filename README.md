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
| Security | Spring Security (stateless, token-based) |
| Frontend *(Phase 3+)* | HTML5, CSS3, Vanilla JavaScript, Chart.js |

---

## Project Phases

- [x] **Phase 1 – Backend foundation** — Maven / Spring Boot scaffold, health endpoint, tests
- [x] **Phase 2 – Firebase Auth + Firestore integration** ← *current*
  - Firebase Admin SDK initialised via Application Default Credentials
  - Spring Security filter that verifies Firebase ID tokens server-side
  - `GET /api/auth/me` — authenticated identity endpoint
  - Reusable `FirebaseAuth` and `Firestore` Spring beans
  - Full test suite (10 tests, all passing, no real credentials required)
- [ ] Phase 3 – Frontend (HTML5 / JS / Chart.js)
- [ ] Phase 4 – Client management & transactions API
- [ ] Phase 5 – Analytics engine & dashboard charts
- [ ] Phase 6 – SaaS multi-tenancy & billing

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
    │   │   │   └── SecurityConfig.java            ← Spring Security rules
    │   │   ├── controller/
    │   │   │   ├── AuthController.java            ← GET /api/auth/me (protected)
    │   │   │   └── HealthController.java          ← GET /api/health (public)
    │   │   ├── exception/
    │   │   │   └── FirebaseAuthenticationException.java
    │   │   ├── model/
    │   │   │   ├── AuthUserResponse.java          ← {uid, email} record
    │   │   │   └── HealthResponse.java            ← {status, application} record
    │   │   ├── repository/                        ← (reserved for Firestore repos – Phase 4)
    │   │   ├── security/
    │   │   │   └── FirebaseAuthFilter.java        ← Bearer token extraction & verification
    │   │   └── service/                           ← (reserved for business logic – Phase 4)
    │   └── resources/
    │       └── application.properties
    └── test/
        ├── java/com/businessops/dashboard/
        │   ├── BusinessOpsApplicationTests.java   ← full context smoke test
        │   └── controller/
        │       ├── AuthControllerTest.java        ← 5 tests (mocked FirebaseAuth)
        │       └── HealthControllerTest.java      ← 4 tests (public access verified)
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

Go to [console.firebase.google.com](https://console.firebase.google.com) and
create or select a project.

### 2. Enable Firebase Authentication

In the console: **Authentication → Sign-in method** → enable the providers you need
(e.g. Email/Password, Google).

### 3. Generate a service-account key

**Project Settings → Service Accounts → Generate new private key**

Download the JSON file and store it **outside the repository**.

> ⚠️ **Never commit this file.** It is blocked by `.gitignore`.

### 4. Set `GOOGLE_APPLICATION_CREDENTIALS`

```bash
export GOOGLE_APPLICATION_CREDENTIALS="/absolute/path/to/serviceAccountKey.json"
```

Add this line to `~/.zshrc` (or `~/.bashrc`) to make it permanent.

The Firebase Admin SDK reads this environment variable automatically — no path
needs to be written into `application.properties`.

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

### Public (no authentication required)

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/health` | Service liveness check |

```bash
curl -s http://localhost:8080/api/health
# {"status":"UP","application":"BusinessOPS and analyticsDASHBOARD"}
```

### Protected (Firebase ID token required)

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/auth/me` | Returns the verified caller's UID and email |

#### How to get a Firebase ID token (for manual testing)

In any Firebase-enabled client (web, Android, iOS) or via the Firebase REST API:

```bash
# Firebase REST Auth API – Email/Password sign-in
curl -s -X POST \
  "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=YOUR_WEB_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","password":"password","returnSecureToken":true}' \
  | python3 -m json.tool
# Copy the value of "idToken"
```

#### Test the auth endpoint

```bash
ID_TOKEN="<paste-id-token-here>"

curl -s \
  -H "Authorization: Bearer $ID_TOKEN" \
  http://localhost:8080/api/auth/me | python3 -m json.tool
# {
#   "uid": "abc123firebaseUid",
#   "email": "user@example.com"
# }
```

#### Missing or invalid token → 401

```bash
curl -s http://localhost:8080/api/auth/me
# {"error":"Authentication required.","status":401}
```

---

## Running Tests

No real Firebase credentials are needed to run the test suite.
The Firebase beans are mocked in tests.

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
mvn test
```

### Test results (Phase 2)

```
Tests run: 10, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS
```

| Test class | Tests | What is covered |
|---|---|---|
| `HealthControllerTest` | 4 | Public health endpoint, no token required |
| `AuthControllerTest` | 5 | Missing token → 401, invalid token → 401, valid token → 200 with uid+email, null email, no raw token fields |
| `BusinessOpsApplicationTests` | 1 | Full Spring context loads with mocked Firebase beans |

---

## Security Design

| Concern | Approach |
|---|---|
| Token trust | UID extracted **only** from server-verified `FirebaseToken`, never from client input |
| Stateless | No HTTP sessions; every request carries its own ID token |
| Secrets | `GOOGLE_APPLICATION_CREDENTIALS` env var only; no paths in version-controlled files |
| `.gitignore` | Service-account JSON patterns, `.env`, `.pem`, `.p12` all blocked |
| Graceful degradation | App starts without Firebase credentials; only Firebase-backed endpoints fail |
| CSRF | Disabled (REST API, not a browser form) |

---

## Contributing

This project is in active development. Pull requests and issue reports are welcome.

---

## License

MIT – see [LICENSE](LICENSE) *(to be added)*.
