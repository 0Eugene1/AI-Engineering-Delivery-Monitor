# Vision — AI Engineering Delivery Monitor


|              |                                         |
| ------------ | --------------------------------------- |
| **Status**   | Accepted                                |
| **Version**  | 2.1                                     |
| **Audience** | Product owner, tech lead, delivery team |


## Problem

В одной Jira-задаче часто участвуют несколько потоков разработки (разные репозитории и роли). Сейчас невозможно быстро понять:

- кто реально работает над задачей;
- что уже сделано и что осталось;
- кто кого блокирует;
- успевает ли команда попасть в релиз.

Разработчики не должны вручную обновлять ещё одну систему. Источник правды — уже существующие инструменты.

## Goal

Внутренний веб-dashboard, который **автоматически** собирает события из Jira, GitLab и Jenkins и показывает реальное состояние разработки задач в спринте и готовность релиза.

## Non-goals (MVP)

- Замена Jira как системы учёта задач
- Ручной ввод статусов в Monitor
- AI внутри основного backend
- Уведомления (Slack/Teams digests)
- Экран People / WIP как приоритет MVP



## Users


| Роль           | Что нужно увидеть                                            |
| -------------- | ------------------------------------------------------------ |
| Tech lead / SM | Риски спринта, блокеры, готовность релиза                    |
| Developer      | Timeline своей задачи, MR, builds, соседние workstreams      |
| QA             | Какие workstreams уже merged/build_ok, что можно тестировать |




## Team context

- 9 человек: 7 разработчиков + 2 QA
- Спринт 2 недели: неделя 1 — разработка; неделя 2 — QA, сборки, исправления, релиз
- Jira Server 8.20.30 (`https://jira.eltc.ru`)
- GitLab (ветки вида `feature/<JIRA-KEY>`)
- Jenkins
- Отдельный Git-репозиторий Oracle-разработки (как обычный GitLab project)



## Product principles

1. **Read-only проекция** — Monitor не пишет в Jira/GitLab/Jenkins (кроме приёма webhooks).
2. **Issue key — якорь** — всё линкуется по Jira key (например `MPTPSUPP-1234`).
3. **Workstream Type конфигурируем** — система не хардкодит Backend/Frontend/Oracle/QA.
4. **Timeline-first** — главный UX ответа на «что происходит с задачей».
5. **Простая архитектура** — Spring Boot + PostgreSQL + Scheduler + REST до появления реальной боли.



## Success criteria (pilot)

После 2 спринтов в shadow mode:

- на стендапе команда опирается на Sprint Board / Risks / Release Health, а не только на Jira;
- Issue Timeline заполняется без ручного ввода для ≥90% задач с корректным naming веток;
- нет обязательного ручного обновления Monitor.



## Related docs

- [ai_context.md](./ai_context.md)
- [architecture.md](./architecture.md)
- [architecture-overview.md](./architecture-overview.md)
- [ux.md](./ux.md)
- [roadmap.md](./roadmap.md)
- [decisions.md](./decisions.md) / [adr/](./adr/)

