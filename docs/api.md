# API

| | |
|---|---|
| **Status** | Draft (MVP contract) |
| **Version** | 2.9 |
| **Style** | REST, JSON |
| **Related** | [architecture.md](./architecture.md), [ux.md](./ux.md), [database.md](./database.md), [security.md](./security.md), [ADR-012](./adr/0012-minimal-auth-baseline-admin-endpoints.md) |

## Conventions

- Base path: `/api`
- Auth (implemented, Phase 2.4, [ADR-012](./adr/0012-minimal-auth-baseline-admin-endpoints.md)): `/api/admin/**` требуют Bearer admin-токен из env (`DELIVERY_MONITOR_ADMIN_TOKEN`, **отдельный** от `JIRA_TOKEN`); read-эндпоинты (`GET /api/**`) пока открыты внутри VPN. Корпоративный SSO / OIDC / LDAP — **целевая** модель для пользователей, вводится с UI (детали стенда — при развёртывании). См. [security.md](./security.md) §2.
- Ошибки: стандартный JSON `{ "error": "...", "code": "..." }`
- Время: ISO-8601 UTC
- Workstream Type в ответах — `code` + `display_name` из справочника (не хардкод)

GraphQL **не** используется в MVP.

## Endpoints (MVP)

### Workstream types (Phase 3.7 — implemented)

```
GET /api/workstream-types
```

Активные типы для UI (Board badges, Release Health rows, Timeline pills). Реализация:
`api.workstream.WorkstreamTypeController` → `WorkstreamTypeQueryService` →
`domain.workstream_type.WorkstreamTypeRepository.findAllByActiveTrueOrderBySortOrderAsc()`.
Response — массив `WorkstreamTypeResponse` (`code`, `displayName`, `sortOrder`).

### Sprints

```
GET /api/sprints/current
GET /api/sprints/{id}/board
```

`board` — issues спринта + workstreams + risk badges + last activity summary.

**Не реализовано** (архитектурное решение перед Read API, 2026-07-15): в текущей persistence-модели нет таблицы `sprints` ([database.md](./database.md) — Planned/future). Запрещено: создавать таблицу `sprints`, делать mock/stub response, возвращать фиктивные данные, получать sprint напрямую из Jira для этого endpoint. TODO: реализовать после появления sprint persistence — см. [discovery.md](./discovery.md).

### Issues (Read API — implemented)

```
GET /api/issues
GET /api/issues/{key}
GET /api/issues/{key}/timeline
```

`GET /api/issues` — список issues **из PostgreSQL** (после sync), не live-запрос в Jira. Реализация:
`api.issue.IssueController` — тонкий HTTP-адаптер, без бизнес-логики; читает через `api.issue.IssueQueryService`
(read-only, `@Transactional(readOnly = true)`) поверх `domain.issue.IssueRepository`. Зависимость строго
`PostgreSQL → domain.issue → api.issue`: не использует `sync.jira`/`integration.jira`/`JiraClient`, никаких
live-запросов в Jira.

`GET /api/issues/{key}` — одна issue по публичному Jira `key` (не `jiraId`, не database id). Если issue с таким
`key` не персистирована — `404` с телом `{ "error": "Issue not found: ...", "code": "ISSUE_NOT_FOUND" }`
(формат из "Conventions" выше).

Response — отдельный DTO `IssueResponse` (не JPA entity):

```json
{
  "issueKey": "MPTPSUPP-1234",
  "summary": "Fix the thing",
  "status": "In Review",
  "statusCategory": "In Progress",
  "assigneeUsername": "j.doe",
  "assigneeDisplayName": "John Doe",
  "issueType": "Bug",
  "jiraCreated": "2026-07-01T09:00:00Z",
  "jiraUpdated": "2026-07-10T12:30:00Z",
  "fixVersions": ["5.7.27"],
  "labels": ["backend", "urgent"]
}
```

`GET /api/issues` возвращает JSON-массив таких объектов, без pagination/sorting/filtering/search.

`GET /api/issues/{key}/timeline` — **Phase 3.7 реализовано**: упорядоченный список `activity_events` по issue
(главный UX). Реализация: `api.issue.TimelineController` → `TimelineQueryService` →
`ActivityEventRepository.findAllByIssueKeyOrderByOccurredAtDesc`. Только PostgreSQL; без live Jira/GitLab;
**не** требует `IssueEntity`. Пустой/неизвестный key → `200` + `{ "issueKey", "events": [] }`.

### Phase 3 — read / admin endpoints (3.7–3.9 implemented; next Phase 4)

| Endpoint | Назначение | Статус |
|---|---|---|
| `GET /api/workstream-types` | Активные типы для UI pills | **Done** (3.7) |
| `GET /api/issues/{key}/timeline` | Issue Timeline (hero) | **Done** (3.7) |
| `GET /api/issues/{key}` *(расширение)* | Опционально: вложенные `workstreams[]` + derived status | Не в 3.7 (endpoint не меняли) |
| `POST /api/admin/sync/gitlab` | Ручной GitLab sync (зеркало Jira) | **Done** (3.8) |

#### `POST /api/admin/sync/gitlab` — contract (реализован в Phase 3.8)

Зеркало `POST /api/admin/sync/jira`: `api.admin.GitLabSyncController` → `GitLabSyncService#syncAll()`, ответ — существующий `GitLabSyncResult` as-is (без отдельного DTO). Request body нет. Защита — тот же Bearer `DELIVERY_MONITOR_ADMIN_TOKEN` / `api.security` (ADR-012); без новой auth-логики. Mock e2e + **Live E2E 2026-07-20** (rest+rest): после sync `GET /api/issues/{key}/timeline` отдаёт GitLab-события из PostgreSQL.

