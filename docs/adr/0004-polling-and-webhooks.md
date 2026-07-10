# ADR-004: Polling and webhooks (no message broker)

| Field | Value |
|---|---|
| **Status** | Accepted |
| **Date** | 2026-07-09 |
| **Deciders** | Architecture working group |
| **Related** | [integrations.md](../integrations.md), [ADR-006](./0006-no-broker-redis-in-mvp.md) |

## Context

Нужно собирать события из Jira Server 8.20, GitLab, Jenkins. Объём — сотни/тысячи событий в день. Jira Server webhooks часто ограничены/ненадёжны.

## Decision

Гибридный ingest **без брокера сообщений**:

| Source | Primary | Secondary |
|---|---|---|
| Jira | `@Scheduled` poll 2–5 мин | Optional webhook |
| GitLab | Webhook → сразу в БД | Reconcile poll 15–30 мин |
| Jenkins | Webhook или poll известных jobs | Reconcile |

Webhook payload пишется **сразу в PostgreSQL** (нормализация в том же потоке). Periodic reconcile обязателен.

## Consequences

### Positive

- Простой ops: нет RabbitMQ/Kafka в MVP.
- Достаточная свежесть данных для стендапа (минуты).

### Negative / Trade-offs

- Не real-time миллисекундный.
- Нужен `sync_state` / watermark и идемпотентный upsert.

## Alternatives considered

| Option | Why rejected / deferred |
|---|---|
| RabbitMQ / Redis Streams | Преждевременно для объёма |
| Только webhooks | Пропуски доставки на Jira Server |
