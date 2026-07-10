# ADR-003: Modular monolith (Spring Boot + PostgreSQL)

| Field | Value |
|---|---|
| **Status** | Accepted |
| **Date** | 2026-07-09 |
| **Deciders** | Architecture working group |
| **Related** | [architecture.md](../architecture.md), [ADR-005](./0005-no-cqrs-in-mvp.md) |

## Context

Команда 9 человек (7 разработчиков + 2 QA). Внутренний инструмент. Умеренный поток событий. Нужна скорость до ценности, а не распределённая система.

## Decision

Один **Spring Boot** deployable (modular monolith) + **PostgreSQL**.

Пакеты по границам: `integration.*`, `domain.*`, `api`.

Микросервисы на старте не используем. При росте нагрузки допустимо позже выделить процессы ingest/worker — отдельным ADR.

## Consequences

### Positive

- Проще deploy, отладка, онбординг.
- Транзакции и консистентность проще.

### Negative / Trade-offs

- Один процесс — единая точка масштабирования (приемлемо для MVP).

## Alternatives considered

| Option | Why rejected |
|---|---|
| Микросервисы ingest/projection/api | Over-engineering для команды и объёма |
| Serverless-only | Хуже стыкуется с on-prem Jira Server / корпоративной сетью |
