<<<<<<< HEAD
# Architecture Decision Records

Индекс принятых решений. Полные тексты — в отдельных файлах `docs/adr/`.

| ADR | Title | Status |
|---|---|---|
| [0001](./adr/0001-jira-source-of-truth.md) | Jira issue key as integration source of truth | Accepted |
| [0002](./adr/0002-workstream-type.md) | Configurable Workstream Type | Accepted |
| [0003](./adr/0003-modular-monolith.md) | Modular monolith (Spring Boot + PostgreSQL) | Accepted |
| [0004](./adr/0004-polling-and-webhooks.md) | Polling and webhooks (no message broker) | Accepted |
| [0005](./adr/0005-no-cqrs-in-mvp.md) | No CQRS / event sourcing in MVP | Accepted |
| [0006](./adr/0006-no-broker-redis-in-mvp.md) | No message broker / Redis in MVP | Accepted |
| [0007](./adr/0007-rest-only.md) | REST only (no GraphQL) | Accepted |
| [0008](./adr/0008-activity-events.md) | Timeline and Activity Feed share activity_events | Accepted |
| [0009](./adr/0009-ai-summary-separate-service.md) | AI Summary as a separate service | Accepted (post-MVP) |
| [0010](./adr/0010-people-notifications-out-of-mvp.md) | People screen and Notifications out of MVP | Accepted |
| [0011](./adr/0011-docs-markdown-source-of-truth.md) | Documentation lives in Markdown under docs/ | Accepted |

## How to add a decision

1. Скопируйте [adr/0000-template.md](./adr/0000-template.md).
2. Создайте `docs/adr/00XX-short-title.md` со следующим свободным номером.
3. Добавьте строку в таблицу выше.
4. Обновите затронутые документы (`architecture.md`, `database.md`, `api.md`, `integrations.md`, `ux.md`, `roadmap.md`, …).
5. Добавьте запись в [changelog.md](./changelog.md) и при крупном этапе — в [session_log.md](./session_log.md).

> Устаревшее монолитное содержимое ADR больше не ведётся в этом файле — только индекс.
=======
# Architecture Decision Records (ADR)

| | |
|---|---|
| **Status** | Living |
| **Version** | 2.1 |
| **Related** | [architecture.md](./architecture.md), [vision.md](./vision.md) |

Формат: короткий ADR. Новые решения добавляйте **в конец** этого файла (или выносите в `docs/adr/NNNN-title.md`, если файл разрастётся).

---

## ADR-001: Modular monolith (Spring Boot + PostgreSQL)

**Status:** Accepted

**Context:** Команда 9 человек (7 разработчиков + 2 QA), внутренний инструмент, умеренный поток событий.

**Decision:** Один Spring Boot сервис + PostgreSQL. Пакеты по границам домена/интеграций.

**Consequences:** Проще deploy и отладка. При росте нагрузки можно выделить ingest/worker процессы позже, не начиная с микросервисов.

---

## ADR-002: No CQRS / event sourcing in MVP

**Status:** Accepted

**Context:** Предлагался CQRS-lite (write events / read projections).

**Decision:** Не делаем. Пишем нормализованные таблицы + `activity_events`. При необходимости проекции — обычные SQL/views/сервисные методы.

**Consequences:** Меньше сложности. Replay «как в event store» ограничен; для MVP достаточно reconcile из источников.

---

## ADR-003: No message broker / Redis in MVP

**Status:** Accepted

**Context:** Jira/GitLab/Jenkins не создают миллионы событий для одной команды.

**Decision:** Scheduler + webhooks пишут сразу в БД. Без RabbitMQ, Redis Streams, Redis cache.

**Consequences:** Проще ops. Если появятся пики или долгие sync — пересмотреть (новое ADR).

---

## ADR-004: REST only (no GraphQL)

**Status:** Accepted

**Decision:** Публичный API — REST. GraphQL не рассматриваем до реальной боли с over/under-fetching на многих клиентах.

---

## ADR-005: Configurable Workstream Type (no hardcoded platforms)

**Status:** Accepted

**Context:** Состав потоков меняется (раньше фигурировали Backend/Android/Oracle; фактически Backend/Frontend/Oracle/QA). Хардкод ломает переиспользование.

**Decision:** Абстракция `Workstream Type` в БД/конфиге. `Workstream = Issue × Workstream Type`. Репозитории и jobs мапятся на `workstream_type_code`.

**Consequences:** UI и Release Health динамические. Добавление `ios` / `analytics` — данные, не релиз домена. См. [database.md](./database.md), [glossary.md](./glossary.md).

---

## ADR-006: Timeline and Activity Feed share activity_events

**Status:** Accepted

**Decision:** Одна таблица фактов. Timeline = filter by issue; Feed = global order by time.

**Consequences:** Единая модель событий; проще интеграции. Нужна дисциплина типов событий.

---

## ADR-007: AI Summary as a separate service

**Status:** Accepted (post-MVP)

**Decision:** AI не входит в Monitor backend. Отдельный сервис читает REST и вызывает LLM (GPT/Claude/Grok — сменяемо).

**Consequences:** Dashboard работает без LLM. Смена модели без релиза Monitor.

---

## ADR-008: People screen and Notifications out of MVP

**Status:** Accepted

**Decision:** Фокус на задачах, timeline, release health. People/WIP и digests — после пилота.

**Consequences:** Быстрее до ценности на стендапе по задачам/релизу.

---

## ADR-009: Documentation lives in Markdown under docs/

**Status:** Accepted

**Decision:** Source of truth по продукту/архитектуре — `docs/*.md` в Git. Cursor Canvas допускается как визуальный черновик/обзор, но не как основной документ.

**Consequences:** При изменении архитектуры обновляются соответствующие `.md` файлы (см. таблицу в [README.md](./README.md)).

---

## How to add a decision

1. Добавьте ADR-0XX с Context / Decision / Consequences.
2. Обновите затронутые файлы: обычно [architecture.md](./architecture.md), плюс database/api/integrations/ux/roadmap по смыслу.
>>>>>>> 48a07f2d2fb95923c83a28ff08044d9a9c5f8691
