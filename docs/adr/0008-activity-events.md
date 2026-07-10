# ADR-008: Timeline and Activity Feed share activity_events

| Field | Value |
|---|---|
| **Status** | Accepted |
| **Date** | 2026-07-09 |
| **Related** | [database.md](../database.md), [ux.md](../ux.md) |

## Context

Нужны Issue Timeline и командный Activity Feed без дублирования моделей событий.

## Decision

Одна таблица **`activity_events`**.

- Issue Timeline = filter by `issue_key`
- Activity Feed = global order by `occurred_at`

Типы событий включают GitLab/Jenkins/Jira, в том числе `JIRA_COMMENT`.

## Consequences

Единая модель; нужна дисциплина enum типов. См. [database.md](../database.md).
