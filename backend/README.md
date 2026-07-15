# Backend

Spring Boot modular monolith. **Status:** Skeleton (Phase 1) done; Jira REST Client (Phase 2.1 of [roadmap.md](../docs/roadmap.md)) done; Jira board context provider (Task 2.3) done — with a config-switchable offline mock. No sync orchestration, persistence, REST API, scheduler, GitLab or Jenkins yet.

## Stack

- Java 21
- Spring Boot 3.5 (web, actuator, data-jpa, webflux — `WebClient` only, MVC/Servlet stack stays primary)
- PostgreSQL (via JDBC/JPA)
- Liquibase (schema migrations)
- Maven (with wrapper — no local Maven install required)

## Run locally (with Docker Postgres)

From the repo root:

```powershell
cd docker
copy .env.example .env   # first time only
docker compose up --build
```

Then check:

- Health: http://localhost:8080/actuator/health
- Info: http://localhost:8080/actuator/info

## Run backend only (Postgres already running elsewhere)

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

Configure `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`, `SERVER_PORT` via environment variables or `-D` system properties (see `src/main/resources/application.yml` for defaults).

## Tests

```powershell
cd backend
.\mvnw.cmd clean verify
```

The context test uses an embedded H2 database in PostgreSQL-compatibility mode, so it runs without Docker and still exercises the Liquibase wiring.

## Package layout

```text
src/main/java/ru/eltc/deliverymonitor/
├── DeliveryMonitorApplication.java
└── integration/jira/           # Jira REST client + board context provider (see below)
    ├── config/                 # JiraProperties (jira.* in application.yml) + WebClient bean
    ├── auth/                   # Basic / Bearer(PAT) authentication strategies
    ├── client/                 # JiraClient — getMyself(), search(), searchByFilter()
    ├── dto/                    # Jira REST API v2 response DTOs (records)
    ├── provider/               # Task 2.3 — JiraContextProvider (rest/mock, switched by jira.mode)
    └── exception/              # JiraClientException
```

See [docs/architecture.md](../docs/architecture.md) (Backend packages table) for the full planned package layout; packages are added when their phase starts, not ahead of time.

## Jira REST client (Phase 2.1)

HTTP client for Jira Server 8.x (`/rest/api/2`) built on Spring `WebClient`. Scope is deliberately
narrow — see [roadmap.md](../docs/roadmap.md) Phase 2.1/2.2: only the client, config, auth and DTOs.
**Not implemented yet:** sync orchestration (`POST /api/admin/sync/jira`), persistence, read REST API,
scheduler.

Configuration (`jira.*` in `application.yml`, overridable via env — see table below). No secrets are
committed; `JIRA_USERNAME`/`JIRA_TOKEN` are empty by default.

| Env var | Default | Purpose |
|---|---|---|
| `JIRA_BASE_URL` | `https://jira.eltc.ru` | Jira Server base URL |
| `JIRA_AUTH_TYPE` | `bearer` | `bearer` (PAT) or `basic` — see `docs/discovery.md` §1 |
| `JIRA_USERNAME` | *(empty)* | Only used for `basic` |
| `JIRA_TOKEN` | *(empty)* | PAT (`bearer`) or password/PAT (`basic`) |
| `JIRA_CONNECT_TIMEOUT` | `5s` | TCP connect timeout |
| `JIRA_RESPONSE_TIMEOUT` | `10s` | Overall response timeout |
| `JIRA_PROJECT_KEYS` | `MPTPSUPP` | Informational, comma-separated |
| `JIRA_BOARD_ID` | `718` | Observed board id (`docs/discovery.md` §9.1) |
| `JIRA_DEFAULT_FILTER_ID` | `30532` | Board filter id (`docs/discovery.md` §9.1) |
| `JIRA_MODE` | `rest` | Board context source: `rest` (real Jira) or `mock` (offline demo data) |

`JiraClient` methods: `getMyself()` (auth smoke test), `search(jql, startAt, maxResults, fields)`,
`searchByFilter(filterId, startAt, maxResults)`. All return `Mono<...>`; non-2xx responses raise
`JiraClientException` (HTTP status + Jira `errorMessages`). Unit tests use a mock HTTP server
(`mockwebserver3`) — no real Jira instance required to run `mvnw verify`.

## Jira board context provider (Task 2.3)

`integration.jira.provider.JiraContextProvider` is a domain-meaningful seam over `JiraClient`:
`getBoardContext(startAt, maxResults)` returns a `JiraBoardContext` (board/filter ids, paging,
`total`, the issues page, `fetchedAt`, `mocked`). Future sync orchestration (Phase 2.2/2.4) depends
on this interface, **not** on `JiraClient` directly.

Two implementations, selected purely by config (`jira.mode`, default `rest`):

- **`RestJiraContextProvider`** (`jira.mode=rest`) — fetches from the real Jira via `JiraClient.searchByFilter`.
- **`MockJiraContextProvider`** (`jira.mode=mock`) — serves **sanitized demo data** from
  `classpath:jira/mock/board-718-filter-30532.json`, so the app and the layers built on it can be
  developed **without a real Jira service account**.

Run offline with the `jira-mock` profile:

```powershell
cd backend
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=jira-mock"
```

**Mock is never for production:** `MockJiraContextProvider` refuses to start if a `prod`/`production`
profile is active, and the fixture is explicitly marked as demo/sanitized. Switching to real Jira when
a token becomes available is a config change only (`JIRA_TOKEN` + `jira.mode=rest`) — no code change.

## Migrations

Liquibase changesets live in `src/main/resources/db/changelog/changes/`, included from `db.changelog-master.yaml`. Add one changeset per logical change; never edit an already-applied changeset.

## Next task

Phase **2.1** (Jira REST Client + auth) and Task **2.3** (board context provider + config-switchable
mock) are done — stop here until explicitly told to continue.

Дальше по порядку (не начинать без явного go-ahead): **Phase 2.2 «Jira Sync»** — оркестрация
`POST /api/admin/sync/jira` поверх `JiraContextProvider` (task 2.4 «Получение задач») → PostgreSQL
persistence (task 2.5) → `GET /api/issues` → scheduler. См. [roadmap.md](../docs/roadmap.md).
