# Sall Whisky Distillery Application

An app for running the Sall Whisky Distillery: grain, distillation runs, barrel fills, warehouse storage and bottling.

[![CI](https://github.com/CMaintz/sall-whisky-distillery/actions/workflows/ci.yml/badge.svg)](https://github.com/CMaintz/sall-whisky-distillery/actions/workflows/ci.yml)

There are two versions in this repo:
- A JavaFX desktop app (`src/`, `Test/`). This is the original, a group exam project from May to June 2024 that I built with
  [@alex8307](https://github.com/alex8307) and [@math113a](https://github.com/math113a).
- A Spring Boot + Angular app (`backend/`, `frontend/`). I rewrote it on my own in 2026 to port the same
  domain to a REST API and a web client.

### Who did what (JavaFX version)

Going by the commit history and `git blame`, the split was roughly:

| Contributor | Main areas |
|-------------|------------|
| @CMaintz | `Controller` facade, `ListStorage` / serialization (save & load), most of the domain models (incl. `WhiskyProdukt` and bottle-label generation), `WhiskyPane` / `WhiskyWindow`, `App` bootstrap and seed data, most of `ModelsTest` |
| @alex8307 | Barrel windows (`FadVindue`, `PåfyldFad`, `FlytFadWindow`, `VisHistorik`), `LagerstyringPane`, `OpretReol`, parts of the models and tests |
| @math113a | Creation dialogs (`OpretFad`, `OpretDestillering`, `OpretKorn`, `OpretLager`), `DestilleringPane`, `StartVindue`, `LoginPane`, parts of the models and tests |

The Spring Boot/Angular rewrite in `backend/` and `frontend/` is mine alone.

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────┐
│              JavaFX Desktop App (src/)               │
│  LoginPane → StartVindue → [4 tabs]                 │
│       ↓ calls                                        │
│  Controller (static facade)                          │
│       ↓ calls                                        │
│  ListStorage (.srl serialization)                    │
└─────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────┐
│         Spring Boot + Angular (backend/ + frontend/) │
│  Angular 17 SPA ──HTTP Basic──> Spring Boot 3.2 REST │
│                                    ↓ JPA             │
│                               H2 in-memory DB        │
└─────────────────────────────────────────────────────┘
```

### Domain Model

```
Korn ──> Destillering ──> Påfyldning ──> Destillat ──> Fad ──> Hylde ──> Reol ──> Lager
                                                         │
                                                    FadTapning ──> WhiskyProdukt ──> WhiskyFlaske
```

The full class diagram, the architecture diagram and sequence diagrams for filling and re-casking a barrel are in [DIAGRAMS.md](DIAGRAMS.md).

---

## Stack 1: JavaFX desktop app

### Requirements
- Java 19 (liberica-19)
- IntelliJ IDEA (no Maven or Gradle, it's a plain IDE project)
- JavaFX on the module path/classpath

### Running
Open it in IntelliJ and run `App.main()` in `src/gui/App.java`.

Login: `admin` / `admin`

### Architecture
Three layers: GUI → Controller → Storage.

| Layer | Location | Notes |
|-------|----------|-------|
| GUI | `src/gui/` | JavaFX panes and modal dialogs |
| Controller | `src/application/controller/Controller.java` | Static abstract class; every method is static |
| Storage | `src/storage/ListStorage.java` | ArrayList-backed, Java object serialization to `storage.srl` |

### Key Behaviour
- `storage.srl` is written to the working directory on shutdown. Delete it to reset to sample data.
- Static counters (`Fad.antalFade`, `Destillering.antalDestilleringer`) are restored from the snapshot on load.
- A `Destillat` is ready for bottling after 3 years, checked by `Destillat.destillatKlar()`.

### Tests
`Test/application/models/ModelsTest.java` has 9 JUnit 5 tests, run through IntelliJ's JUnit runner.
The JavaFX app has no build tool, so these tests are not part of CI.

---

## Stack 2: Spring Boot + Angular

### Requirements
- Java 17+ (Maven is provided by the wrapper, `./mvnw`)
- Node.js 18+ (CI uses 20), npm

### Running the Backend

```bash
cd backend
./mvnw spring-boot:run
```

- API base: `http://localhost:8080/api`
- H2 console: `http://localhost:8080/h2-console` (JDBC URL: `jdbc:h2:mem:sallwhisky`)
- Auth: HTTP Basic. Credentials come from `app.security.admin.*` in `application.properties`, which reads
  the `APP_ADMIN_USERNAME` / `APP_ADMIN_PASSWORD` environment variables and falls back to `admin` / `admin`.
  That fallback is only meant for local development, so set both variables anywhere shared.
  The Angular client sends `admin:admin` from `frontend/src/app/core/interceptors/auth.interceptor.ts`,
  so update that file too if you change the backend credentials.
- Errors: domain rule violations return `application/problem+json` (RFC 9457) bodies:
  `409` for an operation not allowed in the current state (e.g. filling an already-filled barrel,
  tapping a barrel younger than 3 years), `404` for unknown ids, `400` for invalid input.

The database is seeded automatically on startup by `DataInitializer`.

### Running the Frontend

```bash
cd frontend
npm install
npm start
```

Opens at `http://localhost:4200`.

### Tests

```bash
cd backend
./mvnw verify      # unit, @WebMvcTest and @SpringBootTest tests + JaCoCo coverage check
```

- `FadServiceTest`: domain/service unit tests with Mockito
- `FadControllerTest`, `WhiskyControllerTest`: `@WebMvcTest` slices with the real security config, covering
  authentication, validation, and exception → problem-detail mapping
- `BarrelLifecycleIntegrationTest`: `@SpringBootTest` against H2 with the seed data, running
  fill, move, re-barrel and the business-rule rejections end to end

JaCoCo fails the build below 80% line / 60% branch coverage (seed data and the `main` class are excluded).
The HTML report is written to `backend/target/site/jacoco/`.

```bash
cd frontend
npm test           # Karma + Jasmine (watch mode)
npm run test:ci    # single headless run with coverage
```

Karma needs Chrome; on a machine with only Edge, point `CHROME_BIN` at the Edge executable.

GitHub Actions (`.github/workflows/ci.yml`) runs the backend `verify` and the frontend build and tests on pushes to `main` and on every pull request.

---

## API Reference

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/fade` | All barrels |
| `GET` | `/api/fade/tomme` | Empty barrels |
| `GET` | `/api/fade/fyldte` | Filled barrels |
| `GET` | `/api/fade/klar` | Barrels ready for bottling (≥3 years) |
| `POST` | `/api/fade` | Create barrel |
| `DELETE` | `/api/fade/{id}` | Delete empty barrel |
| `POST` | `/api/fade/{id}/destillat` | Fill barrel with distillate |
| `PUT` | `/api/fade/{id}/flyt` | Move barrel to shelf |
| `POST` | `/api/fade/{fra}/omhaeld/{til}` | Re-barrel distillate |
| `GET/POST` | `/api/destilleringer` | List / create distillation run |
| `GET` | `/api/destilleringer/{id}` | One distillation run |
| `GET/POST` | `/api/korn` | List / create grain type |
| `GET/POST` | `/api/lagre` | List / create warehouse |
| `POST` | `/api/lagre/{id}/reoler` | Add racks to warehouse |
| `GET/POST` | `/api/whisky` | List / create whisky product |
| `POST` | `/api/whisky/{id}/tap` | Tap a barrel into product |
| `POST` | `/api/whisky/{id}/vand` | Add water (body: `{ "liter": N }`) |
| `POST` | `/api/whisky/{id}/flasker` | Bottle the product |

---

## Project Structure

```
Sall-Whisky-Destillery-Application/
├── src/                          # JavaFX desktop app
│   ├── application/
│   │   ├── controller/           # Static Controller facade
│   │   └── models/               # Domain models (Serializable)
│   ├── gui/                      # JavaFX panes and dialogs
│   └── storage/                  # ListStorage + Storage interface
├── backend/                      # Spring Boot 3.2
│   └── src/main/java/dk/sallwhisky/
│       ├── api/controller/       # REST controllers
│       ├── api/dto/              # Request/Response records
│       ├── api/error/            # @RestControllerAdvice → problem-detail responses
│       ├── domain/entity/        # JPA entities
│       ├── domain/repository/    # Spring Data repositories
│       ├── domain/service/       # Business logic
│       └── config/               # Security (+ CORS), admin credentials, DataInitializer
├── frontend/                     # Angular 17 SPA
│   └── src/app/
│       ├── core/                 # Auth interceptor, models, HTTP service
│       └── features/             # fade, destillering, lagerstyring, whisky
├── Test/                         # JavaFX JUnit 5 tests
└── diagrams/                     # Mermaid sources and rendered PNGs (see DIAGRAMS.md)
```

---

## Key Design Decisions

- The domain uses Danish names throughout (`Fad`, `Påfyldning`, `Reol`, `Destillering` and so on), matching the real-world domain.
- When a barrel is filled from several distillations, its ABV is the volume-weighted average.
- `WhiskyProdukt.whiskyType()` returns `"Cask Strength"`, `"Single Cask"`, or `"Single Malt"` based on composition.
- Re-barrelling a distillate (`omhæld`) adds a new `ModningsHistorik` entry, so the full ageing history is kept.
- Angular state is all signals (`signal()`, `computed()`), no NgRx.
- Modals are plain Bootstrap 5 from the CDN (`(window as any).bootstrap.Modal`), no ng-bootstrap.
