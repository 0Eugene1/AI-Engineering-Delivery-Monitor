# AI Engineering Delivery Monitor

<<<<<<< HEAD
Внутренний delivery-dashboard для команды разработки: автоматически показывает реальное состояние задач в спринте и готовность релиза по данным **Jira**, **GitLab** и **Jenkins** — без ручного ввода.

| | |
|---|---|
| **Status** | Pre-development — documentation complete (architecture v2.1) |
| **Team** | 9 people: 7 developers + 2 QA |
| **Concept version** | 2.1 |
| **App code** | Not started |

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
| [docs/ux.md](./docs/ux.md) | Экраны |
| [docs/decisions.md](./docs/decisions.md) | Индекс ADR |
| [docs/adr/](./docs/adr/) | Architecture Decision Records |
| [docs/changelog.md](./docs/changelog.md) | История изменений |
| [docs/glossary.md](./docs/glossary.md) | Термины |
| [CONTRIBUTING_AI.md](./CONTRIBUTING_AI.md) | Правила для AI-агентов |

Полный индекс: [docs/README.md](./docs/README.md).

---

## Roadmap (short)

0. Discovery → 1. Skeleton → 2. Jira Board → 3. GitLab + Timeline → 4. Feed + Risks → 5. Jenkins + Release Health → 6. Pilot → 7. AI Summary  

Детали: [docs/roadmap.md](./docs/roadmap.md).

**Сейчас:** завершить Discovery (маппинги репо, доступы), затем Skeleton.

---

## Repository layout

```text
AI-Engineering-Delivery-Monitor/
├── docs/                 # Source of Truth (Markdown + ADR)
├── backend/              # Spring Boot (empty until Skeleton)
├── frontend/             # React UI (empty until Skeleton)
├── docker/               # Compose / local infra later
├── scripts/              # Dev/ops helpers later
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
4. Код приложения пока не запускается — его ещё нет.

---

## Getting started (AI agents)

1. Read [docs/ai_context.md](./docs/ai_context.md).
2. Read [docs/session_log.md](./docs/session_log.md).
3. Follow [CONTRIBUTING_AI.md](./CONTRIBUTING_AI.md).
4. Do **not** write application code until implementation stage is explicitly started.

---

## License

Internal / proprietary — корпоративное использование. Публичная OSS-лицензия не предполагается, пока не принято отдельное решение.
=======
Internal engineering dashboard that automatically visualizes software delivery status using Jira, GitLab and Jenkins.

## Vision

The system does not replace Jira.

It provides a real-time delivery view by collecting information from existing development tools and automatically detecting:

- active workstreams
- blockers
- release risks
- development progress
- dependencies

## Current status

🚧 Architecture Phase

## Documentation

Source of truth: [`docs/`](./docs/README.md)

| Document | Description |
|---|---|
| [vision.md](./docs/vision.md) | Problem, goals, principles |
| [architecture.md](./docs/architecture.md) | Stack, data flow, packages |
| [architecture-overview.md](./docs/architecture-overview.md) | Shareable overview with Mermaid diagrams |
| [roadmap.md](./docs/roadmap.md) | Implementation phases and MVP scope |
| [database.md](./docs/database.md) | Schema, Workstream Type, events |
| [api.md](./docs/api.md) | REST contract for MVP |
| [integrations.md](./docs/integrations.md) | Jira, GitLab, Jenkins |
| [ux.md](./docs/ux.md) | Screens, Timeline, Release Health |
| [decisions.md](./docs/decisions.md) | ADRs and trade-offs |
| [glossary.md](./docs/glossary.md) | Terms |

## Tech Stack

Backend:
- Java 21
- Spring Boot
- PostgreSQL

Frontend:
- React
- TypeScript

Infrastructure:
- Docker
>>>>>>> 48a07f2d2fb95923c83a28ff08044d9a9c5f8691
