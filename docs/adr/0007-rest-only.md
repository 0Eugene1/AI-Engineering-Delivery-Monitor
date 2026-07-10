# ADR-007: REST only (no GraphQL)

| Field | Value |
|---|---|
| **Status** | Accepted |
| **Date** | 2026-07-09 |
| **Related** | [api.md](../api.md) |

## Context

MVP — около четырёх экранов, один UI-клиент.

## Decision

Публичный API — **только REST**. GraphQL не используем до реальной боли с over/under-fetching на нескольких клиентах.

## Consequences

Проще контракт и отладка. OpenAPI можно добавить на этапе skeleton.
