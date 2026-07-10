# Architecture

| | |
|---|---|
| **Status** | Accepted |
| **Version** | 2.1 |
| **Related** | [vision.md](./vision.md), [database.md](./database.md), [integrations.md](./integrations.md), [decisions.md](./decisions.md) |

## Overview

AI Engineering Delivery Monitor — **modular monolith**:

```
Jira / GitLab / Jenkins
        │
        ▼
   Spring Boot
   (scheduler + webhooks + domain + REST)
        │
        ▼
   PostgreSQL
        │
        ▼
   React Dashboard
        │
        └──► AI Summary (отдельный сервис, после MVP)
```

Единый shareable-документ (эквивалент Canvas): [architecture-overview.md](./architecture-overview.md).  
Cursor canvas `architecture-design.canvas.tsx` — только локальный обзор в IDE.

## Stack (MVP)

| Layer | Choice |
|---|---|
| Backend | Spring Boot (один deployable) |
| Database | PostgreSQL |
| Ingest | `@Scheduled` pollers + optional webhooks → сразу в БД |
| API | REST |
| Frontend | React |
| AI | Отдельный сервис поверх REST (не в monolith) |

## Data flow

1. Scheduler тянет Jira / GitLab / Jenkins (и/или принимает webhooks).
2. Webhook payload пишется сразу в БД (нормализация в том же запросе/транзакции или сразу после).
3. Сервис линкует сущности по **issue key** и назначает **Workstream Type** через конфиг репозиториев/jobs.
4. REST отдаёт данные экранам.
5. AI Summary (позже) читает только REST Monitor, не БД.

## Backend packages

| Package | Responsibility | MVP |
|---|---|---|
| `integration.jira` | Poll sprint / issues / changelog / links | Yes |
| `integration.gitlab` | Poll/webhook: branches, commits, MR, notes | Yes |
| `integration.jenkins` | Poll/webhook: builds | Yes |
| `domain.issue` | Issue + sprint + fixVersion | Yes |
| `domain.workstream` | Workstream = issue × Workstream Type | Yes |
| `domain.workstream_type` | Справочник типов (config/data, не хардкод) | Yes |
| `domain.timeline` | Единый поток событий по задаче | Yes |
| `domain.activity` | Командный activity feed | Yes |
| `domain.release` | Release Health по fixVersion | Yes |
| `domain.risk` | Правила рисков | Yes |
| `api` | REST controllers | Yes |
| AI Summary service | REST → LLM → markdown | After MVP |

## Core abstractions

### Issue key

Якорь системы. Пример: `MPTPSUPP-1234`. Извлекается из Jira напрямую и из GitLab/Jenkins через naming веток / commit messages / MR.

### Workstream Type

Конфигурируемый тип потока работы (`backend`, `frontend`, `oracle`, `qa`, …).  
Система **не** содержит захардкоженных знаний о конкретных платформах.

### Workstream

`Workstream = Issue × Workstream Type`.  
Статус выводится из веток, MR, builds и (при необходимости) Jira transitions.

### activity_events

Единая таблица фактов. Из неё строятся Issue Timeline и Activity Feed.  
Подробности: [database.md](./database.md).

## What we deliberately avoid (for now)

| Idea | Why deferred |
|---|---|
| CQRS / event sourcing | Нет объёма и команды на две модели |
| RabbitMQ / Redis Streams | Сотни событий/день; webhook → DB достаточно |
| Redis cache | Postgres + короткий poll хватает |
| GraphQL | Четыре экрана — REST проще |
| Notification service | Не проблема №1 |
| People screen in MVP | Боль про задачи и релиз |
| AI inside monolith | Связывает продукт с вендором LLM |

<<<<<<< HEAD
См. [decisions.md](./decisions.md) и каталог [adr/](./adr/).
=======
См. [decisions.md](./decisions.md).
>>>>>>> 48a07f2d2fb95923c83a28ff08044d9a9c5f8691

## Architectural risks

| Risk | Mitigation |
|---|---|
| Кривой naming веток | Regex + orphan report; soft-link по MR title |
| Репозиторий без Workstream Type | Обязательный map `repo → workstream_type_code` |
| Шум inferred-блокеров | Явные Jira links vs soft inferred |
| Медленный Jira Server poll | Инкремент по `updated` |
| Пустой Timeline | Jira transitions с этапа 2 |
| Release % врёт | Прозрачная формула + drill-down |

## When architecture changes

Обновляйте этот файл и связанные:

- схема → [database.md](./database.md)
- контракты → [api.md](./api.md)
- источники → [integrations.md](./integrations.md)
- экраны → [ux.md](./ux.md)
<<<<<<< HEAD
- trade-offs → новый файл в [adr/](./adr/) + [decisions.md](./decisions.md)
- план → [roadmap.md](./roadmap.md)
- крупный этап → [session_log.md](./session_log.md), [changelog.md](./changelog.md)
=======
- trade-offs → [decisions.md](./decisions.md)
- план → [roadmap.md](./roadmap.md)
>>>>>>> 48a07f2d2fb95923c83a28ff08044d9a9c5f8691
