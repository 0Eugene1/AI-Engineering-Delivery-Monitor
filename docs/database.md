# Database

| | |
|---|---|
| **Status** | Accepted |
| **Version** | 2.4 |
| **Related** | [architecture.md](./architecture.md), [glossary.md](./glossary.md) |

## Principles

- Одна PostgreSQL.
- Платформы **не** зашиты в схему — только **Workstream Type**.
- `activity_events` — first-class entity: Timeline и Activity Feed читают одну таблицу.
- Сырые webhook payloads можно хранить в `payload` события или отдельном audit-поле; полноценный event store не требуется для MVP.

## Workstream Type (configuration)

| Field | Meaning |
|---|---|
| `code` | Стабильный ключ: `backend`, `frontend`, `oracle`, `qa`, `ios`, … |
| `display_name` | Подпись в UI |
| `sort_order` | Порядок на Board / Release Health |
| `is_active` | Отключение типа без миграций кода |

### Seed example (current team — data, not domain code)

```yaml
workstream_types:
  - { code: backend,  display_name: Backend,  sort_order: 1 }
  - { code: frontend, display_name: Frontend, sort_order: 2 }
  - { code: oracle,   display_name: Oracle,   sort_order: 3 }
  - { code: qa,       display_name: QA,       sort_order: 4 }
```

## Logical tables

| Table | Key fields | Purpose | Status |
|---|---|---|---|
| `people` | `id`, `name`, `jira_user`, `gitlab_user`, `role` | Маппинг личностей (без People-экрана в MVP) | Planned |
| `sprints` | `jira_id`, `name`, `start`, `end`, `state` | Контекст спринта | **Planned / future** — отложено с Phase 2.3: board 718 — Kanban, активных sprint-данных сейчас нет; вводится вместе с sprint metadata из Jira. **Блокирует `GET /api/sprints/current`** (Read API, 2026-07-15): endpoint сознательно не реализован до появления этой таблицы — без mock/stub/live-Jira substitute, см. TODO в [discovery.md](./discovery.md) |
| `issues` | `id`, `jira_id`, `issue_key`, `summary`, `status_name`, `status_category`, `assignee_username`, `assignee_display_name`, `issue_type`, `jira_created`, `jira_updated`, `synced_at` | Якорь — реальная таблица Phase 2.3 (см. ниже) | **Real (Phase 2.3, реализовано)** |
| `issue_fix_versions` | `issue_id` (FK → `issues.id`), `fix_version_name` | Множественные fix versions одной issue (не единичное поле) | **Real (Phase 2.3)** |
| `issue_labels` | `issue_id` (FK → `issues.id`), `label` | Jira labels issue, симметрично `issue_fix_versions` | **Real (Phase 2.3)** |
| `workstream_types` | `code` PK, `display_name`, `sort_order`, `is_active` | Конфигурируемые типы | **Phase 3 planned** (seed: backend/frontend/oracle/qa) |
| `workstreams` | `id`, `issue_key`, `issue_id` nullable FK, `repository_id` nullable FK, `workstream_type_code`, `derived_status` | Issue × Workstream Type; Git optional | **Phase 3 planned** — UNIQUE `(issue_key, workstream_type_code)` |
| `repositories` | `id` PK, `gitlab_project_id` UK, `path`, `name`, `workstream_type_code` | Repo → Workstream Type (не `gitlab_projects`) | **Phase 3 planned** — seed discovery §9.2; matching по `gitlab_project_id` (см. ниже) |
| `branches` | `repo_id`, `name`, `issue_key` nullable, `last_commit_at`, `author_*` | Feature branches | **Phase 3 planned** |
| `commits` | `sha`, `repo_id`, `branch_id` nullable, `issue_key` nullable, `author_*`, `message`, `committed_at` | Dev activity | **Phase 3 planned** |
| `merge_requests` | `(repo_id, iid)` UK, `issue_key` nullable, `state`, `source_branch`, `title`, `merged_at`, … | Review gate | **Phase 3 planned** |
| `builds` | `jenkins_job`, `build_no`, `status`, `branch`, `mr_iid`, `started_at` | CI result (Jenkins / later pipelines) | Planned — **Phase 5** (не Phase 3) |
| `activity_events` | `id`, `occurred_at`, `issue_key` nullable, `workstream_type_code`, `actor_*`, `type`, `payload`, `source`, `source_ref` | Timeline + Feed; UNIQUE `(source, source_ref)` | **Phase 3 planned** (писать GitLab; Feed UI — Phase 4) |
| `dependencies` | `from_ws`, `to_ws`, `type`, `source` | Блокеры между workstreams | Planned |
| `risk_flags` | `issue_id`, `code`, `severity`, `open` | Риски | Planned |
| `sync_state` | `source`, `last_sync_at`, `cursor` | Watermark scheduler | **Planned / future** — отложено с Phase 2.3, подтверждено отложенным решением Phase 2.5 Scheduler design: incremental sync и watermark/cursor ещё не реализованы (сейчас — full-refresh постраничный upsert по `jira_id` при каждом запуске, в т.ч. из нового `sync.jira.JiraSyncScheduler`); вводится только вместе с incremental sync, отдельной будущей задачей |

