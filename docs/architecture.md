# Architecture

| | |
|---|---|
| **Status** | Accepted |
| **Version** | 2.10 |
| **Related** | [vision.md](./vision.md), [database.md](./database.md), [integrations.md](./integrations.md), [decisions.md](./decisions.md), [security.md](./security.md) |

## Overview

AI Engineering Delivery Monitor — **modular monolith**:

```
Jira / GitLab / Jenkins
        │
        ▼
   Spring Boot
   (scheduler + webhooks + domain + REST)
        │
        ▼
   PostgreSQL
        │
        ▼
   React Dashboard
        │
        └──► AI Summary (отдельный сервис, после MVP)
```

Единый shareable-документ (эквивалент Canvas): [architecture-overview.md](./architecture-overview.md).  
Cursor canvas `architecture-design.canvas.tsx` — только локальный обзор в IDE.

## Stack (MVP)

| Layer | Choice |
|---|---|
| Backend | Spring Boot (один deployable) |
| Database | PostgreSQL |
| Ingest | `@Scheduled` pollers + optional webhooks → сразу в БД |
| API | REST |
| Frontend | React |
| AI | Отдельный сервис поверх REST (не в monolith) |

## Data flow

1. Scheduler тянет Jira / GitLab / Jenkins (и/или принимает webhooks).
2. Webhook payload пишется сразу в БД (нормализация в том же запросе/транзакции или сразу после).
3. Сервис линкует сущности по **issue key** и назначает **Workstream Type** через конфиг репозиториев/jobs.
4. REST отдаёт данные экранам.
5. AI Summary (позже) читает только REST Monitor, не БД.

## Package dependency direction

Слои зависят **только вниз**, каждый владеет собственными контрактами; обратной зависимости нет:

```
integration.jira  →  sync.jira  →  domain.issue
```

`sync.jira` зависит от `domain.issue` (вызывает `IssuePersistencePort`), но `domain.issue` ничего не импортирует из `sync.jira` — свой входной контракт (`IssueUpsertCommand`) он определяет сам; маппинг из `JiraIssueSnapshot` делает вызывающая сторона (`sync.jira`). Тот же принцип действует на границе `integration.jira → sync.jira` (`JiraContextProvider` — контракт integration-слоя, `sync.jira` от него зависит). См. [decisions.md](./decisions.md) (Design notes, 2026-07-15 — Phase 2.3 Persistence).

`api.admin` зависит на `sync.jira` (вызывает `JiraSyncService.syncBoard()` и реюзает `JiraSyncResult` как HTTP response), но ничего не знает про `domain.issue`/`integration.jira` напрямую. `api.security` не зависит ни от одного бизнес-пакета — это чистый HTTP-уровень (Servlet filter + Spring Security config), не знающий про Jira/issues.

`sync.jira.JiraSyncScheduler` (Phase 2.5) — второй, полностью симметричный вызывающий той же точки входа `JiraSyncService.syncBoard()`; живёт **внутри** `sync.jira` (не в `api.admin`, не в `integration.jira`), не вводит новой зависимости и не обходит `JiraSyncService`. Ни `api.admin.JiraSyncController`, ни `sync.jira.JiraSyncScheduler` не зависят друг от друга — оба зависят только на `JiraSyncService`.

`api.issue` (Read API) зависит **только** на `domain.issue` (`IssueEntity`/`IssueRepository`) — не на `sync.jira`, не на `integration.jira`, не на `JiraClient`. Это отдельная от `api.admin` ветвь зависимостей:

```
PostgreSQL  →  domain.issue  →  api.issue
```

`domain.issue` не знает о существовании `api.issue`, как не знает и о `sync.jira` — оно лишь предоставляет `IssueEntity`/`IssueRepository`; маппинг Entity → внешний DTO (`IssueResponse`) — обязанность `api.issue.IssueQueryService`, а не `domain.issue`.

## Backend packages

