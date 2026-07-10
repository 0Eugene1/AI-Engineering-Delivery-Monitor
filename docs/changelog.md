# Changelog

История изменений проекта для людей. Формат близок к [Keep a Changelog](https://keepachangelog.com/).

Типы: **Added** · **Changed** · **Deprecated** · **Removed** · **Fixed** · **Documentation**

---

## [Unreleased]

### Documentation

- (пусто — готовьте записи сюда до релиза/тега)

---

## [0.1.0-docs] — 2026-07-10

### Added

- Профессиональная структура репозитория: `docs/`, `backend/`, `frontend/`, `docker/`, `scripts/`, `.github/`.
- `docs/ai_context.md` — точка входа для AI-агентов.
- `docs/session_log.md` — журнал крупных этапов.
- `docs/changelog.md` — этот файл.
- `docs/adr/` — полноценные ADR-0001…0011 + шаблон.
- `CONTRIBUTING_AI.md` — правила для AI-агентов.
- Корневой `README.md`, `.gitignore`, GitHub issue/PR templates.
- `docs/architecture-overview.md` — единый shareable-документ (эквивалент Canvas).

### Changed

- `docs/decisions.md` превращён в индекс ADR (тексты перенесены в `docs/adr/`).
- Уточнена численность команды: 9 человек = 7 разработчиков + 2 QA.
- В типы `activity_events` добавлен `JIRA_COMMENT`.
- Workstream Type вместо хардкода платформ (Backend/Android/…).

### Notes

- Код приложения ещё не начат. Версия `0.1.0-docs` — только документация и bootstrap репозитория.
- Архитектурная концепция: **v2.1**.
