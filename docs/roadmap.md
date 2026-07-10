# Roadmap

| | |
|---|---|
| **Status** | Accepted |
| **Version** | 2.1 |
| **Related** | [vision.md](./vision.md), [architecture.md](./architecture.md), [ux.md](./ux.md) |

## Guiding rule

Каждый этап даёт проверяемый результат для команды, а не «инфраструктуру ради инфраструктуры».

**Первая ценность — этап 3** (GitLab + Issue Timeline).

## Phases

| Phase | Duration | Deliverable | Done when |
|---|---|---|---|
| **0. Discovery** | 3–5 days | Seed Workstream Types, repo→type map, Jenkins jobs, naming convention, service accounts | Документ маппингов + доступы выданы |
| **1. Skeleton** | 3–5 days | Spring Boot + PostgreSQL + auth + пустой UI | Deploy на внутренний стенд |
| **2. Jira + Board** | ~1 week | Sprint Board + `JIRA_STATUS` / `JIRA_COMMENT` в `activity_events` | Состав board совпадает с Jira sprint |
| **3. GitLab + Timeline** | 1.5–2 weeks | Workstreams по Type + Issue Timeline | ≥90% задач спринта с корректным naming имеют события в Timeline |
| **4. Activity Feed + Risks** | 3–5 days | Лента команды + risk badges на board | Стендап можно вести по Risks/Feed |
| **5. Jenkins + Release Health** | ~1 week | Builds в timeline + % по каждому Workstream Type | Failed build → risk; Release Health drill-down работает |
| **6. Pilot** | 2 sprints | Shadow mode рядом с Jira | Нет обязательного ручного ввода |
| **7. AI Summary** | after MVP | Отдельный сервис, кнопка Summarize | Summary строится только из REST Monitor |

## MVP scope (screens)

In MVP:

- Sprint Board
- Issue Detail + Timeline
- Activity Feed
- Release Health
- Risks as filter/tab on Board (P1)

Out of MVP:

- People / WIP
- Notifications
- AI Summary service
- GraphQL, queues, Redis, CQRS

## Current team seed (config, not code)

Пример Workstream Types для текущей команды (данные):

- `backend`
- `frontend`
- `oracle`
- `qa`

Любое изменение набора типов — конфиг/`workstream_types`, не релиз доменной модели. См. [database.md](./database.md).

## Tracking changes

При сдвиге приоритетов или scope обновляйте этот файл и при необходимости [vision.md](./vision.md) / [ux.md](./ux.md).