| Package | Responsibility | MVP |
|---|---|---|
| `integration.jira` | HTTP-клиент + auth + wire DTO + `JiraContextProvider` (только integration layer) | Yes |
| `sync.jira` | Application layer: `JiraSyncService` (оркестрация sync поверх `JiraContextProvider`, постраничная пагинация, нормализация в `JiraIssueSnapshot` — собственный контракт слоя), `JiraSyncResult` (агрегаты прогона). `JiraSyncScheduler` (Phase 2.5, **реализовано**) — фоновый вход в тот же `JiraSyncService.syncBoard()`, что и manual `POST /api/admin/sync/jira`: `SchedulingConfigurer`, условная регистрация по `jira.sync.enabled` (default `false`), `ScheduledTaskRegistrar.addFixedDelayTask` (не `fixedRate`) с интервалом `jira.sync.interval` (default `5m`). `JiraSyncService` несёт in-process guard (`AtomicBoolean`) — второй одновременный вызов `syncBoard()` (manual или scheduled) пропускается, не меняя форму `JiraSyncResult` | Yes |
| `integration.gitlab` | **Phase 3.1 реализовано:** HTTP-клиент GitLab API v4 + auth + wire DTO (project/branch/commit/MR). **`RestGitLabClient` + `MockGitLabClient`**, выбор через конфиг (`gitlab.mode=rest\|mock`) — симметрия с Jira mock. Notes/approvals — по возможности API (EE); pipelines — **не** в Phase 3 | Yes |
| `sync.gitlab` | **Phase 3.2+ реализовано:** application layer — `GitLabSyncService` (оркестрация; production list из `domain.repository` / PostgreSQL через `RepositoryPersistencePort`; yaml только mock/local/tests), upsert git entities + `activity_events` + workstreams; глубина commits — `gitlab.sync.commit-history-days` + API `since`. Admin HTTP (3.8) и reconcile scheduler (3.9) — ещё нет. **Не** зависит от `api.*` | Yes |
| `integration.jenkins` | Poll/webhook: builds | Yes (Phase 5) |
| `domain.issue` | Issue + sprint + fixVersion. Persistence-слой (Phase 2.3, реализовано) — единственный владелец своих контрактов: `IssueEntity`, `IssueRepository`, `IssuePersistencePort` (+ `IssueUpsertCommand`, `IssueUpsertOutcome`), `IssueUpsertService` | Yes |
| `domain.workstream` | **Phase 3.6 реализовано:** Workstream = Issue × Type; upsert из Git sync; derived status минимум (`not_started`/`in_progress`/`in_review`/`merged`); `repository_id`/`issue_id` nullable | Yes |
| `domain.workstream_type` | **Phase 3.3 реализовано:** справочник типов (Liquibase seed, не хардкод) | Yes |
| `domain.repository` | **Phase 3.3 реализовано:** GitLab project → `workstream_type_code` (таблица `repositories`; matching по `gitlab_project_id`, не по path/name); `RepositoryPersistencePort` | Yes |
| `domain.gitlab` | **Phase 3.4 реализовано:** persistence `branches` / `commits` / `merge_requests` + upsert ports; wired из `sync.gitlab` | Yes |
| `domain.timeline` | **Phase 3.5 реализовано (write):** `IssueKeyExtractor` + `activity_events` upsert; **read API** — Phase 3.7. Activity Feed read — Phase 4 | Yes |
| `domain.activity` | Командный activity feed (read API — Phase 4; пишет в ту же `activity_events`) | Yes (Phase 4 UI) |
| `domain.release` | Release Health по fixVersion | Yes |
| `domain.risk` | Правила рисков | Yes |
| `api` | REST controllers + минимальный security enforcement — **реализовано** (Phase 2.4). `api.admin.JiraSyncController`: `POST /api/admin/sync/jira`, тонкий HTTP-адаптер над `sync.jira.JiraSyncService`, без бизнес-логики, реюзает `JiraSyncResult` (без отдельного response DTO). `api.security`: `SecurityConfig` (`SecurityFilterChain` — `/actuator/health` открыт, `/api/admin/**` и прочие `/actuator/**` (в т.ч. `/actuator/info`) требуют аутентификации, CSRF off, sessions `STATELESS`, отказ → `401`; остальное как было), `AdminTokenAuthenticationFilter` (`OncePerRequestFilter`, сравнивает `Authorization: Bearer <token>` с конфигом), `AdminTokenProperties` (`delivery-monitor.admin.token` ⇐ `DELIVERY_MONITOR_ADMIN_TOKEN`, fail-fast). Bearer admin-token на `/api/admin/**`, [ADR-012](./adr/0012-minimal-auth-baseline-admin-endpoints.md). `api.issue` — Read API, **реализовано**: `IssueController` (`GET /api/issues`, `GET /api/issues/{key}`, тонкий HTTP-адаптер), `IssueQueryService` (read-only, `@Transactional(readOnly = true)`, маппинг `IssueEntity → IssueResponse`), `IssueResponse`/`ErrorResponse` (DTO). Зависит только от `domain.issue` — без `sync.jira`/`integration.jira`/`JiraClient`, без live Jira. `GET /api/sprints/current` — не реализован (нет `sprints` persistence, см. [database.md](./database.md), [discovery.md](./discovery.md)) | Yes |
| AI Summary service | REST → LLM → markdown | After MVP |

