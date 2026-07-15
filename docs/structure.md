# Repository structure

| | |
|---|---|
| **Status** | Accepted |
| **Date** | 2026-07-10 |
| **Related** | [ai_context.md](./ai_context.md), root [README.md](../README.md) |

## Recommended local path

```text
C:\Users\<you>\Projects\AI-Engineering-Delivery-Monitor
```

Для текущего пользователя:

```text
C:\Users\repin.ea\Projects\AI-Engineering-Delivery-Monitor
```

### Почему не `C:\Program Files\...`

| Program Files | Projects under user profile |
|---|---|
| Нужны admin-права на запись | Обычная запись без UAC |
| Типично для установленных программ, не для git-worktree | Стандарт для исходников |
| Ломает `npm`/`gradle`/`git` hooks | Нормальный DX |
| Cursor/IDE часто не могут сохранить файлы | Полный контроль |

---

## Top-level layout

```text
AI-Engineering-Delivery-Monitor/
├── docs/                 # Source of Truth — продуктовая и инженерная документация
│   ├── adr/              # Architecture Decision Records (по одному решению)
│   ├── ai_context.md     # Точка входа для AI
│   ├── session_log.md    # Журнал крупных этапов
│   ├── changelog.md      # Человеческая история изменений
│   └── …                 # vision, architecture, api, …
├── backend/              # Spring Boot — Jira integration, sync, domain.issue
├── frontend/             # React dashboard (not started)
├── docker/               # docker-compose, local Postgres
├── scripts/              # Вспомогательные скрипты (migrate, seed, sync helpers)
├── .github/              # Issue/PR templates; workflows позже
├── README.md             # Главная страница репозитория
├── CONTRIBUTING_AI.md    # Правила для AI-агентов
├── CHANGELOG.md          # Указатель на docs/changelog.md
└── .gitignore
```

## Folder responsibilities

| Folder | Purpose | When it fills |
|---|---|---|
| `docs/` | Единственный SoT по продукту/архитектуре | Уже сейчас |
| `docs/adr/` | Формальные решения | Уже сейчас |
| `backend/` | Код Spring Boot monolith | Phase 1 Skeleton+ |
| `frontend/` | React UI | Phase 1–2+ |
| `docker/` | Локальный Postgres и т.п. | Skeleton |
| `scripts/` | One-off / CI helpers | По мере нужды |
| `.github/` | Contribution hygiene | Уже templates; CI — позже |

## What we intentionally skip for now

| Item | Why |
|---|---|
| GitHub Project / Milestones via API | Нужен `gh` + org access; завести вручную на GitHub при желании |
| Heavy CODEOWNERS | Команда ещё не зафиксировала owners в GitHub handles |
| CI workflows | Workflows ещё не настроены (код backend собирается через `mvnw verify`) |
| Open-source LICENSE | Внутренний продукт |

Можно добавить позже без ломки структуры.
