# Backend

Spring Boot modular monolith. **Status:** Phase 1 Skeleton + Phase 2.1–2.5 (Jira full path) + Phase **3.1–3.8** (GitLab client → sync → config/git entities → activity_events → workstreams → Read API → Admin sync HTTP) done. **Milestone:** mock e2e Jira+GitLab → Timeline. **Next:** Phase **3.9** reconcile scheduler (design approved). Not yet: Jenkins, frontend.

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

The context test uses an embedded H2 database in PostgreSQL-compatibility mode, so it runs without Docker and still exercises the Liquibase wiring. Current baseline: **194** tests, 0 failures, 2 skipped (`JiraSmokeTest` without token).

## Smoke checklist (после крупного этапа)

Быстрый интеграционный прогон (~пара минут) на реальном Postgres + запущенном backend.
Удобно с профилями `jira-mock` (и при необходимости `gitlab-mock`). Нужен `DELIVERY_MONITOR_ADMIN_TOKEN`.

| # | Check | Ожидание |
|---|---|---|
| 1 | Приложение стартует | нет stacktrace / `Started DeliveryMonitorApplication` |
| 2 | Liquibase применяет миграции | в логе успешный changelog; таблицы на месте |
| 3 | `GET /actuator/health` | `{"status":"UP"}` |
| 4 | `GET /api/workstream-types` | seed: `backend`, `frontend`, `oracle`, `qa` |
| 5 | `POST /api/admin/sync/jira` + `Authorization: Bearer <token>` | `errors` пустой; `fetched`/`created`/`updated` > 0 (mock: обычно 5) |
| 6 | Таблица `issues` (или `GET /api/issues`) | появились записи (mock: `MPTPSUPP-90001`…`90005`) |
| 7 | `POST /api/admin/sync/gitlab` + Bearer (профиль `gitlab-mock`) | `mocked=true`; `errors` пустой; branches/commits/MRs → БД |
| 8 | `GET /api/issues/MPTPSUPP-90001/timeline` | events не пустые: `BRANCH_CREATED` / `COMMIT` / `MR_*` |
| 9 | `POST /api/admin/sync/jira` **без** Bearer | `401` |

