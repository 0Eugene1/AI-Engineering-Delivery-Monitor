# ADR-002: Configurable Workstream Type

| Field | Value |
|---|---|
| **Status** | Accepted |
| **Date** | 2026-07-09 |
| **Deciders** | Architecture working group |
| **Related** | [database.md](../database.md), [glossary.md](../glossary.md), [architecture.md](../architecture.md) |

## Context

В одной Jira-задаче участвуют несколько потоков (репозитории/роли). Ранние черновики хардкодили Backend / Android / Oracle — это неверно и ломает эволюцию состава команды.

Фактический seed текущей команды: `backend`, `frontend`, `oracle`, `qa`.

## Decision

Вводим абстракцию **Workstream Type** — конфигурируемый справочник (`code`, `display_name`, `sort_order`, `is_active`).

**Workstream = Issue × Workstream Type.**

Репозитории и Jenkins jobs мапятся на `workstream_type_code` через конфиг/данные, не через if/else в доменном коде.

Система **не** содержит захардкоженных знаний о конкретных платформах.

## Consequences

### Positive

- Смена набора типов — данные, не релиз домена.
- UI / Release Health строятся динамически.
- Один продукт пригоден для разных команд.

### Negative / Trade-offs

- Нужна дисциплина конфигурации repo → type.
- Репозиторий без маппинга = orphan / слепая зона.

### Follow-ups

- Seed и map фиксируются на этапе Discovery ([roadmap.md](../roadmap.md)).

## Alternatives considered

| Option | Why rejected |
|---|---|
| Хардкод Backend/Frontend/Oracle/QA | Ломается при любом изменении состава |
| Только Jira components | Не покрывает Git/Jenkins активность надёжно |
