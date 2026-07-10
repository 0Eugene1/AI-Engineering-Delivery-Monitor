# Documentation

Source of truth для продукта **AI Engineering Delivery Monitor**.

Все архитектурные и продуктовые решения фиксируются здесь в Markdown и готовы к коммиту в Git.

## Index

| File | Contents |
|---|---|
| [vision.md](./vision.md) | Проблема, цель, принципы, success criteria |
| [architecture.md](./architecture.md) | Стек, поток данных, пакеты, риски |
| [architecture-overview.md](./architecture-overview.md) | **Единый shareable-документ** (эквивалент Canvas) + Mermaid |
| [roadmap.md](./roadmap.md) | Этапы реализации и MVP scope |
| [database.md](./database.md) | Логическая схема, Workstream Type, events |
| [api.md](./api.md) | REST-контракт MVP |
| [integrations.md](./integrations.md) | Jira / GitLab / Jenkins |
| [ux.md](./ux.md) | Экраны, Timeline, Release Health |
| [decisions.md](./decisions.md) | ADR / принятые trade-offs |
| [glossary.md](./glossary.md) | Термины |

## Documentation rules

1. **Markdown в `docs/` — основной источник.** Не используйте Cursor Canvas как source of truth.
2. Canvas допускается только как визуальный обзор (сейчас: `architecture-design.canvas.tsx` в Cursor project canvases). При расхождении побеждает Markdown.
3. Один крупный документ = один `.md` файл.
4. Документы должны быть пригодны к Git as-is (без «допилить перед коммитом»).
5. Версия концепции сейчас: **2.1** (простой monolith + configurable Workstream Type).

## What to update when architecture changes

| Change | Update these files |
|---|---|
| Стек, пакеты, границы сервисов | `architecture.md`, `decisions.md` |
| Таблицы / поля / статусы | `database.md`, при необходимости `api.md`, `glossary.md` |
| Эндпоинты / контракты | `api.md`, `ux.md` |
| Jira / GitLab / Jenkins | `integrations.md`, `architecture.md` |
| Экраны / UX-приоритеты | `ux.md`, `roadmap.md`, `vision.md` |
| Scope / порядок этапов | `roadmap.md`, `vision.md` |
| Новый trade-off | `decisions.md` (+ затронутые файлы выше) |
| Новый термин | `glossary.md` |

## Repository location

Документация хранится в корне репозитория: `docs/`.