### `issues` — matching key (Phase 2.3)

Upsert матчит существующую строку по `jira_id` (иммутабельный Jira internal id), **не** по `key`: `key` может измениться при переносе issue между Jira-проектами, `jira_id` — никогда. `key` остаётся уникальным и является бизнес-якорем связей (ADR-001) — используется для join с GitLab/Jenkins и для отображения в UI, но не для поиска строки при sync.

`fixVersions`/`labels` реализованы как `@ElementCollection` (без отдельных JPA-сущностей/repository) — `issue_fix_versions`/`issue_labels` — только value-таблицы с уникальностью `(issue_id, value)` и `ON DELETE CASCADE`.

**Реализационное отклонение (2026-07-15, при кодировании Phase 2.3):** физическая колонка бизнес-якоря названа `issue_key`, а не `key`, как было в исходном эскизе выше. `KEY` — зарезервированное слово SQL; H2 (тестовая БД) принимает его без кавычек в `CREATE TABLE`, но отвергает без кавычек в обычных `SELECT`-выражениях, а сквозное квотирование (`objectQuotingStrategy: QUOTE_ALL_OBJECTS`) оказалось хрупким (ломает регистр имени схемы между H2/PostgreSQL). На уровне Java контракт не изменился — `IssueEntity.getKey()`/`IssueUpsertCommand.key()` называются `key`, только физическое имя колонки в БД — `issue_key`. Matching-логика (по `jira_id`, не по `key`) не затронута. См. [session_log.md](./session_log.md) (Phase 2.3 Persistence implemented).

Подробности дизайна и обоснование — [decisions.md](./decisions.md) (Design notes, 2026-07-15) и [session_log.md](./session_log.md).

## Phase 3 tables — design decisions (2026-07-17)

> Миграции **не создаются** до go-ahead на реализацию. Ниже — согласованный минимум.

### Нужны в Phase 3

| Table | Решение |
|---|---|
| `workstream_types` | Да — seed справочник |
| `repositories` | Да — **не** отдельное имя `gitlab_projects`; `gitlab_project_id` (UNIQUE) + `path`/`name` (mutable) + `workstream_type_code` |
| `branches` | Да |
| `commits` | Да |
| `merge_requests` | Да |
| `activity_events` | Да — обязательна для Timeline ([ADR-008](./adr/0008-activity-events.md)) |
| `workstreams` | Да — Issue × Type; **`repository_id` nullable** (Git optional; см. ниже) |

### Не нужны в Phase 3

| Table / idea | Почему |
|---|---|
| `gitlab_projects` | Дубликат `repositories` |
| отдельная `pipelines` | CI → Phase 5 (для mptp8 — GitLab Pipelines вместо Jenkins) |
| `builds` | Phase 5 |
| `people` | Actor как строки в event/git entities достаточно; People screen out of MVP |
| `sprints` / `sync_state` / `risk_flags` / `dependencies` | Другие фазы / отложены |

