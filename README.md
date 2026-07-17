# AI Engineering Delivery Monitor

Внутренний delivery-dashboard для команды разработки: автоматически показывает реальное состояние задач в спринте и готовность релиза по данным **Jira**, **GitLab** и **Jenkins** — без ручного ввода.

| | |
|---|---|
| **Status** | Phase 3.1–3.7 done; next **3.8** Admin sync HTTP (`POST /api/admin/sync/gitlab`) |
| **Done** | 2.1–2.5 Jira · 3.1–3.6 GitLab path · 3.7 Read API (timeline + workstream-types) |
| **Team** | 9 people: 7 developers + 2 QA |
| **Concept version** | 2.9 |
| **App code** | Backend: Jira full path + GitLab path through workstreams + Timeline/workstream-types REST (`api.issue` / `api.workstream`, Liquibase `0003`–`0007`); **191** tests. Not yet: `POST /api/admin/sync/gitlab` (3.8), GitLab scheduler (3.9), frontend, Jenkins |

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

0. Discovery → 1. Skeleton → **2.1–2.5 Jira** (Client → Sync → DB → API → Scheduler) → **3. GitLab + Timeline** (3.1–3.7 done; 3.8 next) → 4. Feed + Risks → 5. CI + Release Health → 6. Pilot → 7. AI Summary  

Детали: [docs/roadmap.md](./docs/roadmap.md).

**Сейчас:** Phase 2.1–2.5 и Phase 3.1–3.7 завершены. GitLab sync пишет branches/commits/MRs, `activity_events` и workstreams; Read API отдаёт Timeline и workstream-types из PostgreSQL. Следующий шаг — Phase **3.8** `POST /api/admin/sync/gitlab`, затем 3.9 reconcile scheduler.

---

## Repository layout

```text
AI-Engineering-Delivery-Monitor/
├── docs/                 # Source of Truth (Markdown + ADR)
├── backend/              # Spring Boot — Jira + GitLab sync, domain.*, api (admin/issue/workstream/security)
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
4. Backend: см. [backend/README.md](./backend/README.md) / [docker/README.md](./docker/README.md). Офлайн без токенов — профили `jira-mock` и/или `gitlab-mock`. Frontend и Jenkins — ещё не реализованы. Следующий шаг — `POST /api/admin/sync/gitlab` (3.8).

---

## Getting started (AI agents)

1. Read [docs/ai_context.md](./docs/ai_context.md).
2. Read [docs/session_log.md](./docs/session_log.md).
3. Follow [CONTRIBUTING_AI.md](./CONTRIBUTING_AI.md).
4. Follow [docs/roadmap.md](./docs/roadmap.md); do not skip ahead without explicit approval.

---

## License

Internal / proprietary — корпоративное использование. Публичная OSS-лицензия не предполагается, пока не принято отдельное решение.
