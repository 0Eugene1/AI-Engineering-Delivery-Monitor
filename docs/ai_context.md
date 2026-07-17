# AI Context — точка входа для AI-агентов

| | |
|---|---|
| **Project** | AI Engineering Delivery Monitor |
| **Version** | 2.12 |
| **Stage** | Phase 2.1–2.5 done. Phase 3: **3.1–3.6 implemented** (GitLab → activity_events → workstreams; 182 tests). Next: **3.7** Read API (`timeline` + `workstream-types`). Then 3.8 admin sync HTTP, 3.9 scheduler |
| **Last updated** | 2026-07-17 |

> Прочитай этот файл **первым** в любом новом чате. Затем — [session_log.md](./session_log.md) и нужные документы из списка ниже.

---

## 1. Цель проекта

Внутренний веб-dashboard, который **автоматически** показывает реальное состояние разработки задач в спринте и готовность релиза, собирая события из:

- Jira Server 8.20.30 (`https://jira.eltc.ru`)
- GitLab
- Jenkins

Без ручного ввода статусов разработчиками.

Команда: **9 человек = 7 разработчиков + 2 QA**.

Подробнее: [vision.md](./vision.md).

---

## 2. Текущая стадия

| Стадия | Статус |
|---|---|
| Vision / architecture docs | Done (v2.1) |
| ADR formalization | Done |
| Repository bootstrap | Done |
| Discovery (repo maps, credentials) | Done (см. [discovery.md](./discovery.md); часть `[TODO]` — некритичные, донабираются по ходу) |
| Backend skeleton (Spring Boot + PostgreSQL + Liquibase + Actuator) | Done — см. [backend/README.md](../backend/README.md) |
| Frontend | **Not started** |
| Jira REST Client + auth (Phase 2.1) | Done — `integration.jira` (`config`/`auth`/`client`/`dto`/`exception`), Spring `WebClient`, Basic/Bearer(PAT) auth. См. [backend/README.md](../backend/README.md#jira-rest-client-phase-21) |
| Jira board context provider (Task 2.3) | Done — `integration.jira.provider`: `JiraContextProvider` + `Rest`/`Mock` реализации, переключение `jira.mode=rest\|mock` (офлайн-разработка без аккаунта, mock защищён от prod). См. [session_log.md](./session_log.md), [decisions.md](./decisions.md) (Design notes) |
| Jira Sync (Phase 2.2) — application layer | Done — пакет `sync.jira`: `JiraSyncService` (пагинация поверх `JiraContextProvider`), `JiraIssueSnapshot` (seam к persistence), `JiraSyncResult`, конфиг `jira.sync.page-size`. См. [session_log.md](./session_log.md) |
| Persistence (Phase 2.3) | Done — пакет `domain.issue`: `IssueEntity`/`IssueRepository`/`IssuePersistencePort`/`IssueUpsertCommand`/`IssueUpsertOutcome`/`IssueUpsertService`, Liquibase `0002-issues.yaml` (`issues`/`issue_fix_versions`/`issue_labels`). Upsert постранично, matching по `jiraId`. `sprints`/`sync_state` не созданы (отложены). **Без** REST endpoint/scheduler/security/incremental sync. См. [session_log.md](./session_log.md) |
| Admin Sync HTTP API (Phase 2.4) | Done — `POST /api/admin/sync/jira` (`api.admin.JiraSyncController`, тонкий HTTP-адаптер над `sync.jira.JiraSyncService`, реюз `JiraSyncResult`) + минимальный security-baseline (`api.security`: `SecurityConfig`/`AdminTokenAuthenticationFilter`/`AdminTokenProperties`, Bearer `DELIVERY_MONITOR_ADMIN_TOKEN`, stateless, [ADR-012](./adr/0012-minimal-auth-baseline-admin-endpoints.md)). **Без** OIDC/JWT/LDAP/users/roles/UI/scheduler/audit database/incremental sync. См. [session_log.md](./session_log.md) |
| Read API | Done — пакет `api.issue`: `IssueController` (`GET /api/issues`, `GET /api/issues/{key}`, `404` при отсутствии), `IssueQueryService` (`@Transactional(readOnly = true)`, Entity → `IssueResponse` DTO). Зависит **только** от `domain.issue`. **`GET /api/sprints/current` сознательно отложен** — нет `sprints` persistence, без mock/stub/live-Jira substitute (TODO в [discovery.md](./discovery.md)). Security baseline/sync flow не менялись. См. [session_log.md](./session_log.md) |
| Scheduler (Phase 2.5) | Done — `sync.jira.JiraSyncScheduler` (`SchedulingConfigurer`, `ScheduledTaskRegistrar.addFixedDelayTask`, **не** `fixedRate`), env-driven `jira.sync.enabled` (default `false`)/`jira.sync.interval` (default `5m`), `@EnableScheduling` на `DeliveryMonitorApplication`. Вызывает ровно тот же `JiraSyncService.syncBoard()`, что manual `POST /api/admin/sync/jira` — не вызывает `Controller`, не вызывает `JiraClient` напрямую, не обходит `JiraSyncService`. In-process guard (`AtomicBoolean`) в `JiraSyncService` не даёт manual и scheduled sync выполняться одновременно — без HTTP `409`, без изменения формы `JiraSyncResult`. **Без** `sync_state`, distributed lock, incremental sync, retry framework. См. [session_log.md](./session_log.md) |
| Phase 3.1–3.2 GitLab client + sync | Done — `integration.gitlab`, `sync.gitlab`. См. [session_log.md](./session_log.md) |
| Phase 3.3 Config persistence | Done — `domain.workstream_type`, `domain.repository`, Liquibase seed (`0003`/`0004`). См. [session_log.md](./session_log.md) |
| Phase 3.4 Git entities + sync wiring | Done — `domain.gitlab` (`branches`/`commits`/`merge_requests`, `0005`); production SoT = `repositories` via `RepositoryPersistencePort`; yaml только mock/local/tests. См. [session_log.md](./session_log.md) |
| Phase 3.5 Linking + activity_events | Done — `domain.timeline` (`IssueKeyExtractor`, `ActivityEvent*`, `0006`); GitLab sync штампует `issue_key` и пишет `BRANCH_CREATED`/`COMMIT`/`MR_*`. См. [session_log.md](./session_log.md) |
| Phase 3.6 Workstreams | Done — `domain.workstream` (`WorkstreamUpsertService`, derived status, `0007`); upsert при Git-активности с `issue_key`; `repository_id` nullable; auto shell-`qa` нет. См. [session_log.md](./session_log.md) |
| Phase 3.7–3.9 Read API / admin sync / scheduler | **Next 3.7:** `GET /api/issues/{key}/timeline`, `GET /api/workstream-types`. Затем 3.8–3.9. **Не** pipelines/Jenkins/Feed/Risks/AI |

Discovery и Skeleton завершены. **Jira Phase 2.1–2.5** и **Phase 3.1–3.6** реализованы (`.\mvnw.cmd clean verify` — 182 теста). Next: **3.7** Read API. Следовать [roadmap.md](./roadmap.md), не перескакивать этапы без явного решения.

---

## 3. Source of Truth

**Только Markdown в `docs/`** (+ корневые `README.md`, `CONTRIBUTING_AI.md`, `CHANGELOG.md`).

- Cursor Canvas **не** является источником истины.
- При расхождении побеждает Markdown.
- ADR живут в `docs/adr/`; индекс — [decisions.md](./decisions.md).

См. [ADR-011](./adr/0011-docs-markdown-source-of-truth.md).

---

## 4. Основные архитектурные принципы

1. Read-only проекция поверх существующих инструментов.
2. **Jira issue key** — якорь связей ([ADR-001](./adr/0001-jira-source-of-truth.md)).
3. **Workstream Type** конфигурируем; нет хардкода платформ ([ADR-002](./adr/0002-workstream-type.md)).
4. `Workstream = Issue × Workstream Type`.
5. Modular monolith: Spring Boot + PostgreSQL ([ADR-003](./adr/0003-modular-monolith.md)).
6. Scheduler + webhooks → DB; без брокера/Redis в MVP ([ADR-004](./adr/0004-polling-and-webhooks.md), [ADR-006](./adr/0006-no-broker-redis-in-mvp.md)).
7. Без CQRS в MVP ([ADR-005](./adr/0005-no-cqrs-in-mvp.md)).
8. REST only ([ADR-007](./adr/0007-rest-only.md)).
9. Timeline-first UX; `activity_events` — сердце модели ([ADR-008](./adr/0008-activity-events.md)).
10. AI Summary — отдельный сервис после MVP ([ADR-009](./adr/0009-ai-summary-separate-service.md)).

Seed Workstream Types текущей команды (данные): `backend`, `frontend`, `oracle`, `qa`.

---

## 5. Workflow разработки

1. Прочитать `ai_context.md` → `session_log.md` → релевантные docs.
2. Следовать [roadmap.md](./roadmap.md); не перескакивать этапы без явного решения.
3. Изменения архитектуры → обновить Markdown + ADR при необходимости.
4. Код — только после явного старта этапа реализации и в согласованных каталогах (`backend/`, `frontend/`).
5. PR: описание, ссылки на docs/ADR, чеклист из `.github/pull_request_template.md`.

Инструкция для агентов: [CONTRIBUTING_AI.md](../CONTRIBUTING_AI.md).

---

## 6. Правила изменения архитектуры

| Изменение | Обновить |
|---|---|
| Стек / границы сервисов | `architecture.md`, `decisions.md` / новый ADR |
| Схема БД | `database.md`, при необходимости `api.md`, `glossary.md` |
| API | `api.md`, `ux.md` |
| Интеграции | `integrations.md`, `architecture.md` |
| UX / экраны | `ux.md`, `roadmap.md`, `vision.md` |
| Scope / этапы | `roadmap.md`, `vision.md` |
| Новое решение | новый файл в `docs/adr/` + строка в `decisions.md` |
| Крупный этап | запись в `session_log.md` + `changelog.md` |

---

## 7. Что запрещено делать AI

- Перескакивать этапы [roadmap.md](./roadmap.md) или писать код вне текущей фазы без явного go-ahead.
- Усложнять архитектуру (CQRS, Kafka, Redis, GraphQL, микросервисы) без нового Accepted ADR.
- Хардкодить платформы (Backend/Android/…) вместо Workstream Type.
- Использовать Canvas / чат как source of truth вместо `docs/`.
- Менять ADR status молча; deprecated/superseded — явно, с ссылкой на новый ADR.
- Коммитить секреты, токены, `.env` с credentials.
- Использовать `C:\Program Files\...` как рабочую директорию проекта.
- Удалять или «упрощать» docs без замены ссылок и обновления индекса.

---

## 8. Что читать перед началом работы

**Обязательно (каждый новый чат):**

1. [ai_context.md](./ai_context.md) (этот файл)
2. [session_log.md](./session_log.md)
3. [CONTRIBUTING_AI.md](../CONTRIBUTING_AI.md)

**По задаче:**

| Задача | Читать |
|---|---|
| Продукт / scope | `vision.md`, `roadmap.md` |
| Архитектура | `architecture.md`, `architecture-overview.md`, `decisions.md` |
| БД | `database.md`, ADR-002, ADR-008 |
| API | `api.md` |
| Интеграции | `integrations.md`, ADR-001, ADR-004 |
| UI | `ux.md` |
| Термины | `glossary.md` |

Shareable one-pager: [architecture-overview.md](./architecture-overview.md).

---

## 9. Локальный путь репозитория

Рекомендуемый корень:

`C:\Users\repin.ea\Projects\AI-Engineering-Delivery-Monitor`

Не использовать `C:\Program Files\...`.
