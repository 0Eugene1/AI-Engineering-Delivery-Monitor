# Architecture Decision Records

Индекс принятых решений. Полные тексты — в отдельных файлах `docs/adr/`.

| ADR | Title | Status |
|---|---|---|
| [0001](./adr/0001-jira-source-of-truth.md) | Jira issue key as integration source of truth | Accepted |
| [0002](./adr/0002-workstream-type.md) | Configurable Workstream Type | Accepted |
| [0003](./adr/0003-modular-monolith.md) | Modular monolith (Spring Boot + PostgreSQL) | Accepted |
| [0004](./adr/0004-polling-and-webhooks.md) | Polling and webhooks (no message broker) | Accepted |
| [0005](./adr/0005-no-cqrs-in-mvp.md) | No CQRS / event sourcing in MVP | Accepted |
| [0006](./adr/0006-no-broker-redis-in-mvp.md) | No message broker / Redis in MVP | Accepted |
| [0007](./adr/0007-rest-only.md) | REST only (no GraphQL) | Accepted |
| [0008](./adr/0008-activity-events.md) | Timeline and Activity Feed share activity_events | Accepted |
| [0009](./adr/0009-ai-summary-separate-service.md) | AI Summary as a separate service | Accepted (post-MVP) |
| [0010](./adr/0010-people-notifications-out-of-mvp.md) | People screen and Notifications out of MVP | Accepted |
| [0011](./adr/0011-docs-markdown-source-of-truth.md) | Documentation lives in Markdown under docs/ | Accepted |
| [0012](./adr/0012-minimal-auth-baseline-admin-endpoints.md) | Minimal authentication baseline for admin endpoints | Accepted |

## Design notes (within accepted ADRs — no separate ADR)

Мелкие реализационные решения, не меняющие архитектуру и не требующие отдельного ADR (см. [ai_context.md](./ai_context.md) §6/§7 — новый ADR нужен только при изменении стека/границ/крупного решения).