## Core abstractions

### Issue key

Якорь системы. Пример: `MPTPSUPP-1234`. Извлекается из Jira напрямую и из GitLab/Jenkins через naming веток / commit messages / MR.

### Workstream Type

Конфигурируемый тип потока работы (`backend`, `frontend`, `oracle`, `qa`, …).  
Система **не** содержит захардкоженных знаний о конкретных платформах.

### Workstream

`Workstream = Issue × Workstream Type`.  
Статус выводится из веток, MR, builds и (при необходимости) Jira transitions.

### activity_events

Единая таблица фактов. Из неё строятся Issue Timeline и Activity Feed.  
Подробности: [database.md](./database.md).

## What we deliberately avoid (for now)

| Idea | Why deferred |
|---|---|
| CQRS / event sourcing | Нет объёма и команды на две модели |
| RabbitMQ / Redis Streams | Сотни событий/день; webhook → DB достаточно |
| Redis cache | Postgres + короткий poll хватает |
| GraphQL | Четыре экрана — REST проще |
| Notification service | Не проблема №1 |
| People screen in MVP | Боль про задачи и релиз |
| AI inside monolith | Связывает продукт с вендором LLM |

См. [decisions.md](./decisions.md) и каталог [adr/](./adr/).

## Phase 3 — GitLab + Timeline (3.1–3.6 Done; next 3.7)

> Design status: **approved**. Реализация по [roadmap.md](./roadmap.md) 3.1–3.9: **3.1–3.6 done**; next **3.7** Read API (`timeline` + `workstream-types`).

### Целевая зависимость пакетов (зеркало Jira)

```
integration.gitlab  →  sync.gitlab  →  domain.repository
                                    →  domain.gitlab (branches/commits/MRs)
                                    →  domain.timeline (activity_events)
                                    →  domain.workstream (+ domain.workstream_type)

PostgreSQL  →  domain.timeline / domain.workstream  →  api.issue (timeline) / api.workstream
```

`api.admin` вызывает `sync.gitlab` (как сейчас `sync.jira`); read API **не** ходит в GitLab live.

### Observed repositories — single source of truth (**реализовано в 3.4**)

**Production flow (с Phase 3.4):**

```
PostgreSQL repositories
        →  RepositoryPersistencePort
        →  GitLabSyncService
        →  GitLabClient
```

- Список наблюдаемых проектов в production — **только** таблица `repositories` (seed Liquibase / позже admin).
- `GitLabSyncProperties.repositories` **не** участвует в production sync path.
- Yaml `gitlab.sync.repositories` допускается **только** для mock / local dev / tests — не второй SoT рядом с БД.
- *Текущий долг после 3.3:* sync ещё читает yaml; dual source закрывается wiring’ом в 3.4 ([decisions.md](./decisions.md)).

