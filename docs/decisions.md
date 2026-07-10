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
