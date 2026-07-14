# Changelog

История изменений проекта для людей. Формат близок к [Keep a Changelog](https://keepachangelog.com/).

Типы: **Added** · **Changed** · **Deprecated** · **Removed** · **Fixed** · **Documentation**

---

## [Unreleased]

### Added

- Jira REST-клиент (Phase 2.1, [roadmap.md](./roadmap.md)): `ru.eltc.deliverymonitor.integration.jira` на Spring `WebClient` — конфигурация (`JiraProperties`, `jira.*` в `application.yml` + env), аутентификация (Basic / Bearer PAT, переключаемая `jira.auth.type`), DTO ответов Jira REST API v2 (`myself`, `search`, issue fields: summary/status/assignee/fixVersions/labels), `JiraClient` (`getMyself`, `search`, `searchByFilter`) и `JiraClientException` для ошибок API. Юнит-тесты — на mock HTTP-сервере (`mockwebserver3`), без реального Jira.
- `spring-boot-starter-webflux` в `backend/pom.xml` — только ради `WebClient`; Servlet/MVC стек остаётся основным.

### Notes

- Sync-оркестрация (`POST /api/admin/sync/jira`), сохранение в PostgreSQL, `GET /api/issues` и `@Scheduled` polling — **не реализованы**; это Phase 2.2–2.5. GitLab/Jenkins — не начаты.

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
