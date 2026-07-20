# Frontend — Монитор доставки

Минимальный React product layer (Phase 4.3). UI на русском; API и backend не меняются.

## Экраны

| Route | Экран | APIs |
|---|---|---|
| `/` | Дашборд — Проекты, Риски, Последняя активность | `GET /api/workstreams/progress`, `/api/risks`, `/api/activity` |
| `/activity` | Лента активности | `GET /api/activity`, `/api/workstream-types` |
| `/issues/:key` | История задачи | `GET /api/issues/{key}`, `/api/issues/{key}/timeline` |

## Локализация

Без i18n-библиотеки: строки в компонентах + мапперы:

- `src/lib/labels.ts` — риски, типы событий, известные workstream codes (`backend`→Бэкенд, …)
- `src/lib/format.ts` — даты `ru-RU`, «Сегодня»/«Вчера», `eventDetail()` для русских detail-строк вместо английских `summary` с API

Технические идентификаторы (issue keys, имена репозиториев, Git/Jira/MR) не переводятся.

## Запуск локально

Backend должен быть на `http://localhost:8080` (Vite проксирует `/api`).

```bash
cd frontend
npm install
npm run dev
```

Открыть `http://localhost:5173`.

## Stack

React + TypeScript + Vite + React Router. Без design system — read-only UI поверх Monitor REST.

См.:

- [docs/ux.md](../docs/ux.md)
- [docs/api.md](../docs/api.md)
- [docs/roadmap.md](../docs/roadmap.md)
