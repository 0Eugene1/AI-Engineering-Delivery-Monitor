# Documentation

<<<<<<< HEAD
Source of Truth для **AI Engineering Delivery Monitor**.

Все продуктовые и архитектурные решения фиксируются здесь в Markdown и готовы к коммиту в Git.

> AI-агенты: начните с [ai_context.md](./ai_context.md), затем [session_log.md](./session_log.md).
=======
Source of truth для продукта **AI Engineering Delivery Monitor**.

Все архитектурные и продуктовые решения фиксируются здесь в Markdown и готовы к коммиту в Git.
>>>>>>> 48a07f2d2fb95923c83a28ff08044d9a9c5f8691

## Index

| File | Contents |
|---|---|
<<<<<<< HEAD
| [ai_context.md](./ai_context.md) | **Точка входа для AI** — цель, стадия, правила |
| [session_log.md](./session_log.md) | Журнал крупных этапов (состояние за 5 мин) |
| [changelog.md](./changelog.md) | Человеческая история изменений |
| [structure.md](./structure.md) | Структура репозитория и локальный путь |
| [vision.md](./vision.md) | Проблема, цель, принципы, success criteria |
| [architecture.md](./architecture.md) | Стек, поток данных, пакеты, риски |
| [architecture-overview.md](./architecture-overview.md) | Единый shareable overview + Mermaid |
=======
| [vision.md](./vision.md) | Проблема, цель, принципы, success criteria |
| [architecture.md](./architecture.md) | Стек, поток данных, пакеты, риски |
| [architecture-overview.md](./architecture-overview.md) | **Единый shareable-документ** (эквивалент Canvas) + Mermaid |
>>>>>>> 48a07f2d2fb95923c83a28ff08044d9a9c5f8691
| [roadmap.md](./roadmap.md) | Этапы реализации и MVP scope |
| [database.md](./database.md) | Логическая схема, Workstream Type, events |
| [api.md](./api.md) | REST-контракт MVP |
| [integrations.md](./integrations.md) | Jira / GitLab / Jenkins |
| [ux.md](./ux.md) | Экраны, Timeline, Release Health |
<<<<<<< HEAD
| [decisions.md](./decisions.md) | Индекс ADR |
| [adr/](./adr/) | Architecture Decision Records |
| [glossary.md](./glossary.md) | Термины |

Корневые файлы репозитория: [README.md](../README.md), [CONTRIBUTING_AI.md](../CONTRIBUTING_AI.md), [CHANGELOG.md](../CHANGELOG.md).

## Documentation rules

1. **Markdown в `docs/` — Source of Truth.** Cursor Canvas не является источником истины.
2. Один крупный документ = один `.md` (или один ADR на решение).
3. Документы должны быть пригодны к Git as-is.
4. Концепция архитектуры: **v2.1** (modular monolith + configurable Workstream Type).
5. После крупного этапа — запись в [session_log.md](./session_log.md) и [changelog.md](./changelog.md).
=======
| [decisions.md](./decisions.md) | ADR / принятые trade-offs |
| [glossary.md](./glossary.md) | Термины |

## Documentation rules

1. **Markdown в `docs/` — основной источник.** Не используйте Cursor Canvas как source of truth.
2. Canvas допускается только как визуальный обзор (сейчас: `architecture-design.canvas.tsx` в Cursor project canvases). При расхождении побеждает Markdown.
3. Один крупный документ = один `.md` файл.
4. Документы должны быть пригодны к Git as-is (без «допилить перед коммитом»).
5. Версия концепции сейчас: **2.1** (простой monolith + configurable Workstream Type).
>>>>>>> 48a07f2d2fb95923c83a28ff08044d9a9c5f8691

## What to update when architecture changes

| Change | Update these files |
|---|---|
<<<<<<< HEAD
| Стек, пакеты, границы сервисов | `architecture.md`, новый ADR, `decisions.md` |
=======
| Стек, пакеты, границы сервисов | `architecture.md`, `decisions.md` |
>>>>>>> 48a07f2d2fb95923c83a28ff08044d9a9c5f8691
| Таблицы / поля / статусы | `database.md`, при необходимости `api.md`, `glossary.md` |
| Эндпоинты / контракты | `api.md`, `ux.md` |
| Jira / GitLab / Jenkins | `integrations.md`, `architecture.md` |
| Экраны / UX-приоритеты | `ux.md`, `roadmap.md`, `vision.md` |
| Scope / порядок этапов | `roadmap.md`, `vision.md` |
<<<<<<< HEAD
| Новое trade-off | `docs/adr/00XX-….md` + `decisions.md` |
| Новый термин | `glossary.md` |
| Крупный этап завершён | `session_log.md`, `changelog.md` |
| Структура репо / локальный путь | `structure.md`, root `README.md` |

## ADR

Шаблон: [adr/0000-template.md](./adr/0000-template.md)  
Индекс: [decisions.md](./decisions.md)
=======
| Новый trade-off | `decisions.md` (+ затронутые файлы выше) |
| Новый термин | `glossary.md` |

## Repository location

Документация хранится в корне репозитория: `docs/`.
>>>>>>> 48a07f2d2fb95923c83a28ff08044d9a9c5f8691
