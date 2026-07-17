# Session Log

Журнал крупных этапов. Цель: за **~5 минут** понять состояние проекта без чтения чатов.

Правило: после каждого завершённого крупного этапа добавляйте запись **в начало** (новее сверху).

Шаблон записи:

```markdown
## YYYY-MM-DD — <краткий заголовок этапа>

**Stage:** …
**Summary:** …
**Decisions:** …
**Docs touched:** …
**Next:** …
```

---

## 2026-07-17 — First local run (Postgres + mock Jira sync)

**Stage:** операционный smoke первого локального запуска (не новая фаза roadmap). Цель — поднять стек и проверить manual Jira path end-to-end на mock-данных.

**Summary:**

1. **PostgreSQL** — через Docker Compose (`docker/`, контейнер `delivery-monitor-postgres`, healthy на `localhost:5432`). Учётка: `delivery_monitor` / `delivery_monitor` / БД `delivery_monitor`. pgAdmin подключение OK.
2. **Env** — `DELIVERY_MONITOR_ADMIN_TOKEN=my-secret-admin-token` (User env Windows).
3. **Backend** — `.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=jira-mock,gitlab-mock"`.
4. **Проверки:**
   - `GET /actuator/health` → `{"status":"UP"}`
   - `GET /api/workstream-types` → `backend` / `frontend` / `oracle` / `qa` (seed Liquibase)
   - `POST /api/admin/sync/jira` + Bearer → `fetched=5`, `created=5`, `mocked=true`, `errors=[]`
   - `GET /api/issues` → demo keys `MPTPSUPP-90001`…`90005`
5. **Блокер при старте (исправлен):** Hibernate schema-validate падал на `activity_events.payload` — БД `text`, entity `@Lob` ожидал PostgreSQL `oid`. Фикс: `@JdbcTypeCode(SqlTypes.LONGVARCHAR)` вместо `@Lob` в `ActivityEventEntity`.

**Decisions / заметки:**

- Первый запуск удобнее на **mock-профилях**, без реальных `JIRA_TOKEN` / `GITLAB_TOKEN`.
- `POST /api/admin/sync/gitlab` **ещё нет** (Phase **3.8**) — `activity_events` / `workstreams` / timeline после GitLab sync пока пустые.
- UI нет: смотреть данные в браузере через GET API или в pgAdmin.

**Docs touched:** `docs/session_log.md` (this entry), `docs/changelog.md`.

**Code touched:** `backend/.../domain/timeline/ActivityEventEntity.java` (`@Lob` → `LONGVARCHAR`).