### GitLab Mock Mode (implementation decision)

```
integration.gitlab
  RestGitLabClient   ← gitlab.mode=rest (default)
  MockGitLabClient   ← gitlab.mode=mock
```

Назначение: локальная разработка без `GITLAB_TOKEN`, unit/integration tests, CI. Режим — только конфиг (как `jira.mode`). Mock защищать от production (тот же паттерн, что у Jira).

### Источники данных GitLab (Phase 3)

| Источник | Нужен в Phase 3? | Зачем | Персистентность |
|---|---|---|---|
| **Branches** | Да | `BRANCH_CREATED`, привязка issue key, сигнал workstream | таблица `branches` |
| **Commits** | Да | основной объём Timeline (`COMMIT`) | таблица `commits` |
| **Merge Requests** | Да | review gate: `MR_OPENED` / `MR_APPROVED` / `MR_MERGED`; derived status | таблица `merge_requests` |
| **Pipelines** | **Нет** (Phase 5) | CI для `mptp8` = GitLab Pipelines, не Jenkins; first-class позже | не в Phase 3 |
| Notes / approvals | Nice-to-have | `MR_APPROVED` точнее на EE; иначе state/approvals fields MR | в payload MR / событие |

### Commit History Policy (implementation decision)

- **Не** загружать бесконечную историю commits.
- Глубина: конфиг `gitlab.sync.commit-history-days` → GitLab API параметр `since`.
- Цель: предсказуемое время sync; full historical import **вне** Phase 3.

### GitLab API (v4) — минимальный набор

| API | Использование |
|---|---|
| `GET /api/v4/projects/:id` | smoke / resolve path → id |
| `GET /api/v4/projects/:id/repository/branches` | sync branches |
| `GET /api/v4/projects/:id/repository/commits` | sync commits с `since` (см. commit-history-days) |
| `GET /api/v4/projects/:id/merge_requests` | list MRs |
| `GET /api/v4/projects/:id/merge_requests/:iid` | detail (state, merged_at, source_branch) |
| `GET /api/v4/projects/:id/merge_requests/:iid/approvals` | optional (EE) |
| Webhooks `push` / `merge_request` | **после** manual sync (ADR-004); не стартовая точка Phase 3 |

Auth: `PRIVATE-TOKEN` / Project·Group token, scopes `read_api` (+ `read_repository` при необходимости). Env: `GITLAB_BASE_URL`, `GITLAB_TOKEN` (отдельно от `JIRA_TOKEN` / admin-token).

### Timeline + activity_events idempotency

- **Отдельная таблица `activity_events` — да** ([ADR-008](./adr/0008-activity-events.md)).
- Issue Timeline = `WHERE issue_key = ? ORDER BY occurred_at DESC` (newest first — перед 3.7).
- Phase 3 пишет GitLab-события; Activity Feed (Phase 4) читает ту же таблицу.
- **Идемпотентность:** `source` + `source_ref`, UNIQUE `(source, source_ref)`. GitLab: `source=GITLAB`; COMMIT → `<project_id>:<commit_sha>`; MR → `<project_id>:mr:<iid>`; BRANCH → `<project_id>:branch:<branch_name>`. Повторный sync не создаёт дублей.
- Связь с Jira Issue: `issue_key` из regex (branch / commit / MR `source_branch`; soft-link title). FK на `issues.id` не обязателен.
- **Extraction home (перед 3.5):** `domain.timeline.IssueKeyExtractor` — отдельный чистый компонент (`String → Optional<String>`), **не** в `sync.gitlab` и **не** в `domain.issue`. Вызывается sync-слоями (stamp на git entities) и writer’ом `activity_events`; не private-логика внутри upsert события. См. [decisions.md](./decisions.md) Design notes.
- **Orphan policy:** объекты и события **без** ключа сохраняются (`issue_key` null); linked и unlinked поддерживаются одинаково.
- **Timeline empty (перед 3.7):** нет событий по ключу → `200` + `events: []`, не `404`. Не требует строки в `issues`. См. [api.md](./api.md), [decisions.md](./decisions.md).
- Phase 3 **не** требует `JIRA_STATUS`/`JIRA_COMMENT` для done-when.

