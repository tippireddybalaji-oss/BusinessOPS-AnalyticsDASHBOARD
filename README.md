# BusinessOPS Analytics Dashboard

> The first full-stack application I'm building end-to-end with the goal of
> scaling it into a successful SaaS product.

---

## Overview

**BusinessOPS Analytics Dashboard** is a full-stack business operations and
analytics platform built on:

| Layer | Technology |
|---|---|
| Backend runtime | Java 21 + Spring Boot 3 |
| Build tool | Apache Maven 3.x |
| Authentication | Firebase Authentication |
| Database | Cloud Firestore |
| Firebase SDK | Firebase Admin SDK for Java |
| Frontend *(Phase 2)* | HTML5, CSS3, Vanilla JavaScript, Chart.js |

---

## Project Phases

- [x] **Phase 1 – Backend foundation** ← *current*
  - Maven / Spring Boot project scaffold
  - Package structure (config, controller, service, security, model, repository, exception)
  - `GET /api/health` endpoint
  - Firebase Admin SDK wired with graceful degradation
  - Automated test suite
- [ ] Phase 2 – Frontend (HTML5 / JS / Chart.js)
- [ ] Phase 3 – Client management & transactions API
- [ ] Phase 4 – Analytics engine & dashboard charts
- [ ] Phase 5 – SaaS multi-tenancy & billing

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
    │   │   ├── BusinessOpsApplication.java   ← entry point
    │   │   ├── config/
    │   │   │   └── FirebaseConfig.java       ← Firebase Admin SDK init
    │   │   ├── controller/
    │   │   │   └── HealthController.java     ← GET /api/health
    │   │   ├── exception/                    ← (reserved for Phase 3+)
    │   │   ├── model/
    │   │   │   └── HealthResponse.java       ← health JSON record
    │   │   ├── repository/                   ← (reserved for Firestore repos)
    │   │   ├── security/                     ← (reserved for Firebase auth filter)
    │   │   └── service/                      ← (reserved for business logic)
    │   └── resources/
    │       └── application.properties
    └── test/
        └── java/com/businessops/dashboard/
            ├── BusinessOpsApplicationTests.java
            └── controller/
                └── HealthControllerTest.java
```

---

## Prerequisites

| Tool | Required version |
|---|---|
| Java (JDK) | **21** (LTS) |
| Apache Maven | 3.8 or newer |
| Firebase project | Any plan (Spark / Blaze) |

> **macOS with Homebrew:** install Java 21 with `brew install openjdk@21` and
> Maven with `brew install maven`.  
> Make sure `JAVA_HOME` points to Java 21 when you run Maven:
> ```bash
> export JAVA_HOME=$(/usr/libexec/java_home -v 21)
> ```

---

## Firebase Credentials Setup

> ⚠️ **Never commit your service-account JSON file.** It is already listed in
> `.gitignore`.

1. Go to your [Firebase Console](https://console.firebase.google.com) →
   Project Settings → Service Accounts.
2. Click **Generate new private key** and download the JSON file.
3. Store the file somewhere safe **outside** the repository, then export:

```bash
export GOOGLE_APPLICATION_CREDENTIALS="/path/to/your-service-account.json"
```

Alternatively, set the property in `application.properties` *(local only – do not commit)*:

```properties
firebase.service-account-path=/path/to/your-service-account.json
```

The application starts without Firebase credentials (graceful degradation) –
all non-Firebase endpoints work normally; Firebase-backed endpoints will fail
at runtime with a clear error.

---

## Running the Application

```bash
# 1. Clone the repository
git clone https://github.com/tippireddybalaji-oss/BusinessOPS-AnalyticsDASHBOARD.git
cd BusinessOPS-AnalyticsDASHBOARD

# 2. (Optional) set Firebase credentials
export GOOGLE_APPLICATION_CREDENTIALS="/path/to/serviceAccountKey.json"

# 3. Build and run
export JAVA_HOME=$(/usr/libexec/java_home -v 21)   # macOS only
mvn spring-boot:run
```

The server starts on **http://localhost:8080**.

---

## API Endpoints – Phase 1

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/api/health` | None | Service liveness check |

### Health check example

```bash
curl -s http://localhost:8080/api/health | python3 -m json.tool
```

Expected response:

```json
{
  "status": "UP",
  "application": "BusinessOPS and analyticsDASHBOARD"
}
```

---

## Running Tests

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)   # macOS only
mvn test
```

Tests run without real Firebase credentials (the SDK initialisation degrades
gracefully and logs a warning).

---

## Contributing

This project is in active development.  
Pull requests and issue reports are welcome once the Phase 1 foundation is stable.

---

## License

MIT – see [LICENSE](LICENSE) *(to be added)*.
