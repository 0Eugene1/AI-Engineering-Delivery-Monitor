# Changelog

История изменений проекта для людей. Формат близок к [Keep a Changelog](https://keepachangelog.com/).

Типы: **Added** · **Changed** · **Deprecated** · **Removed** · **Fixed** · **Documentation**

---

## [Unreleased]

### Added

- **Persistence (Phase 2.3, [roadmap.md](./roadmap.md)) implemented**: новый пакет `ru.eltc.deliverymonitor.domain.issue` — `IssueEntity` (JPA, `issues` + `@ElementCollection` `issue_fix_versions`/`issue_labels`), `IssueRepository` (`findAllByJiraIdIn`), `IssuePersistencePort`/`IssueUpsertCommand`/`IssueUpsertOutcome`, `IssueUpsertService` (упсерт постранично, matching по `jiraId`, не по `key`). Liquibase `0002-issues.yaml` (таблицы `issues`/`issue_fix_versions`/`issue_labels`; `sprints`/`sync_state` не создавались — как согласовано). `sync.jira.JiraSyncService` теперь вызывает `IssuePersistencePort.upsertPage(...)` на каждой странице вместо накопления всего списка в памяти; `JiraIssueSnapshot` дополнен `jiraId`/`created`, `updated`/`created` — `Instant` (парсинг Jira timestamp: ошибка → `null` + warning, sync не падает), `assigneeName` заменён на `assigneeUsername`/`assigneeDisplayName`. `JiraSyncResult` больше не хранит список issues — только агрегаты `created`/`updated` (+ derived `saved()`). Ошибки БД не маскируются (не ловятся в `JiraSyncService`, поднимаются наверх) — в отличие от `JiraClientException`, которая пишется в `errors`. Тесты (новые): `IssueUpsertServiceTest` (unit, Mockito), `IssueUpsertServiceIntegrationTest` (`@DataJpaTest` против настоящего Liquibase-changeset на H2 PostgreSQL-compat), переписанный `JiraSyncServiceTest` (постраничная персистентность через recording fake-порт). `.\mvnw.cmd clean verify` — 49 тестов, 0 failures, 0 errors, 2 skipped (`JiraSmokeTest` без токена). **Отклонение от исходного эскиза `database.md`**: физическая колонка бизнес-якоря названа `issue_key`, не `key` (зарезервированное слово, ломает H2 в `SELECT`); Java-контракт (`getKey()`/`key()`) не изменился — см. `docs/database.md`.
- Jira Sync — первый application-level слой (Phase 2.2, [roadmap.md](./roadmap.md)): новый пакет `ru.eltc.deliverymonitor.sync.jira` **над** `integration.jira`. `JiraSyncService.syncBoard()` оркеструет постраничную выгрузку задач наблюдаемой board через шов `JiraContextProvider` (не через `JiraClient` напрямую), нормализует каждый wire `JiraIssueDto` во внутренний `JiraIssueSnapshot` (seam к будущему persistence) и возвращает `JiraSyncResult` (`startedAt`/`finishedAt`/`fetched`/`pages`/`mocked`/`errors`/`issues`). `JiraClientException` не пробрасывается «сырым» — пишется в `errors`, прогон возвращает то, что успел получить. Размер страницы конфигурируем: `jira.sync.page-size` (default `50`, `JiraSyncProperties`). Тесты (7 новых): пагинация по нескольким страницам, нормализация полей, пустая board, unassigned/`null`-поля, проброс `mocked`, `JiraClientException` → `errors` (полный и частичный сбой). `.\mvnw.cmd clean verify` — 39 тестов, 0 failures, 0 errors, 2 skipped (`JiraSmokeTest` без токена).
- Jira board context provider (Task 2.3, [roadmap.md](./roadmap.md)): пакет `integration.jira.provider` — интерфейс `JiraContextProvider` (`getBoardContext(startAt, maxResults)` → `JiraBoardContext`) поверх `JiraClient`, доменно-осмысленный шов для будущего sync. Две реализации, переключаемые **только конфигом** `jira.mode=rest|mock` (default `rest`): `RestJiraContextProvider` (реальная Jira через `JiraClient.searchByFilter`) и `MockJiraContextProvider` (санитизированные demo-данные из `classpath:jira/mock/board-718-filter-30532.json` для офлайн-разработки без сервисного аккаунта). Профиль `jira-mock` (`application-jira-mock.yml`) поднимает mock офлайн. Тесты (7 новых): rest через `mockwebserver3`, mock-данные + постранично + prod-guard, выбор бина по `jira.mode`.
- Защита mock от production: `MockJiraContextProvider` кидает `IllegalStateException`, если активен профиль `prod`/`production`; fixture явно помечен как demo/sanitized. Mock **никогда** не используется в production.
- `jira.mode` и `jira.board-id` в `JiraProperties`/`application.yml` (обратносовместимо; `JiraClient` не менялся).
- Jira REST-клиент (Phase 2.1, [roadmap.md](./roadmap.md)): `ru.eltc.deliverymonitor.integration.jira` на Spring `WebClient` — конфигурация (`JiraProperties`, `jira.*` в `application.yml` + env), аутентификация (Basic / Bearer PAT, переключаемая `jira.auth.type`), DTO ответов Jira REST API v2 (`myself`, `search`, issue fields: summary/status/assignee/fixVersions/labels), `JiraClient` (`getMyself`, `search`, `searchByFilter`) и `JiraClientException` для ошибок API. Юнит-тесты — на mock HTTP-сервере (`mockwebserver3`), без реального Jira.
- `spring-boot-starter-webflux` в `backend/pom.xml` — только ради `WebClient`; Servlet/MVC стек остаётся основным.

### Documentation

- **Security checkpoint перед Phase 2.4 — минимальный auth-baseline зафиксирован** (код не писался): новый [ADR-012](./adr/0012-minimal-auth-baseline-admin-endpoints.md) «Minimal authentication baseline for admin endpoints» (Context/Problem/Decision/Alternatives rejected/Consequences). Решения: Spring Security вводится только как минимальный enforcement-слой (защита привилегированных endpoint + admin-gate + основа под будущий OIDC, не полная аутентификация пользователей); auth baseline — статичный Bearer API-token из env для `/api/admin/**` (нет локальных пользователей / user storage / ролей PM/QA/Developer в коде / OIDC — временный machine/admin baseline до корпоративного SSO/OIDC); **входящий `DELIVERY_MONITOR_ADMIN_TOKEN` отделён от исходящего `JIRA_TOKEN`** (разные границы доверия, утечка одного не открывает второе направление); endpoint policy — protected `POST /api/admin/sync/jira` + actuator кроме `health`/liveness, currently open `GET /api/issues` и `GET /api/sprints/current` (internal read-only внутри VPN, защита пересматривается при UI/SSO); alternatives rejected — OIDC now / basic auth / VPN only. Обновлены: `docs/security.md` (v1.1 — §2 baseline, §5 разделение токенов, §9 checklist), `docs/api.md` (v2.2 — Auth conventions + примечание admin-endpoint), `docs/architecture.md` (v2.2 — пакет `api` + security), `docs/decisions.md` (индекс ADR-012). **Не менялись** ADR-001/003/011 и `docs/roadmap.md`.
- **Phase 2.3 Persistence — дизайн согласован** (код не писался): `docs/decisions.md` (Design notes) — новая запись; `docs/database.md` — реальные таблицы `issues`/`issue_fix_versions`/`issue_labels` для Phase 2.3, `sprints`/`sync_state` помечены Planned/future с причиной отложения; `docs/architecture.md` — детализация состава `domain.issue`/`sync.jira`, новый раздел «Package dependency direction» (`integration.jira → sync.jira → domain.issue`, без обратной зависимости). Ключевые решения: `domain.issue` — единственный владелец persistence-контрактов (`IssuePersistencePort`, `IssueUpsertCommand`, `IssueEntity`, `IssueRepository`, `IssueUpsertService`), `sync.jira` зависит от `domain.issue` (не наоборот); matching при upsert — по `jiraId`, не по `key`; upsert постранично, без `saveAll` после полного сбора списка; `JiraSyncResult` больше не хранит список issues, только агрегаты `created`/`updated` (+ derived `saved()`). Новый ADR не создавался — решения в рамках ADR-001/003/011. См. `docs/session_log.md` (запись «Phase 2.3 Persistence design approved»).
- `docs/security.md` (new) — архитектурные принципы безопасности: Security Assumptions (текущий этап: только внутренняя сеть, доступ через корпоративный VPN, сервис не публикуется в Интернет, интеграции через сервисные аккаунты), Security Goals + модель угроз, Authentication (корпоративный SSO/LDAP/OIDC, без локальных пользователей), Authorization (роли Admin/PM/QA/Developer, least privilege), Service Accounts (read-only Jira/GitLab/Jenkins с минимальными правами), Secrets (только env vars, никаких секретов в Git и логах), Network (VPN, HTTPS, внутренний сервис), Audit, Logging (запрет логировать токены и `Authorization` header), Security Checklist. Только документация: код не менялся, Spring Security не добавлялся, архитектура не менялась, новый ADR не создавался.
- `docs/README.md` — `security.md` добавлен в индекс и в таблицу «что обновлять при изменениях».
- `docs/discovery.md` — новая секция §1 «Task 2.2 (Auth) check — 2026-07-15»: подтверждено, что `JiraSmokeTest` использует production `JiraClient`/`JiraClientConfig`, проверена конфигурация env variables (`JIRA_BASE_URL`, `JIRA_AUTH_TYPE`, `JIRA_USERNAME`, `JIRA_TOKEN`, `JIRA_DEFAULT_FILTER_ID`) в `application.yml`, `mvnw clean verify` зелёный (25 тестов, 2 skipped без токена). Реальный авторизованный прогон (`/myself` → `200`) остаётся открытым `TODO` — реальный `JIRA_TOKEN` недоступен в этой среде; инструкция запуска smoke test с реальным токеном задокументирована. Код не менялся — только верификация существующей Phase 2.1 реализации.

### Notes

- Phase 2.2 Jira Sync: реализован **только application-слой** (`JiraSyncService` + `JiraSyncResult` + `JiraIssueSnapshot`). Endpoint `POST /api/admin/sync/jira` (REST controller), сохранение в PostgreSQL (JPA/Liquibase/repository/`sync_state`), incremental sync, `GET /api/issues`, `@Scheduled` polling и Spring Security — **не реализованы** (Phase 2.3–2.5). Поле `saved` в результат **не добавлялось** — persistence ещё нет. GitLab/Jenkins — не начаты. `JiraClient`/`JiraContextProvider` не менялись.
- Task 2.3: live board configuration / columns / swimlanes / sprint metadata (Jira Agile API) — **вне scope** (доска Kanban, конфиг уже в `discovery.md` §9.1); оставлен extension point на будущую отдельную задачу.
- Task 2.3 done-when (filter 30532 → issues) подтверждён **структурно** (rest-провайдер против mock HTTP-сервера + demo-данные); реальный авторизованный прогон — тот же открытый `TODO`, что и у 2.2.
- Новый ADR не создавался — решение (provider + config-switchable mock) в рамках принятых ADR; зафиксировано в `docs/decisions.md` (Design notes).
- Task 2.2 (Auth) закрыта **по коду и конфигурации** (auth strategy + env + smoke test), но **не по факту** — авторизованный прогон против реального Jira ждёт реального `JIRA_TOKEN`.

---

## [0.2.0-skeleton] — 2026-07-14

### Added

- Минимальный backend-каркас (`backend/`): Java 21, Spring Boot 3.5.16, Maven (с wrapper `mvnw`/`mvnw.cmd`).
- PostgreSQL: JDBC-драйвер + Spring Data JPA (без бизнес-сущностей), конфигурация через `application.yml` и env-переменные (`DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`).
- Liquibase: master changelog + один baseline changeset (`tagDatabase`), без бизнес-таблиц.
- Spring Boot Actuator: `/actuator/health`, `/actuator/info` (с build-info).
- Docker: `backend/Dockerfile` (multi-stage, `eclipse-temurin:21-jre-alpine`), `docker/docker-compose.yml` (postgres + backend), `docker/.env.example`.
- Пакет `ru.eltc.deliverymonitor.integration.jira` (пустой, только `package-info.java`) — точка старта следующей задачи (Jira integration).
- Context/Liquibase smoke-тест на embedded H2 (PostgreSQL-режим), без зависимости от Docker.

### Notes

- Jira, GitLab, Jenkins — **не реализованы**; бизнес-сущности (`issues`, `workstreams`, `activity_events`, …) — **не созданы**. Это сознательная граница Phase 1 — Skeleton ([roadmap.md](./roadmap.md)).
- Архитектура не изменилась относительно принятых ADR — новый ADR не создавался.

---

## [0.1.0-docs] — 2026-07-10

### Added

- Профессиональная структура репозитория: `docs/`, `backend/`, `frontend/`, `docker/`, `scripts/`, `.github/`.
- `docs/ai_context.md` — точка входа для AI-агентов.
- `docs/session_log.md` — журнал крупных этапов.
- `docs/changelog.md` — этот файл.
- `docs/adr/` — полноценные ADR-0001…0011 + шаблон.
- `CONTRIBUTING_AI.md` — правила для AI-агентов.
- Корневой `README.md`, `.gitignore`, GitHub issue/PR templates.
- `docs/architecture-overview.md` — единый shareable-документ (эквивалент Canvas).

### Changed

- `docs/decisions.md` превращён в индекс ADR (тексты перенесены в `docs/adr/`).
- Уточнена численность команды: 9 человек = 7 разработчиков + 2 QA.
- В типы `activity_events` добавлен `JIRA_COMMENT`.
- Workstream Type вместо хардкода платформ (Backend/Android/…).

### Notes

- Код приложения ещё не начат. Версия `0.1.0-docs` — только документация и bootstrap репозитория.
- Архитектурная концепция: **v2.1**.
