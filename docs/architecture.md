# Architecture

| | |
|---|---|
| **Status** | Accepted |
| **Version** | 2.5 |
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
| `integration.gitlab` | Poll/webhook: branches, commits, MR, notes | Yes |
| `integration.jenkins` | Poll/webhook: builds | Yes |
| `domain.issue` | Issue + sprint + fixVersion. Persistence-слой (Phase 2.3, реализовано) — единственный владелец своих контрактов: `IssueEntity`, `IssueRepository`, `IssuePersistencePort` (+ `IssueUpsertCommand`, `IssueUpsertOutcome`), `IssueUpsertService` | Yes |
| `domain.workstream` | Workstream = issue × Workstream Type | Yes |
| `domain.workstream_type` | Справочник типов (config/data, не хардкод) | Yes |
| `domain.timeline` | Единый поток событий по задаче | Yes |
| `domain.activity` | Командный activity feed | Yes |
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

## Architectural risks

| Risk | Mitigation |
|---|---|
| Кривой naming веток | Regex + orphan report; soft-link по MR title |
| Репозиторий без Workstream Type | Обязательный map `repo → workstream_type_code` |
| Шум inferred-блокеров | Явные Jira links vs soft inferred |
| Медленный Jira Server poll | Инкремент по `updated` |
| Пустой Timeline | Jira transitions с этапа 2 |
| Release % врёт | Прозрачная формула + drill-down |

## When architecture changes

Обновляйте этот файл и связанные:

- схема → [database.md](./database.md)
- контракты → [api.md](./api.md)
- источники → [integrations.md](./integrations.md)
- экраны → [ux.md](./ux.md)
- trade-offs → новый файл в [adr/](./adr/) + [decisions.md](./decisions.md)
- план → [roadmap.md](./roadmap.md)
- крупный этап → [session_log.md](./session_log.md), [changelog.md](./changelog.md)
