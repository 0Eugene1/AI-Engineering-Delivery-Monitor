# ADR-001: Jira issue key as integration source of truth

| Field | Value |
|---|---|
| **Status** | Accepted |
| **Date** | 2026-07-09 |
| **Deciders** | Architecture working group |
| **Related** | [integrations.md](../integrations.md), [glossary.md](../glossary.md), [ADR-002](./0002-workstream-type.md) |

## Context

Данные о разработке размазаны по Jira, GitLab и Jenkins. Нужен единый ключ связи без ручного ввода в Monitor.

## Decision

**Jira issue key** (например `MPTPSUPP-1234`) — якорь всех связей в системе.

- Jira отдаёт key напрямую.
- GitLab/Jenkins линкуются через naming веток, commit messages, MR title/source branch, Jenkins `BRANCH_NAME`.
- Monitor **не** является системой учёта задач и не заменяет Jira.

## Consequences

### Positive

- Одна понятная join-модель для всех интеграций.
- Совпадает с уже принятым naming `feature/<JIRA-KEY>`.

### Negative / Trade-offs

- Кривой naming веток даёт orphan events.
- Нужен orphan report и soft-link (MR title) как митигация.

### Follow-ups

- Держать regex naming в [integrations.md](../integrations.md).
- Orphan report — этап GitLab + Timeline.

## Alternatives considered

| Option | Why rejected |
|---|---|
| Отдельный ID Monitor | Дублирование и ручной ввод |
| Связь только по MR | Теряются commits/branches до MR |
