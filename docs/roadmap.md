# Roadmap

| | |
|---|---|
| **Status** | Accepted |
| **Version** | 2.3 |
| **Related** | [vision.md](./vision.md), [architecture.md](./architecture.md), [ux.md](./ux.md), [discovery.md](./discovery.md) |

## Guiding rule

Каждый этап даёт **проверяемый результат**, а не «инфраструктуру ради инфраструктуры».

**Первая ценность — этап 3** (GitLab + Issue Timeline).

**Правило Jira (Phase 2):** сначала **ручной sync** (`POST /api/admin/sync/jira`), потом scheduler. Не начинать с фонового polling.

---

## Фактический статус фаз (по коду, на 2026-07-15)

> Таблицы плана ниже — исходная разбивка. Ниже — что **реально реализовано** в коде (см. [ai_context.md](./ai_context.md) §2, [session_log.md](./session_log.md)).

| Фаза | Статус |
|---|---|
| 0. Discovery | Done |
| 1. Skeleton (Spring Boot + PostgreSQL + Liquibase + Actuator) | Done |
| 2.1 Jira Client (REST-клиент + auth) | Done |
| 2.2 Jira Sync — application layer (`sync.jira`: `JiraSyncService`/`JiraSyncResult`/`JiraIssueSnapshot`) | Done |
| 2.3 Persistence (`domain.issue`, Liquibase `issues`/`issue_fix_versions`/`issue_labels`; `sprints`/`sync_state` отложены) | Done |
| Admin Sync HTTP API — `POST /api/admin/sync/jira` + минимальный security-baseline (`api.admin`/`api.security`, Bearer admin-token, [ADR-012](./adr/0012-minimal-auth-baseline-admin-endpoints.md)) | Done — помечено «Phase 2.4» в [ai_context.md](./ai_context.md)/[session_log.md](./session_log.md) |
| Read API (`GET /api/issues`, `GET /api/sprints/current`) | **Next** |
| Scheduler (`@Scheduled` polling) | Планируется после read API |
| Phase 3+ (GitLab / Jenkins / Timeline / Release Health / AI Summary) | Не начаты |

**Замечание по нумерации:** в плановых таблицах ниже endpoint `POST /api/admin/sync/jira` отнесён к Phase 2.2, а «REST API» (read) — к Phase 2.4. Фактически admin-sync endpoint был выделен в **отдельный** шаг и во всей остальной документации помечен как **Phase 2.4**; read API и scheduler, соответственно, сдвинулись на Phase 2.5+.

---

## Phases (overview)

| Phase | Duration | Deliverable | Done when |
|---|---|---|---|
| **0. Discovery** | 3–5 days | Seed Workstream Types, repo→type map, Jenkins jobs, naming, service accounts | Документ маппингов + доступы (критичные) |
| **1. Skeleton** | 3–5 days | Spring Boot + PostgreSQL + Actuator | `docker compose up`, `/actuator/health` OK |
| **2.1 Jira Client** | 1–2 days | REST-клиент + auth к Jira | Smoke: `/rest/api/2/myself`, JQL search |
| **2.2 Jira Sync** | 1–2 days | Оркестрация выгрузки (board/filter) | `POST /api/admin/sync/jira` → данные получены |
| **2.3 Persistence** | 2–3 days | Liquibase + сохранение в PostgreSQL | После sync данные в БД |
| **2.4 REST API** | 1–2 days | Read API для UI | `GET /api/issues`, `GET /api/sprints/current` |
| **2.5 Jira Scheduler** *(после 2.4)* | 1 day | Polling 2–5 min | Фоновый sync без ручного POST |
| **3. GitLab + Timeline** | 1.5–2 weeks | Workstreams + Issue Timeline | ≥90% задач с naming → события в Timeline |
| **4. Activity Feed + Risks** | 3–5 days | Лента + risk badges | Стендап по Risks/Feed |
| **5. Jenkins / CI + Release Health** | ~1 week | Builds + % по Workstream Type | Failed build → risk |
| **6. Pilot** | 2 sprints | Shadow mode рядом с Jira | Нет обязательного ручного ввода |
| **7. AI Summary** | after MVP | Отдельный сервис | Summary только из REST Monitor |

