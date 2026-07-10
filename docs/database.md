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

| Table | Key fields | Purpose |
|---|---|---|
| `people` | `id`, `name`, `jira_user`, `gitlab_user`, `role` | Маппинг личностей (без People-экрана в MVP) |
| `sprints` | `jira_id`, `name`, `start`, `end`, `state` | Контекст спринта |
| `issues` | `key`, `summary`, `status`, `assignee_id`, `sprint_id`, `fix_version` | Якорь |
| `workstream_types` | `code` PK, `display_name`, `sort_order`, `is_active` | Конфигурируемые типы |
| `workstreams` | `issue_id`, `workstream_type_code`, `owner_id`, `derived_status` | Issue × Workstream Type |
| `repositories` | `gitlab_id`, `name`, `workstream_type_code` | Repo → Workstream Type |
| `branches` | `repo_id`, `name`, `issue_key`, `last_commit_at`, `author_id` | Feature branches |
| `commits` | `sha`, `branch_id`, `author_id`, `message`, `committed_at` | Dev activity |
| `merge_requests` | `iid`, `repo_id`, `issue_key`, `state`, reviewers/approvals | Review gate |
| `builds` | `jenkins_job`, `build_no`, `status`, `branch`, `mr_iid`, `started_at` | CI result |
| `activity_events` | `id`, `occurred_at`, `issue_key`, `workstream_type_code`, `actor_id`, `type`, `payload` | Timeline + Feed |
| `dependencies` | `from_ws`, `to_ws`, `type`, `source` | Блокеры между workstreams |
| `risk_flags` | `issue_id`, `code`, `severity`, `open` | Риски |
| `sync_state` | `source`, `last_sync_at`, `cursor` | Watermark scheduler |

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
