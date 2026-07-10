# ADR-005: No CQRS / event sourcing in MVP

| Field | Value |
|---|---|
| **Status** | Accepted |
| **Date** | 2026-07-09 |
| **Related** | [architecture.md](../architecture.md), [database.md](../database.md) |

## Context

В раннем черновике предлагался CQRS-lite (write events / read projections).

## Decision

**Не делаем CQRS и event sourcing в MVP.**

Пишем нормализованные таблицы + `activity_events`. Проекции для UI — обычные SQL / сервисные методы. Replay из источников через reconcile.

## Consequences

Меньше сложности и онбординга. Полноценный event-store replay откладывается до реальной боли (новое ADR).

## Alternatives considered

| Option | Why rejected |
|---|---|
| CQRS-lite с outbox | Нет объёма и команды на две модели данных |
