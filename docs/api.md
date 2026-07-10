# API

| | |
|---|---|
| **Status** | Draft (MVP contract) |
| **Version** | 2.1 |
| **Style** | REST, JSON |
| **Related** | [architecture.md](./architecture.md), [ux.md](./ux.md), [database.md](./database.md) |

## Conventions

- Base path: `/api`
- Auth: корпоративный SSO / OIDC / LDAP (детали стенда — при skeleton)
- Ошибки: стандартный JSON `{ "error": "...", "code": "..." }`
- Время: ISO-8601 UTC
- Workstream Type в ответах — `code` + `display_name` из справочника (не хардкод)

GraphQL **не** используется в MVP.

## Endpoints (MVP)

### Workstream types

```
GET /api/workstream-types
```

Активные типы для UI (Board badges, Release Health rows, Timeline pills).

### Sprints

```
GET /api/sprints/current
GET /api/sprints/{id}/board
```

`board` — issues спринта + workstreams + risk badges + last activity summary.

### Issues

```
GET /api/issues/{key}
GET /api/issues/{key}/timeline
```

`timeline` — упорядоченный список `activity_events` по issue (главный UX).

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
