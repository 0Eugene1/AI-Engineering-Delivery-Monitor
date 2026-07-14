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
