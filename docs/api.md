# API

| | |
|---|---|
| **Status** | Draft (MVP contract) |
| **Version** | 2.14 |
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

**Не в Phase 3:** `GET /api/releases/.../health`, `POST /hooks/gitlab` можно отложить до после manual sync (ADR-004 preferred webhook — но **после** 3.8, который Done), pipelines API. Activity Feed / Risks — **Phase 4** (ниже).

### Phase 4 — Activity Feed + Risks + Dashboard UI (4.1–4.3 implemented)

> Design: [architecture.md](./architecture.md) § Phase 4, [ux.md](./ux.md), [decisions.md](./decisions.md). Без AI / Kafka / Redis / CQRS / Jenkins / Release Health. Без live Jira/GitLab.

#### `GET /api/activity` — Activity Feed (task 4.1 — **implemented**)

```
GET /api/activity?since={iso}&limit={n}&workstreamType={code}&orphans={true|false}
```

Реализация: `api.activity.ActivityController` → `ActivityQueryService` →
`ActivityEventRepository.findFeed` → shared `api.ActivityEventMapper` (тот же summary/actor/payload,
что Timeline). Конфиг: `activity.feed.default-limit` / `activity.feed.max-limit`.

| Параметр | Default | Meaning |
|---|---|---|
| `since` | (none) | `occurred_at >= since` (ISO-8601 UTC); без параметра — без нижней границы |
| `limit` | `50` (`activity.feed.default-limit`) | Макс. число событий; потолок `activity.feed.max-limit` (`200`) |
| `workstreamType` | (none) | Фильтр по `workstream_type_code` |
| `orphans` | **`true`** | `true` — включать события с `issue_key` null; `false` — только linked |

- Источник: только PostgreSQL `activity_events` — **без новой таблицы**.
- Сортировка: `ORDER BY occurred_at DESC`.
- Пустой результат → `200` + `{ "events": [] }` (не `404`).
- Пакет: `api.activity` → `domain.timeline`. **Без** `domain.activity`.
- Auth: как остальные read (`permitAll` внутри VPN).
- Index: Liquibase `0008` `(workstream_type_code, occurred_at)`.

Response:

```json
{
  "events": [
    {
      "id": "42",
      "occurredAt": "2026-07-20T10:12:00Z",
      "type": "MR_MERGED",
      "source": "GITLAB",
      "issueKey": "MPTPSUPP-43006",
      "workstreamType": { "code": "backend", "displayName": "Backend" },
      "actor": { "id": "j.doe", "name": "John Doe" },
      "summary": "merged MR !88",
      "payload": { "iid": 88 }
    }
  ]
}
```

Отличие от Timeline item: поля `issueKey` (nullable для orphan) и `source` на каждом событии. `summary` — derived at read (как в Timeline, через shared mapper).

#### `GET /api/risks` — Risks (task 4.2 — **implemented**)

```
GET /api/risks?severity={LOW|MEDIUM|HIGH}&code={CODE}&issueKey={key}&limit={n}
```

Реализация: `api.risk.RiskController` → `RiskQueryService` → `domain.risk.RiskService`
(evaluate-on-read). Конфиг: `risk.stale-activity-days` / `risk.open-mr-stale-days` /
`risk.default-limit` / `risk.max-limit`.

| Параметр | Default | Meaning |
|---|---|---|
| `severity` | (none) | Фильтр по severity |
| `code` | (none) | Фильтр по коду правила (`STALE_ACTIVITY`, …) |
| `issueKey` | (none) | Риски одной issue |
| `limit` | `100` (`risk.default-limit`) | Потолок списка; clamp `risk.max-limit` (`200`) |

**Не** используется `sprintId` — таблицы `sprints` нет ([database.md](./database.md)).

- Вычисление: **evaluate-on-read** — **без** `risk_flags` / JPA risk entity.
- **Scope:** workstream-правила итерируют **`workstreams`** (не полный каталог issues).
- Пороги (locked): `STALE_ACTIVITY` = **3 days** (`risk.stale-activity-days`), `OPEN_MR_STALE` = **5 days** (`risk.open-mr-stale-days`).
- Правила: `STALE_ACTIVITY`, `OPEN_MR_STALE`, `NO_MR` (workstream без MR), `JIRA_ACTIVE_NO_GIT` (active Jira `status_category` name = `In Progress` без Git).
- Empty → `200` + `{ "risks": [] }`.
- Пакет: `api.risk` → `domain.risk`.
- Auth: как остальные read (`permitAll` внутри VPN).

Response:

```json
{
  "risks": [
    {
      "code": "OPEN_MR_STALE",
      "severity": "MEDIUM",
      "issueKey": "MPTPSUPP-43006",
      "workstreamType": { "code": "backend", "displayName": "Backend" },
      "explanation": "MR !88 opened for 7 days without merge",
      "detectedAt": "2026-07-20T12:00:00Z",
      "evidence": {
        "mergeRequestIid": 88,
        "openedAt": "2026-07-13T09:00:00Z"
      }
    }
  ]
}
```

Коды Phase 4: `STALE_ACTIVITY`, `OPEN_MR_STALE`, `NO_MR`, `JIRA_ACTIVE_NO_GIT`. Severity enum: `LOW` | `MEDIUM` | `HIGH`.

#### `GET /api/workstreams/progress` — Projects bars (task 4.3 — **implemented**)

```
GET /api/workstreams/progress
```

Агрегат для Dashboard «Projects»: доля `merged` workstreams по каждому **active** Workstream Type.

Реализация: `api.workstream.WorkstreamProgressController` → `WorkstreamProgressQueryService`
→ `WorkstreamRepository.countTotalsAndMergedByType` + `workstream_types` (active, `sort_order`).

- Formula: `percent = total == 0 ? 0 : round(100 * merged / total)`.
- Не Release Health (нет `fixVersion`) — глобальный срез по всем workstreams.
- Empty types still returned with `total=0`, `percent=0`.
- Auth: read `permitAll`.

Response:

```json
{
  "items": [
    {
      "workstreamType": { "code": "backend", "displayName": "Backend" },
      "total": 40,
      "merged": 32,
      "percent": 80
    }
  ]
}
```

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
GET /api/activity?since={iso}&limit={n}&workstreamType={code}&orphans={true|false}
```

Командная лента (как GitHub). **Phase 4.1 реализовано** — контракт выше (§ Phase 4). Источник = `activity_events` (ADR-008), не отдельная таблица.

### Release health

```
GET /api/releases/{fixVersion}/health
```

Процент готовности **по каждому активному Workstream Type** + drill-down ids/keys.

Формула MVP: для каждого Workstream Type — доля его workstreams в статусе `merged` / `build_ok` / `done` среди issues с данным `fixVersion`.

### Risks

```
GET /api/risks?severity={…}&code={…}&issueKey={…}&limit={n}
```

Список вычисленных рисков (evaluate-on-read). **Phase 4.2 реализовано** — контракт выше (§ Phase 4).  
`?sprintId=` из раннего эскиза **снят** (нет `sprints` persistence). Persistence `risk_flags` — не в Phase 4.

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