### Workstream model

- **Workstream = Issue × Workstream Type** ([ADR-002](./adr/0002-workstream-type.md)). Identity: UNIQUE `(issue_key, workstream_type_code)`.
- Тип **не** выводится из кода платформы — из конфига: для Git — `repositories.workstream_type_code`; позже Jenkins job map / non-Git writers.
- **`repository_id` nullable:** workstream **не обязан** иметь Git. Git-писатель заполняет FK; `qa` и другие non-Git потоки — `repository_id = null`. См. [database.md](./database.md) § Workstreams — Git optional; [decisions.md](./decisions.md) Design notes.
- Seed map Git-репо: [discovery.md](./discovery.md) §9.2 (`qa` в seed типов есть; отдельного QA Git-репо нет — это нормально).
- Отдельный доменный слой **нужен**: `domain.workstream` + `domain.workstream_type` (не хардкод в `sync.gitlab`).
- Workstream создаётся при реальном сигнале **с** `issue_key` (Git в Phase 3.6; non-Git writers позже). Orphan Git без ключа workstream не создаёт. Пустые shell-`qa` на каждую issue **не** автосоздаются.
- Детект *когда* стартовал `qa` (Jira status / assignee → `WORKSTREAM_STARTED`) — follow-up Discovery §5; **модель** без-Git workstream закрыта.

### Ingest order (как Phase 2)

```text
POST /api/admin/sync/gitlab
  → sync.gitlab тянет branches/commits(since)/MRs по repositories
  → upsert git-сущностей (в т.ч. orphan, issue_key null)
  → extract issue_key → activity_events (idempotent by source+source_ref)
                      → upsert workstreams (только если issue_key найден)
  → { fetched, saved, errors, … }

GET /api/issues/{key}/timeline
  → только PostgreSQL (activity_events WHERE issue_key = ? ORDER BY occurred_at DESC)
  → 200 + { issueKey, events } даже если events пуст (не 404)
```

### Out of scope Phase 3

AI Summary · Kafka · Redis · CQRS · GraphQL · notifications · Jenkins/`builds` · pipelines table · Activity Feed UI · Risks · Release Health · `people` · `sprints` · full historical commit import.

## Architectural risks

| Risk | Mitigation |
|---|---|
| Кривой naming веток | Regex `(?<key>[A-Z]+-\d+)` anywhere in branch; soft-link по MR title; orphan **сохраняется** (не дропается) |
| Репозиторий без Workstream Type | Обязательный map `repo → workstream_type_code`; seed из discovery §9.2 |
| Шум inferred-блокеров | Явные Jira links vs soft inferred (Phase 4+) |
| Медленный Jira Server poll | Инкремент по `updated` (future) |
| Пустой Timeline | Phase 3: GitLab events; позже Jira transitions |
| Release % врёт | Прозрачная формула + drill-down (Phase 5) |
| GitLab token / webhook недоступны | Manual sync first + `gitlab.mode=mock` |
| Объём commits на больших репо | `gitlab.sync.commit-history-days` + API `since`; без full history |
| Дубли событий при re-sync | UNIQUE `(source, source_ref)` |

## When architecture changes

Обновляйте этот файл и связанные:

- схема → [database.md](./database.md)
- контракты → [api.md](./api.md)
- источники → [integrations.md](./integrations.md)
- экраны → [ux.md](./ux.md)
- trade-offs → новый файл в [adr/](./adr/) + [decisions.md](./decisions.md)
- план → [roadmap.md](./roadmap.md)
- крупный этап → [session_log.md](./session_log.md), [changelog.md](./changelog.md)
