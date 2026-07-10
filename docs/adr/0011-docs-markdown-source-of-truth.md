# ADR-011: Documentation lives in Markdown under docs/

| Field | Value |
|---|---|
| **Status** | Accepted |
| **Date** | 2026-07-09 |
| **Related** | [ai_context.md](../ai_context.md), [README.md](../README.md), [CONTRIBUTING_AI.md](../../CONTRIBUTING_AI.md) |

## Context

Нужен устойчивый source of truth для многолетней разработки людьми и AI-агентами. Canvas удобен для обзора, но плох как единственный артефакт в Git/онбординге.

## Decision

**Source of Truth** — только Markdown в `docs/` (и корневые инженерные файлы: README, CONTRIBUTING_AI, CHANGELOG).

Cursor Canvas допускается как локальный визуальный черновик, **не** как источник истины. При расхождении побеждает Markdown.

Любое архитектурное изменение сопровождается обновлением соответствующих `.md` и при необходимости новым ADR в `docs/adr/`.

## Consequences

Документы готовы к Git и ревью. AI-агенты стартуют с [ai_context.md](../ai_context.md).