PowerShell (после старта с `jira-mock,gitlab-mock`):

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
Invoke-RestMethod http://localhost:8080/api/workstream-types
try { Invoke-WebRequest -Method POST http://localhost:8080/api/admin/sync/jira -UseBasicParsing } catch { $_.Exception.Response.StatusCode.value__ }  # → 401
Invoke-RestMethod -Method POST http://localhost:8080/api/admin/sync/jira -Headers @{ Authorization = "Bearer $env:DELIVERY_MONITOR_ADMIN_TOKEN" }
Invoke-RestMethod http://localhost:8080/api/issues
Invoke-RestMethod -Method POST http://localhost:8080/api/admin/sync/gitlab -Headers @{ Authorization = "Bearer $env:DELIVERY_MONITOR_ADMIN_TOKEN" }
Invoke-RestMethod http://localhost:8080/api/issues/MPTPSUPP-90001/timeline
```

## Package layout

```text
src/main/java/ru/eltc/deliverymonitor/
├── DeliveryMonitorApplication.java
├── integration/
│   ├── jira/                   # Phase 2.1 — Jira REST client + board context provider
│   └── gitlab/                 # Phase 3.1 — GitLab REST client (rest/mock)
├── sync/
│   ├── jira/                   # Phase 2.2 + 2.5 — Jira sync + scheduler
│   └── gitlab/                 # Phase 3.2+ — GitLab sync (entities, events, workstreams)
├── domain/
│   ├── issue/                  # Phase 2.3 — issues persistence
│   ├── workstream_type/        # Phase 3.3 — workstream_types seed
│   ├── repository/             # Phase 3.3 — observed GitLab repos (SoT)
│   ├── gitlab/                 # Phase 3.4 — branches / commits / merge_requests
│   ├── timeline/               # Phase 3.5 — IssueKeyExtractor + activity_events
│   └── workstream/             # Phase 3.6 — Workstream = Issue × Type
└── api/
    ├── admin/                  # Phase 2.4 + 3.8 — POST /api/admin/sync/jira|gitlab
    ├── security/               # Phase 2.4 — Bearer admin-token baseline
    ├── issue/                  # Read API — GET /api/issues, GET /api/issues/{key},
    │                           #            GET /api/issues/{key}/timeline (Phase 3.7)
    └── workstream/             # Phase 3.7 — GET /api/workstream-types
```

See [docs/architecture.md](../docs/architecture.md) (Backend packages table) for the full planned package layout.

## Jira REST client (Phase 2.1)

HTTP client for Jira Server 8.x (`/rest/api/2`) built on Spring `WebClient`. Scope is deliberately
narrow — see [roadmap.md](../docs/roadmap.md) Phase 2.1.

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
| `JIRA_SYNC_PAGE_SIZE` | `50` | Page size for `JiraSyncService` pagination |
| `JIRA_SYNC_ENABLED` | `false` | Enable background Jira scheduler |
| `JIRA_SYNC_INTERVAL` | `5m` | Fixed-delay between scheduled sync runs |

`JiraClient` methods: `getMyself()` (auth smoke test), `search(jql, startAt, maxResults, fields)`,
`searchByFilter(filterId, startAt, maxResults)`. All return `Mono<...>`; non-2xx responses raise
`JiraClientException` (HTTP status + Jira `errorMessages`). Unit tests use a mock HTTP server
(`mockwebserver3`) — no real Jira instance required to run `mvnw verify`.

## Jira board context provider (Task 2.3)

`integration.jira.provider.JiraContextProvider` is a domain-meaningful seam over `JiraClient`:
`getBoardContext(startAt, maxResults)` returns a `JiraBoardContext` (board/filter ids, paging,
`total`, the issues page, `fetchedAt`, `mocked`). Sync orchestration depends on this interface,
**not** on `JiraClient` directly.

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

## Jira Sync (Phase 2.2) + Scheduler (Phase 2.5)

`sync.jira.JiraSyncService.syncBoard()` orchestrates page-by-page fetch via `JiraContextProvider`,
normalizes each wire `JiraIssueDto` into `JiraIssueSnapshot`, upserts via `IssuePersistencePort`,
and returns `JiraSyncResult` (`startedAt`/`finishedAt`/`fetched`/`pages`/`created`/`updated`/`mocked`/`errors`).

`JiraClientException` is caught and written to `errors` — the run returns whatever was fetched before
the failure. Database errors are **not** masked and propagate up.

**HTTP:** `POST /api/admin/sync/jira` (`api.admin.JiraSyncController`).  
**Scheduler:** `JiraSyncScheduler` (`SchedulingConfigurer`, `fixedDelay`) — same `syncBoard()`; gated by `jira.sync.enabled`.

## Persistence (Phase 2.3)

`domain.issue` owns all persistence contracts. `IssueUpsertService` upserts issues page-by-page
(matching by `jiraId`, not `key`). `JiraSyncService` calls `IssuePersistencePort.upsertPage(...)` on
each page instead of accumulating the full list in memory.

Liquibase `0002-issues.yaml` creates `issues`, `issue_fix_versions`, `issue_labels`.
`sprints` and `sync_state` are deferred — see [docs/database.md](../docs/database.md).

## Admin Sync HTTP API (Phase 2.4 + 3.8)

`api.admin.JiraSyncController` exposes `POST /api/admin/sync/jira` — a thin HTTP adapter over
`JiraSyncService.syncBoard()` that returns the `JiraSyncResult` as-is (no separate response DTO).
There is no request body; the board filter and page size come from config (`jira.default-filter-id`,
`jira.sync.page-size`).

`api.admin.GitLabSyncController` (**Phase 3.8**) exposes `POST /api/admin/sync/gitlab` — the same
pattern over `GitLabSyncService.syncAll()`, returning `GitLabSyncResult` as-is.

Access is gated by a minimal Spring Security baseline (`api.security`, [ADR-012](../docs/adr/0012-minimal-auth-baseline-admin-endpoints.md)):
`/api/admin/**` and every non-`health` actuator endpoint require an `Authorization: Bearer <token>`
header matching `DELIVERY_MONITOR_ADMIN_TOKEN`; `/actuator/health` stays public. The check is
**stateless** — no user identity is derived, no `User`/`Role` storage. A missing/invalid token →
`401`. No JWT / OAuth2 Resource Server / OIDC / LDAP.

| Env var | Default | Purpose |
|---|---|---|
| `DELIVERY_MONITOR_ADMIN_TOKEN` | *(empty)* | Bearer token gating `/api/admin/**`; a **separate** secret from `JIRA_TOKEN` / `GITLAB_TOKEN`, fail-fast if unset |

## GitLab (Phase 3.1–3.8)

| Env var | Default | Purpose |
|---|---|---|
| `GITLAB_BASE_URL` | *(see application.yml)* | GitLab API base URL |
| `GITLAB_TOKEN` | *(empty)* | `PRIVATE-TOKEN` header |
| `GITLAB_MODE` | `rest` | `rest` or `mock` (offline fixtures) |
| `GITLAB_SYNC_PAGE_SIZE` | `50` | Pagination for branches/commits/MRs |
| `GITLAB_SYNC_COMMIT_HISTORY_DAYS` | `30` | Commit fetch window (`since`) |

- **3.1** `integration.gitlab` — `GitLabClient` / `RestGitLabClient` / `MockGitLabClient`.
- **3.2–3.6** `sync.gitlab.GitLabSyncService` — sync observed repos from PostgreSQL (`RepositoryPersistencePort`); upserts branches/commits/MRs; stamps `issue_key`; writes `activity_events`; upserts workstreams. Yaml `gitlab.sync.repositories` — mock/local/tests only.
- Liquibase: `0003-workstream-types`, `0004-repositories`, `0005-git-entities`, `0006-activity-events`, `0007-workstreams`.

Offline GitLab:

```powershell
cd backend
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=gitlab-mock"
```

**Not yet:** GitLab scheduler (3.9).

## Read API (issues + timeline + workstream types)

- `api.issue.IssueController`: `GET /api/issues`, `GET /api/issues/{key}` — PostgreSQL via `domain.issue`. `GET /api/sprints/current` deferred (no `sprints` table).
- `api.issue.TimelineController`: `GET /api/issues/{key}/timeline` — PostgreSQL `activity_events` only (`occurred_at DESC`); empty/unknown key → `200` + `events: []` (no `IssueEntity` required).
- `api.workstream.WorkstreamTypeController`: `GET /api/workstream-types` — active seeded types.

## Migrations

Liquibase changesets live in `src/main/resources/db/changelog/changes/`, included from `db.changelog-master.yaml`. Add one changeset per logical change; never edit an already-applied changeset.

Current: `0001` baseline → `0002` issues → `0003` workstream_types → `0004` repositories → `0005` git entities → `0006` activity_events → `0007` workstreams.

## Next task

Phase **3.1–3.8** done. Stop here until explicitly told to continue.

Дальше по порядку (не начинать без явного go-ahead): Phase **3.9** —
GitLab reconcile scheduler (`sync.gitlab`, fixedDelay; webhooks optional later).
См. [roadmap.md](../docs/roadmap.md).