#### `GET /api/issues/{key}/timeline` — contract (реализован в Phase 3.7)

- Источник: только PostgreSQL `activity_events WHERE issue_key = ?`.
- **Сортировка:** `ORDER BY occurred_at DESC` — самое новое сверху.
- **Пустой результат:** `200 OK`, не `404`. Нет событий (и/или нет строки в `issues`) — валидный ответ с пустым списком.
- **Не** требует существования `IssueEntity` (в отличие от `GET /api/issues/{key}`).
- Response DTO: `TimelineResponse` (`issueKey`, `events[]` с `id`/`occurredAt`/`type`/`workstreamType`/`actor`/`summary`/`payload`); `summary` derived at read из type + payload.

Пример пустого ответа:

```json
{
  "issueKey": "UNKNOWN",
  "events": []
}
```

**Не в Phase 3:** `GET /api/activity` (Feed — Phase 4), `GET /api/releases/.../health`, `GET /api/risks`, `POST /hooks/gitlab` можно отложить до после manual sync (ADR-004 preferred webhook — но **после** 3.8, который Done), pipelines API.

### Admin sync (Phase 2.4 — implemented, до scheduler)

```
POST /api/admin/sync/jira
```

**Ручной** запуск синхронизации Jira → PostgreSQL. Scheduler **не** используется до Phase 2.5. Реализация:
`api.admin.JiraSyncController` (пакет `api.admin`) — тонкий HTTP-адаптер, без бизнес-логики; вызывает
`sync.jira.JiraSyncService#syncBoard()` и возвращает его результат as-is.

Request body: **нет** (тело запроса не принимается; фильтр/страница берутся из конфига —
`jira.default-filter-id`, `jira.sync.page-size`, см. [discovery.md](./discovery.md) §9.1).

Response — существующий контракт application layer, `sync.jira.JiraSyncResult`, без отдельного
response DTO (реюз, а не параллельная модель):

```json
{
  "startedAt": "2026-07-14T10:00:00Z",
  "finishedAt": "2026-07-14T10:00:03Z",
  "fetched": 142,
  "pages": 3,
  "mocked": false,
  "created": 120,
  "updated": 22,
  "errors": []
}
```

`saved` (`created + updated`) — derived-метод модели, **не** сериализуется в JSON (не record-компонент);
считать суммой `created`+`updated` на стороне клиента при необходимости.

Защита: **admin-only** через Bearer admin-токен из env (`DELIVERY_MONITOR_ADMIN_TOKEN`, отдельный от `JIRA_TOKEN` — разные границы доверия), реализовано в `api.security` (`SecurityConfig` + `AdminTokenAuthenticationFilter` + `AdminTokenProperties`) по [ADR-012](./adr/0012-minimal-auth-baseline-admin-endpoints.md) / [security.md](./security.md) §2, §5. Проверка **stateless**: подтверждает только «запрос предъявил валидный admin-токен», identity пользователя из токена **не** извлекается ([security.md](./security.md) §2, §7). Отсутствие/неверный токен → `401 Unauthorized`, до контроллера запрос не доходит.

**Порядок внедрения:** этот endpoint работает end-to-end (реализован), затем `GET /api/issues`, затем `@Scheduled` polling ([roadmap.md](./roadmap.md) Phase 2.5).

### Activity feed

```
GET /api/activity?since={iso}&limit={n}
```

Командная лента (как GitHub).

### Release health

```
GET /api/releases/{fixVersion}/health
```

Процент готовности **по каждому активному Workstream Type** + drill-down ids/keys.

Формула MVP: для каждого Workstream Type — доля его workstreams в статусе `merged` / `build_ok` / `done` среди issues с данным `fixVersion`.

### Risks

```
GET /api/risks?sprintId={id}
```

Список открытых `risk_flags` (также можно встраивать в board response).

### Webhooks (ingest)

```
POST /hooks/gitlab
POST /hooks/jenkins
```

Пишут события сразу в БД. Не часть публичного UI API; защита secret/token.

## AI Summary (after MVP, separate service)

Не часть Monitor monolith:

```
POST {ai-summary}/summarize
Body: { "issueKey" | "sprintId" | "fixVersion" }
```

AI-сервис **сам** читает Monitor REST (`/timeline`, `/board`, `/health`) и возвращает markdown.

## Response sketches

### Timeline item

```json
{
  "id": "…",
  "occurredAt": "2026-07-09T09:15:00Z",
  "type": "BRANCH_CREATED",
  "workstreamType": { "code": "backend", "displayName": "Backend" },
  "actor": { "id": "…", "name": "…" },
  "summary": "created branch feature/MPTPSUPP-1234",
  "payload": {}
}
```

### Release health

```json
{
  "fixVersion": "5.7.27",
  "byType": [
    { "workstreamType": { "code": "backend", "displayName": "Backend" }, "percent": 90 },
    { "workstreamType": { "code": "frontend", "displayName": "Frontend" }, "percent": 70 },
    { "workstreamType": { "code": "oracle", "displayName": "Oracle" }, "percent": 100 },
    { "workstreamType": { "code": "qa", "displayName": "QA" }, "percent": 20 }
  ]
}
```

Строки `byType` = активные записи `workstream_types`, не фиксированный enum в коде.

## Change policy

Изменение контракта → обновить **этот файл**, [ux.md](./ux.md) и при необходимости OpenAPI spec (когда появится в репозитории).