### `repositories` — matching key (Phase 3.3)

Upsert / lookup репозитория матчит строку по **`gitlab_project_id`** (числовой GitLab project id, иммутабельный), **не** по `path`/`name`: path и display name могут смениться при rename/move проекта в GitLab (`old-name` → `new-name`), id — нет. Симметрия с matching `issues` по `jira_id`, не по `key` (Phase 2.3).

Минимальный эскиз колонок:

| Column | Role |
|---|---|
| `id` | PK (внутренний) |
| `gitlab_project_id` | UNIQUE — внешний GitLab project id, ключ matching при sync/seed |
| `path` | `path_with_namespace` (mutable; обновляется при rename) |
| `name` | display name (mutable) |
| `workstream_type_code` | FK/код типа (ADR-002) |

`path` остаётся полезным для отображения и как запасной аргумент GitLab API (`GET /projects/:path`), но **не** для поиска строки в Monitor.

### Linking Jira ↔ GitLab

- Бизнес-якорь: `issue_key` (текст, как в `issues.issue_key`), **nullable** на git-сущностях и на `activity_events`.
- `workstreams.issue_id` — опциональный FK: заполняется, если issue уже есть в Monitor; иначе workstream живёт по `issue_key` до появления issue.
- `workstreams.repository_id` — **nullable** FK → `repositories.id`: provenance Git-сигнала; `null` для non-Git потоков (`qa`, будущие ручные/внешние). **Не** часть identity.
- `activity_events.issue_key` — заполняется при успешном extract; **null** для orphan (событие всё равно пишется). Без обязательного FK на `issues`.
- Matching репозитория: по `gitlab_project_id` (не по path/name).
- Matching git-сущностей: branches — `(repo_id, name)`; commits — `(repo_id, sha)`; MRs — `(repo_id, iid)`.
- Matching workstream: **`(issue_key, workstream_type_code)`** UNIQUE — не по `repository_id`.

### Workstreams — Git optional (перед Phase 3.6)

**Решение (2026-07-17):** workstream **может существовать без Git**. Не все Workstream Types обязаны иметь repository.

Минимальный эскиз колонок:

| Column | Role |
|---|---|
| `id` | PK |
| `issue_key` | бизнес-якорь (ADR-001), часть UNIQUE |
| `issue_id` | nullable FK → `issues.id` (если issue уже в Monitor) |
| `repository_id` | **nullable** FK → `repositories.id` (Git provenance; `null` = non-Git) |
| `workstream_type_code` | тип потока (ADR-002), часть UNIQUE |
| `derived_status` | выводимый статус |

Правила создания:

| Сигнал | `repository_id` | Пример |
|---|---|---|
| GitLab branch/commit/MR с `issue_key` | заполнен (репо-источник) | `backend` / `frontend` / `oracle` (сейчас у oracle есть Git) |
| Будущий non-Git writer (Jira / inferred `WORKSTREAM_STARTED`) | `null` | `qa` |
| Orphan Git без `issue_key` | — | workstream **не** создаётся |

Phase 3.6 **не** создаёт пустые shell-строки `qa` на каждую issue «на всякий случай». Открытый вопрос Discovery §5 сужается до *источника сигнала* для `qa` (Jira status / assignee), не до «может ли workstream жить без Git» — модель это уже допускает.

### Orphan GitLab objects (Phase 3 implementation decision)

Если branch / commit / MR **не** содержит Jira key:

- объект **сохраняется** в своей таблице;
- `issue_key` остаётся `null`;
- `activity_event` **создаётся** без Jira-связи (`issue_key` null);
- linked и unlinked обрабатываются одинаково (инженерная активность важнее полного coverage linking).

Issue Timeline (`WHERE issue_key = ?`) показывает только linked; orphan-события доступны для будущего Feed / orphan report.