```text
Phase 1 Skeleton
       ↓
Phase 2.1 Jira Client      ← только HTTP-клиент + credentials
       ↓
Phase 2.2 Jira Sync        ← POST /api/admin/sync/jira (ручной!)
       ↓
Phase 2.3 Persistence      ← PostgreSQL
       ↓
Phase 2.4 REST API         ← GET /api/issues
       ↓
Phase 2.5 Scheduler        ← polling (после того как ручной sync стабилен)
       ↓
Phase 3 GitLab + Timeline
```

---

## Phase 2 — детализация (контролируемые задачи)

> Конфиг Jira: [discovery.md](./discovery.md) §9.1 — board **718** (Kanban), filter **30532**, project **MPTPSUPP**.

| Task | Scope | **Не делать** на этом шаге | Done when |
|---|---|---|---|
| **2.1 Jira REST Client** | HTTP-клиент Jira Server 8.x (`RestTemplate` / `WebClient`), DTO ответов, обработка ошибок | Auth wiring, БД, REST API, scheduler | Unit/integration test: mock Jira отвечает |
| **2.2 Auth** | Конфиг credentials (env), basic auth / PAT; smoke `GET /rest/api/2/myself` | Sync, persistence, публичный API | Реальный Jira token из env → `/myself` 200 |
| **2.3 Получение контекста board** | Board 718 / filter 30532: активные sprints **если есть**, board configuration | Сохранение в БД, GET /issues | JQL filter 30532 выполняется, issues возвращаются |
| **2.4 Получение задач** | Search по JQL filter 30532; поля: key, summary, status, assignee, fixVersion | PostgreSQL, scheduler | Список issues совпадает с filter view в Jira |
| **2.5 Persistence** | Liquibase: `sprints`, `issues`, `sync_state`; upsert после sync | Scheduler, UI | После `POST …/sync/jira` строки в БД |
| **2.6 REST API** | `GET /api/issues`, `GET /api/sprints/current` (из БД, не live Jira) | Scheduler, Board UI | curl → JSON из PostgreSQL |
| **2.7 Scheduler** *(отложено)* | `@Scheduled` polling 2–5 min | — | Только после стабильного ручного sync |

### Маппинг Task → Phase

| Phase | Включает tasks |
|---|---|
| **2.1 Jira Client** | 2.1 + 2.2 |
| **2.2 Jira Sync** | 2.3 + 2.4 + endpoint `POST /api/admin/sync/jira` |
| **2.3 Persistence** | 2.5 |
| **2.4 REST API** | 2.6 |
| **2.5 Scheduler** | 2.7 |

### Принцип: manual sync first

```text
POST /api/admin/sync/jira
  → клиент ходит в Jira
  → получает issues (filter 30532)
  → сохраняет в PostgreSQL
  → возвращает { fetched, saved, errors }

GET /api/issues
  → читает только из PostgreSQL
```

Scheduler добавляется **только когда** ручной sync проверен на стенде с реальным token.

Контракт admin endpoint: [api.md](./api.md).

---

## MVP scope (screens)

In MVP:

- Sprint Board
- Issue Detail + Timeline
- Activity Feed
- Release Health
- Risks as filter/tab on Board (P1)

Out of MVP:

- People / WIP
- Notifications
- AI Summary service
- GraphQL, queues, Redis, CQRS

## Current team seed (config, not code)

Пример Workstream Types для текущей команды (данные):

- `backend`
- `frontend`
- `oracle`
- `qa`

Любое изменение набора типов — конфиг/`workstream_types`, не релиз доменной модели. См. [database.md](./database.md).

## Tracking changes

При сдвиге приоритетов или scope обновляйте этот файл и при необходимости [vision.md](./vision.md) / [ux.md](./ux.md) / [api.md](./api.md).

**v2.2 (2026-07-14):** Phase 2 разбита на 2.1–2.5; manual sync перед scheduler.

**v2.3 (2026-07-15):** добавлен блок «Фактический статус фаз» (2.1–2.4 реализованы); зафиксирована разница нумерации — admin-sync endpoint фактически помечен как Phase 2.4, read API/scheduler сдвинуты на 2.5+.