**Docs follow-up:** smoke checklist (7 пунктов) зафиксирован в [backend/README.md](../backend/README.md#smoke-checklist-после-крупного-этапа); ссылка из `docs/README.md`.

**Next:** Phase **3.8** Admin sync HTTP (`POST /api/admin/sync/gitlab`) — после него полный mock e2e до timeline; опционально — live Jira/GitLab с реальными токенами и project ID.

---

## 2026-07-17 — Docs sync: статус Phase 3.7 + commit/push

**Stage:** docs sync (после реализации 3.7). Код Phase 3.7 уже написан; здесь — актуализация entry-point `.md` под фактический статус.

**Summary:**

| Файл | Что исправлено |
|---|---|
| `roadmap.md` (v2.9) | 3.7 **Done**, next **3.8**; таблица tasks / overview |
| `ai_context.md` (v2.13) | Stage → 3.1–3.7 done; 191 tests; next 3.8 |
| `architecture.md` (v2.11) | пакеты `api.issue` Timeline + `api.workstream`; Phase 3 header |
| `api.md` (v2.7) | timeline + workstream-types marked implemented |
| `README.md` / `backend/README.md` / `structure.md` | Status, Done, package layout, Next |

**Docs touched:** перечисленные выше + `session_log.md`, `changelog.md`.

**Code touched:** Phase 3.7 (тот же commit/push): `api.issue.Timeline*`, `api.workstream/**`, `ActivityEventRepository`, тесты.

**Next:** go-ahead → Phase **3.8** Admin sync HTTP.

---

## 2026-07-17 — Phase 3.7 Read API: timeline + workstream-types implemented

**Stage:** Phase 3.7 «Read API» ([roadmap.md](./roadmap.md) task 3.7) — **реализован**. Код в `api.issue` (Timeline) + `api.workstream`. Сознательно **не** добавлялись: write API, dashboard/UI, scheduler, security changes, Jira/GitLab live calls, новые persistence-слои, nested `workstreams[]` в `GET /api/issues/{key}`, Activity Feed.

**Summary:**

1. **`GET /api/issues/{key}/timeline`** — `TimelineController` + `TimelineQueryService` + `TimelineResponse`. Читает только PostgreSQL `activity_events` через `ActivityEventRepository.findAllByIssueKeyOrderByOccurredAtDesc` (`ORDER BY occurred_at DESC`). Пустой/неизвестный key → `200` + `{ issueKey, events: [] }` — **не** `404`. Не требует `IssueEntity`. `GET /api/issues/{key}` не менялся.
2. **`GET /api/workstream-types`** — `WorkstreamTypeController` + `WorkstreamTypeQueryService` + `WorkstreamTypeResponse`. Активные типы через существующий `WorkstreamTypeRepository.findAllByActiveTrueOrderBySortOrderAsc()`.
3. Зависимости: `PostgreSQL → domain.timeline (+ domain.workstream_type) → api.issue` и `PostgreSQL → domain.workstream_type → api.workstream`. Без `sync.*` / `integration.*`.

**Decisions / отклонения:**

- Добавлены **`TimelineQueryService`** / **`WorkstreamTypeQueryService`** (не в явном списке классов ТЗ) — симметрия с `IssueQueryService`; контроллеры остаются тонкими HTTP-адаптерами.
- Nested records в `TimelineResponse`: `TimelineEvent`, `WorkstreamTypeRef`, `ActorRef` (sketch api.md).
- **`summary`** в timeline item — **derived at read** из `type` + payload (в БД колонки нет); не хранится.
- **`actor.id`** = `actor_username` (отдельного people id нет).
- **`WorkstreamTypeResponse.sortOrder`** — доп. поле сверх «code + displayName» в Conventions (список уже отсортирован; поле удобно UI).
- `ActivityEventRepository` расширен query-методом (не новый persistence-слой).
- Опциональное вложение `workstreams[]` в issue detail — **не** делалось (ТЗ: `GET /api/issues/{key}` не менять).

**Docs touched:** `docs/session_log.md` (this entry), `docs/changelog.md`.

**Code touched:** `backend/.../api/issue/Timeline*`, `backend/.../api/workstream/**`, `ActivityEventRepository`, тесты.

**Verify:** `.\mvnw.cmd clean verify` — **191** тест (было 182), 0 failures, 0 errors, 2 skipped.

**Next:** Phase **3.8** — Admin sync HTTP (`POST /api/admin/sync/gitlab`).

---

## 2026-07-17 — Docs sync: статус Phase 3.1–3.6 + commit/push

**Stage:** docs sync (после реализации 3.1–3.6). Код не менялся в этом шаге — только актуализация `.md` под фактический статус.

**Summary:**

Сверены и обновлены документы, которые ещё писали «Phase 3 не начат» / «next 3.4»:

| Файл | Что исправлено |
|---|---|
| `roadmap.md` (v2.8) | Фактический статус: 3.1–3.6 **Done**, next **3.7**; таблица tasks со Status |
| `ai_context.md` (v2.12) | Stage → 3.1–3.6 done; next 3.7 |
| `architecture.md` (v2.10) | Пакеты + Phase 3 header под 3.1–3.6 |
| `README.md` / `backend/README.md` / `structure.md` | Status, Done, package layout, Next |

**Docs touched:** перечисленные выше + `session_log.md`, `changelog.md`.

**Code touched:** none (в этом шаге); ранее не закоммиченный код Phase 3.1–3.6 уходит в тот же push.

**Next:** go-ahead → Phase **3.7** Read API.

---

## 2026-07-17 — Design checkpoint: Timeline sort + empty response (перед Phase 3.7)

**Stage:** docs-only (перед Phase 3.7 Read API). Код **не** менялся.

**Summary:**

Закрыты два контрактных вопроса для `GET /api/issues/{key}/timeline`:

| Вопрос | Решение |
|---|---|
| Порядок событий | `ORDER BY occurred_at DESC` (newest first) |
| Нет событий / неизвестный key | `200 OK` + `{ "issueKey", "events": [] }` — **не** `404` |
| Нужна ли строка в `issues`? | **Нет** — timeline читает только `activity_events` по path key |

`GET /api/issues/{key}` по-прежнему `404`, если issue нет в Monitor — это другой endpoint.

**Docs touched:** `docs/decisions.md`, `docs/architecture.md` (v2.9), `docs/database.md`, `docs/api.md` (v2.6), `docs/session_log.md`, `docs/changelog.md`.

**Code touched:** none.

**Next:** go-ahead → Phase **3.7** Read API (`timeline` + `workstream-types`).

---

## 2026-07-17 — Phase 3.6 Workstreams persistence + Git-driven upsert implemented

**Stage:** Phase 3.6 «Workstreams» ([roadmap.md](./roadmap.md) task 3.6) — **реализован**. Код в `domain.workstream` + wiring в `sync.gitlab` + Liquibase. Сознательно **не** добавлялись: timeline API, dashboard, IssueEntity lookup (`issue_id`), Release Health, notifications, AI, auto shell-`qa`.

**Summary:**

1. **Liquibase** `0007-workstreams.yaml` — таблица `workstreams`; identity `UNIQUE (issue_key, workstream_type_code)`; `repository_id` / `issue_id` nullable FK; FK `workstream_type_code` → `workstream_types`.
2. **`domain.workstream`:** `WorkstreamEntity` / `WorkstreamRepository` / `WorkstreamPersistencePort` / `WorkstreamUpsertCommand` / `WorkstreamUpsertOutcome` / `WorkstreamUpsertService` + `WorkstreamDerivedStatuses` (минимум: `not_started` / `in_progress` / `in_review` / `merged`).
3. **`GitLabSyncService`:** при Git-активности с `issue_key` upsert workstream (`repository_id` = provenance репо; `issue_id = null`). Orphan без ключа workstream **не** создаёт. Derived status: branch/commit → `in_progress`; open MR → `in_review`; merged MR → `merged` (монотонный merge, без downgrade).

**Decisions / отклонения:**

- Порт назван **`WorkstreamPersistencePort`** (не bare `PersistencePort`) — симметрия с `IssuePersistencePort` / `ActivityEventPersistencePort`.
- Добавлен **`WorkstreamDerivedStatuses`** (string constants + `rank`/`max`) — не было в явном списке классов ТЗ; нужен для минимального derived status.
- **`issue_id` всегда null** в Phase 3.6 — IssueEntity lookup отложен (Design note: `IssueKeyResolver` later; «Jira lookup» out of scope).
- `created`/`updated` в `GitLabSyncResult` теперь включают **и** git entities, **и** activity_events, **и** workstreams.
- Batch collapse дублей `(issue_key, type)` внутри upsert; статус не понижается при weaker signal.

**Docs touched:** `docs/session_log.md` (this entry), `docs/changelog.md`.

**Code touched:** `backend/.../domain/workstream/**`, Liquibase `0007` + master, `GitLabSyncService`, тесты.

**Verify:** `.\mvnw.cmd clean verify` — **182** теста (было 170), 0 failures, 0 errors, 2 skipped.

**Next:** Phase **3.7** — Read API (`GET /api/issues/{key}/timeline`, `GET /api/workstream-types`).

---

## 2026-07-17 — Design checkpoint: workstream without Git (`repository_id` nullable)

**Stage:** docs-only (перед Phase 3.6). Код **не** менялся.

**Summary:**

Закрыт открытый вопрос «qa без Git — пустой workstream»:

| Вопрос | Решение |
|---|---|
| Workstream обязан иметь Git / known repository? | **Нет** |
| `workstreams.repository_id` | **nullable** FK — provenance, не identity |
| Identity (ADR-002) | UNIQUE `(issue_key, workstream_type_code)` |
| Phase 3.6 Git writer | заполняет `repository_id` |
| `qa` / non-Git | `repository_id = null`; без auto-shell на каждую issue |
| Discovery §5 | остаётся только *источник сигнала* для старта `qa` |

**Docs touched:** `docs/decisions.md`, `docs/database.md`, `docs/architecture.md`, `docs/discovery.md`, `docs/session_log.md`, `docs/changelog.md`.

**Code touched:** none.

**Next:** go-ahead → Phase **3.6** Workstreams persistence + upsert из Git sync.

---

## 2026-07-17 — Phase 3.5 issue-key extraction + activity_events implemented

**Stage:** Phase 3.5 «Linking + activity_events» ([roadmap.md](./roadmap.md) task 3.5) — **реализован**. Код в `domain.timeline` + wiring в `sync.gitlab` + Liquibase. Сознательно **не** добавлялись: timeline API, workstreams, dashboard, Jira resolution (`IssueEntity` lookup), Jenkins, scheduler.

**Summary:**

1. **`domain.timeline.IssueKeyExtractor`** — чистый `extract(text) → Optional<String>`, regex `(?<key>[A-Z]+-\d+)`, без БД / Jira / `IssueEntity` (Design note перед 3.5).
2. **Liquibase** `0006-activity-events.yaml` — таблица `activity_events`; idempotency `UNIQUE (source, source_ref)`; `issue_key` nullable (orphan policy).
3. **Persistence:** `ActivityEventEntity` / `ActivityEventRepository` / `ActivityEventPersistencePort` / `ActivityEventUpsertCommand` / `ActivityEventUpsertOutcome` / `ActivityEventUpsertService`.
4. **`GitLabSyncService`:** stamp `issue_key` на branches (name) / commits (message→title) / MRs (source_branch→title soft-link); пишет `BRANCH_CREATED` / `COMMIT` / `MR_OPENED`|`MR_MERGED` с `source=GITLAB` и стабильными `source_ref`. Orphan → `issue_key=null`, объект и событие сохраняются.

**Decisions / отклонения:**

- Порт назван **`ActivityEventPersistencePort`** (не bare `PersistencePort`) — симметрия с `IssuePersistencePort` / `BranchPersistencePort`.
- Типы событий — **string constants** (`ActivityEventTypes`), не closed enum (новые writers без миграции кода enum).
- `MR_APPROVED` константа есть; **writer не пишет** (нет approvals API в Phase 3) — только `MR_OPENED` / `MR_MERGED` по `state`.
- `payload` — JSON string через Jackson `ObjectMapper` (Spring bean); колонка `CLOB`.
- `created`/`updated` в `GitLabSyncResult` теперь включают **и** git entities, **и** activity_events.
- FK на `workstream_types` / `issues` для `activity_events` **не** добавлялся (soft text refs, как в database.md).

**Docs touched:** `docs/session_log.md` (this entry), `docs/changelog.md`.

**Code touched:** `backend/.../domain/timeline/**`, Liquibase `0006` + master, `GitLabSyncService`, тесты.

**Verify:** `.\mvnw.cmd clean verify` — **170** тестов (было 146), 0 failures, 0 errors, 2 skipped.

**Next:** Phase **3.6** — workstreams (Issue × Type) при первой Git-активности с `issue_key`.

---

## 2026-07-17 — Design checkpoint: IssueKeyExtractor home (перед Phase 3.5)

**Stage:** docs-only. Код **не** менялся.

**Summary:**

Где живёт extraction `issue_key` (regex / soft-link):

| Выбор | Решение |
|---|---|
| Пакет | **`domain.timeline`** |
| Имя | **`IssueKeyExtractor`** (`String → Optional<String>`) |
| Не в | `GitLabSyncService` / любой `sync.*` |
| Не в | `domain.issue` + имя `IssueKeyResolver` (Resolver = lookup `IssueEntity` — другая роль) |

Отдельный маленький компонент, **не** спрятанный внутрь создания `activity_events`: его вызывают sync (stamp на branches/commits/MRs) и timeline writer (поле события). Orphan → empty/`null`, запись всё равно идёт. GitHub/Jenkins позже реюзят тот же extractor.

**Docs touched:** `docs/decisions.md`, `docs/architecture.md`, `docs/session_log.md`, `docs/changelog.md`.

**Code touched:** none.

**Next:** go-ahead → Phase **3.5** Linking + `activity_events`.

---

## 2026-07-17 — Phase 3.4.1 GitLab Sync persistence wiring implemented

**Stage:** Phase 3.4.1 — закрывает dual source после 3.3/3.4. Код в `sync.gitlab` (+ конфиг). Сознательно **не** добавлялись: `activity_events`, issue-key extraction, timeline, workstreams, REST API, scheduler.

**Summary:**

1. **Production (`gitlab.mode=rest`):** `GitLabSyncService.syncAll()` берёт список **только** из `RepositoryPersistencePort.findAllOrdered()` — yaml `gitlab.sync.repositories` **игнорируется**.
2. **Mock (`gitlab.mode=mock`):** yaml фильтрует проекты; каждый `gitlab-id` резолвится в seeded `repositories` row (FK `repository_id` всё равно из БД). Список перенесён в `application-gitlab-mock.yml` (только 2159 — покрытие fixtures).
3. **Upsert:** после fetch → snapshots → `BranchPersistencePort` / `CommitPersistencePort` / `MergeRequestPersistencePort` (`issue_key=null`).
4. **`GitLabSyncResult`:** агрегаты `created`/`updated`/`saved()`; список snapshots убран (как `JiraSyncResult` после 2.3).

**Decisions / отклонения:**

- Инжект **портов** (`*PersistencePort`), не конкретных `*UpsertService` — Spring всё равно даёт service beans; симметрия с `sync.jira → IssuePersistencePort`.
- Upsert **per-project** (после полной выгрузки проекта), не page-by-page как Jira — текущий fetch копит страницы в snapshot.
- Mock yaml без строки в БД → error в `errors`, sync остальных продолжается.
- `workstreamTypeCode` в snapshots берётся из **DB** entity (не из yaml), даже в mock.

**Docs touched:** `docs/session_log.md` (this entry), `docs/changelog.md`.

**Code touched:** `GitLabSyncService`, `GitLabSyncResult`, `GitLabSyncProperties`, `application.yml`, `application-gitlab-mock.yml`, `GitLabSyncServiceTest` (13 тестов).

**Verify:** `.\mvnw.cmd clean verify` — **146** тестов (было 144), 0 failures, 0 errors, 2 skipped.

**Next:** Phase **3.5** — issue-key extraction + `activity_events`.

---

## 2026-07-17 — Phase 3.4 GitLab entities persistence implemented

**Stage:** Phase 3.4 «Git entities persistence» ([roadmap.md](./roadmap.md) task 3.4) — **частично реализован** (git entities). Код только в `domain.gitlab` + Liquibase. Сознательно **не** добавлялись: `activity_events`, timeline, workstreams, issue-key extraction/linking, REST API, scheduler, security. **`sync.gitlab` не подключён к persistence** (ни upsert git-сущностей, ни wiring списка репо на `RepositoryPersistencePort`) — по явному scope этой реализации; dual yaml+DB остаётся долгом.

**Summary:**

1. **Liquibase** `0005-git-entities.yaml` — таблицы `branches`, `commits`, `merge_requests` с FK `repository_id` → `repositories.id`.
2. **Matching:** branches — `(repository_id, name)`; commits — `(repository_id, sha)`; merge_requests — `(repository_id, gitlab_iid)`.
3. **`domain.gitlab`:** entities + Spring Data repos + `*PersistencePort` / `*UpsertCommand` / `*UpsertOutcome` / `*UpsertService` для каждой сущности. `issue_key` nullable (orphan policy; linking — 3.5). `synced_at` на каждой строке.

**Decisions / отклонения:**

- Колонка FK названа **`repository_id`** (не черновой `repo_id` из database.md sketch).
- MR iid колонка — **`gitlab_iid`** (не bare `iid`) — как в ТЗ.
- Пакет **единый** `domain.gitlab` (не split branch/commit/mr) — вариант из architecture.md.
- Доп. поля сверх минимального эскиза (из sync snapshots): `tip_commit_sha`/`web_url` на branches; `short_id`/`title`/`web_url` на commits; `gitlab_id`/`target_branch`/`author_*`/`gitlab_created_at`/`gitlab_updated_at`/`web_url` на MRs.
- **Wiring sync → DB SoT отложен** относительно roadmap/decisions Design note («обязателен в 3.4») — явный scope этой задачи: только persistence слой, без изменения `sync.gitlab`.

**Docs touched:** `docs/session_log.md` (this entry), `docs/changelog.md`.

**Code touched:** `backend/.../domain/gitlab/**`, Liquibase `0005` + master, тесты (+17).

**Verify:** `.\mvnw.cmd clean verify` — **144** теста (было 127), 0 failures, 0 errors, 2 skipped (`JiraSmokeTest` без токена).

**Next:**

1. Wire `GitLabSyncService` → `RepositoryPersistencePort` (SoT) **и** upsert через `domain.gitlab` ports.
2. Phase **3.5** — issue-key extraction + `activity_events`.
3. Не трогать admin HTTP / Timeline API / pipelines до соответствующих подфаз.

---

## 2026-07-17 — Decision: sync repo list = PostgreSQL only (перед Phase 3.4)

**Stage:** docs-only. Код **не** менялся. Закрывает главный technical debt после 3.3 (dual source yaml + DB).

**Summary:**

Зафиксировано ([decisions.md](./decisions.md) Design notes):

- Dual source после 3.3 (**yaml** `gitlab.sync.repositories` + **seeded** `repositories` table) — нормален только до 3.4.
- **В Phase 3.4 (обязательный wiring вместе с git-entities):**  
  `repositories` table → `RepositoryPersistencePort` → `GitLabSyncService` → `GitLabClient`.
- Production: `GitLabSyncService` **не** читает `GitLabSyncProperties.repositories`.
- Yaml list — **только** mock / local dev / tests.
- `page-size` / `commit-history-days` остаются в properties.

**Docs touched:** `docs/decisions.md`, `docs/architecture.md` (v2.8), `docs/roadmap.md` (task 3.4), `docs/ai_context.md`, `docs/session_log.md`, `docs/changelog.md`.

**Code touched:** none.

**Next:** Phase **3.4** — git entities persistence **и** wiring sync → `RepositoryPersistencePort` (один SoT).

---

## 2026-07-17 — Phase 3.3 Config persistence (workstream_types + repositories) implemented

**Stage:** Phase 3.3 «Config persistence» ([roadmap.md](./roadmap.md) task 3.3) — **реализован**. Код только в `domain.workstream_type` / `domain.repository` + Liquibase. Сознательно **не** добавлялись: `branches` / `commits` / `merge_requests` / `activity_events` / `workstreams` / pipelines / `sync_state`, REST API (`GET /api/workstream-types` → 3.7), scheduler, security изменения. `sync.gitlab` **не** переключён на БД — yaml `gitlab.sync.repositories` остаётся источником списка до отдельного шага.

**Summary:**

1. **Liquibase** `0003-workstream-types.yaml` — таблица `workstream_types` (`code` PK, `display_name`, `sort_order`, `is_active`) + seed `backend`/`frontend`/`oracle`/`qa`.
2. **Liquibase** `0004-repositories.yaml` — таблица `repositories` (`id` PK, `gitlab_project_id` UNIQUE, `path`, `name`, `workstream_type_code` FK → `workstream_types.code`) + seed discovery §9.2: 760→frontend, 2159→backend, 3494→oracle.
3. **`domain.workstream_type`:** `WorkstreamTypeEntity`, `WorkstreamTypeRepository` (`findByCode`, `findAllByActiveTrueOrderBySortOrderAsc`).
4. **`domain.repository`:** `RepositoryEntity` (matching по `gitlabProjectId`, не path/name), `RepositoryJpaRepository`, `RepositoryPersistencePort` / `RepositoryUpsertCommand` / `RepositoryUpsertOutcome` / `RepositoryUpsertService` — шов для следующих этапов (обновление mutable path/name при rename).

**Decisions / отклонения:**

- Spring Data интерфейс назван **`RepositoryJpaRepository`**, не `RepositoryRepository` — избежание двойного `Repository` в имени типа; пакет остаётся `domain.repository`.
- Seed `name` = leaf от `path` (например `mptp8`), не отдельное display name из GitLab API — mutable, обновится при будущем sync.
- **`qa`** в seed типов есть; отдельного Git-репозитория для QA нет (как в discovery/architecture).
- Dual source: БД seeded, но **`sync.gitlab` ещё читает yaml** — **осознанный долг 3.3**; закрытие зафиксировано отдельным Design note (см. запись выше): wiring в **Phase 3.4**, yaml только mock/local/tests.
- `GET /api/workstream-types` — Phase 3.7 (roadmap: «можно вместе с 3.7»).

**Docs touched:** `docs/session_log.md` (this entry), `docs/changelog.md`.

**Code touched:** `backend/.../domain/workstream_type/**`, `backend/.../domain/repository/**`, Liquibase `0003`/`0004` + master, тесты (+11), комментарий в `application.yml`.

**Verify:** `.\mvnw.cmd clean verify` — **127** тестов (было 116), 0 failures, 0 errors, 2 skipped (`JiraSmokeTest` без токена).

**Next:**

1. Phase **3.4** — git entities (`branches` / `commits` / `merge_requests`) **+** sync list → `RepositoryPersistencePort` (один SoT).
2. Не трогать admin HTTP / Timeline API / pipelines до соответствующих подфаз.

---

## 2026-07-17 — Phase 3.3 design note: repositories match by gitlab_project_id

**Stage:** Phase 3.3 «Config persistence» — **docs-only** уточнение перед кодом. Миграции / entities **не** создавались.

**Summary:**

Зафиксировано: таблица `repositories` обязана иметь внешний стабильный GitLab id:

- `id` — внутренний PK
- `gitlab_project_id` — **UNIQUE**, ключ matching при seed/sync
- `path` / `name` — mutable (rename в GitLab не ломает строку)
- `workstream_type_code` — тип потока (ADR-002)

Аналогия с Phase 2.3: matching `issues` по `jira_id`, не по `key`. Имя колонки уточнено с чернового `gitlab_id` → `gitlab_project_id`.

**Docs touched:** `docs/decisions.md`, `docs/database.md` (v2.4), `docs/architecture-overview.md`, `docs/session_log.md`, `docs/changelog.md`.

**Code touched:** none.

**Next:** go-ahead → реализация Phase 3.3 (`workstream_types` + `repositories` seed) с matching по `gitlab_project_id`.

---

## 2026-07-17 — Phase 3.2 GitLab Sync Application Layer implemented

**Stage:** Phase 3.2 «GitLab Sync (manual)» ([roadmap.md](./roadmap.md) task 3.2) — **реализован**. Код только в `sync.gitlab`. Сознательно **не** добавлялись: JPA entities, Liquibase, controllers, Spring Security, scheduler, `activity_events` persistence, workstreams, issue-key extraction, pipelines.

**Summary:**

Новый пакет `ru.eltc.deliverymonitor.sync.gitlab` (зеркало стиля early Phase 2.2 `sync.jira` — оркестрация + snapshots без persistence):

1. **`GitLabSyncService`** — `syncAll()` / `syncProject(projectIdOrPath)` поверх `GitLabClient`; пагинация branches / commits / MRs; `commit-history-days` → API `since`; MR list с `state=all`; нормализация wire DTO → snapshot-контракты слоя; `GitLabClientException` по проекту → `errors`, остальные репозитории продолжают.
2. **`GitLabSyncResult`** — агрегаты (`projectsSynced`, `branchesFetched`, `commitsFetched`, `mergeRequestsFetched`, `pages`, `mocked`, `errors`) + derived `fetched()`; до persistence несёт `projects` (список `GitLabProjectSyncSnapshot`).
3. **`GitLabSyncProperties`** (`gitlab.sync.*`): `page-size` (default 50), `commit-history-days` (default **30**), `repositories[]` (seed discovery §9.2: 760/frontend, 2159/backend, 3494/oracle) — временный источник списка проектов до таблицы `repositories` (Phase 3.3).
4. Snapshots: `GitLabProjectSyncSnapshot`, `GitLabBranchSnapshot`, `GitLabCommitSnapshot`, `GitLabMergeRequestSnapshot` (без `issueKey` — Phase 3.5).

**Decisions / отклонения от дизайна:**

- Список репозиториев из **yaml-конфига**, не из БД — таблица `repositories` ещё не создана (3.3); seed совпадает с discovery §9.2.
- Default `gitlab.sync.commit-history-days` = **30** (открытый вопрос в session_log закрыт при кодировании).
- Snapshots **включены** в `GitLabSyncResult` (как early Phase 2.2 Jira до persistence); после 3.3–3.4 ожидается переход на агрегаты `created`/`updated` без полного списка в result.
- Ошибка одного проекта **не** останавливает multi-repo run (изоляция per-project) — у Jira одна board, там иначе.
- In-process concurrency guard **не** добавлен (scheduler — 3.9).
- `issue_key` / orphan linking / `activity_events` / workstreams — **не** в 3.2.
- Admin `POST /api/admin/sync/gitlab` — Phase 3.8.

**Docs touched:** `docs/session_log.md` (this entry), `docs/changelog.md`.

**Code touched:** `backend/src/main/java/.../sync/gitlab/**`, `application.yml` (`gitlab.sync.*`), тесты (+16).

**Verify:** `.\mvnw.cmd clean verify` — **116** тестов (было 100), 0 failures, 0 errors, 2 skipped (`JiraSmokeTest` без токена).

**Next:**

1. Phase **3.3** Config persistence (`workstream_types` + `repositories` seed) — и/или **3.4** git entities persistence.
2. Не трогать admin HTTP / scheduler / Timeline API / pipelines до соответствующих подфаз.

---

## 2026-07-17 — Phase 3.1 GitLab REST Client implemented

**Stage:** Phase 3.1 «GitLab REST Client» ([roadmap.md](./roadmap.md) task 3.1) — **реализован**. Код только в `integration.gitlab`. Сознательно **не** добавлялись: sync layer, persistence, Liquibase, controllers, scheduler, pipelines, Jenkins, approvals API.

**Summary:**

Новый пакет `ru.eltc.deliverymonitor.integration.gitlab` (зеркало стиля `integration.jira`, с отличием mock на уровне клиента — как в design decision):

1. **`GitLabClient`** (интерфейс) + **`RestGitLabClient`** (`gitlab.mode=rest`, default) + **`MockGitLabClient`** (`gitlab.mode=mock`).
2. **`GitLabProperties`** / **`GitLabClientConfig`**: `gitlab.base-url` ⇐ `GITLAB_BASE_URL` (default `https://git.eltc.ru`), timeouts, `gitlab.mode`, `gitlab.token` ⇐ `GITLAB_TOKEN` → заголовок **`PRIVATE-TOKEN`**.
3. Wire DTO: `GitLabProjectDto`, `GitLabBranchDto`, `GitLabCommitDto`, `GitLabMergeRequestDto`, `GitLabUserDto`, `GitLabErrorResponseDto`.
4. **`GitLabClientException`** — единый тип для HTTP- и transport-ошибок (как `JiraClientException`).
5. API v4: `getProject`, `listBranches`, `listCommits` (с опциональным `since`), `listMergeRequests`, `getMergeRequest`.
6. Mock fixtures: `classpath:gitlab/mock/*` (demo project **2159** / `mptp/mptp8`); prod-guard на профилях `prod`/`production`. Профиль `gitlab-mock` (`application-gitlab-mock.yml`).

**Decisions / отклонения от дизайна:**

- Mock на уровне **`GitLabClient`**, а не отдельного provider-слоя (как у Jira) — **по design** Phase 3 (`RestGitLabClient`/`MockGitLabClient` + `gitlab.mode`).
- В **mock** режиме `GITLAB_TOKEN` **может быть пустым** (в отличие от Jira mock, где placeholder обязателен) — в духе «локальная разработка без `GITLAB_TOKEN`».
- Approvals API (EE) **не** реализован в 3.1 — optional в architecture; достаточны list/detail MR.
- `gitlab.sync.commit-history-days` **не** в `GitLabProperties` — принадлежит `sync.gitlab` (3.2); клиент уже принимает `Instant since`.

**Docs touched:** `docs/session_log.md` (this entry), `docs/changelog.md`.

**Code touched:** `backend/src/main/java/.../integration/gitlab/**`, fixtures, `application.yml`, `application-gitlab-mock.yml`, тесты (+25), `DeliveryMonitorApplicationTests` (placeholder `gitlab.token`).

**Verify:** `.\mvnw.cmd clean verify` — **100** тестов (было 75), 0 failures, 0 errors, 2 skipped (`JiraSmokeTest` без токена).

**Next:**

1. Phase **3.2** GitLab Sync (manual) — `sync.gitlab`.
2. Не трогать pipelines / Jenkins / persistence / Timeline API до соответствующих подфаз.

---

## 2026-07-17 — Phase 3 implementation decisions fixed (docs-only)

**Stage:** Phase 3 «GitLab + Timeline» — **design остаётся approved**; зафиксированы четыре implementation decisions перед кодом. Код, миграции, ADR — **не создавались**.

**Summary:**

Перед стартом реализации 3.1 закрыты открытые вопросы discovery:

1. **Activity Events Idempotency** — идемпотентная запись `activity_events`: поля `source` + `source_ref`, UNIQUE `(source, source_ref)`. GitLab (`source=GITLAB`): COMMIT → `<project_id>:<commit_sha>`; MR → `<project_id>:mr:<iid>`; BRANCH → `<project_id>:branch:<branch_name>`. Повторный sync не создаёт дублей.
2. **GitLab Mock Mode** — `integration.gitlab`: `RestGitLabClient` + `MockGitLabClient`, режим через конфиг (`gitlab.mode`), для локальной разработки без `GITLAB_TOKEN`, тестов и CI (симметрия Jira).
3. **Commit History Policy** — глубина через `gitlab.sync.commit-history-days` + GitLab API `since`; full historical import вне Phase 3.
4. **Orphan GitLab Objects Policy** — branch/commit/MR без `issue_key` сохраняются (`issue_key=null`); `activity_event` пишется без Jira-связи; linked и unlinked поддерживаются одинаково.

**Decisions:**

- Новый ADR **не** создавался — решения в рамках ADR-001/003/004/008/011 ([decisions.md](./decisions.md) Design notes).
- Phase 3 design status: **approved** (без смены scope: pipelines/Jenkins/Feed/AI по-прежнему вне фазы).

**Docs touched:**

- `docs/decisions.md` (новая строка Design notes — Phase 3 implementation decisions)
- `docs/database.md` (v2.3 — idempotency, orphan, UNIQUE `(source, source_ref)`, nullable `issue_key`)
- `docs/architecture.md` (v2.7 — mock client, commit-history, idempotency, orphan)
- `docs/ai_context.md` (v2.10)
- `docs/session_log.md` (this entry), `docs/changelog.md`

**Code touched:** none.

**Open before coding (осталось):**

1. Реальный `GITLAB_TOKEN` (+ scopes).
2. CE vs EE (approvals API).
3. Naming coverage % (измерение; orphan policy уже зафиксирована).
4. Источник `qa` workstream без Git.
5. Конкретный default для `gitlab.sync.commit-history-days` при кодировании 3.2.

**Next:**

1. Явный go-ahead → **3.1 GitLab REST Client** (+ mock mode с первой подфазы).
2. Код Phase 3 не писать без go-ahead.

---

## 2026-07-17 — Phase 3 GitLab + Timeline: architectural discovery approved (docs-only)

**Stage:** Phase 3 «GitLab + Timeline» ([roadmap.md](./roadmap.md)) — **дизайн согласован, код не писался**. Миграции, entities, controllers, GitLab-клиент — сознательно не создавались.

**Summary:**

Проведён architectural discovery перед реализацией Phase 3 (первая ценность продукта: Issue Timeline из GitLab). Опора: vision/architecture/ux/database/api, ADR-001/002/004/008, discovery §2/§9.2/§9.3 (3 репо + naming).

1. **Источники GitLab в Phase 3:** branches + commits + merge requests. **Pipelines — вне Phase 3** (CI → Phase 5; для `mptp8` pipelines = замена Jenkins).
2. **API GitLab v4 (минимум):** projects, branches, commits, merge_requests (+ optional approvals EE). Auth: `PRIVATE-TOKEN` / `GITLAB_TOKEN`.
3. **Таблицы Phase 3:** `workstream_types`, `repositories` (**не** `gitlab_projects`), `branches`, `commits`, `merge_requests`, `activity_events`, `workstreams`. Не нужны: pipelines, builds, people, sprints, sync_state.
4. **Timeline:** отдельная `activity_events` — да (ADR-008). События Phase 3: `BRANCH_CREATED`, `COMMIT`, `MR_OPENED`/`MR_APPROVED`/`MR_MERGED`. Связь с Jira — `issue_key` через regex (branch / commit / MR source_branch; soft-link title).
5. **Workstream:** тип из `repositories.workstream_type_code`; нужен отдельный `domain.workstream` + `domain.workstream_type`. `qa` без Git — открытый вопрос, не блокер Timeline.
6. **Ingest:** manual sync first (`POST /api/admin/sync/gitlab`) → persistence → Timeline read API → reconcile scheduler; webhooks после стабильного manual path (как Jira).
7. **Пакеты:** `integration.gitlab → sync.gitlab → domain.*` (зеркало Jira). Read: `GET /api/issues/{key}/timeline`, `GET /api/workstream-types`.
8. **Запрещено в Phase 3:** AI Summary, Kafka, Redis, CQRS, GraphQL, notifications, Jenkins, Activity Feed UI, Risks, Release Health.

**Decisions:**

- Все решения в рамках ADR-001/002/003/004/008/011 — **новый ADR не создавался**.
- Имя таблицы репозиториев остаётся `repositories` (отклонено `gitlab_projects`).
- Pipelines отложены в Phase 5, несмотря на то что `mptp8` уже на GitLab CI.
- `JIRA_STATUS`/`JIRA_COMMENT` не обязательны для Phase 3 done-when (GitLab-first Timeline).

**Docs touched:**

- `docs/roadmap.md` (v2.6 — Phase 3 tasks 3.1–3.9, статус Design approved)
- `docs/architecture.md` (v2.6 — § Phase 3, пакеты gitlab/workstream/timeline)
- `docs/database.md` (v2.2 — § Phase 3 tables)
- `docs/api.md` (v2.5 — § Phase 3 endpoints)
- `docs/integrations.md` (GitLab: manual sync first + Phase 3 scope)
- `docs/decisions.md` (Design notes 2026-07-17)
- `docs/ai_context.md` (v2.9)
- `docs/session_log.md` (this entry), `docs/changelog.md`

**Code touched:** none.

**Open before coding (см. также discovery TODO):**

1. Реальный `GITLAB_TOKEN` (+ scopes).
2. CE vs EE (approvals API).
3. ~~Политика orphan branches~~ → **закрыто** implementation decision (orphan сохраняется). Coverage % naming — измерить отдельно.
4. Источник `qa` workstream без Git.
5. ~~Нужен ли mock-режим~~ → **закрыто**: `RestGitLabClient`/`MockGitLabClient` + `gitlab.mode`.
6. ~~Глубина истории commits~~ → **закрыто**: `gitlab.sync.commit-history-days` + API `since`.

**Next:**

1. Явный go-ahead на реализацию → начать с **3.1 GitLab REST Client** ([roadmap.md](./roadmap.md)).
2. Не писать код Phase 3 без go-ahead; не трогать pipelines/Jenkins/Feed.

---

## 2026-07-15 — Phase 2.5 Scheduler implemented: `JiraSyncScheduler` + in-process sync guard

**Stage:** Phase 2.5 «Scheduler» ([roadmap.md](./roadmap.md)) — **реализована** строго по Scheduler Design, принятому перед этим этапом. Manual sync (`POST /api/admin/sync/jira`) остаётся рабочим и неизменным; scheduler переиспользует ровно тот же `JiraSyncService.syncBoard()`, никакого параллельного пути в Jira/БД не появилось. Сознательно **не добавлялись** (явные ограничения задачи): `sync_state` таблица, distributed lock (ShedLock/Redis), incremental sync, retry framework, новый persistence-слой, HTTP `409`, изменение существующего API-контракта.

**Summary:**

Текущий flow (manual sync) не изменился:

```text
POST /api/admin/sync/jira
        │
        ▼
JiraSyncController
        │
        ▼
JiraSyncService.syncBoard()
        │
        ▼
IssuePersistencePort
```

Добавлен второй, полностью симметричный, вход в тот же `syncBoard()`:

```text
JiraSyncScheduler (fixedDelay, jira.sync.enabled)
        │
        ▼
JiraSyncService.syncBoard()  ← та же точка входа, что у manual sync
        │
        ▼
IssuePersistencePort
```

1. `sync.jira.JiraSyncScheduler` (новый класс, пакет `sync.jira`, **не** `api.admin`/`integration.jira`) — единственная ответственность: регистрация задачи по расписанию, логирование результата, обработка ошибок. Реализован через `SchedulingConfigurer#configureTasks(ScheduledTaskRegistrar)`, а не через простую аннотацию `@Scheduled(fixedDelayString = ...)`: задача должна регистрироваться **условно** (только если `jira.sync.enabled=true`) и её интервал — env-driven `Duration` (например `JIRA_SYNC_INTERVAL=5m`), а не compile-time константа, что `@Scheduled` на уровне аннотации не поддерживает без доп. ухищрений. `configureTasks()`: если `jira.sync.enabled=false` — не регистрирует ничего (лог-сообщение, никаких вызовов `ScheduledTaskRegistrar`/`JiraSyncService`); если `true` — регистрирует **ровно один** `ScheduledTaskRegistrar.addFixedDelayTask(Runnable, Duration)` (**не** `addFixedRateTask`) с интервалом из `jira.sync.interval`. `fixedDelay` выбран намеренно: следующий запуск планируется только **после завершения** предыдущего (никогда не может начаться, пока предыдущий ещё выполняется) — `fixedRate` запускал бы задачи по абсолютному расписанию независимо от длительности предыдущего прогона, что для потенциально медленного Jira-запроса неприемлемо. Метод `runScheduledSync()` вызывает `jiraSyncService.syncBoard()` и логирует агрегаты результата (`fetched`/`pages`/`mocked`/`created`/`updated`/`errors`); **любое** исключение, включая непойманное внутри `JiraSyncService` (например ошибка БД, которая туда не перехватывается), логируется здесь и **не** пробрасывается — сбой одного прогона не мешает следующему `fixedDelay`-запуску.
2. `@EnableScheduling` — добавлена на `DeliveryMonitorApplication` (главный класс приложения), включает инфраструктуру `TaskScheduler` Spring целиком; сама активация фактического запуска Jira-задачи остаётся отдельным env-driven решением в `JiraSyncScheduler`/`jira.sync.enabled` — `@EnableScheduling` только включает механизм, не поведение.
3. `sync.jira.JiraSyncProperties` расширен двумя полями (тот же класс, что уже нёс `page-size`, biндинг `jira.sync.*`): `enabled` (`boolean`, default `false`) и `interval` (`java.time.Duration`, default `5m`, `@NotNull`). `application.yml`: `jira.sync.enabled: ${JIRA_SYNC_ENABLED:false}`, `jira.sync.interval: ${JIRA_SYNC_INTERVAL:5m}` — оба env-driven, ничего не хардкожено. По умолчанию (`enabled=false`) — точное соответствие правилу roadmap «manual sync first»: без явного `JIRA_SYNC_ENABLED=true` в env scheduler не активируется вообще, сервис ведёт себя как раньше.
4. **In-process guard в** `sync.jira.JiraSyncService` — единственное изменение существующего класса. Новое приватное поле `AtomicBoolean syncInProgress`; `syncBoard()` теперь сначала пытается `compareAndSet(false, true)`; если уже `true` (другой прогон — manual или scheduled — уже выполняется), немедленно возвращает `JiraSyncResult` со всеми агрегатами равными нулю и единственным сообщением в `errors()` (`"Sync already in progress; this run was skipped"`) — **без** нового HTTP-статуса `409` и **без** изменения формы `JiraSyncResult` (тот же record, что и раньше, ни одного нового поля). Существующий прогон при этом не прерывается и продолжает выполняться как обычно. Реальная работа синка вынесена в приватный `runSync()`, вызываемый из `syncBoard()` внутри `try { … } finally { syncInProgress.set(false); }` — флаг гарантированно снимается даже при исключении. Это единственная точка защиты от одновременного запуска: и `JiraSyncController` (manual), и `JiraSyncScheduler` (scheduled) вызывают один и тот же `syncBoard()`, поэтому один `AtomicBoolean` внутри сервиса достаточен — распределённая блокировка не нужна (единственный instance приложения, не кластер).

**Не менялись:** `JiraSyncController`/`api.admin` (scheduler не вызывает Controller — обращается напрямую к `JiraSyncService`, как и было решено), `integration.jira`/`JiraClient` (scheduler не обращается к нему напрямую), `JiraSyncResult` (форма/контракт не изменились — только новое смысловое использование существующего поля `errors()` для случая guard-skip), `domain.issue`, Liquibase-схема, `api.security`/`SecurityConfig` (у scheduler нет HTTP-эндпоинта, ничего защищать не нужно).

**Тесты (новые — 5, итог 75, было 70):**

- `JiraSyncSchedulerTest` (4, unit, `JiraSyncService` — Mockito-мок, никакого Spring context/реальной Jira/БД):
  - `disabledSchedulerRegistersNoTask` — `jira.sync.enabled=false` → `configureTasks()` не вызывает **ни одного** метода mock `ScheduledTaskRegistrar` и не трогает `JiraSyncService` (`verifyNoInteractions` на обоих).
  - `enabledSchedulerRegistersFixedDelayTaskWithConfiguredInterval` — `enabled=true`, `interval=2m` → ровно один вызов `addFixedDelayTask(any(Runnable), eq(Duration.ofMinutes(2)))`; отдельно проверено, что `addFixedRateTask` **не** вызывается ни разу.
  - `scheduledRunCallsJiraSyncService` — `runScheduledSync()` вызывает `jiraSyncService.syncBoard()` ровно один раз.
  - `scheduledRunSwallowsExceptionSoFutureRunsAreNotBroken` — `syncBoard()` бросает `RuntimeException`, `runScheduledSync()` не пробрасывает исключение наружу (тест сам не падает).
- `JiraSyncServiceTest` (+1 новый — `guardSkipsAConcurrentSyncBoardCallWhileAnotherRunIsInProgress`, итог 9 в файле) — реальная многопоточность (`Thread` + `CountDownLatch`, без mock-таймеров): первый прогон блокируется внутри fake-провайдера на первой странице; второй вызов `syncBoard()` из основного потока, сделанный пока первый ещё «внутри» вызова провайдера, возвращает результат немедленно (`fetched=0`, `pages=0`, `errors()` содержит ровно сообщение про skip), `IssuePersistencePort` при этом не тронут (`port.pages()` пуст); после разблокировки первый прогон завершается штатно (`fetched=1`, `errors()` пуст, страница персистирована); третий вызов после завершения первого снова выполняется штатно (флаг корректно снят в `finally`).

`.\mvnw.cmd clean verify` (JDK 21, Android Studio JBR) — **75 тестов, 0 failures, 0 errors, 2 skipped** (оба — `JiraSmokeTest`, без токена) → `BUILD SUCCESS`.

**Roadmap compliance:**

- Правило «manual sync first» ([roadmap.md](./roadmap.md) Guiding rule) выполнено буквально: scheduler default `disabled`, добавлен **только после** того, как ручной sync уже реализован и покрыт тестами (Phase 2.2–2.4); ничего не переставлялось местами.
- Прямые ограничения задачи выполнены без отклонений: scheduler не вызывает `Controller`, не вызывает `JiraClient` напрямую, не обходит `JiraSyncService`; `sync_state` не добавлен; distributed lock не добавлен; incremental sync не делался; используется `fixedDelay`, не `fixedRate`; guard не добавляет HTTP `409` и не меняет существующий API-контракт.
- Фаза помечена «Next» → «Done» в [roadmap.md](./roadmap.md)/[ai_context.md](./ai_context.md); **Phase 3 не начата** — явное ограничение задачи.



**Decisions:**

- `SchedulingConfigurer` **вместо** `@Scheduled(fixedDelayString=...)` — единственная реализационная развилка, не предусмотренная явно в тексте задачи, принята здесь: аннотационная форма `fixedDelayString` не умеет (а) регистрировать задачу условно по `jira.sync.enabled` и (б) принимать значения вида `"5m"` напрямую (Spring резолвит строку либо как ISO-8601 `Duration` через `Duration.parse` (нужен префикс `P`), либо как `long` миллисекунд — `DurationStyle`/суффиксы `5m`/`10s`, которые поддерживает Spring **Boot**-биндинг `@ConfigurationProperties`, аннотация `@Scheduled` не понимает). `SchedulingConfigurer#configureTasks` даёт полный императивный контроль: условная регистрация + `Duration` из уже забинденных `JiraSyncProperties` без дополнительного парсинга строк. Не меняет ни одно из требований задачи (`fixedDelay`, env-driven конфиг, `JiraSyncScheduler` в `sync.jira`) — только способ регистрации задачи внутри самого класса.
- **Guard возвращает существующую форму** `JiraSyncResult` **с сообщением в** `errors()`, а не новое поле (`skipped: true`) — при любом добровольном расширении контракта пришлось бы аргументировать, что это действительно ничего не меняет; реюз уже существующего поля `errors()` (тот же приём, что уже применялся для `JiraClientException` в Phase 2.2) держит `JiraSyncResult` буквально без единого изменения состава полей.
- `AtomicBoolean` **внутри** `JiraSyncService`**, не отдельный компонент/аспект** — единственный instance приложения (без кластера/нескольких pod'ов, ADR-003 modular monolith), поэтому in-process примитив достаточен; выносить guard в отдельный класс не даёт дополнительной пользы при текущем объёме кода.
- Новый ADR не создавался — Scheduler Design был согласован (принят) перед этой сессией отдельно; реализация не вводит архитектурных решений сверх уже принятого design (в рамках ADR-001/003/004/006/007/011).

**Docs touched:**

- `docs/roadmap.md` (v2.5 — Scheduler: «Next» → «Done»; фактический статус фаз обновлён)
- `docs/architecture.md` (v2.5 — `sync.jira` описание дополнено `JiraSyncScheduler`; package dependency direction не изменилось — scheduler зависит от `domain.issue` тем же путём, что и manual sync, через `JiraSyncService`)
- `docs/ai_context.md` (стадия/версия 2.8 — Scheduler done)
- `docs/session_log.md` (this entry), `docs/changelog.md`

**Code touched (new):**

- `backend/src/main/java/.../sync/jira/JiraSyncScheduler.java`
- `backend/src/test/java/.../sync/jira/JiraSyncSchedulerTest.java`

**Code touched (modified):**

- `backend/src/main/java/.../sync/jira/JiraSyncService.java` (in-process guard: `AtomicBoolean syncInProgress`, `syncBoard()`/`runSync()` split)
- `backend/src/main/java/.../sync/jira/JiraSyncProperties.java` (+ `enabled`, `interval`)
- `backend/src/main/java/.../sync/jira/package-info.java` (scheduler больше не «out of scope»)
- `backend/src/main/java/.../DeliveryMonitorApplication.java` (+ `@EnableScheduling`)
- `backend/src/main/resources/application.yml` (`jira.sync.enabled`, `jira.sync.interval`)
- `backend/src/test/java/.../sync/jira/JiraSyncServiceTest.java` (+1 guard test)

**Остающиеся ограничения (сознательно, по прямому решению задачи):**

1. `sync_state` **не добавлен** — нет watermark/cursor персистентности; каждый прогон (manual или scheduled) — full-refresh постраничный upsert по `jiraId`, как и раньше (Phase 2.3). Incremental sync по `updated` — отдельная будущая задача.
2. **Distributed lock не добавлен** — guard работает только в рамках одного instance приложения (`AtomicBoolean`); если/когда появится несколько инстансов за балансировщиком, потребуется отдельное решение (ShedLock/Redis/DB-lock) — не в этом MVP (ADR-006 «no broker/Redis in MVP»).
3. **Retry framework не добавлен** — ошибка scheduled-прогона логируется и просто ждёт следующего `fixedDelay`-тика; нет экспоненциальных backoff/повторов внутри одного прогона.
4. **Реальный прогон scheduler против настоящей Jira не выполнялся** — та же причина, что и в предыдущих записях: `JIRA_TOKEN` недоступен в этой среде. Проверено только через юнит-тесты (fake provider, Mockito) — офлайн, без реального Jira/PostgreSQL/HTTP.
5. **Phase 3 не начата** — явное ограничение этой сессии.

**Next:**

1. Получить реальный `JIRA_TOKEN`, включить `JIRA_SYNC_ENABLED=true` на стенде и подтвердить фоновый sync на реальных данных — тот же открытый `TODO`, что и в предыдущих записях, плюс новый пункт для scheduler.
2. Phase 3 «GitLab + Timeline» — по [roadmap.md](./roadmap.md), первая ценность проекта. Не начинать без явного go-ahead.

---



## 2026-07-15 — Read API implemented: `GET /api/issues`, `GET /api/issues/{key}`

**Stage:** «Read API» ([roadmap.md](./roadmap.md), Phase 2.4/2.5 по нумерации плановых таблиц; ранее размечено как «Next» после Phase 2.4 Admin Sync HTTP API) — **реализована** строго по дизайну, зафиксированному в архитектурном review перед этим этапом (см. ограничения ниже). Сознательно **не реализован** `GET /api/sprints/current` (явное решение review — нет `sprints` persistence). Никаких mock/stub/live-Jira substitute для sprint endpoint не добавлялось.

**Summary:**

Новый пакет `ru.eltc.deliverymonitor.api.issue` — read-only HTTP слой поверх `domain.issue`, без единого обращения к `sync.jira`/`integration.jira`/`JiraClient`:

1. `IssueController` (`@RestController`, `/api/issues`) — чистый HTTP-адаптер, без бизнес-логики:
  - `GET /api/issues` — все persisted issues из PostgreSQL (без pagination/sorting/filtering).
  - `GET /api/issues/{key}` — одна issue по публичному Jira `key` (`issue_key`); при отсутствии — `404` с телом `{ "error": "...", "code": "ISSUE_NOT_FOUND" }` (формат из `docs/api.md` "Conventions").
2. `IssueQueryService` (`@Service`, класс аннотирован `@Transactional(readOnly = true)`) — read-слой: грузит `IssueEntity` через `IssueRepository` и маппит в `IssueResponse` **внутри** транзакции, пока LAZY `@ElementCollection` (`fixVersions`/`labels`) ещё доступны — иначе `LazyInitializationException` вне сессии.
3. `IssueResponse` (record, DTO) — `issueKey, summary, status, statusCategory, assigneeUsername, assigneeDisplayName, issueType, jiraCreated, jiraUpdated, fixVersions, labels`. Entity `IssueEntity` **не** отдаётся напрямую — ни `jiraId`, ни database `id` не попадают в контракт.
4. `ErrorResponse` (record) — `{ error, code }`, тело `404`-ответа.
5. `IssueRepository` расширен одним методом — `findByKey(String key)` (Optional), поиск по бизнес-якорю (`key`/`issue_key`), не по `jiraId` и не по database id. `findAllByJiraIdIn` (Phase 2.3) не тронут.

Зависимость строго `PostgreSQL → domain.issue → api.issue` (docs/architecture.md): `api.issue` импортирует только `IssueEntity`/`IssueRepository` из `domain.issue`; `sync.jira`, `integration.jira`, `JiraClient`, `JiraSyncService`, `api.admin.JiraSyncController`, `api.security.SecurityConfig` — **не изменены**. `/api/issues/`** остаётся под существующим `anyRequest().permitAll()` в `SecurityConfig` (открыт внутри VPN, как и было до этого этапа) — security baseline не менялся.

**Тесты (новые — 7, итог 70, было 63):**

- `IssueControllerTest` (3, `@WebMvcTest(IssueController.class)` + `@Import(SecurityConfig.class)`, mock `IssueQueryService` — без реальной БД): `GET /api/issues` → `200` со списком; `GET /api/issues/{key}` → `200` для существующего key; `GET /api/issues/{unknown}` → `404` с телом `{error, code: "ISSUE_NOT_FOUND"}`.
- `IssueQueryServiceIntegrationTest` (3, `@DataJpaTest` + `@AutoConfigureTestDatabase(replace=NONE)` против настоящего Liquibase-changeset `0002-issues.yaml` на файловом H2 в режиме PostgreSQL, `@Import({IssueUpsertService, IssueQueryService})` — тот же приём, что в `IssueUpsertServiceIntegrationTest`): `findAll()` маппит несколько persisted issues в `IssueResponse`, включая **реальную** LAZY-загрузку `fixVersions`/`labels` (не мок) — тест провалился бы с `LazyInitializationException`, если бы сервис не был `@Transactional(readOnly = true)`; `findByKey()` находит существующий key; `findByKey()` возвращает `Optional.empty()` для неизвестного key.
- `DeliveryMonitorApplicationTests` (+1, полный контекст на H2): `issuesEndpointIsPubliclyAccessibleAndReadsFromPostgres` — `GET /api/issues` без `Authorization` header → `200` с `[]` (пустая схема), подтверждает, что security baseline для read-эндпоинтов не изменился этим этапом.

`.\mvnw.cmd clean verify` (JDK 21, Android Studio JBR) — **70 тестов, 0 failures, 0 errors, 2 skipped** (оба — `JiraSmokeTest`, без токена) → `BUILD SUCCESS`.

**Ограничения (по прямому решению архитектурного review перед этим этапом):**

- `GET /api/sprints/current` **не реализован.** Причина: в текущей persistence-модели нет таблицы `sprints` (`docs/database.md` — «Planned / future», отложено с Phase 2.3: board 718 — Kanban, sprint metadata из Jira ещё нет). Запрещено и не сделано: создавать таблицу `sprints`; делать mock/stub response; возвращать фиктивные данные; получать sprint напрямую из Jira для этого endpoint. TODO зафиксирован в `docs/discovery.md` — sprint API реализуется после появления sprint persistence.
- **Никаких pagination/sorting/filtering/search** — оба endpoint возвращают данные как есть.
- **Не менялись:** `JiraSyncService`, `JiraClient`, `api.admin.JiraSyncController` (Admin Sync API), `api.security.SecurityConfig`/`AdminTokenAuthenticationFilter`/`AdminTokenProperties` (security baseline), существующий sync flow, Liquibase-схема (использована уже существующая `0002-issues.yaml`, новых миграций не добавлено).
- Без live-запросов в Jira из `api.issue` — оба endpoint читают исключительно PostgreSQL через `domain.issue`.

**Decisions:**

- Архитектура не менялась относительно решений, зафиксированных в review перед этим этапом (дальнейшая детализация уже принятых ADR-001/003/011, без нового ADR): владение DTO/read-слоем — `api.issue`, а не `domain.issue`, чтобы entity/persistence-слой не знал о внешнем HTTP-контракте; поиск по `key`, не `jiraId`/id — единственный публичный якорь для внешнего клиента (ADR-001).
- `IssueQueryService` размещён в `api.issue` (не в `domain.issue`) — согласно диаграмме review `PostgreSQL → domain.issue → api.issue`: `domain.issue` предоставляет только entity/repository, маппинг Entity→DTO и его read-only транзакционная граница — обязанность API-слоя.
- `@Transactional(readOnly = true)` на уровне класса `IssueQueryService`, а не отдельных методов — оба метода read-only, дублировать аннотацию на каждом не нужно.

**Docs touched:**

- `docs/architecture.md` (пакет `api.issue` в «Backend packages», package-info ссылки)
- `docs/api.md` (endpoints `GET /api/issues`/`GET /api/issues/{key}` помечены implemented; `GET /api/sprints/current` — явный TODO с причиной)
- `docs/database.md` (примечание: `sprints` TODO уточнён — блокирует sprint API)
- `docs/discovery.md` (TODO: sprint API endpoint ждёт sprint persistence)
- `docs/ai_context.md` (стадия/версия — Read API done)
- `docs/roadmap.md` (статус фаз — Read API done, sprint endpoint отложен)
- `docs/session_log.md` (this entry), `docs/changelog.md`

**Code touched (new):**

- `backend/src/main/java/.../api/issue/{IssueController,IssueQueryService,IssueResponse,ErrorResponse,package-info}.java`
- `backend/src/test/java/.../api/issue/{IssueControllerTest,IssueQueryServiceIntegrationTest}.java`

**Code touched (modified):**

- `backend/src/main/java/.../domain/issue/IssueRepository.java` (+ `findByKey(String)`)
- `backend/src/main/java/.../api/package-info.java` (упомянут `api.issue`)
- `backend/src/test/java/.../DeliveryMonitorApplicationTests.java` (+1 тест — публичный доступ к `/api/issues`)

**Next:**

1. `GET /api/sprints/current` — только после появления sprint persistence (новая таблица `sprints`, Liquibase-миграция, дизайн matching key). Не начинать без явного go-ahead.
2. Phase 2.5+ scheduler (`@Scheduled` polling) — по [roadmap.md](./roadmap.md), не начинать в этой сессии (по прямому ограничению задачи).
3. Получить реальный `JIRA_TOKEN` и `DELIVERY_MONITOR_ADMIN_TOKEN` — тот же открытый `TODO`, что и в предыдущих записях.

---



## 2026-07-15 — Phase 2.4 Admin Sync HTTP API implemented

**Stage:** Phase 2.4 «Admin Sync HTTP API» ([roadmap.md](./roadmap.md), [ADR-012](./adr/0012-minimal-auth-baseline-admin-endpoints.md)) — **реализована** по согласованному дизайну (ADR-012 Decision, ранее задокументировано в Design notes). Первый HTTP-controller и первый security-слой в проекте. Сознательно **не добавлялись** (явные ограничения задачи): OIDC/JWT/LDAP, `User`/`Role` entity, `UserRepository`, principal model, permissions, UI, scheduler, webhook security, audit database, incremental sync, async execution, retry.

**Summary:**

Перед реализацией проверена текущая структура: package root `ru.eltc.deliverymonitor`, `DeliveryMonitorApplication` (обычный `@SpringBootApplication`, без существующих `@Configuration` security-классов), `application.yml` (env-driven конфиг по образцу `jira.*`), `backend/pom.xml` (`spring-boot-starter-security` отсутствовал — добавлен). Никакого `api`-пакета и контроллеров в коде не было — реализация с нуля, без параллельной архитектуры.

1. `api.admin.JiraSyncController` — `POST /api/admin/sync/jira`. Чистый HTTP-адаптер: принимает запрос, вызывает `sync.jira.JiraSyncService#syncBoard()`, возвращает его `JiraSyncResult` как есть (`ResponseEntity.ok(result)`) — без отдельного response DTO (реюз существующего application-layer контракта) и без бизнес-логики в контроллере. Без request body — фильтр/страница берутся из конфига (`jira.default-filter-id`, `jira.sync.page-size`), как и было в `JiraSyncService` до этой задачи.
2. `api.security` **(новый пакет):**
  - `AdminTokenProperties` — `delivery-monitor.admin.token` ⇐ `DELIVERY_MONITOR_ADMIN_TOKEN`, `@Validated`/`@NotBlank`, тот же fail-fast паттерн, что у `JiraProperties.Auth#token` (`JIRA_TOKEN`).
  - `AdminTokenAuthenticationFilter` — `OncePerRequestFilter`. Читает `Authorization` header, проверяет префикс `Bearer` , сравнивает токен с конфигом константным по времени сравнением (`MessageDigest.isEqual`). При совпадении кладёт в `SecurityContextHolder` generic `Authentication` с единственной authority `ROLE_ADMIN` и principal-заглушкой `"admin-token"` — **никакой** идентичности из токена не извлекается (stateless, как и требовалось). Пустой/blank сконфигурированный токен никогда не аутентифицирует даже пустой предъявленный токен (защита от вырождения «токен не задан → доступ открыт всем»). Сам токен (ни предъявленный, ни ожидаемый) не логируется ни при успехе, ни при отказе.
  - `SecurityConfig` (`@EnableWebSecurity`) — `SecurityFilterChain`: `/actuator/health` открыт; `/api/admin/**` **и** любой другой `/actuator/`** (в т.ч. `/actuator/info` — раскрывает build-конфигурацию) требуют аутентификации; остальное не тронуто (`anyRequest().permitAll()` — read-эндпоинтов пока нет). CSRF отключён и sessions — `STATELESS` (machine-to-machine Bearer, не браузерная сессия). Отказ аутентификации → `401` через `HttpStatusEntryPoint`, не redirect на login. Фильтр подключён `addFilterBefore(..., UsernamePasswordAuthenticationFilter.class)`. Никакого `JWT`/`OAuth2ResourceServer`/`OIDC`/`LDAP`.
3. `backend/pom.xml` — добавлены `spring-boot-starter-security` (main) и `spring-security-test` (test, `@MockitoBean`/MockMvc).
4. `application.yml` — `delivery-monitor.admin.token: ${DELIVERY_MONITOR_ADMIN_TOKEN:}` (пустой default — секрет не коммитится, fail-fast при старте без него).

**Тесты (новые — 14, итог 63, было 49):**

- `AdminTokenAuthenticationFilterTest` (6, unit, без Spring context): корректный Bearer → authenticated с `ROLE_ADMIN`; без header; неверный токен; не-Bearer схема (`Basic`); пустой Bearer-токен; пустой сконфигурированный токен никогда не матчит.
- `AdminTokenPropertiesTest` (3): биндинг значения, fail-fast при отсутствии/blank токене (по образцу `JiraPropertiesTest`).
- `JiraSyncControllerTest` (3, `@WebMvcTest(JiraSyncController.class)` + `@Import(SecurityConfig.class)`, mock `JiraSyncService` — без реальной Jira/PostgreSQL): 200 + тело `JiraSyncResult` с верным токеном; 401 без header; 401 с неверным токеном; во всех отрицательных случаях `verifyNoInteractions(jiraSyncService)`.
- `DeliveryMonitorApplicationTests` (+2, полный контекст на H2, без реальной Jira/PostgreSQL): `adminSyncJiraRequiresAuthentication` (401 без header — до контроллера/сервиса запрос не доходит) и `actuatorInfoRequiresAuthenticationButHealthDoesNot` (401 на `/actuator/info`, health по-прежнему открыт). Добавлен placeholder `delivery-monitor.admin.token` в `@DynamicPropertySource` (аналогично `jira.auth.token`).

`.\mvnw.cmd clean verify` (JDK 21, Android Studio JBR) — **63 теста, 0 failures, 0 errors, 2 skipped** (оба — `JiraSmokeTest`, без токена) → `BUILD SUCCESS`.

**Deviations from the agreed design (обнаружено при реализации, зафиксировано явно):**

- **Endpoint policy для actuator сужена до буквы, не только по духу.** Согласованный дизайн ADR-012 говорит «actuator, кроме `health`/liveness — Protected»; при реализации это явно закодировано как отдельное правило `.requestMatchers("/actuator/**").authenticated()` **после** `/actuator/health`, а не оставлено на volume «остальное не трогаем» (`anyRequest().permitAll()`) — иначе `/actuator/info` (build info, потенциально раскрывает конфигурацию) остался бы публичным по умолчанию, что противоречило бы явной строке ADR-012's endpoint policy table. Не расширяет и не сужает сам ADR — только более строгая буквальная реализация уже согласованного правила.
- **Response не содержит производного поля** `saved`**.** `JiraSyncResult.saved()` — Java-метод (derived, `created + updated`), не record-компонент, поэтому Jackson его не сериализует в JSON. `docs/api.md`'s более ранний response-sketch показывал `saved` как поле — исправлено на фактическую форму (`startedAt`/`finishedAt`/`fetched`/`pages`/`mocked`/`created`/`updated`/`errors`); `saved` в JSON **не появляется**, клиент может посчитать сумму сам при необходимости. Не решение, а исправление устаревшего sketch под уже принятый (Phase 2.3) контракт `JiraSyncResult`.
- Остальное реализовано **без отклонений** от ADR-012 Decision: владение контрактами (`JiraSyncController` реюзает `JiraSyncResult`, не вводит DTO), разделение токенов (`DELIVERY_MONITOR_ADMIN_TOKEN` ≠ `JIRA_TOKEN`), fail-fast на отсутствующий admin-токен, stateless-модель без identity/User/Role/permissions, отсутствие JWT/OAuth2/OIDC/LDAP.

**Decisions:**

- Архитектура не менялась — реализация строго в рамках ADR-012 Decision (см. предыдущие Design notes записи). Новый ADR не создавался.
- Endpoint-policy строгая буквальная трактовка actuator-правила — реализационная деталь, задокументирована как отклонение здесь и в `decisions.md`, а не молча.

**Docs touched:**

- `docs/architecture.md` (v2.3 — состав `api` пакета: `api.admin`/`api.security` реализованы, package dependency direction дополнен)
- `docs/api.md` (v2.3 — admin sync endpoint помечен «implemented», response sketch исправлен под реальный `JiraSyncResult`, убран несуществующий request body)
- `docs/security.md` (v1.2 — §2 baseline помечен «реализован», §9 checklist аннотирован)
- `docs/decisions.md` (новая строка Design notes — подтверждение реализации ADR-012 без отклонений от Decision + одно самостоятельно принятое уточнение)
- `docs/ai_context.md` (Stage/версия — Phase 2.4 done, next — GitLab/Timeline или read API/scheduler)
- `docs/session_log.md` (this entry), `docs/changelog.md`

**Code touched (new):**

- `backend/src/main/java/.../api/{package-info,admin/{JiraSyncController,package-info},security/{SecurityConfig,AdminTokenAuthenticationFilter,AdminTokenProperties,package-info}}.java`
- `backend/src/test/java/.../api/admin/JiraSyncControllerTest.java`
- `backend/src/test/java/.../api/security/{AdminTokenAuthenticationFilterTest,AdminTokenPropertiesTest}.java`

**Code touched (modified):**

- `backend/pom.xml` (`spring-boot-starter-security`, `spring-security-test`)
- `backend/src/main/resources/application.yml` (`delivery-monitor.admin.token`)
- `backend/src/main/java/.../sync/jira/package-info.java`, `backend/src/main/java/.../domain/issue/package-info.java` (Javadoc: `POST /api/admin/sync/jira` больше не «out of scope» — реализован в `api.admin`, обе стороны его не импортируют)
- `backend/src/test/java/.../DeliveryMonitorApplicationTests.java` (`delivery-monitor.admin.token` placeholder + 2 новых теста)



**Next:**

1. `GET /api/issues`, `GET /api/sprints/current` (read API поверх `domain.issue`, не live Jira) и/или Phase 2.5 scheduler — по [roadmap.md](./roadmap.md). Не начинать без явного go-ahead.
2. Получить реальный `JIRA_TOKEN` и `DELIVERY_MONITOR_ADMIN_TOKEN`, прогнать `JiraSmokeTest` и живой `POST /api/admin/sync/jira` — тот же открытый `TODO`, что и в предыдущих записях.

---



## 2026-07-15 — Phase 2.3 Persistence implemented

**Stage:** Phase 2.3 «Persistence» ([roadmap.md](./roadmap.md)) — **реализована** по дизайну, согласованному в предыдущей записи ("Phase 2.3 Persistence design approved"). Без изменений в архитектурных решениях — все они подтверждены и реализованы как согласовано. Сознательно **не добавлялись**: REST controller, Spring Security, scheduler, `sync_state`, `sprints`, incremental sync, GitLab/Jenkins (явные ограничения задачи).

**Summary:**
Новый пакет `ru.eltc.deliverymonitor.domain.issue` — единственный владелец своих persistence-контрактов, зависимость строго `sync.jira → domain.issue`:

1. `IssueEntity` (JPA) — таблица `issues`: `jiraId` (immutable, matching key), `key` (бизнес-якорь, ADR-001), статусные/assignee-поля, `jiraCreated`/`jiraUpdated`/`syncedAt` (`Instant`), `fixVersions`/`labels` — `Set<String>` через `@ElementCollection` (`issue_fix_versions`/`issue_labels`). Метод `applyUpsert(command, syncedAt)` мутирует существующую запись in-place (clear+addAll для коллекций).
2. `IssueRepository` (Spring Data JPA, прямо в `domain.issue`) — `findAllByJiraIdIn` для batch-матчинга страницы.
3. `IssueUpsertCommand` (входной контракт домена, без ссылок на `sync.jira`) и `IssueUpsertOutcome` (`created`/`updated`).
4. `IssuePersistencePort` (`upsertPage(List<IssueUpsertCommand>)`) и его реализация `IssueUpsertService` (`@Service`, `@Transactional`): batch `findAllByJiraIdIn` → merge/create → `saveAll` на уровне страницы.
5. **Liquibase** `0002-issues.yaml` — таблицы `issues`, `issue_fix_versions`, `issue_labels` (unique constraints, `ON DELETE CASCADE`); подключена в `db.changelog-master.yaml`. `sprints`/`sync_state` не созданы.
6. `sync.jira.JiraIssueSnapshot` — добавлены `jiraId`, `created`; `updated`/`created` стали `Instant` (парсинг Jira-таймстампа: ошибка → `null` + warning в лог, sync не падает); `assigneeName` заменён на `assigneeUsername`/`assigneeDisplayName`.
7. `sync.jira.JiraSyncService` — конструктор принимает `IssuePersistencePort`; на каждой странице маппит `JiraIssueSnapshot → IssueUpsertCommand` и сразу вызывает `upsertPage(...)` (без накопления полного списка в памяти); агрегирует `created`/`updated` по страницам. Ошибки БД **не ловятся** здесь — поднимаются наверх как есть (в отличие от `JiraClientException`, которая пишется в `errors`).
8. `sync.jira.JiraSyncResult` — убран `List<JiraIssueSnapshot> issues`; добавлены `created`/`updated`; `saved()` — derived-метод (`created + updated`).

Тесты (новые/переписанные): `IssueUpsertServiceTest` (unit, Mockito-мок `IssueRepository`: create/update matching по `jiraId` даже при смене `key`, replace fixVersions/labels in-place, `syncedAt`, агрегация по смешанной странице); `IssueUpsertServiceIntegrationTest` (`@DataJpaTest` + `@AutoConfigureTestDatabase(replace=NONE)` против настоящего Liquibase-changeset на файловом H2 в режиме PostgreSQL — тот же приём, что в `DeliveryMonitorApplicationTests`; проверяет реальную схему, upsert-обновление без дублирования, unique-constraint на бизнес-ключ); переписанный `JiraSyncServiceTest` (recording fake `IssuePersistencePort` — постраничный вызов `upsertPage`, а не один вызов на весь прогон; нормализация полей в `IssueUpsertCommand`; unparsable timestamp → `null` без ошибки; partial-persisted result при сбое на второй странице).

`.\mvnw.cmd clean verify` (JDK 21, Android Studio JBR) — **49 тестов, 0 failures, 0 errors, 2 skipped** (оба — `JiraSmokeTest`, без токена) → `BUILD SUCCESS`.

**Deviations from the agreed design (обнаружены при реализации, зафиксированы явно):**

- **Физическая колонка бизнес-якоря названа** `issue_key`**, а не** `key`**.** Причина: `KEY` — зарезервированное слово SQL; H2 (тестовая БД, файловый режим PostgreSQL-compat) принимает его без кавычек в `CREATE TABLE`, но отвергает без кавычек в обычных `SELECT`-выражениях (`column "ie1_0.key" not found` / syntax error в зависимости от способа квотирования). Опробованный fallback — Hibernate backtick-квотирование (``key``) плюс `objectQuotingStrategy: QUOTE_ALL_OBJECTS` в Liquibase — оказался более хрупким: квотирование всех объектов в changeset ломает регистр имени схемы (`"PUBLIC"` в кавычках не находит фактическую схему `public`, созданную Liquibase без кавычек). Решение: переименовать физическую колонку в `issue_key`, оставив Java-контракт (`IssueEntity.getKey()`, `IssueUpsertCommand.key()`) без изменений — matching-логика (по `jiraId`, не по `key`) не затронута, это чисто SQL-уровневая деталь. `docs/database.md` обновлён.
- Остальное реализовано **без отклонений** от согласованного дизайна (см. предыдущую запись): владение контрактами, направление зависимости, matching по `jiraId`, постраничный upsert, состав `JiraSyncResult`, `@ElementCollection` для `fixVersions`/`labels`, отложенные `sprints`/`sync_state`.

**Decisions:**

- Архитектура не менялась — реализация строго в рамках дизайна, согласованного и задокументированного в предыдущей сессии (в рамках ADR-001/003/011). Новый ADR не создавался.
- `issue_key` как физическое имя колонки — реализационная деталь (не бизнес-решение), задокументирована как отклонение в `database.md` и здесь, а не молча.

**Docs touched:**

- `docs/database.md` (физическая колонка `issue_key`, статус таблицы `issues` → «реализовано»)
- `docs/architecture.md` (статус `domain.issue` → «реализовано»)
- `docs/ai_context.md` (стадия/версия 2.5, Phase 2.3 done, Phase 2.4 next)
- `docs/session_log.md` (this entry), `docs/changelog.md`

**Code touched (new):**

- `backend/src/main/java/.../domain/issue/{IssueEntity,IssueRepository,IssueUpsertCommand,IssueUpsertOutcome,IssuePersistencePort,IssueUpsertService,package-info}.java`
- `backend/src/main/resources/db/changelog/changes/0002-issues.yaml`
- `backend/src/test/java/.../domain/issue/{IssueUpsertServiceTest,IssueUpsertServiceIntegrationTest}.java`

**Code touched (modified):**

- `backend/src/main/java/.../sync/jira/{JiraIssueSnapshot,JiraSyncService,JiraSyncResult,package-info}.java`
- `backend/src/main/resources/db/changelog/db.changelog-master.yaml` (include `0002-issues.yaml`)
- `backend/src/test/java/.../sync/jira/JiraSyncServiceTest.java` (переписан под постраничную персистентность)

**Next:**

1. Phase 2.4 «REST API» — `POST /api/admin/sync/jira`, `GET /api/issues` (read-only поверх `domain.issue`, не live Jira), по [roadmap.md](./roadmap.md). Не начинать без явного go-ahead.
2. Получить реальный `JIRA_TOKEN`, прогнать `JiraSmokeTest` и живой sync (`jira.mode=rest`) — тот же открытый `TODO`, что и в предыдущих записях.

---



## 2026-07-15 — Phase 2.3 Persistence design approved

**Stage:** Phase 2.3 «Persistence» ([roadmap.md](./roadmap.md)) — **дизайн согласован, реализация не начата**. Код, миграции, entities, repositories в этой сессии **не создавались** — сознательное ограничение задачи (зафиксировать design decisions до кода).

**Summary:**
Перед стартом кода Phase 2.3 проведён анализ текущего состояния (`db.changelog-master.yaml` — только baseline `tagDatabase`, реальных таблиц нет; `pom.xml` — JPA/PostgreSQL/Liquibase уже подключены, но не использованы; `sync.jira.JiraIssueSnapshot`/`JiraSyncService`/`JiraSyncResult` — текущий seam, собирающий все issues в памяти перед возвратом) и `database.md`/`architecture.md`/ADR-001/ADR-003/ADR-011. По итогам согласован минимальный дизайн persistence-слоя:

1. **Владение контрактами и направление зависимости.** Новый пакет `domain.issue` — единственный владелец своих persistence-контрактов: `IssueEntity`, `IssueRepository`, `IssuePersistencePort`, `IssueUpsertCommand` (входной контракт), `IssueUpsertOutcome`, `IssueUpsertService`. Зависимость строго `sync.jira → domain.issue`; `domain.issue` **не** зависит от `sync.jira` — не импортирует `JiraIssueSnapshot`. `sync.jira` сам маппит `JiraIssueSnapshot → IssueUpsertCommand` перед вызовом `IssuePersistencePort`. Это соответствует Dependency Rule (внутренние слои не знают о внешних) и было явно пересмотрено в процессе обсуждения: первый вариант (`IssuePersistencePort` объявлен в `sync.jira`, `domain.issue` его реализует) создавал обратную зависимость `domain.issue → sync.jira` через тип параметра порта — отклонён.
2. `JiraIssueSnapshot` **остаётся контрактом** `sync.jira` (нормализованный вид Jira issue из integration-слоя), не переиспользуется persistence-слоем напрямую. Изменения контракта: `jiraId` (Jira internal id) добавлен, `key` остаётся бизнес-якорем (ADR-001), `created`/`updated` — `Instant` вместо `String`, `assigneeUsername`/`assigneeDisplayName` вместо единого `assigneeName`.
3. **Matching при upsert — по** `jiraId`, не по `key`: `jiraId` иммутабелен, `key` может измениться при переносе issue между Jira-проектами. `key` остаётся уникальным индексом и бизнес-якорем для будущих join (GitLab/Jenkins), но не используется для поиска существующей строки.
4. **Upsert — постранично**, не `saveAll` после полного сбора списка: `JiraSyncService` вызывает `IssuePersistencePort.upsertPage(...)` сразу на каждой странице (`findAllByJiraIdIn` → merge/create → `saveAll` на уровне страницы), без накопления полного списка issues в памяти. Побочный эффект, зафиксированный явно: при сбое Jira на середине прогона уже обработанные страницы остаются сохранёнными в БД (partial persisted result), а не теряются, как было бы при полном накоплении в памяти. Retry не добавляется (fail-fast сохраняется).
5. `JiraSyncResult` больше не хранит `List<JiraIssueSnapshot> issues` — только агрегаты: `created`, `updated` (+ существующие `startedAt`/`finishedAt`/`fetched`/`pages`/`mocked`/`errors`). `saved()` — derived-метод (`created + updated`), не отдельное хранимое поле, чтобы не держать состояние, которое может разойтись с суммой.
6. `fixVersions`**/**`labels` сохраняются через `@ElementCollection` (`issue_fix_versions`/`issue_labels`) — множественность не теряется; `labels` решено сохранять симметрично `fixVersions`, а не отбрасывать, так как данные уже приходят из Jira и стоимость сохранения мала.
7. `sprints`**/**`sync_state` **отложены** — не заводятся в Phase 2.3. Причина: board 718 (Kanban) не даёт sprint-данных сейчас, incremental sync/watermark не реализован — заводить таблицы без писателя в них признано избыточным. Возвращаемся к ним отдельной задачей вместе с sprint metadata / incremental sync / scheduler / sync history.

**Decisions:**

- Все решения выше — в рамках уже принятых ADR-001 (issue key как якорь), ADR-003 (modular monolith, `domain.*` пакеты), ADR-011 (Markdown source of truth); **новый ADR не создавался** — архитектура не менялась, только реализационный дизайн внутри уже принятых границ.
- `IssueRepository` (Spring Data JPA) размещается прямо в `domain.issue`, без выделения отдельного infrastructure-модуля — прагматичное упрощение для modular monolith (ADR-003), а не строгая гексагональная архитектура с отдельным слоем адаптеров.
- `synced_at` (таймстамп последнего upsert) — небольшое добавление к минимальному дизайну сверх исходного запроса, задел на будущий staleness-репорт без введения `sync_state` уже сейчас; при реализации может быть пересмотрено.

**Docs touched:**

- `docs/decisions.md` (Design notes — новая строка «Phase 2.3 Persistence — дизайн согласован»)
- `docs/database.md` (реальные таблицы `issues`/`issue_fix_versions`/`issue_labels` для Phase 2.3; `sprints`/`sync_state` помечены Planned/future с причиной; добавлена заметка про matching key)
- `docs/architecture.md` (детализация `domain.issue`/`sync.jira` в таблице пакетов; новый раздел «Package dependency direction»: `integration.jira → sync.jira → domain.issue`)
- `docs/session_log.md` (this entry)
- `docs/changelog.md`

**Next:**

1. Реализация Phase 2.3 по согласованному дизайну: `sync.jira.JiraIssueSnapshot` (правки полей), новый пакет `domain.issue` (`IssueEntity`/`IssueRepository`/`IssuePersistencePort`/`IssueUpsertCommand`/`IssueUpsertOutcome`/`IssueUpsertService`), изменения `JiraSyncService`/`JiraSyncResult`, Liquibase changeset `0002-issues.yaml` (`issues`, `issue_fix_versions`, `issue_labels`), тесты. Не начинать без явного go-ahead.
2. После реализации — обновить `database.md`/`architecture.md` фактическими деталями (если разойдутся с дизайном) и добавить запись в `session_log.md`/`changelog.md` по факту.
3. Получить реальный `JIRA_TOKEN` — остаётся тем же открытым `TODO`, что и в предыдущих записях.

---



## 2026-07-15 — Phase 2.2 (Jira Sync) done: application-level sync layer over JiraContextProvider

**Stage:** Phase 2.2 «Jira Sync» ([roadmap.md](./roadmap.md)) — реализован **первый application-level слой** поверх `JiraContextProvider`. Сознательно **только** оркестрация: **без** REST controller, persistence (JPA/Liquibase/repository/`sync_state`), scheduler, Spring Security, incremental sync, GitLab/Jenkins (явные ограничения задачи). `JiraClient`/`JiraContextProvider` и их DTO **не менялись**.

**Summary:**
Новый пакет `ru.eltc.deliverymonitor.sync.jira` — отдельный application layer над integration layer (`integration.jira` остаётся только HTTP-клиент + auth + wire DTO + provider):

1. `JiraSyncService` (`@Service`) — `JiraSyncResult syncBoard()`: листает **все** страницы board через `JiraContextProvider.getBoardContext(startAt, maxResults)` (не через `JiraClient` напрямую), продвигается по фактически возвращённому числу issues (устойчиво к server-side cap на `maxResults`), с защитным лимитом `MAX_PAGES=10000` от бесконечного цикла. Нормализует каждый `JiraIssueDto` в `JiraIssueSnapshot`. `JiraClientException` **не пробрасывается сырым** — пишется в `errors`, прогон возвращает уже собранное (поведение будущего endpoint/scheduler).
2. `JiraIssueSnapshot` (record) — внутренний нормализованный контракт sync-слоя, **seam к будущему persistence**: `key, summary, statusName, statusCategory, assigneeName, issueType, fixVersions, labels, updated`. Минимальный набор (task 2.4), ничего «на будущее». Изолирует верхние слои/будущую БД от структуры Jira REST API. `fixVersions`/`labels` — никогда не `null`.
3. `JiraSyncResult` (record) — честный отчёт прогона: `startedAt, finishedAt, fetched, pages, mocked, errors, issues`. Поле `saved` **не добавлено** — persistence ещё нет, результат не имитирует сохранение. Форма согласована с `api.md` sketch (`startedAt`/`finishedAt`/`fetched`/`errors`) для переиспользования будущими endpoint (2.4)/scheduler (2.5)/логированием.
4. `JiraSyncProperties` (`@ConfigurationProperties("jira.sync")`, `@Validated`) — `page-size` (default 50, `@Min(1)`). Отдельно от `jira.*` (integration), т.к. это операционная настройка sync-слоя; захардкожена не была. `application.yml`: `jira.sync.page-size: ${JIRA_SYNC_PAGE_SIZE:50}`.

`.\mvnw.cmd clean verify` (JDK 21, Android Studio JBR; системный JDK 17) — **39 тестов, 0 failures, 0 errors, 2 skipped** (оба — `JiraSmokeTest`, без токена) → `BUILD SUCCESS`. 7 новых unit-тестов `JiraSyncServiceTest` — через **fake** `JiraContextProvider` (без реального Jira, без HTTP, без Spring): пагинация по нескольким страницам + порядок, нормализация полей → snapshot, пустая board, unassigned/`null`-fields, проброс `mocked`, `JiraClientException` → `errors` (полный сбой на первой странице и частичный сбой с сохранением уже полученного). Полный context-тест (`DeliveryMonitorApplicationTests`) зелёный — подтверждает wiring `JiraSyncService` с default `RestJiraContextProvider` + `JiraSyncProperties`.

**Decisions:**

- **Пакет** `sync.jira` **(top-level, сосед** `integration`**)**, а не `integration.jira.sync` — sync это application-оркестрация, roadmap выделяет Sync в отдельную фазу; `integration.jira` держим чистым (integration layer). Отражено в `architecture.md` (Backend packages). Новый ADR **не создавался** (в рамках принятых ADR-001/003/004/005/007/011, как и при вводе provider/auth).
- `JiraIssueSnapshot` **как отдельный DTO**, а не переиспользование `JiraIssueDto` — верхние слои и будущая БД не должны зависеть от структуры внешнего Jira API; изменение Jira API не должно ломать схему.
- **Без поля** `saved` — persistence не существует (Phase 2.3); не имитируем то, чего нет.
- `page-size` **в конфиге** (`jira.sync.page-size`) — операционная настройка Jira, не хардкод; вынесена в отдельный `JiraSyncProperties`, чтобы не нарушать инвариант `JiraProperties` («только client/auth, без sync-настроек»).
- `JiraClientException` **→** `errors`**, не пробрасывается** — прогон устойчив к сбоям и возвращает partial-результат, как потребуется endpoint/scheduler.
- **Done-when по факту** (авторизованный прогон против реального Jira) остаётся тем же открытым `TODO`, что у 2.2/2.3 — реальный `JIRA_TOKEN` недоступен; офлайн проверено через fake provider и `jira.mode=mock`.

**Docs touched:**

- `docs/session_log.md` (this entry), `docs/changelog.md`, `docs/architecture.md` (пакет `sync.jira`), `docs/ai_context.md` (стадия/версия 2.4)

**Code touched (new):**

- `backend/src/main/java/.../sync/jira/{JiraSyncService,JiraIssueSnapshot,JiraSyncResult,JiraSyncProperties,package-info}.java`
- `backend/src/test/java/.../sync/jira/JiraSyncServiceTest.java`

**Code touched (modified):**

- `backend/src/main/resources/application.yml` (`jira.sync.page-size`)

**Next:**

1. Phase 2.3 «Persistence» — Liquibase (`sprints`/`issues`/`sync_state`), маппинг `JiraIssueSnapshot` → JPA entity, upsert; затем endpoint `POST /api/admin/sync/jira` (2.4) поверх `JiraSyncService`. Не начинать без явного go-ahead.
2. Получить реальный `JIRA_TOKEN`, прогнать `JiraSmokeTest` и живой sync (`jira.mode=rest`); офлайн — `jira.mode=mock`.

---



## 2026-07-15 — Task 2.3 (Jira board context) done: JiraContextProvider + config-switchable mock

**Stage:** Phase 2 detail task **2.3 — Получение контекста board** ([roadmap.md](./roadmap.md), «Phase 2 — детализация»; группируется под Phase «2.2 Jira Sync»). Реализован шов получения контекста board **без** зависимости от реального Jira-аккаунта.

**Summary:**
Добавлен доменно-осмысленный слой над HTTP-клиентом Phase 2.1 и офлайн-режим разработки. **Без** persistence, domain-сущностей, scheduler, REST API, UI (явные ограничения задачи). Новый пакет `integration.jira.provider`:

1. `JiraContextProvider` — интерфейс: `JiraBoardContext getBoardContext(int startAt, int maxResults)`. Верхние слои (будущий sync — Phase 2.2/2.4) зависят от него, а **не** от `JiraClient`.
2. `JiraBoardContext` — record-результат: `boardId`, `filterId`, `startAt`, `maxResults`, `total`, `List<JiraIssueDto> issues`, `fetchedAt`, `mocked`. Переиспользует существующий `JiraIssueDto` (одна форма данных для обоих источников).
3. `RestJiraContextProvider` (default, `@ConditionalOnProperty jira.mode=rest`, `matchIfMissing=true`) — обёртка над `JiraClient.searchByFilter(defaultFilterId, …)`; адаптирует реактивный вызов в синхронный контракт с ограниченным `block`-таймаутом (reactive-типы не «протекают» выше клиента).
4. `MockJiraContextProvider` (`@ConditionalOnProperty jira.mode=mock`) — отдаёт **санитизированные demo-данные** из `classpath:jira/mock/board-718-filter-30532.json`. Fixture явно помечен `_comment` как demo/sanitized (fake-пользователи `demo.`*, статусы из публичного column-config). **Защита от production:** конструктор кидает `IllegalStateException`, если активен профиль `prod`/`production` — mock физически не может уехать в прод, даже если свойство протечёт.
5. Переключение источника — **только конфиг** `jira.mode=rest|mock` (default `rest`), профиль `jira-mock` (`application-jira-mock.yml`) поднимает mock офлайн + placeholder token для fail-fast валидации. Переход на реальную Jira при появлении сервисного аккаунта = задать `JIRA_TOKEN` и оставить `rest`; кода менять не нужно.

`JiraClient` (Phase 2.1) **не менялся**. `JiraProperties` — минимально расширены (`mode`, `boardId`), обратносовместимо; валидация не тронута. `.\mvnw.cmd clean verify` (JDK 21, Android Studio JBR) — **32 теста, 0 failures, 0 errors, 2 skipped** (оба — `JiraSmokeTest`, без токена) → `BUILD SUCCESS`. 7 новых тестов: rest-провайдер через `mockwebserver3` (filter query + маппинг + проброс `JiraClientException`), mock-провайдер (demo-данные, постранично, prod-guard), выбор бина по `jira.mode` (`ApplicationContextRunner`).

**Decisions:**

- **Нужен интерфейс** `JiraContextProvider` — это шов, дающий (а) офлайн-разработку без аккаунта и (б) переход на реальную Jira без переписывания (downstream зависит от интерфейса). Мокать сам `JiraClient` хуже — он конкретный HTTP-класс из 2.1; провайдер оставляет его чистым.
- **Mock в** `src/main`**, а не в** `src/test` — цель офлайн-режима не только тесты, а развитие следующих слоёв и запуск приложения без Jira; источник переключается конфигом. Компенсировано: fixture помечен demo/sanitized, prod-guard в конструкторе, warning-лог при старте.
- **Live board configuration / активные sprints — вне scope 2.3** (отдельная будущая задача): доска Kanban (sprint lifecycle может отсутствовать), column/swimlane-конфиг уже статически в `discovery.md` §9.1; добавление Jira Agile API + новых DTO сейчас без ценности. Интерфейс оставляет extension point.
- **Новый ADR не создавался** — решение в рамках уже принятых ADR-001/003/004/005/006/007/011 (аналогично тому, как auth-strategy в 2.1 вводилась без ADR). Зафиксировано в `docs/decisions.md` (Design notes), этой записи и changelog.
- **Done-when (filter 30532 → issues) — структурно** подтверждён (rest-провайдер против mock HTTP-сервера + realistic demo-данные для downstream). Реальный авторизованный прогон против аккаунта остаётся тем же открытым `TODO`, что и у task 2.2 — не имитируется.

**Docs touched:**

- `docs/session_log.md` (this entry), `docs/changelog.md`, `docs/decisions.md` (Design notes), `docs/discovery.md` (§1 — заметка про офлайн `jira.mode=mock`), `docs/ai_context.md` (стадия), `backend/README.md` (provider + `jira.mode` + package layout)

**Code touched (new):**

- `backend/src/main/java/.../integration/jira/provider/{JiraContextProvider,JiraBoardContext,RestJiraContextProvider,MockJiraContextProvider}.java`
- `backend/src/main/resources/jira/mock/board-718-filter-30532.json` (sanitized demo fixture)
- `backend/src/main/resources/application-jira-mock.yml` (offline profile)
- `backend/src/test/java/.../integration/jira/provider/{RestJiraContextProviderTest,MockJiraContextProviderTest,JiraContextProviderSelectionTest}.java`

**Code touched (modified):**

- `backend/src/main/java/.../integration/jira/config/JiraProperties.java` (+ `mode`, `boardId`; enum `Mode`)
- `backend/src/main/resources/application.yml` (`jira.mode`, `jira.board-id`)
- `backend/src/main/java/.../integration/jira/package-info.java` (описан `provider`)

**Next:**

1. Phase 2.2 «Jira Sync» — оркестрация `POST /api/admin/sync/jira` поверх `JiraContextProvider` (task 2.4 «Получение задач» → затем persistence, task 2.5). Не начинать без явного go-ahead.
2. Получить реальный `JIRA_TOKEN`, прогнать `JiraSmokeTest` (закрыть done-when 2.2/2.3 фактически); при работе офлайн использовать `jira.mode=mock`.

---



## 2026-07-15 — Task 2.2 (Auth) check: JiraSmokeTest + env wiring verified, real token still TODO

**Stage:** Phase 2 detail task **2.2 Auth** ([roadmap.md](./roadmap.md), «Phase 2 — детализация»); Phase 2.1 (Jira Client) уже done, следующий шаг по roadmap — Phase 2.2 (Jira Sync), но перед этим явно запрошена точечная проверка task 2.2 (Auth)

**Summary:**
По запросу выполнена целевая проверка готовности аутентификации Jira Server 8.20.30 — **без нового кода** (sync, БД, entity, repository, scheduler, REST API сознательно не добавлялись, это явное ограничение задачи):

1. `JiraSmokeTest` **проверен** (`backend/src/test/java/.../integration/jira/JiraSmokeTest.java`, создан в предыдущей gate-check сессии 2026-07-14) — подтверждено, что он покрывает ровно то, что нужно task 2.2: `GET /rest/api/2/myself` и `GET /rest/api/2/search` (default filter из `JIRA_DEFAULT_FILTER_ID`).
2. **Подтверждено использование production** `JiraClient` — тест собирает клиент через те же бины, что и приложение (`JiraClientConfig#jiraAuthenticationStrategy`, `JiraClientConfig#jiraWebClient`), никакой отдельной test-only реализации auth/HTTP нет.
3. **Проверена конфигурация env variables** в `backend/src/main/resources/application.yml` — все пять переменных из задачи подтверждены как реально читаемые Spring/тестом: `JIRA_BASE_URL`, `JIRA_AUTH_TYPE`, `JIRA_USERNAME`, `JIRA_TOKEN`, `JIRA_DEFAULT_FILTER_ID` (плюс уже существующие `JIRA_CONNECT_TIMEOUT`/`JIRA_RESPONSE_TIMEOUT`/`JIRA_PROJECT_KEYS`, вне scope задачи, но не мешают).
4. **Прогнан** `.\mvnw.cmd clean verify` (JDK 21 через Android Studio JBR — `C:\Program Files\Android\Android Studio\jbr`; системный JDK в этой среде 17, как и в предыдущей сессии) — **25 тестов, 0 failures, 0 errors, 2 skipped** (оба — методы `JiraSmokeTest`, корректно пропущены из-за отсутствия `JIRA_TOKEN`, `@EnabledIfEnvironmentVariable`) → `BUILD SUCCESS`.
5. **Реальный** `JIRA_TOKEN` **в этой сессии недоступен** (проверено: не задан в env процесса, нет `.env`-файлов с реальными секретами в репозитории — только `docker/.env.example`). Авторизованный прогон (`/myself` → `200`, реальный ответ по filter 30532) **не выполнен** — вместо имитации результата оставлен явный `TODO`.

Подготовлена и задокументирована инструкция запуска smoke test с реальным токеном (PowerShell) — см. `docs/discovery.md` §1 «Task 2.2 (Auth) check» (та же инструкция уже была в Javadoc `JiraSmokeTest`, теперь она также продублирована в docs для видимости без чтения кода).

**Decisions:**

- Никакой новый код не написан: аудит подтвердил, что существующая реализация Phase 2.1 (auth strategy + env config + smoke test) уже полностью закрывает task 2.2 **с точки зрения кода**; отсутствует только реальный прогон с валидными credentials.
- Явно не тронуты sync/persistence/REST API/scheduler — по прямому ограничению задачи; переход к Phase 2.3 (или к Phase 2.2 «Jira Sync» по overview-таблице roadmap) не выполняется.
- Не выдумано и не имитировано успешное прохождение auth без реального токена — оставлен честный `TODO`.
- Архитектура и ADR не менялись.

**Docs touched:**

- `docs/discovery.md` (§1 — обновлён статус TODO "PAT vs basic auth", новая секция "Task 2.2 (Auth) check — 2026-07-15" с чеклистом проверки и инструкцией запуска)
- `docs/session_log.md` (this entry)
- `docs/changelog.md`

**Next:**

1. Получить реальный `JIRA_TOKEN` (PAT или Basic) от Jira-админа; прогнать `JiraSmokeTest` по инструкции в `docs/discovery.md` §1 — закрыть task 2.2 done-when (`/myself` → `200`).
2. Только после этого — Phase 2.2 «Jira Sync» (`POST /api/admin/sync/jira`, tasks 2.3+2.4 по [roadmap.md](./roadmap.md)). Не начинать без явного go-ahead (по просьбе в этой сессии — не переходить к Phase 2.3).

---



## 2026-07-15 — Security principles doc (docs-only)

**Stage:** Документация; вне кодовых Phase 2.x ([roadmap.md](./roadmap.md))

**Summary:**
Добавлен новый документ [security.md](./security.md), фиксирующий **архитектурные принципы безопасности** проекта. Только документация — код не писался, Spring Security не добавлялся, архитектура не менялась. Разделы: (1) Security Goals — что защищаем (в первую очередь service-account credentials, спроецированные данные, доступ к dashboard) + модель угроз; (2) Authentication — только корпоративный SSO/LDAP/OIDC, локальных пользователей нет; (3) Authorization — роли Admin/PM/QA/Developer + least privilege (по умолчанию read-only, admin-операции только Admin); (4) Service Accounts — отдельные read-only аккаунты Jira/GitLab/Jenkins с минимальными правами (Jira read, GitLab `read_api`/`read_repository`, Jenkins read jobs/builds); (5) Secrets — только environment variables, никаких секретов в Git и логах, fail-fast при отсутствии обязательного секрета; (6) Network — внутренний сервис за VPN, HTTPS/TLS, whitelisted webhooks; (7) Audit — какие действия логируются (привилегированные операции, изменения ролей/конфига, отказы доступа, sync); (8) Logging — запрет логировать токены и `Authorization` header; (9) Security Checklist. Документ опирается на уже принятые решения ([architecture.md](./architecture.md), [integrations.md](./integrations.md), [discovery.md](./discovery.md) §7/§9.5, [ai_context.md](./ai_context.md) §7) и не вводит новых.

**Decisions:**

- Документ — только принципы, не guide по реализации; конкретный auth-провайдер/библиотека умышленно не фиксируются (выбор при развёртывании).
- Spring Security **не** добавляется на данном этапе (явное требование задачи).
- Архитектура не менялась → **новый ADR не создавался** (в рамках уже принятых ADR-003/007/011 и правил `ai_context.md`).
- Роли и их набор трактуются как конфигурация, а не доменная модель — изменение списка ролей не требует нового ADR.

**Docs touched:**

- `docs/security.md` (new)
- `docs/README.md` (index + таблица «что обновлять» — строка про безопасность)
- `docs/session_log.md` (this entry)
- `docs/changelog.md`

**Next:**

1. Продолжить по [roadmap.md](./roadmap.md): получить реальный `JIRA_TOKEN` и прогнать `JiraSmokeTest`, затем Phase 2.2 (Jira Sync) — без изменений относительно предыдущей записи.
2. При старте реализации auth/ролей сверяться с `security.md` (checklist §9).

---



## 2026-07-14 — Phase 2.1 gate-check + hardening (before Phase 2.2)

**Stage:** Phase 2.1 — Jira REST Client, gate-check перед Phase 2.2 ([roadmap.md](./roadmap.md))

**Summary:**
После code review Phase 2.1 выполнен ограниченный gate-check и точечное укрепление `integration.jira`, **без** новых sync/persistence/scheduler/domain-сущностей (сознательно вне scope):

1. **Fail-fast валидация** `JiraProperties` — добавлен `spring-boot-starter-validation`; класс помечен `@Validated`, `baseUrl` (`@NotBlank`), `auth` (`@NotNull @Valid`), `auth.token` (`@NotBlank`), `auth.type` (`@NotNull`), и cross-field проверка `@AssertTrue`: при `auth.type=basic` `username` не может быть пустым. Теперь отсутствующий/пустой `JIRA_TOKEN` **не даёт приложению запуститься** (раньше это всплыло бы только при первом реальном вызове Jira). Тесты (`JiraPropertiesTest`, `JiraClientConfigTest`) проверяют и валидные, и невалидные конфигурации — `ApplicationContextRunner` + `assertThat(context).hasFailed()`. Побочный эффект: `DeliveryMonitorApplicationTests` (полный контекст) и часть существующих тестов теперь явно передают тестовый (не секретный) `jira.auth.token`, иначе они тоже не стартуют — это осознанное и ожидаемое поведение fail-fast.
2. **Единый тип ошибки в** `JiraClient` — все ошибки взаимодействия с Jira (HTTP-статус **и** транспортные: timeout, connection refused, DNS failure, прочие network errors) теперь оборачиваются в `JiraClientException`; публичный API `JiraClient` (`getMyself/search/searchByFilter`) не изменился. Добавлен `JiraClientException.NO_HTTP_STATUS = 0` — sentinel-статус для случаев без HTTP-ответа. Тесты: connection refused / DNS / generic network error через синтетический `ExchangeFunction` (детерминированно, без реальной сети), response timeout — через реальный `MockWebServer` с очень коротким таймаутом (без ответа).
3. `ExchangeStrategies` **на** `jiraWebClient` — `maxInMemorySize` увеличен с дефолтных Spring 256 KB до **10 MB** (внутренний Jira Server с известным, ограниченным объёмом данных — не публичный multi-tenant API; запас на будущее расширение полей типа `changelog`/`links`). Обоснование — в комментарии кода. Тест на `JiraClientConfigTest` гоняет через `MockWebServer` тело ответа ~400 KB (больше дефолтного лимита, меньше нового) и проверяет успешный парсинг.
4. **Временный** `JiraSmokeTest` (`backend/src/test/java/.../integration/jira/JiraSmokeTest.java`) — не входит в обычный `mvnw verify` (`@EnabledIfEnvironmentVariable(JIRA_TOKEN)`, по умолчанию skipped), гоняет **тот же** production-код (`JiraClientConfig`/`JiraClient`), конфиг — из env vars, против настоящего `https://jira.eltc.ru`. Реальных credentials в этой сессии не было — авторизованный прогон (`myself`/`search filter=30532` с валидным токеном) остаётся `[TODO]`.
5. **Частичный gate-check без credentials** (см. `docs/discovery.md` §1 «Gate-check Phase 2.1 → Phase 2.2»): сетевая доступность `jira.eltc.ru` подтверждена, `GET /rest/api/2/serverInfo` (публичный, без auth) подтвердил версию **8.20.30**; `GET /rest/api/2/myself` без валидных credentials (через настоящий `JiraClient`) вернул `401` без тела, `GET /rest/api/2/search?jql=filter=30532` — `400` со стандартным Jira-сообщением о недостатке прав/несуществующем фильтре для анонимного доступа. Оба ответа корректно обернулись в `JiraClientException` тем же кодом, что уже покрыт unit-тестами на mock-сервере — расхождений с реальным сервером не найдено. Открытый `TODO`: авторизованный прогон с реальным PAT/Basic токеном от Jira-админа.

Все backend-тесты зелёные: `.\mvnw.cmd clean verify` (JDK 21 через Android Studio JBR — системный JDK в этой среде 17).

**Decisions:**

- Валидация — только на `baseUrl` и `auth` (как явно запрошено), без расширения на остальные поля (`connectTimeout`, `projectKeys`, …) — не входило в scope этой задачи.
- `JiraClientException.NO_HTTP_STATUS = 0` как единый sentinel вместо отдельного under/over-типа ошибки — сохраняет «один тип ошибки» контракт, который важен для будущей Phase 2.2 (`{ fetched, saved, errors: [] }`).
- `maxInMemorySize = 10 MB` — фиксированное значение, не конфигурируемое через `application.yml`, т.к. это внутренний технический лимит клиента, а не операционная настройка команды.
- `JiraSmokeTest` оставлен как файл в репозитории (не удалён после прогона) — отключён по умолчанию, готов к использованию, когда появится реальный `JIRA_TOKEN`; решение об удалении — за командой после того, как gate-check будет пройден с реальными credentials.
- Архитектура не менялась; новый ADR не создавался (все правки — в рамках уже принятых ADR-003/007).

**Docs touched:**

- `docs/discovery.md` (§1 — статус "PAT vs basic auth", новая секция "Gate-check Phase 2.1 → Phase 2.2")
- `docs/session_log.md` (this entry)

**Code touched (new):**

- `backend/src/test/java/ru/eltc/deliverymonitor/integration/jira/JiraSmokeTest.java`

**Code touched (modified):**

- `backend/pom.xml` (+ `spring-boot-starter-validation`)
- `backend/src/main/java/ru/eltc/deliverymonitor/integration/jira/config/JiraProperties.java` (`@Validated` + Bean Validation)
- `backend/src/main/java/ru/eltc/deliverymonitor/integration/jira/config/JiraClientConfig.java` (`ExchangeStrategies`/`maxInMemorySize`)
- `backend/src/main/java/ru/eltc/deliverymonitor/integration/jira/client/JiraClient.java` (унификация transport-level ошибок)
- `backend/src/main/java/ru/eltc/deliverymonitor/integration/jira/exception/JiraClientException.java` (`NO_HTTP_STATUS`)
- `backend/src/test/java/ru/eltc/deliverymonitor/integration/jira/client/JiraClientTest.java`, `.../config/JiraClientConfigTest.java`, `.../config/JiraPropertiesTest.java`
- `backend/src/test/java/ru/eltc/deliverymonitor/DeliveryMonitorApplicationTests.java` (placeholder `jira.auth.token` для полного контекста)

**Next:**

1. Получить реальный `JIRA_TOKEN` (PAT или Basic) от Jira-админа и прогнать `JiraSmokeTest` — закрыть `[TODO]` "PAT vs basic auth" в `docs/discovery.md` §1.
2. Только после этого — Phase 2.2 (Jira Sync, `POST /api/admin/sync/jira`) по [roadmap.md](./roadmap.md).
3. Не начинать 2.2 без явного go-ahead.

---



## 2026-07-14 — Phase 2.1 (Jira Client) done: Jira REST client + auth

**Stage:** Phase 2.1 — Jira REST Client ([roadmap.md](./roadmap.md))

**Summary:**
Реализован **только** HTTP-клиент Jira Server 8.x поверх Spring `WebClient` — без sync, без БД, без REST API, без scheduler, без GitLab/Jenkins (по явному scope задачи). Новый пакет `ru.eltc.deliverymonitor.integration.jira`:

- `config/JiraProperties` — `@ConfigurationProperties(prefix = "jira")`: `baseUrl`, `connectTimeout`, `responseTimeout`, `projectKeys`, `defaultFilterId`, `auth.{type,username,token}`. Значения — из `application.yml` с env-переопределением (`JIRA_BASE_URL`, `JIRA_AUTH_TYPE`, `JIRA_USERNAME`, `JIRA_TOKEN`, …); секреты по умолчанию пустые, в Git не попадают.
- `config/JiraClientConfig` — бин `WebClient` (base URL + timeouts из properties, `Accept: application/json`) и бин `JiraAuthenticationStrategy`, выбираемый по `jira.auth.type`.
- `auth/JiraAuthenticationStrategy` (+ `BasicAuthenticationStrategy`, `BearerTokenAuthenticationStrategy`) — Basic auth или PAT (`Authorization: Bearer`); выбор auth-схемы на Jira Server 8.20.30 остаётся `[TODO]` в discovery.md, поэтому обе реализованы и переключаются конфигом.
- `client/JiraClient` — `getMyself()` (smoke `/rest/api/2/myself`), `search(jql, startAt, maxResults, fields)` и `searchByFilter(filterId, startAt, maxResults)` (`/rest/api/2/search`); ошибки → `JiraClientException` (HTTP-статус + `errorMessages` из тела ответа Jira).
- `dto/*` — record-DTO для ответов Jira REST API v2: `JiraMyselfDto`, `JiraSearchResultDto`, `JiraIssueDto`, `JiraIssueFieldsDto` (summary/status/assignee/fixVersions/labels), `JiraUserDto`, `JiraStatusDto` (+ `StatusCategory`), `JiraIssueTypeDto`, `JiraFixVersionDto`, `JiraErrorResponseDto`.
- `exception/JiraClientException`.

`pom.xml`: добавлен `spring-boot-starter-webflux` (только ради `WebClient`; т.к. `spring-boot-starter-web` уже на classpath, Spring Boot оставляет Servlet/MVC стек, реактивный сервер не включается) и тестовая зависимость `com.squareup.okhttp3:mockwebserver3-junit5:5.4.0`.

Тесты (12, все зелёные, `mvnw clean verify`): auth-стратегии (Basic/Bearer заголовки), биндинг `JiraProperties` (дефолты + override через properties/env-стиль ключей), выбор auth-стратегии в `JiraClientConfig` (`ApplicationContextRunner`), и `JiraClientTest` — полный цикл через mock HTTP-сервер (`mockwebserver3`): успешный `getMyself`/`search`/`searchByFilter` с парсингом DTO, проверка заголовка `Authorization`, и обработка ошибочных ответов (400 с телом, 401 без тела) → `JiraClientException`.

**Decisions:**

- `WebClient` вместо `RestTemplate` (сохраняет опцию неблокирующих вызовов позже; поддержана обоими вариантами roadmap — "RestTemplate / WebClient").
- Auth реализован как стратегия (Basic vs Bearer/PAT), переключаемая конфигом `jira.auth.type`, а не захардкожена — т.к. discovery.md §1 явно оставляет "PAT vs basic auth on 8.20.30" открытым `[TODO]`.
- DTO — Java records с `@JsonIgnoreProperties(ignoreUnknown = true)`: неизвестные поля Jira не ломают парсинг; включены только поля, реально нужные текущему и следующему шагу (summary/status/assignee/fixVersions/labels), без "на всякий случай" полей.
- Тестирование HTTP-слоя — через `mockwebserver3` (mock-сервер), не мокирование `WebClient` — тест реально проверяет сериализацию URL/заголовков/JSON.
- Единый метод `search(jql, ...)` + удобный `searchByFilter(filterId, ...)`, но **без** привязки к конкретному filter 30532/board 718 в коде клиента — эта логика (Phase 2.2/2.3) намеренно не добавлена.
- Явно не сделано (по scope задачи): sync-оркестрация, `POST /api/admin/sync/jira`, персистентность в PostgreSQL, `GET /api/issues`, `@Scheduled` поллинг, GitLab/Jenkins.

**Docs touched:**

- `docs/ai_context.md` (стадия/версия), `docs/session_log.md` (this entry), `docs/changelog.md`
- `backend/README.md` (секция "Jira REST client (Phase 2.1)", package layout, env vars)

**Code touched (new):**

- `backend/src/main/java/ru/eltc/deliverymonitor/integration/jira/config/{JiraProperties,JiraClientConfig}.java`
- `backend/src/main/java/ru/eltc/deliverymonitor/integration/jira/auth/{JiraAuthenticationStrategy,BasicAuthenticationStrategy,BearerTokenAuthenticationStrategy}.java`
- `backend/src/main/java/ru/eltc/deliverymonitor/integration/jira/client/JiraClient.java`
- `backend/src/main/java/ru/eltc/deliverymonitor/integration/jira/dto/*.java` (9 DTO)
- `backend/src/main/java/ru/eltc/deliverymonitor/integration/jira/exception/JiraClientException.java`
- `backend/src/test/java/ru/eltc/deliverymonitor/integration/jira/**` (auth/config/client tests)
- `backend/src/main/resources/application.yml` (секция `jira:`)
- `backend/pom.xml` (`spring-boot-starter-webflux`, `mockwebserver3-junit5`)

**Code touched (modified):**

- `backend/src/main/java/ru/eltc/deliverymonitor/integration/jira/package-info.java` (обновлён статус пакета)

**Next:**

1. Phase 2.2 — Jira Sync: `POST /api/admin/sync/jira` (ручной), использующий `JiraClient.searchByFilter` для board 718 / filter 30532; получение контекста board (2.3) и задач (2.4) по [roadmap.md](./roadmap.md).
2. Не переходить к 2.2 без явного go-ahead (по просьбе в этой сессии — остановиться после 2.1).
3. Когда появятся реальные Jira credentials — прогнать `getMyself()` вручную (`curl`/small script) для подтверждения `[TODO]` "PAT vs basic auth" в discovery.md §1, затем зафиксировать выбор в `jira.auth.type` по умолчанию.

---



## 2026-07-14 — Phase 1 (Skeleton) started: backend scaffold

**Stage:** Phase 1 — Skeleton ([roadmap.md](./roadmap.md))

**Summary:**
Discovery (Phase 0) завершён; начат Skeleton. Создан минимальный backend-каркас: Java 21, Spring Boot 3.5.16 (Maven, с wrapper — `mvnw`/`mvnw.cmd`, локальная сборка не требует установленного Maven), PostgreSQL (JDBC + Spring Data JPA, без сущностей), Liquibase (master changelog + один baseline-changeset `tagDatabase`, реальных таблиц нет), Actuator (`/actuator/health`, `/actuator/info`). Docker: `backend/Dockerfile` (multi-stage, `eclipse-temurin:21-jre-alpine` runtime) + `docker/docker-compose.yml` (postgres + backend) + `docker/.env.example`. Единственный подготовленный пакет — `integration.jira` (пустой `package-info.java`) как явная точка старта следующей задачи; GitLab/Jenkins и бизнес-сущности не создавались. Сборка и smoke-тест контекста (Spring context + Liquibase поднимаются, embedded H2 в PostgreSQL-режиме, без Docker) проверены локально через `mvnw clean verify` — успешно (JDK 21 из Android Studio JBR, т.к. системный JDK — 17). Реальный `docker compose up` не проверялся в этой среде (Docker не установлен) — рекомендуется проверить на стенде/CI.

**Decisions:**

- Версия Spring Boot: **3.5.16** (последний патч ветки 3.5 на дату сессии).
- Package root: `ru.eltc.deliverymonitor` (по домену `eltc.ru`), артефакт `delivery-monitor`.
- Liquibase master-changelog использует `include` на отдельные файлы в `db/changelog/changes/` — задел на будущие миграции (Jira-домен, Phase 2).
- Hibernate `ddl-auto: validate` — схему меняет только Liquibase.
- Тесты не требуют Docker (embedded H2 в PostgreSQL-режиме) — Testcontainers сознательно не добавлены на этом этапе, чтобы не тянуть Docker-зависимость в unit/context-тесты Skeleton.
- Архитектура не изменилась относительно принятых ADR (Spring Boot + PostgreSQL modular monolith, REST, без CQRS/брокеров) → новый ADR **не создавался**.

**Docs touched:**

- `docs/session_log.md` (this entry)
- `docs/changelog.md`
- `backend/README.md`, `docker/README.md` (обновлены с placeholder на инструкции)
- `.gitignore` (исключение для `.mvn/wrapper/maven-wrapper.jar`)

**Code touched (new):**

- `backend/pom.xml`, `backend/mvnw(.cmd)`, `backend/.mvn/wrapper/**`
- `backend/src/main/java/ru/eltc/deliverymonitor/DeliveryMonitorApplication.java`
- `backend/src/main/java/ru/eltc/deliverymonitor/integration/jira/package-info.java`
- `backend/src/main/resources/application.yml`
- `backend/src/main/resources/db/changelog/db.changelog-master.yaml`, `.../changes/0001-skeleton-baseline.yaml`
- `backend/src/test/java/ru/eltc/deliverymonitor/DeliveryMonitorApplicationTests.java`
- `backend/Dockerfile`, `backend/.dockerignore`
- `docker/docker-compose.yml`, `docker/.env.example`

**Next:**

1. Проверить `docker compose up --build` на машине с установленным Docker (health-check `/actuator/health` через compose).
2. Начать Phase 2 — Jira: клиент/поллер в `integration.jira`, конфиг из `docs/discovery.md` §9.1 (`MPTPSUPP`, board 718, filter 30532), первые Liquibase-changesets для `issues`/`sprints` ([database.md](./database.md)), события `JIRA_STATUS`/`JIRA_COMMENT` в `activity_events`.
3. Не уходить дальше границ Phase 2 без явного go-ahead.

---



## 2026-07-13 — Phase 0 (Discovery) started: discovery checklist

**Stage:** Phase 0 — Discovery ([roadmap.md](./roadmap.md))

**Summary:**  
Начат этап Discovery. Создан `docs/discovery.md` — checklist всей информации, необходимой для старта реализации. Разделы: Jira, GitLab, Jenkins, Oracle, Workstream Types, Naming Convention, Service Accounts, Open Questions. Для каждого пункта зафиксировано: что известно, что нужно узнать, почему важно, как проверить. Известные факты взяты из docs (`integrations.md`, `database.md`, ADR-001/002); недостающие данные помечены `TODO` (ничего не выдумывалось). Код приложения не пишем — Discovery в процессе.

**Decisions:**

- Discovery-документ = единый источник checklist для Phase 0; критичных данных пока нет (проектный ключ Jira, repo→type map, Jenkins jobs, реальный naming, доступы — все `TODO`).
- Статусы пунктов размечены как `[known]` / `[assumed]` / `[TODO]`.
- Oracle трактуется как Workstream Type поверх Git (без отдельного коннектора), согласно `integrations.md`.

**Docs touched:**

- `docs/discovery.md` (new)
- `docs/session_log.md` (this entry)

**Next:**

1. Собрать реальные данные по `TODO`: проектный ключ Jira + JQL, список GitLab проектов и repo→type map, список Jenkins jobs, naming convention команды.
2. Оформить и проверить service accounts / tokens + webhook whitelist.
3. Закрыть Open Questions; довести discovery.md до состояния без критичных `TODO`.
4. Не начинать Skeleton/код до завершения Discovery и явного go-ahead.

---



## 2026-07-10 — Repository bootstrap & ADR formalization

**Stage:** Pre-development / documentation

**Summary:**  
Репозиторий приведён к профессиональной структуре перед разработкой. Документация v2.1 сохранена и дополнена. ADR вынесены в `docs/adr/`. Добавлены AI context, session log, changelog, CONTRIBUTING_AI, корневой README, GitHub templates, `.gitignore`. Рабочий корень рекомендован: `C:\Users\repin.ea\Projects\AI-Engineering-Delivery-Monitor` (не Program Files).

**Decisions:**

- Source of Truth = Markdown в `docs/` ([ADR-011](./adr/0011-docs-markdown-source-of-truth.md)).
- Структура каталогов: `docs/`, `backend/`, `frontend/`, `docker/`, `scripts/`, `.github/`.
- Код приложения пока не пишем; следующий фокус — Discovery.
- ADR-001…0011 зафиксированы как Accepted (0009 — post-MVP).

**Docs touched:**

- `docs/ai_context.md` (new)
- `docs/session_log.md` (new)
- `docs/changelog.md` (new)
- `docs/adr/*` (new)
- `docs/decisions.md` (index only)
- `docs/README.md`, `docs/structure.md`
- `README.md`, `CONTRIBUTING_AI.md`, `CHANGELOG.md`
- `.gitignore`, `.github/**`
- существующие vision/architecture/… сверены и подчищены

**Next:**

1. Привязать локальную папку к GitHub remote (clone/push).
2. Этап Discovery: seed Workstream Types, repo→type map, Jenkins jobs, service accounts ([roadmap.md](./roadmap.md) phase 0).
3. Открыть Cursor workspace на `C:\Users\repin.ea\Projects\AI-Engineering-Delivery-Monitor`.
4. Не начинать Skeleton/код до завершения Discovery и явного go-ahead.

---



## 2026-07-09 — Architecture v2.1 (docs + Workstream Type)

**Stage:** Architecture design

**Summary:**  
Спроектирован Delivery Monitor: Spring Boot monolith, PostgreSQL, scheduler/webhooks, REST, Timeline-first UX, Release Health, Activity Feed. Отказ от CQRS/очередей/Redis/GraphQL в MVP. Введена абстракция Workstream Type. Документы разложены по `docs/*.md`. Добавлен `JIRA_COMMENT`. Команда: 7 dev + 2 QA.

**Decisions:** См. ADR-001…011 (формализованы 2026-07-10).

**Docs touched:** `vision`, `architecture`, `architecture-overview`, `database`, `api`, `integrations`, `ux`, `roadmap`, `glossary`, ранний `decisions`.

**Next (на тот момент):** bootstrap репозитория, ADR files, AI context — выполнено 2026-07-10.