| Date | Decision | Rationale | Within ADR |
|---|---|---|---|
| 2026-07-14 | Jira auth как переключаемая стратегия (`basic`/`bearer`) | discovery.md §1 оставляет «PAT vs basic» открытым; выбор конфигом, не хардкод | ADR-001, ADR-003 |
| 2026-07-15 | **`JiraContextProvider` + config-switchable mock** (Task 2.3): интерфейс над `JiraClient`; `RestJiraContextProvider` (default) и `MockJiraContextProvider` (санитизированные demo-данные), выбор через `jira.mode=rest\|mock`; mock защищён от production | Разработка следующих слоёв без реального Jira-аккаунта; переход на реальную Jira = смена конфига, не переписывание кода. Не добавляет persistence/scheduler/REST/брокеров — остаётся read-only projection поверх Jira | ADR-001, ADR-003, ADR-004, ADR-005, ADR-006, ADR-007, ADR-011 |
| 2026-07-15 | **Phase 2.3 Persistence — дизайн согласован** (реализация ещё не начата): `domain.issue` — единственный владелец persistence-контрактов (`IssueEntity`, `IssueRepository`, `IssuePersistencePort`, `IssueUpsertCommand`, `IssueUpsertOutcome`, `IssueUpsertService`); зависимость строго `sync.jira → domain.issue`, обратной зависимости нет — `domain.issue` ничего не импортирует из `sync.jira`. `JiraIssueSnapshot` остаётся собственным контрактом `sync.jira` (нормализованный вид Jira-issue из integration-слоя); `IssueUpsertCommand` — отдельный входной контракт `domain.issue`, `sync.jira` сам маппит `JiraIssueSnapshot → IssueUpsertCommand` перед вызовом порта. Matching существующей записи при upsert — по `jiraId` (иммутабельный Jira internal id); `key` остаётся бизнес-якорем (ADR-001) и уникальным индексом, но **не** используется для поиска строки (устойчивость к переносу issue между проектами в Jira). Upsert выполняется **постранично** (страница из `JiraContextProvider` → `IssuePersistencePort.upsertPage(...)` → `saveAll` на уровне страницы), а не `saveAll` после сбора всего списка. `JiraSyncResult` больше не хранит `List<JiraIssueSnapshot> issues` — только агрегаты `created`/`updated` (+ `mocked`/`errors`/`fetched`/`pages`); `saved()` — derived-метод (`created + updated`), не отдельное хранимое поле. `fixVersions`/`labels` сохраняются через `@ElementCollection` (`issue_fix_versions`/`issue_labels`) — множественность не теряется, обе симметричны. Таблицы `sprints`/`sync_state` **отложены** — в Phase 2.3 нет ни sprint-данных, ни incremental sync/watermark, заводить пустые таблицы без писателя признано избыточным | Соблюдение Dependency Rule (domain не знает о слоях выше); `jiraId` устойчив к переносу issue между проектами, `key` — нет; постраничный upsert ограничивает память и упрощает будущий retry/scheduler; не создаём таблицы, которые нечем заполнять | ADR-001, ADR-003, ADR-011 |
| 2026-07-15 | **ADR-012 implementation note — admin token validation is intentionally stateless.** Из токена **не** извлекается идентичность пользователя (no user identity from token). Audit-записи привилегированных действий хранят **только execution metadata** (endpoint, время, источник/IP, результат `{ fetched, saved, errors }`), **не** конкретную персону — до тех пор, пока OIDC не введёт principal identity. Честный субъект при static token сейчас — «обладатель admin-токена», а не именованный сотрудник (напр. не `Eugene`) | Не «притягивать» пользователя из воздуха: static admin-token не несёт identity, поэтому аудит не должен приписывать действие конкретному человеку. Персональный аудит (кто именно запустил sync) появляется вместе с OIDC/SSO ([security.md](./security.md) §2, §7) | ADR-012 |
| 2026-07-15 | **ADR-012 — реализован как согласовано (Phase 2.4).** Пакет `api.admin` (`JiraSyncController` — `POST /api/admin/sync/jira`, тонкий HTTP-адаптер над `sync.jira.JiraSyncService`, реюз `JiraSyncResult` без отдельного response DTO) и пакет `api.security` (`SecurityConfig`, `AdminTokenAuthenticationFilter` — `OncePerRequestFilter`, `AdminTokenProperties` ⇐ `DELIVERY_MONITOR_ADMIN_TOKEN`, fail-fast как `JIRA_TOKEN`). Без отклонений от Decision в самом ADR. Одно уточнение, принятое самостоятельно при реализации (не меняет ADR, но сужает endpoint policy до буквы): **любой** `/actuator/**` кроме `/actuator/health` требует admin-токен (не только non-health/liveness в общем смысле) — `/actuator/info` тоже раскрывает build-конфигурацию, поэтому явно защищён, а не оставлен в «остальное как было». Не создавались: `User`/`Role` entity, `UserRepository`, principal model, permissions, JWT/OAuth2 Resource Server/OIDC/LDAP — как и требовалось. Тесты: `AdminTokenAuthenticationFilterTest` (unit, без Spring), `AdminTokenPropertiesTest` (fail-fast biding), `JiraSyncControllerTest` (`@WebMvcTest` + реальный `SecurityConfig`, mock `JiraSyncService` — 200 с верным токеном / 401 без токена / 401 с неверным токеном), плюс два новых интеграционных теста в `DeliveryMonitorApplicationTests` (401 на admin-эндпоинт и на `/actuator/info` без токена, в полном контексте). `.\mvnw.cmd clean verify` — 63 теста (было 49), 0 failures, 0 errors, 2 skipped (`JiraSmokeTest` без токена) | Подтверждает, что дизайн ADR-012 реализуем без обхода собственных ограничений; explicit actuator-правило — чтобы политика ADR не читалась только «health публичен», а остальное разрешено по умолчанию | ADR-012 |

Подробности — в [session_log.md](./session_log.md).

## How to add a decision

1. Скопируйте [adr/0000-template.md](./adr/0000-template.md).
2. Создайте `docs/adr/00XX-short-title.md` со следующим свободным номером.
3. Добавьте строку в таблицу выше.
4. Обновите затронутые документы (`architecture.md`, `database.md`, `api.md`, `integrations.md`, `ux.md`, `roadmap.md`, …).
5. Добавьте запись в [changelog.md](./changelog.md) и при крупном этапе — в [session_log.md](./session_log.md).

> Устаревшее монолитное содержимое ADR больше не ведётся в этом файле — только индекс.
