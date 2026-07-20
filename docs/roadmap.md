# Roadmap

| | |
|---|---|
| **Status** | Accepted |
| **Version** | 2.18 |
| **Related** | [vision.md](./vision.md), [architecture.md](./architecture.md), [ux.md](./ux.md), [discovery.md](./discovery.md) |

## Guiding rule

Каждый этап даёт **проверяемый результат**, а не «инфраструктуру ради инфраструктуры».

**Первая ценность — этап 3** (GitLab + Issue Timeline).

**Правило Jira (Phase 2):** сначала **ручной sync** (`POST /api/admin/sync/jira`), потом scheduler. Не начинать с фонового polling.

---

## Фактический статус фаз (по коду, на 2026-07-20)

> Таблицы плана ниже — исходная разбивка. Ниже — что **реально реализовано** в коде (см. [ai_context.md](./ai_context.md) §2, [session_log.md](./session_log.md)).

| Фаза | Статус |
|---|---|
| 0. Discovery | Done |
| 1. Skeleton (Spring Boot + PostgreSQL + Liquibase + Actuator) | Done |
| 2.1 Jira Client (REST-клиент + auth) | Done |
| 2.2 Jira Sync — application layer (`sync.jira`: `JiraSyncService`/`JiraSyncResult`/`JiraIssueSnapshot`) | Done |
| 2.3 Persistence (`domain.issue`, Liquibase `issues`/`issue_fix_versions`/`issue_labels`; `sprints`/`sync_state` отложены) | Done |
| Admin Sync HTTP API — `POST /api/admin/sync/jira` + минимальный security-baseline (`api.admin`/`api.security`, Bearer admin-token, [ADR-012](./adr/0012-minimal-auth-baseline-admin-endpoints.md)) | Done — помечено «Phase 2.4» в [ai_context.md](./ai_context.md)/[session_log.md](./session_log.md) |
| Read API — `GET /api/issues`, `GET /api/issues/{key}` (`api.issue`, зависит только от `domain.issue`) | **Done** |
| `GET /api/sprints/current` | **Отложен** — нет `sprints` persistence (архитектурное решение, [database.md](./database.md), [discovery.md](./discovery.md) TODO); без mock/stub/live-Jira substitute |
| Scheduler (`sync.jira.JiraSyncScheduler`, `fixedDelay`, `jira.sync.enabled`/`jira.sync.interval`, in-process guard в `JiraSyncService`) | **Done** |
| Phase 3.1 GitLab REST Client (`integration.gitlab`) | **Done** |
| Phase 3.2 GitLab Sync application layer (`sync.gitlab`) | **Done** |
| Phase 3.3 Config persistence (`workstream_types`, `repositories`) | **Done** |
| Phase 3.4 Git entities + sync→DB wiring (`branches`/`commits`/`merge_requests`; SoT = PostgreSQL) | **Done** |
| Phase 3.5 Linking + `activity_events` (`IssueKeyExtractor`, timeline writer) | **Done** |
| Phase 3.6 Workstreams (`domain.workstream`, Git-driven upsert) | **Done** |
| Phase 3.7 Read API (`GET /api/issues/{key}/timeline`, `GET /api/workstream-types`) | **Done** |
| Phase 3.8 Admin sync HTTP (`POST /api/admin/sync/gitlab`) | **Done** — mock e2e + **Live E2E 2026-07-20** (rest+rest) |
| Phase 3.9 Reconcile scheduler | **Done** |
| Phase 4 Discovery (Activity Feed + Risks design) | **Done** (docs-only, 2026-07-20) |
| Phase 4.1 Activity Feed Read API | **Done** |
| Phase 4.2 Risks Read API | **Done** |
| Phase 4.3 Delivery Dashboard (UI) | **Done** — React minimal: Дашборд / Лента / История задачи + `GET /api/workstreams/progress`; UI на русском |
| Phase 5+ (Jenkins / Release Health / AI Summary) | Не начаты |

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
| **2.4 REST API** | 1–2 days | Read API для UI | `GET /api/issues` — **Done**; `GET /api/sprints/current` — отложен (нет `sprints` persistence) |
| **2.5 Jira Scheduler** *(после 2.4)* | 1 day | Polling 2–5 min | Фоновый sync без ручного POST — **Done** |
| **3. GitLab + Timeline** | 1.5–2 weeks | Workstreams + Issue Timeline | ≥90% задач с naming → события в Timeline |
| **4. Activity Feed + Risks** | 3–5 days | Лента + risk badges (backend read API) | `GET /api/activity` + `GET /api/risks` из PostgreSQL; стендап без UI |
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
Phase 2.5 Scheduler        ← polling (после того как ручной sync стабилен) — Done
       ↓
