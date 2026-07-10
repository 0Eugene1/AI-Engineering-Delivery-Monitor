# ADR-006: No message broker / Redis in MVP

| Field | Value |
|---|---|
| **Status** | Accepted |
| **Date** | 2026-07-09 |
| **Related** | [ADR-004](./0004-polling-and-webhooks.md), [architecture.md](../architecture.md) |

## Context

Jira / GitLab / Jenkins для одной команды не создают миллионы событий.

## Decision

Без RabbitMQ, Redis Streams и Redis cache в MVP. Достаточно PostgreSQL + scheduler/webhooks.

## Consequences

Проще эксплуатация. Пересмотр — при пиках нагрузки или долгих sync (новое ADR).
