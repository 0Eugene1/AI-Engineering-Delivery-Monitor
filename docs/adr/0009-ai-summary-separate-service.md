# ADR-009: AI Summary as a separate service

| Field | Value |
|---|---|
| **Status** | Accepted (post-MVP) |
| **Date** | 2026-07-09 |
| **Related** | [architecture.md](../architecture.md), [api.md](../api.md), [ux.md](../ux.md) |

## Context

Хочется AI-саммари спринта/задачи, но не привязывать MVP и backend к вендору LLM.

## Decision

AI **не** входит в Monitor monolith. Отдельный сервис читает только публичный REST Monitor и вызывает LLM (GPT / Claude / Grok — сменяемо).

Dashboard обязан работать без AI.

## Consequences

Смена модели без релиза Monitor. MVP не блокируется ключами/промптами.
