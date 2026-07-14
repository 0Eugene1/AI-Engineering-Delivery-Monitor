# Backend

Spring Boot modular monolith. **Status:** Skeleton (Phase 1) done; Jira REST Client (Phase 2.1 of [roadmap.md](../docs/roadmap.md)) done. No sync orchestration, persistence, REST API, scheduler, GitLab or Jenkins yet.

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
└── integration/jira/           # Phase 2.1 — Jira REST client (this phase only, see below)
    ├── config/                 # JiraProperties (jira.* in application.yml) + WebClient bean
    ├── auth/                   # Basic / Bearer(PAT) authentication strategies
    ├── client/                 # JiraClient — getMyself(), search(), searchByFilter()
    ├── dto/                    # Jira REST API v2 response DTOs (records)
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
| `JIRA_DEFAULT_FILTER_ID` | `30532` | Board filter id (`docs/discovery.md` §9.1) |

`JiraClient` methods: `getMyself()` (auth smoke test), `search(jql, startAt, maxResults, fields)`,
`searchByFilter(filterId, startAt, maxResults)`. All return `Mono<...>`; non-2xx responses raise
`JiraClientException` (HTTP status + Jira `errorMessages`). Unit tests use a mock HTTP server
(`mockwebserver3`) — no real Jira instance required to run `mvnw verify`.

## Migrations

Liquibase changesets live in `src/main/resources/db/changelog/changes/`, included from `db.changelog-master.yaml`. Add one changeset per logical change; never edit an already-applied changeset.

## Next task

Phase **2.1** (Jira REST Client + auth) is done — stop here until explicitly told to continue.

Дальше по порядку (не начинать без явного go-ahead): **2.2** `POST /api/admin/sync/jira` (ручной sync,
board/filter context + issue search using this client) → **2.3** PostgreSQL persistence → **2.4**
`GET /api/issues` → **2.5** scheduler. См. [roadmap.md](../docs/roadmap.md).
