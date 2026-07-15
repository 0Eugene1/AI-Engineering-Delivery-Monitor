# AI Engineering Delivery Monitor

Внутренний delivery-dashboard для команды разработки: автоматически показывает реальное состояние задач в спринте и готовность релиза по данным **Jira**, **GitLab** и **Jenkins** — без ручного ввода.

| | |
|---|---|
| **Status** | Phase 2.4 — REST API (next) |
| **Done** | 2.1 Jira Client · 2.2 Jira Sync · 2.3 Persistence |
| **Team** | 9 people: 7 developers + 2 QA |
| **Concept version** | 2.5 |
| **App code** | Backend: Jira client + board provider + sync + DB upsert ([backend/](./backend/)); no REST API / scheduler / Spring Security / frontend / GitLab / Jenkins yet |

---

## Why this exists

В одной Jira-задаче часто несколько потоков работы (разные репозитории / роли). Сейчас сложно быстро понять: кто реально работает, что сделано, кто блокирует, успеем ли в релиз.

Monitor — read-only проекция поверх существующих инструментов. Якорь — Jira issue key. Потоки — конфигурируемые **Workstream Types**.

---

## Tech stack (MVP)

| Layer | Choice |
|---|---|
| Backend | Spring Boot (modular monolith) |
| Database | PostgreSQL |
| Ingest | Scheduler + optional webhooks |
| API | REST |
| Frontend | React |
| AI Summary | Separate service (after MVP) |

**Not in MVP:** CQRS, message brokers, Redis, GraphQL, notifications, People screen.

---

## Documentation (Source of Truth)

Начните здесь:

| Doc | Purpose |
|---|---|
| [docs/ai_context.md](./docs/ai_context.md) | **Точка входа для AI и быстрый контекст** |
| [docs/session_log.md](./docs/session_log.md) | Состояние проекта за 5 минут |
| [docs/architecture-overview.md](./docs/architecture-overview.md) | Единый shareable overview + Mermaid |
| [docs/vision.md](./docs/vision.md) | Цель и принципы |
| [docs/architecture.md](./docs/architecture.md) | Архитектура |
| [docs/roadmap.md](./docs/roadmap.md) | Этапы и MVP |
| [docs/database.md](./docs/database.md) | Схема данных |
| [docs/api.md](./docs/api.md) | REST |
| [docs/integrations.md](./docs/integrations.md) | Jira / GitLab / Jenkins |
| [docs/security.md](./docs/security.md) | Безопасность: auth, secrets, network |
| [docs/ux.md](./docs/ux.md) | Экраны |
| [docs/decisions.md](./docs/decisions.md) | Индекс ADR |
| [docs/adr/](./docs/adr/) | Architecture Decision Records |
| [docs/changelog.md](./docs/changelog.md) | История изменений |
| [docs/glossary.md](./docs/glossary.md) | Термины |
| [CONTRIBUTING_AI.md](./CONTRIBUTING_AI.md) | Правила для AI-агентов |

Полный индекс: [docs/README.md](./docs/README.md).

---

## Roadmap (short)

0. Discovery → 1. Skeleton → **2.1–2.5 Jira** (Client → Sync → DB → API → Scheduler) → 3. GitLab + Timeline → 4. Feed + Risks → 5. CI + Release Health → 6. Pilot → 7. AI Summary  

Детали: [docs/roadmap.md](./docs/roadmap.md).

**Сейчас:** Phase 2.1–2.3 завершены (Jira client, sync orchestration, PostgreSQL persistence). Следующий шаг — **Phase 2.4** REST API (`POST /api/admin/sync/jira`, `GET /api/issues`) и минимальный Spring Security baseline ([ADR-012](./docs/adr/0012-minimal-auth-baseline-admin-endpoints.md)).

---

## Repository layout

```text
AI-Engineering-Delivery-Monitor/
├── docs/                 # Source of Truth (Markdown + ADR)
├── backend/              # Spring Boot — Jira integration, sync, domain.issue
├── frontend/             # React UI (not started)
├── docker/               # Compose / local Postgres
├── scripts/              # Dev/ops helpers
├── .github/              # Issue & PR templates
├── README.md
├── CONTRIBUTING_AI.md
├── CHANGELOG.md
└── .gitignore
```

Назначение папок: [docs/structure.md](./docs/structure.md).

---

## Getting started (humans)

1. Клонируйте репозиторий в writable-путь, например:  
   `C:\Users\<you>\Projects\AI-Engineering-Delivery-Monitor`  
   **Не** используйте `C:\Program Files\...`.
2. Прочитайте [docs/vision.md](./docs/vision.md) и [docs/architecture-overview.md](./docs/architecture-overview.md).
3. Для AI-сессий откройте [docs/ai_context.md](./docs/ai_context.md).
4. Backend можно запустить локально: см. [backend/README.md](./backend/README.md) / [docker/README.md](./docker/README.md). Для офлайн-разработки без Jira-токена — профиль `jira-mock`. REST API, scheduler, frontend, GitLab и Jenkins — ещё не реализованы.

---

## Getting started (AI agents)

1. Read [docs/ai_context.md](./docs/ai_context.md).
2. Read [docs/session_log.md](./docs/session_log.md).
3. Follow [CONTRIBUTING_AI.md](./CONTRIBUTING_AI.md).
4. Follow [docs/roadmap.md](./docs/roadmap.md); do not skip ahead without explicit approval.

---

## License

Internal / proprietary — корпоративное использование. Публичная OSS-лицензия не предполагается, пока не принято отдельное решение.
