# UX

| | |
|---|---|
| **Status** | Accepted |
| **Version** | 2.3 |
| **Related** | [vision.md](./vision.md), [api.md](./api.md), [roadmap.md](./roadmap.md) |

## Design principles

1. **Timeline-first** — главный ответ на «что с задачей» — хронология событий.
2. **Задачи важнее людей** — People/WIP не в MVP.
3. **Типы из конфига** — набор Workstream Type приходит из API (`workstream-types`); UI не хардкодит список типов. Известные `code` → русские подписи маппятся на frontend (`labels.ts`); неизвестные — fallback на `displayName` API.
4. **Без ручного ввода** — UI только читает Monitor.
5. **UI на русском** — пользовательские тексты (заголовки, риски, типы событий, empty/error) локализованы во frontend без i18n-библиотеки и без смены API. Технические идентификаторы (issue keys, MR `!N`, Git/Jira) остаются как есть.

## Screens

| Screen | Purpose | Priority | Status |
|---|---|---|---|
| **Дашборд (Монитор доставки)** | Проекты + агрегаты рисков + последняя активность | P0 | **Done (4.3)** — `/` |
| Sprint Board | Задачи спринта + workstreams + risk badges | P0 | Deferred (нет `sprints`) |
| История задачи (Timeline) | Хронология по Workstream Type + MR/Build | P0 (hero) | **Done (4.3)** — `/issues/:key` |
| Лента активности | Лента команды (как GitHub) | P0 | **Done (4.3)** — `/activity` |
| Release Health | `%` готовности по каждому Workstream Type для fixVersion | P0 | Phase 5 |
| Риски | Список/фильтр на Board | P1 | Агрегаты на дашборде (4.3) |
| People / WIP | Нагрузка людей | After pilot | — |
| AI Summary | Кнопка Summarize → отдельный сервис | After MVP | — |

## Delivery Dashboard (Phase 4.3)

Минимальный главный экран (`frontend/`, route `/`):

```text
Монитор доставки

Проекты
Бэкенд    ████████░░ 80%
Фронтенд  ██████░░░░ 60%

Риски
🔴 12 Просроченный открытый Merge Request
🟡 24 Нет активности
🟠 5 Jira без Git-активности

Последняя активность
MPTPSUPP-43006
  Merge Request смержен
  Коммит
```

Источники: `GET /api/workstreams/progress`, `GET /api/risks`, `GET /api/activity`.
Клик по issue key → История задачи. Набор типов — из API; русские подписи известных codes — frontend mapper.

## Sprint Board

Показывает issues активного спринта.

На карточке/строке задачи:

- Jira key, summary, status
- Чипы активных workstreams (code/display_name + derived status)
- Risk badges
- Last activity (время + короткий summary)

Клик → Issue Detail.

## Issue Detail + Timeline (hero)

Пример (типы — из конфига команды):

```text
MPTPSUPP-1234

09:15  backend   создал ветку feature/MPTPSUPP-1234
11:20  backend   commit — API endpoint
12:05  oracle    commit — package body
13:40  frontend  commit — UI form
14:30  backend   MR !88 opened
16:00  build     Jenkins #412 SUCCESS
```

Рядом с timeline:

- список workstreams и derived status;
- открытые MR / last builds;
- blockers (explicit vs inferred).

Пользователь должен за секунды понять: кто работал, в каком порядке шли Workstream Types, где застряли.

## Лента активности (Activity Feed)

Глобальная лента последних событий команды (`/activity`):

```text
Сегодня
🌱 MPTPSUPP-1201  Создана ветка     👤 Иван     Бэкенд
💻 MPTPSUPP-1234  Коммит            👤 —        Oracle
🔀 MPTPSUPP-1188  Открыт Merge Request  👤 Мария  QA
```

Фильтры (MVP-минимум): по Workstream Type (`Все` + типы из API), по issue key (опционально позже).
Подписи событий/рисков: `frontend/src/lib/labels.ts`; detail-строки timeline: `eventDetail` в `format.ts` (не английский `summary` с backend).

## Release Health

Для выбранного `fixVersion` — строка на каждый **активный** Workstream Type:

```text
5.7.27
backend   90%
frontend  70%
oracle   100%
qa        20%
```

- Набор строк = `GET /api/workstream-types` (active), не фиксированный список в коде.
- Клик по строке → список issues/workstreams, из которых посчитан процент.
- Формула: см. [api.md](./api.md) / [database.md](./database.md).

## Risks (P1)

Не отдельный «сервис», а экран или вкладка:

- сортировка по severity;
- код риска + issue + короткое объяснение;
- переход в Issue Detail.

Правила рисков (логика backend) документируются в [architecture.md](./architecture.md) / позже отдельном `risks.md` при росте.

## AI Summary (after MVP)

Кнопка на Issue / Sprint / Release:

1. UI вызывает AI Summary service.
2. Сервис читает Monitor REST.
3. Показывает markdown (не редактирует данные Monitor).

## Accessibility / ops notes

- Внутренний инструмент: desktop-first допустим для MVP.
- Явно показывать `last sync` / stale warning, если scheduler отстаёт.

## Change policy

Новый экран или изменение hero-flow → обновить **этот файл**, [api.md](./api.md), [roadmap.md](./roadmap.md).