Phase 3 GitLab + Timeline  ← 3.1–3.9 Done
       ↓
Phase 4 Activity Feed + Risks  ← 4.1–4.2 Done
       ↓
Phase 4.3 Delivery Dashboard UI  ← Done (minimal React)
```

---

## Phase 4 — Activity Feed + Risks

> **Статус (2026-07-20):** **4.1–4.3 Done**. Discovery approved + parameters locked; read API + минимальный UI.  
> Полный дизайн: [architecture.md](./architecture.md) § Phase 4, [api.md](./api.md) § Phase 4, [ux.md](./ux.md), [decisions.md](./decisions.md) Design notes.  
> Предусловие: Live E2E доказал, что `activity_events` заполнены (~5640 событий); Timeline уже читает ту же таблицу (ADR-008).

**Правило Phase 4 (4.0–4.2):** только **read API** поверх уже существующих данных. Без новых writers sync, без AI/Kafka/Redis/CQRS/Jenkins/Release Health.

**Phase 4.3:** минимальный React product layer (не полный Sprint Board).

| Task | Scope | **Не делать** на этом шаге | Done when | Status |
|---|---|---|---|---|
| **4.0 Discovery** | Дизайн Feed + Risks; контракты; правила без AI; persistence да/нет | Код, миграции, UI | Docs approved; open questions закрыты | **Done** |
| **4.1 Activity Feed Read API** | `GET /api/activity` — `activity_events`; фильтры `since`/`limit`/`workstreamType`/`orphans` (default `true`); без новой таблицы | `domain.activity` persistence, UI, write path | curl → JSON лента из PostgreSQL | **Done** |
| **4.2 Risks (computed)** | `GET /api/risks` — evaluate-on-read; scope = **workstreams**; правила: `STALE_ACTIVITY` (3d), `OPEN_MR_STALE` (5d), `NO_MR`, `JIRA_ACTIVE_NO_GIT` | `risk_flags`, dismiss/ack, AI, Jenkins, UI, Release Health | curl → JSON список рисков | **Done** |
| **4.3 Delivery Dashboard (UI)** | React: Dashboard / Activity Feed / Issue Timeline; `GET /api/workstreams/progress` | Sprint Board, Release Health, Jenkins, auth UI, SPA-in-JAR | `npm run dev` → три экрана на live/mock API | **Done** |

### Phase 4 out of scope (явно, после 4.3)

- AI Summary, Kafka, Redis, CQRS, GraphQL, notifications
- Full Sprint Board / `sprints` / `GET /api/sprints/current`
- Jenkins / `builds` / pipelines / Release Health (Phase 5)
- Persistence `risk_flags` (до dismiss/ack)
- Новые writers в `activity_events` (`JIRA_*`, `BUILD_*`)
- People, webhooks, corporate SSO UI

---

## Phase 3 — GitLab + Timeline (3.1–3.9 Done)

> **Статус (2026-07-20):** tasks **3.1–3.9 реализованы** в коде. **Milestone:** mock path + **Live E2E 2026-07-20** (`jira.mode=rest` + `gitlab.mode=rest`, personal PATs; ~3506 issues / ~5640 activity_events); reconcile scheduler. Next — Phase **4**. Timeline read: sort `occurred_at DESC`, empty/unknown key → `200` + `events: []`.  
> Полный дизайн: [architecture.md](./architecture.md) § Phase 3, [database.md](./database.md) § Phase 3, [api.md](./api.md) § Phase 3, [integrations.md](./integrations.md).  
> Seed репозиториев: [discovery.md](./discovery.md) §9.2 (`mptp-react-native`→frontend, `mptp8`→backend, `eltcbackend`→oracle).

**Правило Phase 3 (как у Jira):** сначала **ручной** `POST /api/admin/sync/gitlab` (task **3.8** — **Done**), потом reconcile-scheduler / webhooks. Не начинать с webhooks.

**Первая ценность (mock + Live E2E 2026-07-20):** Issue Timeline заполняется событиями GitLab (ветки / commits / MR) без ручного ввода — данные пишутся в `activity_events` (3.5), читаются через Read API (3.7), запускаются manual admin sync (3.8). Live proof — личные PAT; service accounts остаются TODO.

| Task | Scope | **Не делать** на этом шаге | Done when | Status |
|---|---|---|---|---|
| **3.1 GitLab REST Client** | `integration.gitlab`: HTTP-клиент GitLab API v4 (`WebClient`), auth (PRIVATE-TOKEN), wire DTO: project / branch / commit / MR | Sync, БД, Timeline API, webhooks | Unit-тесты на mock HTTP: list branches/commits/MRs | **Done** |
| **3.2 GitLab Sync (manual)** | `sync.gitlab`: оркестрация по сконфигурированным `repositories`, нормализация в snapshot-контракты слоя | Persistence, scheduler, webhooks | Данные получены / агрегаты (HTTP endpoint — 3.8) | **Done** |
| **3.3 Config persistence** | Liquibase: `workstream_types` (seed), `repositories` (seed из discovery §9.2; matching по `gitlab_project_id` UNIQUE, не по path/name) | branches/commits/MR/`activity_events` | Seed в БД; `GET /api/workstream-types` (вместе с 3.7) | **Done** |
| **3.4 Git entities persistence** | Liquibase + domain: `branches`, `commits`, `merge_requests`; upsert из sync. **+ wiring:** `GitLabSyncService` читает список репо из PostgreSQL через `RepositoryPersistencePort` (не из yaml в production); yaml `gitlab.sync.repositories` — только mock/local/tests ([decisions.md](./decisions.md) Design notes) | `activity_events`, derived workstreams UI; dual SoT yaml+DB | После sync строки в БД; production sync list = `repositories` table only | **Done** |
| **3.5 Linking + activity_events** | Извлечение `issue_key` (regex), запись `activity_events` (`BRANCH_CREATED`/`COMMIT`/`MR_*`); soft-link orphan | Jenkins/pipelines, Activity Feed screen, Risks | События с ключом → Timeline queryable | **Done** |
| **3.6 Workstreams** | `domain.workstream`: upsert `Workstream = Issue × Type` при первой Git-активности; derived status (минимум) | Release Health %, auto shell-`qa`, People | Workstreams в БД для linked issues | **Done** |
| **3.7 Read API** | `GET /api/issues/{key}/timeline`, `GET /api/workstream-types`; опц. workstreams в `GET /api/issues/{key}` | Activity Feed, Board, Release Health, pagination | curl → JSON Timeline из PostgreSQL | **Done** |
| **3.8 Admin sync HTTP** | `POST /api/admin/sync/gitlab` + тот же Bearer admin-token | Новый auth, OIDC | Manual sync end-to-end | **Done** |
| **3.9 Reconcile scheduler** *(после 3.8)* | `sync.gitlab.GitLabSyncScheduler` → только `GitLabSyncService.syncAll()`; `SchedulingConfigurer` + `fixedDelay`; `gitlab.sync.enabled`/`interval` (default `false`/`10m`); in-process guard в `GitLabSyncService` | `api.admin`, `GitLabClient` напрямую, Controller, Kafka/Redis, distributed lock, incremental, retry, webhooks, Phase 5 pipelines | Фоновый reconcile без ручного POST | **Done** |

### Phase 3 out of scope (явно)

- AI Summary, Kafka, Redis, CQRS, GraphQL, notifications
- Jenkins / `builds` / Release Health (Phase 5)
- GitLab **Pipelines** как first-class entity (Phase 5 — CI; для `mptp8` pipelines = CI-источник вместо Jenkins)
- Activity Feed screen + Risks (Phase 4) — таблица `activity_events` пишется в Phase 3, глоба Feed читает её позже
- `people` table, `JIRA_STATUS`/`JIRA_COMMENT` events (можно отдельной задачей; Timeline Phase 3 = GitLab-first)
- `sprints` / `GET /api/sprints/current`

### Маппинг Task → подфаза

| Подфаза | Включает tasks |
|---|---|
| **3.1 Client** | 3.1 |
| **3.2 Sync** | 3.2 |
| **3.3–3.4 Persistence** | 3.3 + 3.4 |
| **3.5–3.6 Timeline + Workstream** | 3.5 + 3.6 |
| **3.7–3.8 Read + Admin API** | 3.7 + 3.8 |
| **3.9 Scheduler** | 3.9 |

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
| **2.6 REST API** | `GET /api/issues`, `GET /api/issues/{key}` (из БД, не live Jira) — **Done**; `GET /api/sprints/current` — отложен (нет `sprints` persistence) | Scheduler, Board UI, pagination/sorting/filtering | curl → JSON из PostgreSQL |
| **2.7 Scheduler** | `sync.jira.JiraSyncScheduler` (`SchedulingConfigurer`, `fixedDelay`, `jira.sync.enabled`/`jira.sync.interval`), in-process guard в `JiraSyncService` | `sync_state`, distributed lock, incremental sync, retry framework | **Done** — реализован после стабильного ручного sync |

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

Scheduler добавляется **только когда** ручной sync проверен на стенде с реальным token. **Реализовано** (2026-07-15, `sync.jira.JiraSyncScheduler`) — вызывает тот же `JiraSyncService.syncBoard()`, что и manual endpoint выше; `jira.sync.enabled=false` по умолчанию, включается явно через env; см. [session_log.md](./session_log.md).

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

**v2.4 (2026-07-15):** Read API (`GET /api/issues`, `GET /api/issues/{key}`) реализован — Done. `GET /api/sprints/current` явно отложен (архитектурное решение: нет `sprints` persistence, никакого mock/stub/live-Jira substitute); TODO в [discovery.md](./discovery.md). Scheduler — следующий next step.

**v2.5 (2026-07-15):** Scheduler (Phase 2.5 / task 2.7) реализован — Done. `sync.jira.JiraSyncScheduler` вызывает тот же `JiraSyncService.syncBoard()`, что и manual `POST /api/admin/sync/jira`; `fixedDelay` (не `fixedRate`), env-driven `jira.sync.enabled`(default `false`)/`jira.sync.interval`(default `5m`); in-process guard в `JiraSyncService` не даёт manual и scheduled sync запускаться одновременно. `sync_state`/distributed lock/incremental sync/retry framework — не добавлялись. Phase 3 не начата.

**v2.6 (2026-07-17):** Phase 3 «GitLab + Timeline» — architectural discovery **approved**, код не начат. Добавлена детализация tasks 3.1–3.9 (manual sync first, зеркало Phase 2). Pipelines/Jenkins/Activity Feed/Risks/AI — вне Phase 3. См. [architecture.md](./architecture.md), [database.md](./database.md), [api.md](./api.md), [session_log.md](./session_log.md).

**v2.7 (2026-07-17):** Task **3.4** расширен: вместе с git-entities — обязательный wiring `GitLabSyncService` → `RepositoryPersistencePort` (PostgreSQL SoT); yaml `gitlab.sync.repositories` только mock/local/tests. См. [decisions.md](./decisions.md).

**v2.8 (2026-07-17):** Фактический статус: Phase **3.1–3.6 Done** (GitLab client → sync → config/git entities → activity_events → workstreams; 182 теста). Next — **3.7** Read API. Timeline contract (docs-only): `occurred_at DESC`, empty → `200` + `[]`.

**v2.9 (2026-07-17):** Phase **3.7 Done** — `GET /api/issues/{key}/timeline`, `GET /api/workstream-types` (191 тест). Next — **3.8** Admin sync HTTP.

**v2.10 (2026-07-17):** Phase **3.8 Done** — `POST /api/admin/sync/gitlab` (194 теста). Mock e2e milestone: Jira sync → GitLab sync → Issue Timeline. Next — **3.9** reconcile scheduler.

**v2.10a (2026-07-17):** Phase **3.9 design checkpoint** (docs-only): `GitLabSyncScheduler` в `sync.gitlab` → только `syncAll()`; guard в `GitLabSyncService`; `gitlab.sync.enabled=false` / `interval=10m`. Код не писался. См. [decisions.md](./decisions.md).

**v2.11 (2026-07-20):** Phase **3.9 Done** — `sync.gitlab.GitLabSyncScheduler` (`SchedulingConfigurer`, `fixedDelay`, `gitlab.sync.enabled`/`interval`); in-process guard в `GitLabSyncService`. Next — Phase **4**.

**v2.12 (2026-07-20):** **Live E2E** validated — rest+rest на личных PAT (~3506 Jira issues; 3 GitLab repos; sample `MPTPSUPP-43006`). Service accounts всё ещё TODO. Tests baseline **199**. Next — Phase **4**.

**v2.13 (2026-07-20):** Phase **4 Discovery Done** (docs-only). Activity Feed = read той же `activity_events` (без новой таблицы / без `domain.activity` persistence). Risks = evaluate-on-read (без `risk_flags` в Phase 4); первые правила: `STALE_ACTIVITY`, `OPEN_MR_STALE`, `NO_MR`, `JIRA_ACTIVE_NO_GIT`. Tasks 4.1–4.2 — next после go-ahead. См. [architecture.md](./architecture.md), [api.md](./api.md), [decisions.md](./decisions.md).

**v2.14 (2026-07-20):** Phase 4 **parameters locked** — Feed: `orphans=true` default; Risks: `STALE_ACTIVITY=3d`, `OPEN_MR_STALE=5d`, scope=`workstreams`; out of scope подтверждён (AI/Kafka/Redis/CQRS/UI/Jenkins/Release Health). Open questions закрыты. Next — **4.1**.

**v2.15 (2026-07-20):** Phase **4.1 Done** — `GET /api/activity` (`api.activity` → `domain.timeline`); shared `ActivityEventMapper`; Liquibase `0008`; 208 тестов. Next — **4.2** Risks.

**v2.16 (2026-07-20):** Phase **4.2 Done** — `GET /api/risks` (`api.risk` → `domain.risk`); evaluate-on-read; 217 тестов. Next — Phase **5** (Jenkins / Release Health) или Pilot.

**v2.17 (2026-07-20):** Phase **4.3 Done** — минимальный React dashboard (`frontend/`: `/`, `/activity`, `/issues/:key`) + `GET /api/workstreams/progress`. Next — Phase **5** или Pilot.

**v2.18 (2026-07-20):** Frontend UI локализован на русский (`labels.ts` / `format.ts`, без i18n-lib, без смены API). См. [ux.md](./ux.md) v2.3.