### `activity_events` — Phase 3 writers + idempotency

| `type` | Source в Phase 3 | Notes |
|---|---|---|
| `BRANCH_CREATED` | GitLab branches | |
| `COMMIT` | GitLab commits | |
| `MR_OPENED` / `MR_APPROVED` / `MR_MERGED` | GitLab MRs | APPROVED — если API доступен; upsert по тому же `(source, source_ref)` при смене состояния MR |
| `JIRA_*` / `BUILD_*` / `WORKSTREAM_STARTED` | **не** обязательны для Phase 3 done-when | позже |

**Идемпотентность (обязательно):** колонки `source` + `source_ref`; уникальность **`UNIQUE (source, source_ref)`**. Повторный GitLab sync **не** создаёт дубликаты строк.

Стабильные `source_ref` для GitLab (`source = GITLAB`):

| Событие | `source_ref` |
|---|---|
| COMMIT | `<project_id>:<commit_sha>` |
| MERGE REQUEST | `<project_id>:mr:<iid>` |
| BRANCH | `<project_id>:branch:<branch_name>` |

Пример: `2159:abc123…`, `2159:mr:88`, `2159:branch:feature/MPTPSUPP-1234`.

## activity_events

Каждый значимый факт пишется один раз (идемпотентно по `(source, source_ref)`):

- Issue Timeline = `WHERE issue_key = ? ORDER BY occurred_at DESC` (только linked; newest first — перед Phase 3.7)
- Activity Feed = `ORDER BY occurred_at DESC` (включая orphan с `issue_key` null — Phase 4)
- Release Health агрегирует workstreams по `fix_version` и `workstream_type_code`

### Event types (MVP)

| `type` | Source | Example |
|---|---|---|
| `BRANCH_CREATED` | GitLab | `{workstream_type}` создал `feature/MPTPSUPP-123` |
| `COMMIT` | GitLab | commit в repo данного Workstream Type |
| `MR_OPENED` | GitLab | MR !42 |
| `MR_APPROVED` | GitLab | Review approved |
| `MR_MERGED` | GitLab | Merged to main |
| `BUILD_STARTED` / `BUILD_FINISHED` | Jenkins | SUCCESS / FAILURE |
| `JIRA_STATUS` | Jira | In Progress → In Review |
| `JIRA_COMMENT` | Jira | Комментарий к задаче (автор, текст/snippet) |
| `WORKSTREAM_STARTED` | Jira / inferred | Активность по типу (например `qa`) |
| `BLOCKER_ADDED` | Jira link | blocks MPTPSUPP-99 |

## Derived workstream status (logical)

Статусы выводятся из данных, не вводятся вручную. Примеры состояний:

| Status | Signal |
|---|---|
| `not_started` | Нет ветки / коммитов с issue key для данного type |
| `in_progress` | Есть ветка + свежие commits |
| `in_review` | Открыт MR |
| `changes_requested` | MR с reject / unresolved discussions |
| `merged` | MR merged |
| `build_failed` | Последний Jenkins build = FAILURE |
| `build_ok` | Последний build SUCCESS |
| `done` | Issue Done/Closed и workstream завершён |

Точный enum фиксируется при реализации; обновляйте этот файл при изменении.

## Indexes (recommended)

- `activity_events (source, source_ref)` **UNIQUE** — идемпотентность Phase 3
- `activity_events (issue_key, occurred_at)` — null `issue_key` допустим (orphan)
- `activity_events (occurred_at DESC)`
- `workstreams (issue_key, workstream_type_code)` **UNIQUE** — identity (ADR-002); `repository_id` не в ключе
- `workstreams (repository_id)` — optional lookup; null допустим (non-Git)
- `issues (sprint_id)`, `issues (fix_version)`
- `branches (issue_key)`, `merge_requests (issue_key)`

## Schema change policy

Любое изменение таблиц/полей — обновление **этого файла** и при необходимости [api.md](./api.md), [architecture.md](./architecture.md).
