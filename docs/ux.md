# UX

| | |
|---|---|
| **Status** | Accepted |
| **Version** | 2.1 |
| **Related** | [vision.md](./vision.md), [api.md](./api.md), [roadmap.md](./roadmap.md) |

## Design principles

1. **Timeline-first** — главный ответ на «что с задачей» — хронология событий.
2. **Задачи важнее людей** — People/WIP не в MVP.
3. **Типы из конфига** — подписи Workstream Type приходят из API (`workstream-types`), не захардкожены в UI.
4. **Без ручного ввода** — UI только читает Monitor.

## Screens

| Screen | Purpose | Priority |
|---|---|---|
| Sprint Board | Задачи спринта + workstreams + risk badges | P0 |
| Issue Detail + Timeline | Хронология по Workstream Type + MR/Build | P0 (hero) |
| Activity Feed | Лента команды (как GitHub) | P0 |
| Release Health | `%` готовности по каждому Workstream Type для fixVersion | P0 |
| Risks | Список/фильтр на Board | P1 |
| People / WIP | Нагрузка людей | After pilot |
| AI Summary | Кнопка Summarize → отдельный сервис | After MVP |

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

## Activity Feed

Глобальная лента последних событий команды:

```text
10:12  Иван     MR merged          MPTPSUPP-1201  backend
10:18  —        commit             MPTPSUPP-1234  oracle
10:25  Мария    WORKSTREAM_STARTED MPTPSUPP-1188  qa
```

Фильтры (MVP-минимум): по Workstream Type, по issue key (опционально позже).

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
