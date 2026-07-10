# AI Context — точка входа для AI-агентов

| | |
|---|---|
| **Project** | AI Engineering Delivery Monitor |
| **Version** | 2.1 |
| **Stage** | Pre-development — documentation & repository bootstrap |
| **Last updated** | 2026-07-10 |

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
| Repository bootstrap | In progress / Done after this session |
| Application code (backend/frontend) | **Not started** |
| Discovery (repo maps, credentials) | Next |

**Сейчас код приложения не пишем**, пока не завершён Discovery и не утверждён старт этапа Skeleton ([roadmap.md](./roadmap.md)).

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

- Писать код приложения до утверждения старта реализации.
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
