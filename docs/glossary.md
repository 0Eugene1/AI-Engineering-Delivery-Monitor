# Glossary

| | |
|---|---|
| **Status** | Living |
| **Version** | 2.1 |

| Term | Definition |
|---|---|
| **Monitor** | AI Engineering Delivery Monitor — этот продукт |
| **Issue key** | Ключ Jira-задачи, якорь связей (например `MPTPSUPP-1234`) |
| **Workstream Type** | Конфигурируемый тип потока работы (`code` + `display_name`). Не хардкодится в домене |
| **Workstream** | Экземпляр работы: Issue × Workstream Type |
| **Derived status** | Статус workstream, вычисленный из Git/MR/Build/Jira, не введённый вручную в Monitor |
| **activity_events** | Таблица фактов; основа Timeline и Activity Feed |
| **JIRA_COMMENT** | Тип `activity_events`: комментарий в Jira (автор, время, текст/snippet) |
| **Issue Timeline** | Хронология событий одной задачи |
| **Activity Feed** | Глобальная лента событий команды |
| **Release Health** | Готовность `fixVersion` в разрезе активных Workstream Types (%) |
| **fixVersion** | Версия релиза в Jira; ось Release Health |
| **Risk flag** | Автоматически выставленный признак риска по правилу |
| **Explicit dependency** | Связь из Jira issue links (blocks / is blocked by) |
| **Inferred dependency** | Мягкий вывод системы (например один type ждёт merge другого); не равен Jira link |
| **Orphan branch** | Ветка, из которой не извлечён issue key / нет маппинга |
| **Shadow mode** | Пилот: Monitor рядом с Jira без замены процесса |
| **Reconcile** | Периодическая полная/инкрементальная сверка с источником при наличии webhooks |
| **AI Summary** | Отдельный сервис поверх REST Monitor; не часть monolith |

## Current team Workstream Types (example seed)

Это **данные конфигурации**, не часть словаря домена:

| code | display_name |
|---|---|
| `backend` | Backend |
| `frontend` | Frontend |
| `oracle` | Oracle |
| `qa` | QA |

## Change policy

Новый термин или переименование сущности → обновить **этот файл** и места использования в docs.
