# Integrations

| | |
|---|---|
| **Status** | Accepted |
| **Version** | 2.1 |
| **Related** | [architecture.md](./architecture.md), [database.md](./database.md), [glossary.md](./glossary.md) |

## Principles

- Без очередей в MVP: **Scheduler + optional webhooks → PostgreSQL**.
- Join key: **Jira issue key**.
- Workstream Type назначается через **конфиг** (`repositories` / Jenkins job map), не через if/else по именам платформ в коде.
- Periodic reconcile обязателен даже при webhooks (пропуски доставки).

## Join map

| Source | Signal | → Issue key |
|---|---|---|
| Jira | `issue.key` | прямой |
| GitLab branch | `feature/MPTPSUPP-1234` | regex из имени |
| GitLab commit | `MPTPSUPP-1234: …` | regex из message |
| GitLab MR | `source_branch` / title | branch → key |
| Jenkins | `BRANCH_NAME` / `GIT_BRANCH` | branch → key |
| Jira links | blocks / is blocked by | dependency edges |

Пример regex ветки:

```text
feature\/(?<key>[A-Z]+-\d+)
```

(уточняется в Discovery под реальные conventions команды)

## Jira Server 8.20.30

| | |
|---|---|
| URL | `https://jira.eltc.ru` |
| Mode | **Primary: polling** каждые 2–5 мин |
| Auth | Service account / PAT |
| Optional | Webhook `issue_updated` → сразу INSERT (если админы разрешат) |

### What we sync

- Open sprints
- Issues (summary, status, assignee, fixVersion, components/labels as needed)
- Changelog / transitions → `JIRA_STATUS` events
- Comments → `JIRA_COMMENT` events (автор, время, текст/snippet)
- Issue links (blocks)

### JQL (example)

```text
sprint in openSprints() AND project = MPTPSUPP
```

Проектный ключ и поля уточняются на этапе Discovery.

## GitLab

| | |
|---|---|
| Mode | **Webhook preferred** + reconcile scheduler 15–30 мин |
| Auth | Project/Group token |
| Mapping | `gitlab project → workstream_type_code` |

### What we sync

- Push / branch create
- Commits
- Merge Requests (open / update / merge)
- Notes / approvals (по возможности API)

Oracle-разработка — **обычный GitLab (или Git) project** с `workstream_type_code = oracle` в конфиге. Отдельного «Oracle connector» нет.

Если изменения Oracle идут вне Git — слепая зона; процесс должен требовать Git. См. риски в [architecture.md](./architecture.md).

## Jenkins

| | |
|---|---|
| Mode | Webhook on build finished **или** poll известных jobs |
| Mapping | job / branch → issue key; type через repo/job map |

### What we sync

- Build started / finished
- Result, duration, parameters (`BRANCH_NAME`, MR id if present)

## Pseudocode (monolith)

```java
@Scheduled(fixedDelay = 180_000)
void syncJira() {
  // fetch open sprint → upsert issues + JIRA_STATUS events
}

@PostMapping("/hooks/gitlab")
void gitlab(@RequestBody payload) {
  // resolve issue key + workstream_type_code → write activity_events / MR / commits
}

@PostMapping("/hooks/jenkins")
void jenkins(@RequestBody payload) {
  // resolve branch → issue key → builds + BUILD_* events
}
```

## Configuration required before coding

1. Список GitLab projects + `workstream_type_code`
2. Список Jenkins jobs
3. Branch/commit naming convention
4. Jira project key, sprint board, relevant custom fields
5. Service accounts + webhook URL whitelist

## Change policy

Новый источник или смена режима sync → обновить **этот файл**, [architecture.md](./architecture.md), [roadmap.md](./roadmap.md) (Discovery checklist).
