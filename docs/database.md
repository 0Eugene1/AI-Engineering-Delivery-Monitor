# Database

| | |
|---|---|
| **Status** | Accepted |
| **Version** | 2.1 |
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
| `sprints` | `jira_id`, `name`, `start`, `end`, `state` | Контекст спринта | **Planned / future** — отложено с Phase 2.3: board 718 — Kanban, активных sprint-данных сейчас нет; вводится вместе с sprint metadata из Jira |
| `issues` | `id`, `jira_id`, `issue_key`, `summary`, `status_name`, `status_category`, `assignee_username`, `assignee_display_name`, `issue_type`, `jira_created`, `jira_updated`, `synced_at` | Якорь — реальная таблица Phase 2.3 (см. ниже) | **Real (Phase 2.3, реализовано)** |
| `issue_fix_versions` | `issue_id` (FK → `issues.id`), `fix_version_name` | Множественные fix versions одной issue (не единичное поле) | **Real (Phase 2.3)** |
| `issue_labels` | `issue_id` (FK → `issues.id`), `label` | Jira labels issue, симметрично `issue_fix_versions` | **Real (Phase 2.3)** |
| `workstream_types` | `code` PK, `display_name`, `sort_order`, `is_active` | Конфигурируемые типы | Planned |
| `workstreams` | `issue_id`, `workstream_type_code`, `owner_id`, `derived_status` | Issue × Workstream Type | Planned |
| `repositories` | `gitlab_id`, `name`, `workstream_type_code` | Repo → Workstream Type | Planned |
| `branches` | `repo_id`, `name`, `issue_key`, `last_commit_at`, `author_id` | Feature branches | Planned |
| `commits` | `sha`, `branch_id`, `author_id`, `message`, `committed_at` | Dev activity | Planned |
| `merge_requests` | `iid`, `repo_id`, `issue_key`, `state`, reviewers/approvals | Review gate | Planned |
| `builds` | `jenkins_job`, `build_no`, `status`, `branch`, `mr_iid`, `started_at` | CI result | Planned |
| `activity_events` | `id`, `occurred_at`, `issue_key`, `workstream_type_code`, `actor_id`, `type`, `payload` | Timeline + Feed | Planned |
| `dependencies` | `from_ws`, `to_ws`, `type`, `source` | Блокеры между workstreams | Planned |
| `risk_flags` | `issue_id`, `code`, `severity`, `open` | Риски | Planned |
| `sync_state` | `source`, `last_sync_at`, `cursor` | Watermark scheduler | **Planned / future** — отложено с Phase 2.3: incremental sync и watermark/cursor ещё не реализованы (сейчас — full-refresh постраничный upsert по `jira_id` при каждом запуске); вводится вместе с incremental sync / scheduler |

### `issues` — matching key (Phase 2.3)

Upsert матчит существующую строку по `jira_id` (иммутабельный Jira internal id), **не** по `key`: `key` может измениться при переносе issue между Jira-проектами, `jira_id` — никогда. `key` остаётся уникальным и является бизнес-якорем связей (ADR-001) — используется для join с GitLab/Jenkins и для отображения в UI, но не для поиска строки при sync.

`fixVersions`/`labels` реализованы как `@ElementCollection` (без отдельных JPA-сущностей/repository) — `issue_fix_versions`/`issue_labels` — только value-таблицы с уникальностью `(issue_id, value)` и `ON DELETE CASCADE`.

**Реализационное отклонение (2026-07-15, при кодировании Phase 2.3):** физическая колонка бизнес-якоря названа `issue_key`, а не `key`, как было в исходном эскизе выше. `KEY` — зарезервированное слово SQL; H2 (тестовая БД) принимает его без кавычек в `CREATE TABLE`, но отвергает без кавычек в обычных `SELECT`-выражениях, а сквозное квотирование (`objectQuotingStrategy: QUOTE_ALL_OBJECTS`) оказалось хрупким (ломает регистр имени схемы между H2/PostgreSQL). На уровне Java контракт не изменился — `IssueEntity.getKey()`/`IssueUpsertCommand.key()` называются `key`, только физическое имя колонки в БД — `issue_key`. Matching-логика (по `jira_id`, не по `key`) не затронута. См. [session_log.md](./session_log.md) (Phase 2.3 Persistence implemented).

Подробности дизайна и обоснование — [decisions.md](./decisions.md) (Design notes, 2026-07-15) и [session_log.md](./session_log.md).

## activity_events

Каждый значимый факт пишется один раз:

- Issue Timeline = `WHERE issue_key = ? ORDER BY occurred_at`
- Activity Feed = `ORDER BY occurred_at DESC`
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

- `activity_events (issue_key, occurred_at)`
- `activity_events (occurred_at DESC)`
- `workstreams (issue_id, workstream_type_code)` UNIQUE
- `issues (sprint_id)`, `issues (fix_version)`
- `branches (issue_key)`, `merge_requests (issue_key)`

## Schema change policy

Любое изменение таблиц/полей — обновление **этого файла** и при необходимости [api.md](./api.md), [architecture.md](./architecture.md).
