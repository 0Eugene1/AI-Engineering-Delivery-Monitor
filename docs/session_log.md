# Session Log

Журнал крупных этапов. Цель: за **~5 минут** понять состояние проекта без чтения чатов.

Правило: после каждого завершённого крупного этапа добавляйте запись **в начало** (новее сверху).

Шаблон записи:

```markdown
## YYYY-MM-DD — <краткий заголовок этапа>

**Stage:** …
**Summary:** …
**Decisions:** …
**Docs touched:** …
**Next:** …
```

---

## 2026-07-15 — Phase 2.4 Admin Sync HTTP API implemented

**Stage:** Phase 2.4 «Admin Sync HTTP API» ([roadmap.md](./roadmap.md), [ADR-012](./adr/0012-minimal-auth-baseline-admin-endpoints.md)) — **реализована** по согласованному дизайну (ADR-012 Decision, ранее задокументировано в Design notes). Первый HTTP-controller и первый security-слой в проекте. Сознательно **не добавлялись** (явные ограничения задачи): OIDC/JWT/LDAP, `User`/`Role` entity, `UserRepository`, principal model, permissions, UI, scheduler, webhook security, audit database, incremental sync, async execution, retry.

**Summary:**

Перед реализацией проверена текущая структура: package root `ru.eltc.deliverymonitor`, `DeliveryMonitorApplication` (обычный `@SpringBootApplication`, без существующих `@Configuration` security-классов), `application.yml` (env-driven конфиг по образцу `jira.*`), `backend/pom.xml` (`spring-boot-starter-security` отсутствовал — добавлен). Никакого `api`-пакета и контроллеров в коде не было — реализация с нуля, без параллельной архитектуры.

1. **`api.admin.JiraSyncController`** — `POST /api/admin/sync/jira`. Чистый HTTP-адаптер: принимает запрос, вызывает `sync.jira.JiraSyncService#syncBoard()`, возвращает его `JiraSyncResult` как есть (`ResponseEntity.ok(result)`) — без отдельного response DTO (реюз существующего application-layer контракта) и без бизнес-логики в контроллере. Без request body — фильтр/страница берутся из конфига (`jira.default-filter-id`, `jira.sync.page-size`), как и было в `JiraSyncService` до этой задачи.
2. **`api.security` (новый пакет):**
   - **`AdminTokenProperties`** — `delivery-monitor.admin.token` ⇐ `DELIVERY_MONITOR_ADMIN_TOKEN`, `@Validated`/`@NotBlank`, тот же fail-fast паттерн, что у `JiraProperties.Auth#token` (`JIRA_TOKEN`).
   - **`AdminTokenAuthenticationFilter`** — `OncePerRequestFilter`. Читает `Authorization` header, проверяет префикс `Bearer `, сравнивает токен с конфигом константным по времени сравнением (`MessageDigest.isEqual`). При совпадении кладёт в `SecurityContextHolder` generic `Authentication` с единственной authority `ROLE_ADMIN` и principal-заглушкой `"admin-token"` — **никакой** идентичности из токена не извлекается (stateless, как и требовалось). Пустой/blank сконфигурированный токен никогда не аутентифицирует даже пустой предъявленный токен (защита от вырождения «токен не задан → доступ открыт всем»). Сам токен (ни предъявленный, ни ожидаемый) не логируется ни при успехе, ни при отказе.
   - **`SecurityConfig`** (`@EnableWebSecurity`) — `SecurityFilterChain`: `/actuator/health` открыт; `/api/admin/**` **и** любой другой `/actuator/**` (в т.ч. `/actuator/info` — раскрывает build-конфигурацию) требуют аутентификации; остальное не тронуто (`anyRequest().permitAll()` — read-эндпоинтов пока нет). CSRF отключён и sessions — `STATELESS` (machine-to-machine Bearer, не браузерная сессия). Отказ аутентификации → `401` через `HttpStatusEntryPoint`, не redirect на login. Фильтр подключён `addFilterBefore(..., UsernamePasswordAuthenticationFilter.class)`. Никакого `JWT`/`OAuth2ResourceServer`/`OIDC`/`LDAP`.
3. **`backend/pom.xml`** — добавлены `spring-boot-starter-security` (main) и `spring-security-test` (test, `@MockitoBean`/MockMvc).
4. **`application.yml`** — `delivery-monitor.admin.token: ${DELIVERY_MONITOR_ADMIN_TOKEN:}` (пустой default — секрет не коммитится, fail-fast при старте без него).

**Тесты (новые — 14, итог 63, было 49):**

- `AdminTokenAuthenticationFilterTest` (6, unit, без Spring context): корректный Bearer → authenticated с `ROLE_ADMIN`; без header; неверный токен; не-Bearer схема (`Basic`); пустой Bearer-токен; пустой сконфигурированный токен никогда не матчит.
- `AdminTokenPropertiesTest` (3): биндинг значения, fail-fast при отсутствии/blank токене (по образцу `JiraPropertiesTest`).
- `JiraSyncControllerTest` (3, `@WebMvcTest(JiraSyncController.class)` + `@Import(SecurityConfig.class)`, mock `JiraSyncService` — без реальной Jira/PostgreSQL): 200 + тело `JiraSyncResult` с верным токеном; 401 без header; 401 с неверным токеном; во всех отрицательных случаях `verifyNoInteractions(jiraSyncService)`.
- `DeliveryMonitorApplicationTests` (+2, полный контекст на H2, без реальной Jira/PostgreSQL): `adminSyncJiraRequiresAuthentication` (401 без header — до контроллера/сервиса запрос не доходит) и `actuatorInfoRequiresAuthenticationButHealthDoesNot` (401 на `/actuator/info`, health по-прежнему открыт). Добавлен placeholder `delivery-monitor.admin.token` в `@DynamicPropertySource` (аналогично `jira.auth.token`).

`.\mvnw.cmd clean verify` (JDK 21, Android Studio JBR) — **63 теста, 0 failures, 0 errors, 2 skipped** (оба — `JiraSmokeTest`, без токена) → `BUILD SUCCESS`.

**Deviations from the agreed design (обнаружено при реализации, зафиксировано явно):**

- **Endpoint policy для actuator сужена до буквы, не только по духу.** Согласованный дизайн ADR-012 говорит «actuator, кроме `health`/liveness — Protected»; при реализации это явно закодировано как отдельное правило `.requestMatchers("/actuator/**").authenticated()` **после** `/actuator/health`, а не оставлено на volume «остальное не трогаем» (`anyRequest().permitAll()`) — иначе `/actuator/info` (build info, потенциально раскрывает конфигурацию) остался бы публичным по умолчанию, что противоречило бы явной строке ADR-012's endpoint policy table. Не расширяет и не сужает сам ADR — только более строгая буквальная реализация уже согласованного правила.
- **Response не содержит производного поля `saved`.** `JiraSyncResult.saved()` — Java-метод (derived, `created + updated`), не record-компонент, поэтому Jackson его не сериализует в JSON. `docs/api.md`'s более ранний response-sketch показывал `saved` как поле — исправлено на фактическую форму (`startedAt`/`finishedAt`/`fetched`/`pages`/`mocked`/`created`/`updated`/`errors`); `saved` в JSON **не появляется**, клиент может посчитать сумму сам при необходимости. Не решение, а исправление устаревшего sketch под уже принятый (Phase 2.3) контракт `JiraSyncResult`.
- Остальное реализовано **без отклонений** от ADR-012 Decision: владение контрактами (`JiraSyncController` реюзает `JiraSyncResult`, не вводит DTO), разделение токенов (`DELIVERY_MONITOR_ADMIN_TOKEN` ≠ `JIRA_TOKEN`), fail-fast на отсутствующий admin-токен, stateless-модель без identity/User/Role/permissions, отсутствие JWT/OAuth2/OIDC/LDAP.

**Decisions:**

- Архитектура не менялась — реализация строго в рамках ADR-012 Decision (см. предыдущие Design notes записи). Новый ADR не создавался.
- Endpoint-policy строгая буквальная трактовка actuator-правила — реализационная деталь, задокументирована как отклонение здесь и в `decisions.md`, а не молча.

**Docs touched:**

- `docs/architecture.md` (v2.3 — состав `api` пакета: `api.admin`/`api.security` реализованы, package dependency direction дополнен)
- `docs/api.md` (v2.3 — admin sync endpoint помечен «implemented», response sketch исправлен под реальный `JiraSyncResult`, убран несуществующий request body)
- `docs/security.md` (v1.2 — §2 baseline помечен «реализован», §9 checklist аннотирован)
- `docs/decisions.md` (новая строка Design notes — подтверждение реализации ADR-012 без отклонений от Decision + одно самостоятельно принятое уточнение)
- `docs/ai_context.md` (Stage/версия — Phase 2.4 done, next — GitLab/Timeline или read API/scheduler)
- `docs/session_log.md` (this entry), `docs/changelog.md`

**Code touched (new):**

- `backend/src/main/java/.../api/{package-info,admin/{JiraSyncController,package-info},security/{SecurityConfig,AdminTokenAuthenticationFilter,AdminTokenProperties,package-info}}.java`
- `backend/src/test/java/.../api/admin/JiraSyncControllerTest.java`
- `backend/src/test/java/.../api/security/{AdminTokenAuthenticationFilterTest,AdminTokenPropertiesTest}.java`

**Code touched (modified):**

- `backend/pom.xml` (`spring-boot-starter-security`, `spring-security-test`)
- `backend/src/main/resources/application.yml` (`delivery-monitor.admin.token`)
- `backend/src/main/java/.../sync/jira/package-info.java`, `backend/src/main/java/.../domain/issue/package-info.java` (Javadoc: `POST /api/admin/sync/jira` больше не «out of scope» — реализован в `api.admin`, обе стороны его не импортируют)
- `backend/src/test/java/.../DeliveryMonitorApplicationTests.java` (`delivery-monitor.admin.token` placeholder + 2 новых теста)

**Next:**

1. `GET /api/issues`, `GET /api/sprints/current` (read API поверх `domain.issue`, не live Jira) и/или Phase 2.5 scheduler — по [roadmap.md](./roadmap.md). Не начинать без явного go-ahead.
2. Получить реальный `JIRA_TOKEN` и `DELIVERY_MONITOR_ADMIN_TOKEN`, прогнать `JiraSmokeTest` и живой `POST /api/admin/sync/jira` — тот же открытый `TODO`, что и в предыдущих записях.

---

## 2026-07-15 — Phase 2.3 Persistence implemented

**Stage:** Phase 2.3 «Persistence» ([roadmap.md](./roadmap.md)) — **реализована** по дизайну, согласованному в предыдущей записи ("Phase 2.3 Persistence design approved"). Без изменений в архитектурных решениях — все они подтверждены и реализованы как согласовано. Сознательно **не добавлялись**: REST controller, Spring Security, scheduler, `sync_state`, `sprints`, incremental sync, GitLab/Jenkins (явные ограничения задачи).

**Summary:**
Новый пакет `ru.eltc.deliverymonitor.domain.issue` — единственный владелец своих persistence-контрактов, зависимость строго `sync.jira → domain.issue`:

1. **`IssueEntity`** (JPA) — таблица `issues`: `jiraId` (immutable, matching key), `key` (бизнес-якорь, ADR-001), статусные/assignee-поля, `jiraCreated`/`jiraUpdated`/`syncedAt` (`Instant`), `fixVersions`/`labels` — `Set<String>` через `@ElementCollection` (`issue_fix_versions`/`issue_labels`). Метод `applyUpsert(command, syncedAt)` мутирует существующую запись in-place (clear+addAll для коллекций).
2. **`IssueRepository`** (Spring Data JPA, прямо в `domain.issue`) — `findAllByJiraIdIn` для batch-матчинга страницы.
3. **`IssueUpsertCommand`** (входной контракт домена, без ссылок на `sync.jira`) и **`IssueUpsertOutcome`** (`created`/`updated`).
4. **`IssuePersistencePort`** (`upsertPage(List<IssueUpsertCommand>)`) и его реализация **`IssueUpsertService`** (`@Service`, `@Transactional`): batch `findAllByJiraIdIn` → merge/create → `saveAll` на уровне страницы.
5. **Liquibase `0002-issues.yaml`** — таблицы `issues`, `issue_fix_versions`, `issue_labels` (unique constraints, `ON DELETE CASCADE`); подключена в `db.changelog-master.yaml`. `sprints`/`sync_state` не созданы.
6. **`sync.jira.JiraIssueSnapshot`** — добавлены `jiraId`, `created`; `updated`/`created` стали `Instant` (парсинг Jira-таймстампа: ошибка → `null` + warning в лог, sync не падает); `assigneeName` заменён на `assigneeUsername`/`assigneeDisplayName`.
7. **`sync.jira.JiraSyncService`** — конструктор принимает `IssuePersistencePort`; на каждой странице маппит `JiraIssueSnapshot → IssueUpsertCommand` и сразу вызывает `upsertPage(...)` (без накопления полного списка в памяти); агрегирует `created`/`updated` по страницам. Ошибки БД **не ловятся** здесь — поднимаются наверх как есть (в отличие от `JiraClientException`, которая пишется в `errors`).
8. **`sync.jira.JiraSyncResult`** — убран `List<JiraIssueSnapshot> issues`; добавлены `created`/`updated`; `saved()` — derived-метод (`created + updated`).

Тесты (новые/переписанные): `IssueUpsertServiceTest` (unit, Mockito-мок `IssueRepository`: create/update matching по `jiraId` даже при смене `key`, replace fixVersions/labels in-place, `syncedAt`, агрегация по смешанной странице); `IssueUpsertServiceIntegrationTest` (`@DataJpaTest` + `@AutoConfigureTestDatabase(replace=NONE)` против настоящего Liquibase-changeset на файловом H2 в режиме PostgreSQL — тот же приём, что в `DeliveryMonitorApplicationTests`; проверяет реальную схему, upsert-обновление без дублирования, unique-constraint на бизнес-ключ); переписанный `JiraSyncServiceTest` (recording fake `IssuePersistencePort` — постраничный вызов `upsertPage`, а не один вызов на весь прогон; нормализация полей в `IssueUpsertCommand`; unparsable timestamp → `null` без ошибки; partial-persisted result при сбое на второй странице).

`.\mvnw.cmd clean verify` (JDK 21, Android Studio JBR) — **49 тестов, 0 failures, 0 errors, 2 skipped** (оба — `JiraSmokeTest`, без токена) → `BUILD SUCCESS`.

**Deviations from the agreed design (обнаружены при реализации, зафиксированы явно):**

- **Физическая колонка бизнес-якоря названа `issue_key`, а не `key`.** Причина: `KEY` — зарезервированное слово SQL; H2 (тестовая БД, файловый режим PostgreSQL-compat) принимает его без кавычек в `CREATE TABLE`, но отвергает без кавычек в обычных `SELECT`-выражениях (`column "ie1_0.key" not found` / syntax error в зависимости от способа квотирования). Опробованный fallback — Hibernate backtick-квотирование (`` `key` ``) плюс `objectQuotingStrategy: QUOTE_ALL_OBJECTS` в Liquibase — оказался более хрупким: квотирование всех объектов в changeset ломает регистр имени схемы (`"PUBLIC"` в кавычках не находит фактическую схему `public`, созданную Liquibase без кавычек). Решение: переименовать физическую колонку в `issue_key`, оставив Java-контракт (`IssueEntity.getKey()`, `IssueUpsertCommand.key()`) без изменений — matching-логика (по `jiraId`, не по `key`) не затронута, это чисто SQL-уровневая деталь. `docs/database.md` обновлён.
- Остальное реализовано **без отклонений** от согласованного дизайна (см. предыдущую запись): владение контрактами, направление зависимости, matching по `jiraId`, постраничный upsert, состав `JiraSyncResult`, `@ElementCollection` для `fixVersions`/`labels`, отложенные `sprints`/`sync_state`.

**Decisions:**

- Архитектура не менялась — реализация строго в рамках дизайна, согласованного и задокументированного в предыдущей сессии (в рамках ADR-001/003/011). Новый ADR не создавался.
- `issue_key` как физическое имя колонки — реализационная деталь (не бизнес-решение), задокументирована как отклонение в `database.md` и здесь, а не молча.

**Docs touched:**

- `docs/database.md` (физическая колонка `issue_key`, статус таблицы `issues` → «реализовано»)
- `docs/architecture.md` (статус `domain.issue` → «реализовано»)
- `docs/ai_context.md` (стадия/версия 2.5, Phase 2.3 done, Phase 2.4 next)
- `docs/session_log.md` (this entry), `docs/changelog.md`

**Code touched (new):**

- `backend/src/main/java/.../domain/issue/{IssueEntity,IssueRepository,IssueUpsertCommand,IssueUpsertOutcome,IssuePersistencePort,IssueUpsertService,package-info}.java`
- `backend/src/main/resources/db/changelog/changes/0002-issues.yaml`
- `backend/src/test/java/.../domain/issue/{IssueUpsertServiceTest,IssueUpsertServiceIntegrationTest}.java`

**Code touched (modified):**

- `backend/src/main/java/.../sync/jira/{JiraIssueSnapshot,JiraSyncService,JiraSyncResult,package-info}.java`
- `backend/src/main/resources/db/changelog/db.changelog-master.yaml` (include `0002-issues.yaml`)
- `backend/src/test/java/.../sync/jira/JiraSyncServiceTest.java` (переписан под постраничную персистентность)

**Next:**

1. Phase 2.4 «REST API» — `POST /api/admin/sync/jira`, `GET /api/issues` (read-only поверх `domain.issue`, не live Jira), по [roadmap.md](./roadmap.md). Не начинать без явного go-ahead.
2. Получить реальный `JIRA_TOKEN`, прогнать `JiraSmokeTest` и живой sync (`jira.mode=rest`) — тот же открытый `TODO`, что и в предыдущих записях.

---

## 2026-07-15 — Phase 2.3 Persistence design approved

**Stage:** Phase 2.3 «Persistence» ([roadmap.md](./roadmap.md)) — **дизайн согласован, реализация не начата**. Код, миграции, entities, repositories в этой сессии **не создавались** — сознательное ограничение задачи (зафиксировать design decisions до кода).

**Summary:**
Перед стартом кода Phase 2.3 проведён анализ текущего состояния (`db.changelog-master.yaml` — только baseline `tagDatabase`, реальных таблиц нет; `pom.xml` — JPA/PostgreSQL/Liquibase уже подключены, но не использованы; `sync.jira.JiraIssueSnapshot`/`JiraSyncService`/`JiraSyncResult` — текущий seam, собирающий все issues в памяти перед возвратом) и `database.md`/`architecture.md`/ADR-001/ADR-003/ADR-011. По итогам согласован минимальный дизайн persistence-слоя:

1. **Владение контрактами и направление зависимости.** Новый пакет `domain.issue` — единственный владелец своих persistence-контрактов: `IssueEntity`, `IssueRepository`, `IssuePersistencePort`, `IssueUpsertCommand` (входной контракт), `IssueUpsertOutcome`, `IssueUpsertService`. Зависимость строго `sync.jira → domain.issue`; `domain.issue` **не** зависит от `sync.jira` — не импортирует `JiraIssueSnapshot`. `sync.jira` сам маппит `JiraIssueSnapshot → IssueUpsertCommand` перед вызовом `IssuePersistencePort`. Это соответствует Dependency Rule (внутренние слои не знают о внешних) и было явно пересмотрено в процессе обсуждения: первый вариант (`IssuePersistencePort` объявлен в `sync.jira`, `domain.issue` его реализует) создавал обратную зависимость `domain.issue → sync.jira` через тип параметра порта — отклонён.
2. **`JiraIssueSnapshot` остаётся контрактом `sync.jira`** (нормализованный вид Jira issue из integration-слоя), не переиспользуется persistence-слоем напрямую. Изменения контракта: `jiraId` (Jira internal id) добавлен, `key` остаётся бизнес-якорем (ADR-001), `created`/`updated` — `Instant` вместо `String`, `assigneeUsername`/`assigneeDisplayName` вместо единого `assigneeName`.
3. **Matching при upsert — по `jiraId`**, не по `key`: `jiraId` иммутабелен, `key` может измениться при переносе issue между Jira-проектами. `key` остаётся уникальным индексом и бизнес-якорем для будущих join (GitLab/Jenkins), но не используется для поиска существующей строки.
4. **Upsert — постранично**, не `saveAll` после полного сбора списка: `JiraSyncService` вызывает `IssuePersistencePort.upsertPage(...)` сразу на каждой странице (`findAllByJiraIdIn` → merge/create → `saveAll` на уровне страницы), без накопления полного списка issues в памяти. Побочный эффект, зафиксированный явно: при сбое Jira на середине прогона уже обработанные страницы остаются сохранёнными в БД (partial persisted result), а не теряются, как было бы при полном накоплении в памяти. Retry не добавляется (fail-fast сохраняется).
5. **`JiraSyncResult`** больше не хранит `List<JiraIssueSnapshot> issues` — только агрегаты: `created`, `updated` (+ существующие `startedAt`/`finishedAt`/`fetched`/`pages`/`mocked`/`errors`). `saved()` — derived-метод (`created + updated`), не отдельное хранимое поле, чтобы не держать состояние, которое может разойтись с суммой.
6. **`fixVersions`/`labels`** сохраняются через `@ElementCollection` (`issue_fix_versions`/`issue_labels`) — множественность не теряется; `labels` решено сохранять симметрично `fixVersions`, а не отбрасывать, так как данные уже приходят из Jira и стоимость сохранения мала.
7. **`sprints`/`sync_state` отложены** — не заводятся в Phase 2.3. Причина: board 718 (Kanban) не даёт sprint-данных сейчас, incremental sync/watermark не реализован — заводить таблицы без писателя в них признано избыточным. Возвращаемся к ним отдельной задачей вместе с sprint metadata / incremental sync / scheduler / sync history.

**Decisions:**

- Все решения выше — в рамках уже принятых ADR-001 (issue key как якорь), ADR-003 (modular monolith, `domain.*` пакеты), ADR-011 (Markdown source of truth); **новый ADR не создавался** — архитектура не менялась, только реализационный дизайн внутри уже принятых границ.
- `IssueRepository` (Spring Data JPA) размещается прямо в `domain.issue`, без выделения отдельного infrastructure-модуля — прагматичное упрощение для modular monolith (ADR-003), а не строгая гексагональная архитектура с отдельным слоем адаптеров.
- `synced_at` (таймстамп последнего upsert) — небольшое добавление к минимальному дизайну сверх исходного запроса, задел на будущий staleness-репорт без введения `sync_state` уже сейчас; при реализации может быть пересмотрено.

**Docs touched:**

- `docs/decisions.md` (Design notes — новая строка «Phase 2.3 Persistence — дизайн согласован»)
- `docs/database.md` (реальные таблицы `issues`/`issue_fix_versions`/`issue_labels` для Phase 2.3; `sprints`/`sync_state` помечены Planned/future с причиной; добавлена заметка про matching key)
- `docs/architecture.md` (детализация `domain.issue`/`sync.jira` в таблице пакетов; новый раздел «Package dependency direction»: `integration.jira → sync.jira → domain.issue`)
- `docs/session_log.md` (this entry)
- `docs/changelog.md`

**Next:**

1. Реализация Phase 2.3 по согласованному дизайну: `sync.jira.JiraIssueSnapshot` (правки полей), новый пакет `domain.issue` (`IssueEntity`/`IssueRepository`/`IssuePersistencePort`/`IssueUpsertCommand`/`IssueUpsertOutcome`/`IssueUpsertService`), изменения `JiraSyncService`/`JiraSyncResult`, Liquibase changeset `0002-issues.yaml` (`issues`, `issue_fix_versions`, `issue_labels`), тесты. Не начинать без явного go-ahead.
2. После реализации — обновить `database.md`/`architecture.md` фактическими деталями (если разойдутся с дизайном) и добавить запись в `session_log.md`/`changelog.md` по факту.
3. Получить реальный `JIRA_TOKEN` — остаётся тем же открытым `TODO`, что и в предыдущих записях.

---

## 2026-07-15 — Phase 2.2 (Jira Sync) done: application-level sync layer over JiraContextProvider

**Stage:** Phase 2.2 «Jira Sync» ([roadmap.md](./roadmap.md)) — реализован **первый application-level слой** поверх `JiraContextProvider`. Сознательно **только** оркестрация: **без** REST controller, persistence (JPA/Liquibase/repository/`sync_state`), scheduler, Spring Security, incremental sync, GitLab/Jenkins (явные ограничения задачи). `JiraClient`/`JiraContextProvider` и их DTO **не менялись**.

**Summary:**
Новый пакет `ru.eltc.deliverymonitor.sync.jira` — отдельный application layer над integration layer (`integration.jira` остаётся только HTTP-клиент + auth + wire DTO + provider):

1. **`JiraSyncService`** (`@Service`) — `JiraSyncResult syncBoard()`: листает **все** страницы board через `JiraContextProvider.getBoardContext(startAt, maxResults)` (не через `JiraClient` напрямую), продвигается по фактически возвращённому числу issues (устойчиво к server-side cap на `maxResults`), с защитным лимитом `MAX_PAGES=10000` от бесконечного цикла. Нормализует каждый `JiraIssueDto` в `JiraIssueSnapshot`. `JiraClientException` **не пробрасывается сырым** — пишется в `errors`, прогон возвращает уже собранное (поведение будущего endpoint/scheduler).
2. **`JiraIssueSnapshot`** (record) — внутренний нормализованный контракт sync-слоя, **seam к будущему persistence**: `key, summary, statusName, statusCategory, assigneeName, issueType, fixVersions, labels, updated`. Минимальный набор (task 2.4), ничего «на будущее». Изолирует верхние слои/будущую БД от структуры Jira REST API. `fixVersions`/`labels` — никогда не `null`.
3. **`JiraSyncResult`** (record) — честный отчёт прогона: `startedAt, finishedAt, fetched, pages, mocked, errors, issues`. Поле **`saved` не добавлено** — persistence ещё нет, результат не имитирует сохранение. Форма согласована с `api.md` sketch (`startedAt`/`finishedAt`/`fetched`/`errors`) для переиспользования будущими endpoint (2.4)/scheduler (2.5)/логированием.
4. **`JiraSyncProperties`** (`@ConfigurationProperties("jira.sync")`, `@Validated`) — `page-size` (default 50, `@Min(1)`). Отдельно от `jira.*` (integration), т.к. это операционная настройка sync-слоя; захардкожена не была. `application.yml`: `jira.sync.page-size: ${JIRA_SYNC_PAGE_SIZE:50}`.

`.\mvnw.cmd clean verify` (JDK 21, Android Studio JBR; системный JDK 17) — **39 тестов, 0 failures, 0 errors, 2 skipped** (оба — `JiraSmokeTest`, без токена) → `BUILD SUCCESS`. 7 новых unit-тестов `JiraSyncServiceTest` — через **fake `JiraContextProvider`** (без реального Jira, без HTTP, без Spring): пагинация по нескольким страницам + порядок, нормализация полей → snapshot, пустая board, unassigned/`null`-fields, проброс `mocked`, `JiraClientException` → `errors` (полный сбой на первой странице и частичный сбой с сохранением уже полученного). Полный context-тест (`DeliveryMonitorApplicationTests`) зелёный — подтверждает wiring `JiraSyncService` с default `RestJiraContextProvider` + `JiraSyncProperties`.

**Decisions:**

- **Пакет `sync.jira` (top-level, сосед `integration`)**, а не `integration.jira.sync` — sync это application-оркестрация, roadmap выделяет Sync в отдельную фазу; `integration.jira` держим чистым (integration layer). Отражено в `architecture.md` (Backend packages). Новый ADR **не создавался** (в рамках принятых ADR-001/003/004/005/007/011, как и при вводе provider/auth).
- **`JiraIssueSnapshot` как отдельный DTO**, а не переиспользование `JiraIssueDto` — верхние слои и будущая БД не должны зависеть от структуры внешнего Jira API; изменение Jira API не должно ломать схему.
- **Без поля `saved`** — persistence не существует (Phase 2.3); не имитируем то, чего нет.
- **`page-size` в конфиге** (`jira.sync.page-size`) — операционная настройка Jira, не хардкод; вынесена в отдельный `JiraSyncProperties`, чтобы не нарушать инвариант `JiraProperties` («только client/auth, без sync-настроек»).
- **`JiraClientException` → `errors`, не пробрасывается** — прогон устойчив к сбоям и возвращает partial-результат, как потребуется endpoint/scheduler.
- **Done-when по факту** (авторизованный прогон против реального Jira) остаётся тем же открытым `TODO`, что у 2.2/2.3 — реальный `JIRA_TOKEN` недоступен; офлайн проверено через fake provider и `jira.mode=mock`.

**Docs touched:**

- `docs/session_log.md` (this entry), `docs/changelog.md`, `docs/architecture.md` (пакет `sync.jira`), `docs/ai_context.md` (стадия/версия 2.4)

**Code touched (new):**

- `backend/src/main/java/.../sync/jira/{JiraSyncService,JiraIssueSnapshot,JiraSyncResult,JiraSyncProperties,package-info}.java`
- `backend/src/test/java/.../sync/jira/JiraSyncServiceTest.java`

**Code touched (modified):**

- `backend/src/main/resources/application.yml` (`jira.sync.page-size`)

**Next:**

1. Phase 2.3 «Persistence» — Liquibase (`sprints`/`issues`/`sync_state`), маппинг `JiraIssueSnapshot` → JPA entity, upsert; затем endpoint `POST /api/admin/sync/jira` (2.4) поверх `JiraSyncService`. Не начинать без явного go-ahead.
2. Получить реальный `JIRA_TOKEN`, прогнать `JiraSmokeTest` и живой sync (`jira.mode=rest`); офлайн — `jira.mode=mock`.

---

## 2026-07-15 — Task 2.3 (Jira board context) done: JiraContextProvider + config-switchable mock

**Stage:** Phase 2 detail task **2.3 — Получение контекста board** ([roadmap.md](./roadmap.md), «Phase 2 — детализация»; группируется под Phase «2.2 Jira Sync»). Реализован шов получения контекста board **без** зависимости от реального Jira-аккаунта.

**Summary:**
Добавлен доменно-осмысленный слой над HTTP-клиентом Phase 2.1 и офлайн-режим разработки. **Без** persistence, domain-сущностей, scheduler, REST API, UI (явные ограничения задачи). Новый пакет `integration.jira.provider`:

1. **`JiraContextProvider`** — интерфейс: `JiraBoardContext getBoardContext(int startAt, int maxResults)`. Верхние слои (будущий sync — Phase 2.2/2.4) зависят от него, а **не** от `JiraClient`.
2. **`JiraBoardContext`** — record-результат: `boardId`, `filterId`, `startAt`, `maxResults`, `total`, `List<JiraIssueDto> issues`, `fetchedAt`, `mocked`. Переиспользует существующий `JiraIssueDto` (одна форма данных для обоих источников).
3. **`RestJiraContextProvider`** (default, `@ConditionalOnProperty jira.mode=rest`, `matchIfMissing=true`) — обёртка над `JiraClient.searchByFilter(defaultFilterId, …)`; адаптирует реактивный вызов в синхронный контракт с ограниченным `block`-таймаутом (reactive-типы не «протекают» выше клиента).
4. **`MockJiraContextProvider`** (`@ConditionalOnProperty jira.mode=mock`) — отдаёт **санитизированные demo-данные** из `classpath:jira/mock/board-718-filter-30532.json`. Fixture явно помечен `_comment` как demo/sanitized (fake-пользователи `demo.*`, статусы из публичного column-config). **Защита от production:** конструктор кидает `IllegalStateException`, если активен профиль `prod`/`production` — mock физически не может уехать в прод, даже если свойство протечёт.
5. Переключение источника — **только конфиг** `jira.mode=rest|mock` (default `rest`), профиль `jira-mock` (`application-jira-mock.yml`) поднимает mock офлайн + placeholder token для fail-fast валидации. Переход на реальную Jira при появлении сервисного аккаунта = задать `JIRA_TOKEN` и оставить `rest`; кода менять не нужно.

`JiraClient` (Phase 2.1) **не менялся**. `JiraProperties` — минимально расширены (`mode`, `boardId`), обратносовместимо; валидация не тронута. `.\mvnw.cmd clean verify` (JDK 21, Android Studio JBR) — **32 теста, 0 failures, 0 errors, 2 skipped** (оба — `JiraSmokeTest`, без токена) → `BUILD SUCCESS`. 7 новых тестов: rest-провайдер через `mockwebserver3` (filter query + маппинг + проброс `JiraClientException`), mock-провайдер (demo-данные, постранично, prod-guard), выбор бина по `jira.mode` (`ApplicationContextRunner`).

**Decisions:**

- **Нужен интерфейс `JiraContextProvider`** — это шов, дающий (а) офлайн-разработку без аккаунта и (б) переход на реальную Jira без переписывания (downstream зависит от интерфейса). Мокать сам `JiraClient` хуже — он конкретный HTTP-класс из 2.1; провайдер оставляет его чистым.
- **Mock в `src/main`, а не в `src/test`** — цель офлайн-режима не только тесты, а развитие следующих слоёв и запуск приложения без Jira; источник переключается конфигом. Компенсировано: fixture помечен demo/sanitized, prod-guard в конструкторе, warning-лог при старте.
- **Live board configuration / активные sprints — вне scope 2.3** (отдельная будущая задача): доска Kanban (sprint lifecycle может отсутствовать), column/swimlane-конфиг уже статически в `discovery.md` §9.1; добавление Jira Agile API + новых DTO сейчас без ценности. Интерфейс оставляет extension point.
- **Новый ADR не создавался** — решение в рамках уже принятых ADR-001/003/004/005/006/007/011 (аналогично тому, как auth-strategy в 2.1 вводилась без ADR). Зафиксировано в `docs/decisions.md` (Design notes), этой записи и changelog.
- **Done-when (filter 30532 → issues) — структурно** подтверждён (rest-провайдер против mock HTTP-сервера + realistic demo-данные для downstream). Реальный авторизованный прогон против аккаунта остаётся тем же открытым `TODO`, что и у task 2.2 — не имитируется.

**Docs touched:**

- `docs/session_log.md` (this entry), `docs/changelog.md`, `docs/decisions.md` (Design notes), `docs/discovery.md` (§1 — заметка про офлайн `jira.mode=mock`), `docs/ai_context.md` (стадия), `backend/README.md` (provider + `jira.mode` + package layout)

**Code touched (new):**

- `backend/src/main/java/.../integration/jira/provider/{JiraContextProvider,JiraBoardContext,RestJiraContextProvider,MockJiraContextProvider}.java`
- `backend/src/main/resources/jira/mock/board-718-filter-30532.json` (sanitized demo fixture)
- `backend/src/main/resources/application-jira-mock.yml` (offline profile)
- `backend/src/test/java/.../integration/jira/provider/{RestJiraContextProviderTest,MockJiraContextProviderTest,JiraContextProviderSelectionTest}.java`

**Code touched (modified):**

- `backend/src/main/java/.../integration/jira/config/JiraProperties.java` (+ `mode`, `boardId`; enum `Mode`)
- `backend/src/main/resources/application.yml` (`jira.mode`, `jira.board-id`)
- `backend/src/main/java/.../integration/jira/package-info.java` (описан `provider`)

**Next:**

1. Phase 2.2 «Jira Sync» — оркестрация `POST /api/admin/sync/jira` поверх `JiraContextProvider` (task 2.4 «Получение задач» → затем persistence, task 2.5). Не начинать без явного go-ahead.
2. Получить реальный `JIRA_TOKEN`, прогнать `JiraSmokeTest` (закрыть done-when 2.2/2.3 фактически); при работе офлайн использовать `jira.mode=mock`.

---

## 2026-07-15 — Task 2.2 (Auth) check: JiraSmokeTest + env wiring verified, real token still TODO

**Stage:** Phase 2 detail task **2.2 Auth** ([roadmap.md](./roadmap.md), «Phase 2 — детализация»); Phase 2.1 (Jira Client) уже done, следующий шаг по roadmap — Phase 2.2 (Jira Sync), но перед этим явно запрошена точечная проверка task 2.2 (Auth)

**Summary:**
По запросу выполнена целевая проверка готовности аутентификации Jira Server 8.20.30 — **без нового кода** (sync, БД, entity, repository, scheduler, REST API сознательно не добавлялись, это явное ограничение задачи):

1. **`JiraSmokeTest` проверен** (`backend/src/test/java/.../integration/jira/JiraSmokeTest.java`, создан в предыдущей gate-check сессии 2026-07-14) — подтверждено, что он покрывает ровно то, что нужно task 2.2: `GET /rest/api/2/myself` и `GET /rest/api/2/search` (default filter из `JIRA_DEFAULT_FILTER_ID`).
2. **Подтверждено использование production `JiraClient`** — тест собирает клиент через те же бины, что и приложение (`JiraClientConfig#jiraAuthenticationStrategy`, `JiraClientConfig#jiraWebClient`), никакой отдельной test-only реализации auth/HTTP нет.
3. **Проверена конфигурация env variables** в `backend/src/main/resources/application.yml` — все пять переменных из задачи подтверждены как реально читаемые Spring/тестом: `JIRA_BASE_URL`, `JIRA_AUTH_TYPE`, `JIRA_USERNAME`, `JIRA_TOKEN`, `JIRA_DEFAULT_FILTER_ID` (плюс уже существующие `JIRA_CONNECT_TIMEOUT`/`JIRA_RESPONSE_TIMEOUT`/`JIRA_PROJECT_KEYS`, вне scope задачи, но не мешают).
4. **Прогнан `.\mvnw.cmd clean verify`** (JDK 21 через Android Studio JBR — `C:\Program Files\Android\Android Studio\jbr`; системный JDK в этой среде 17, как и в предыдущей сессии) — **25 тестов, 0 failures, 0 errors, 2 skipped** (оба — методы `JiraSmokeTest`, корректно пропущены из-за отсутствия `JIRA_TOKEN`, `@EnabledIfEnvironmentVariable`) → `BUILD SUCCESS`.
5. **Реальный `JIRA_TOKEN` в этой сессии недоступен** (проверено: не задан в env процесса, нет `.env`-файлов с реальными секретами в репозитории — только `docker/.env.example`). Авторизованный прогон (`/myself` → `200`, реальный ответ по filter 30532) **не выполнен** — вместо имитации результата оставлен явный `TODO`.

Подготовлена и задокументирована инструкция запуска smoke test с реальным токеном (PowerShell) — см. `docs/discovery.md` §1 «Task 2.2 (Auth) check» (та же инструкция уже была в Javadoc `JiraSmokeTest`, теперь она также продублирована в docs для видимости без чтения кода).

**Decisions:**

- Никакой новый код не написан: аудит подтвердил, что существующая реализация Phase 2.1 (auth strategy + env config + smoke test) уже полностью закрывает task 2.2 **с точки зрения кода**; отсутствует только реальный прогон с валидными credentials.
- Явно не тронуты sync/persistence/REST API/scheduler — по прямому ограничению задачи; переход к Phase 2.3 (или к Phase 2.2 «Jira Sync» по overview-таблице roadmap) не выполняется.
- Не выдумано и не имитировано успешное прохождение auth без реального токена — оставлен честный `TODO`.
- Архитектура и ADR не менялись.

**Docs touched:**

- `docs/discovery.md` (§1 — обновлён статус TODO "PAT vs basic auth", новая секция "Task 2.2 (Auth) check — 2026-07-15" с чеклистом проверки и инструкцией запуска)
- `docs/session_log.md` (this entry)
- `docs/changelog.md`

**Next:**

1. Получить реальный `JIRA_TOKEN` (PAT или Basic) от Jira-админа; прогнать `JiraSmokeTest` по инструкции в `docs/discovery.md` §1 — закрыть task 2.2 done-when (`/myself` → `200`).
2. Только после этого — Phase 2.2 «Jira Sync» (`POST /api/admin/sync/jira`, tasks 2.3+2.4 по [roadmap.md](./roadmap.md)). Не начинать без явного go-ahead (по просьбе в этой сессии — не переходить к Phase 2.3).

---

## 2026-07-15 — Security principles doc (docs-only)

**Stage:** Документация; вне кодовых Phase 2.x ([roadmap.md](./roadmap.md))

**Summary:**
Добавлен новый документ [security.md](./security.md), фиксирующий **архитектурные принципы безопасности** проекта. Только документация — код не писался, Spring Security не добавлялся, архитектура не менялась. Разделы: (1) Security Goals — что защищаем (в первую очередь service-account credentials, спроецированные данные, доступ к dashboard) + модель угроз; (2) Authentication — только корпоративный SSO/LDAP/OIDC, локальных пользователей нет; (3) Authorization — роли Admin/PM/QA/Developer + least privilege (по умолчанию read-only, admin-операции только Admin); (4) Service Accounts — отдельные read-only аккаунты Jira/GitLab/Jenkins с минимальными правами (Jira read, GitLab `read_api`/`read_repository`, Jenkins read jobs/builds); (5) Secrets — только environment variables, никаких секретов в Git и логах, fail-fast при отсутствии обязательного секрета; (6) Network — внутренний сервис за VPN, HTTPS/TLS, whitelisted webhooks; (7) Audit — какие действия логируются (привилегированные операции, изменения ролей/конфига, отказы доступа, sync); (8) Logging — запрет логировать токены и `Authorization` header; (9) Security Checklist. Документ опирается на уже принятые решения ([architecture.md](./architecture.md), [integrations.md](./integrations.md), [discovery.md](./discovery.md) §7/§9.5, [ai_context.md](./ai_context.md) §7) и не вводит новых.

**Decisions:**

- Документ — только принципы, не guide по реализации; конкретный auth-провайдер/библиотека умышленно не фиксируются (выбор при развёртывании).
- Spring Security **не** добавляется на данном этапе (явное требование задачи).
- Архитектура не менялась → **новый ADR не создавался** (в рамках уже принятых ADR-003/007/011 и правил `ai_context.md`).
- Роли и их набор трактуются как конфигурация, а не доменная модель — изменение списка ролей не требует нового ADR.

**Docs touched:**

- `docs/security.md` (new)
- `docs/README.md` (index + таблица «что обновлять» — строка про безопасность)
- `docs/session_log.md` (this entry)
- `docs/changelog.md`

**Next:**

1. Продолжить по [roadmap.md](./roadmap.md): получить реальный `JIRA_TOKEN` и прогнать `JiraSmokeTest`, затем Phase 2.2 (Jira Sync) — без изменений относительно предыдущей записи.
2. При старте реализации auth/ролей сверяться с `security.md` (checklist §9).

---

## 2026-07-14 — Phase 2.1 gate-check + hardening (before Phase 2.2)

**Stage:** Phase 2.1 — Jira REST Client, gate-check перед Phase 2.2 ([roadmap.md](./roadmap.md))

**Summary:**
После code review Phase 2.1 выполнен ограниченный gate-check и точечное укрепление `integration.jira`, **без** новых sync/persistence/scheduler/domain-сущностей (сознательно вне scope):

1. **Fail-fast валидация `JiraProperties`** — добавлен `spring-boot-starter-validation`; класс помечен `@Validated`, `baseUrl` (`@NotBlank`), `auth` (`@NotNull @Valid`), `auth.token` (`@NotBlank`), `auth.type` (`@NotNull`), и cross-field проверка `@AssertTrue`: при `auth.type=basic` `username` не может быть пустым. Теперь отсутствующий/пустой `JIRA_TOKEN` **не даёт приложению запуститься** (раньше это всплыло бы только при первом реальном вызове Jira). Тесты (`JiraPropertiesTest`, `JiraClientConfigTest`) проверяют и валидные, и невалидные конфигурации — `ApplicationContextRunner` + `assertThat(context).hasFailed()`. Побочный эффект: `DeliveryMonitorApplicationTests` (полный контекст) и часть существующих тестов теперь явно передают тестовый (не секретный) `jira.auth.token`, иначе они тоже не стартуют — это осознанное и ожидаемое поведение fail-fast.
2. **Единый тип ошибки в `JiraClient`** — все ошибки взаимодействия с Jira (HTTP-статус **и** транспортные: timeout, connection refused, DNS failure, прочие network errors) теперь оборачиваются в `JiraClientException`; публичный API `JiraClient` (`getMyself/search/searchByFilter`) не изменился. Добавлен `JiraClientException.NO_HTTP_STATUS = 0` — sentinel-статус для случаев без HTTP-ответа. Тесты: connection refused / DNS / generic network error через синтетический `ExchangeFunction` (детерминированно, без реальной сети), response timeout — через реальный `MockWebServer` с очень коротким таймаутом (без ответа).
3. **`ExchangeStrategies` на `jiraWebClient`** — `maxInMemorySize` увеличен с дефолтных Spring 256 KB до **10 MB** (внутренний Jira Server с известным, ограниченным объёмом данных — не публичный multi-tenant API; запас на будущее расширение полей типа `changelog`/`links`). Обоснование — в комментарии кода. Тест на `JiraClientConfigTest` гоняет через `MockWebServer` тело ответа ~400 KB (больше дефолтного лимита, меньше нового) и проверяет успешный парсинг.
4. **Временный `JiraSmokeTest`** (`backend/src/test/java/.../integration/jira/JiraSmokeTest.java`) — не входит в обычный `mvnw verify` (`@EnabledIfEnvironmentVariable(JIRA_TOKEN)`, по умолчанию skipped), гоняет **тот же** production-код (`JiraClientConfig`/`JiraClient`), конфиг — из env vars, против настоящего `https://jira.eltc.ru`. Реальных credentials в этой сессии не было — авторизованный прогон (`myself`/`search filter=30532` с валидным токеном) остаётся `[TODO]`.
5. **Частичный gate-check без credentials** (см. `docs/discovery.md` §1 «Gate-check Phase 2.1 → Phase 2.2»): сетевая доступность `jira.eltc.ru` подтверждена, `GET /rest/api/2/serverInfo` (публичный, без auth) подтвердил версию **8.20.30**; `GET /rest/api/2/myself` без валидных credentials (через настоящий `JiraClient`) вернул `401` без тела, `GET /rest/api/2/search?jql=filter=30532` — `400` со стандартным Jira-сообщением о недостатке прав/несуществующем фильтре для анонимного доступа. Оба ответа корректно обернулись в `JiraClientException` тем же кодом, что уже покрыт unit-тестами на mock-сервере — расхождений с реальным сервером не найдено. Открытый `TODO`: авторизованный прогон с реальным PAT/Basic токеном от Jira-админа.

Все backend-тесты зелёные: `.\mvnw.cmd clean verify` (JDK 21 через Android Studio JBR — системный JDK в этой среде 17).

**Decisions:**

- Валидация — только на `baseUrl` и `auth` (как явно запрошено), без расширения на остальные поля (`connectTimeout`, `projectKeys`, …) — не входило в scope этой задачи.
- `JiraClientException.NO_HTTP_STATUS = 0` как единый sentinel вместо отдельного under/over-типа ошибки — сохраняет «один тип ошибки» контракт, который важен для будущей Phase 2.2 (`{ fetched, saved, errors: [] }`).
- `maxInMemorySize = 10 MB` — фиксированное значение, не конфигурируемое через `application.yml`, т.к. это внутренний технический лимит клиента, а не операционная настройка команды.
- `JiraSmokeTest` оставлен как файл в репозитории (не удалён после прогона) — отключён по умолчанию, готов к использованию, когда появится реальный `JIRA_TOKEN`; решение об удалении — за командой после того, как gate-check будет пройден с реальными credentials.
- Архитектура не менялась; новый ADR не создавался (все правки — в рамках уже принятых ADR-003/007).

**Docs touched:**

- `docs/discovery.md` (§1 — статус "PAT vs basic auth", новая секция "Gate-check Phase 2.1 → Phase 2.2")
- `docs/session_log.md` (this entry)

**Code touched (new):**

- `backend/src/test/java/ru/eltc/deliverymonitor/integration/jira/JiraSmokeTest.java`

**Code touched (modified):**

- `backend/pom.xml` (+ `spring-boot-starter-validation`)
- `backend/src/main/java/ru/eltc/deliverymonitor/integration/jira/config/JiraProperties.java` (`@Validated` + Bean Validation)
- `backend/src/main/java/ru/eltc/deliverymonitor/integration/jira/config/JiraClientConfig.java` (`ExchangeStrategies`/`maxInMemorySize`)
- `backend/src/main/java/ru/eltc/deliverymonitor/integration/jira/client/JiraClient.java` (унификация transport-level ошибок)
- `backend/src/main/java/ru/eltc/deliverymonitor/integration/jira/exception/JiraClientException.java` (`NO_HTTP_STATUS`)
- `backend/src/test/java/ru/eltc/deliverymonitor/integration/jira/client/JiraClientTest.java`, `.../config/JiraClientConfigTest.java`, `.../config/JiraPropertiesTest.java`
- `backend/src/test/java/ru/eltc/deliverymonitor/DeliveryMonitorApplicationTests.java` (placeholder `jira.auth.token` для полного контекста)

**Next:**

1. Получить реальный `JIRA_TOKEN` (PAT или Basic) от Jira-админа и прогнать `JiraSmokeTest` — закрыть `[TODO]` "PAT vs basic auth" в `docs/discovery.md` §1.
2. Только после этого — Phase 2.2 (Jira Sync, `POST /api/admin/sync/jira`) по [roadmap.md](./roadmap.md).
3. Не начинать 2.2 без явного go-ahead.

---

## 2026-07-14 — Phase 2.1 (Jira Client) done: Jira REST client + auth

**Stage:** Phase 2.1 — Jira REST Client ([roadmap.md](./roadmap.md))

**Summary:**
Реализован **только** HTTP-клиент Jira Server 8.x поверх Spring `WebClient` — без sync, без БД, без REST API, без scheduler, без GitLab/Jenkins (по явному scope задачи). Новый пакет `ru.eltc.deliverymonitor.integration.jira`:

- `config/JiraProperties` — `@ConfigurationProperties(prefix = "jira")`: `baseUrl`, `connectTimeout`, `responseTimeout`, `projectKeys`, `defaultFilterId`, `auth.{type,username,token}`. Значения — из `application.yml` с env-переопределением (`JIRA_BASE_URL`, `JIRA_AUTH_TYPE`, `JIRA_USERNAME`, `JIRA_TOKEN`, …); секреты по умолчанию пустые, в Git не попадают.
- `config/JiraClientConfig` — бин `WebClient` (base URL + timeouts из properties, `Accept: application/json`) и бин `JiraAuthenticationStrategy`, выбираемый по `jira.auth.type`.
- `auth/JiraAuthenticationStrategy` (+ `BasicAuthenticationStrategy`, `BearerTokenAuthenticationStrategy`) — Basic auth или PAT (`Authorization: Bearer`); выбор auth-схемы на Jira Server 8.20.30 остаётся `[TODO]` в discovery.md, поэтому обе реализованы и переключаются конфигом.
- `client/JiraClient` — `getMyself()` (smoke `/rest/api/2/myself`), `search(jql, startAt, maxResults, fields)` и `searchByFilter(filterId, startAt, maxResults)` (`/rest/api/2/search`); ошибки → `JiraClientException` (HTTP-статус + `errorMessages` из тела ответа Jira).
- `dto/*` — record-DTO для ответов Jira REST API v2: `JiraMyselfDto`, `JiraSearchResultDto`, `JiraIssueDto`, `JiraIssueFieldsDto` (summary/status/assignee/fixVersions/labels), `JiraUserDto`, `JiraStatusDto` (+ `StatusCategory`), `JiraIssueTypeDto`, `JiraFixVersionDto`, `JiraErrorResponseDto`.
- `exception/JiraClientException`.

`pom.xml`: добавлен `spring-boot-starter-webflux` (только ради `WebClient`; т.к. `spring-boot-starter-web` уже на classpath, Spring Boot оставляет Servlet/MVC стек, реактивный сервер не включается) и тестовая зависимость `com.squareup.okhttp3:mockwebserver3-junit5:5.4.0`.

Тесты (12, все зелёные, `mvnw clean verify`): auth-стратегии (Basic/Bearer заголовки), биндинг `JiraProperties` (дефолты + override через properties/env-стиль ключей), выбор auth-стратегии в `JiraClientConfig` (`ApplicationContextRunner`), и `JiraClientTest` — полный цикл через mock HTTP-сервер (`mockwebserver3`): успешный `getMyself`/`search`/`searchByFilter` с парсингом DTO, проверка заголовка `Authorization`, и обработка ошибочных ответов (400 с телом, 401 без тела) → `JiraClientException`.

**Decisions:**

- `WebClient` вместо `RestTemplate` (сохраняет опцию неблокирующих вызовов позже; поддержана обоими вариантами roadmap — "RestTemplate / WebClient").
- Auth реализован как стратегия (Basic vs Bearer/PAT), переключаемая конфигом `jira.auth.type`, а не захардкожена — т.к. discovery.md §1 явно оставляет "PAT vs basic auth on 8.20.30" открытым `[TODO]`.
- DTO — Java records с `@JsonIgnoreProperties(ignoreUnknown = true)`: неизвестные поля Jira не ломают парсинг; включены только поля, реально нужные текущему и следующему шагу (summary/status/assignee/fixVersions/labels), без "на всякий случай" полей.
- Тестирование HTTP-слоя — через `mockwebserver3` (mock-сервер), не мокирование `WebClient` — тест реально проверяет сериализацию URL/заголовков/JSON.
- Единый метод `search(jql, ...)` + удобный `searchByFilter(filterId, ...)`, но **без** привязки к конкретному filter 30532/board 718 в коде клиента — эта логика (Phase 2.2/2.3) намеренно не добавлена.
- Явно не сделано (по scope задачи): sync-оркестрация, `POST /api/admin/sync/jira`, персистентность в PostgreSQL, `GET /api/issues`, `@Scheduled` поллинг, GitLab/Jenkins.

**Docs touched:**

- `docs/ai_context.md` (стадия/версия), `docs/session_log.md` (this entry), `docs/changelog.md`
- `backend/README.md` (секция "Jira REST client (Phase 2.1)", package layout, env vars)

**Code touched (new):**

- `backend/src/main/java/ru/eltc/deliverymonitor/integration/jira/config/{JiraProperties,JiraClientConfig}.java`
- `backend/src/main/java/ru/eltc/deliverymonitor/integration/jira/auth/{JiraAuthenticationStrategy,BasicAuthenticationStrategy,BearerTokenAuthenticationStrategy}.java`
- `backend/src/main/java/ru/eltc/deliverymonitor/integration/jira/client/JiraClient.java`
- `backend/src/main/java/ru/eltc/deliverymonitor/integration/jira/dto/*.java` (9 DTO)
- `backend/src/main/java/ru/eltc/deliverymonitor/integration/jira/exception/JiraClientException.java`
- `backend/src/test/java/ru/eltc/deliverymonitor/integration/jira/**` (auth/config/client tests)
- `backend/src/main/resources/application.yml` (секция `jira:`)
- `backend/pom.xml` (`spring-boot-starter-webflux`, `mockwebserver3-junit5`)

**Code touched (modified):**

- `backend/src/main/java/ru/eltc/deliverymonitor/integration/jira/package-info.java` (обновлён статус пакета)

**Next:**

1. Phase 2.2 — Jira Sync: `POST /api/admin/sync/jira` (ручной), использующий `JiraClient.searchByFilter` для board 718 / filter 30532; получение контекста board (2.3) и задач (2.4) по [roadmap.md](./roadmap.md).
2. Не переходить к 2.2 без явного go-ahead (по просьбе в этой сессии — остановиться после 2.1).
3. Когда появятся реальные Jira credentials — прогнать `getMyself()` вручную (`curl`/small script) для подтверждения `[TODO]` "PAT vs basic auth" в discovery.md §1, затем зафиксировать выбор в `jira.auth.type` по умолчанию.

---

## 2026-07-14 — Phase 1 (Skeleton) started: backend scaffold

**Stage:** Phase 1 — Skeleton ([roadmap.md](./roadmap.md))

**Summary:**
Discovery (Phase 0) завершён; начат Skeleton. Создан минимальный backend-каркас: Java 21, Spring Boot 3.5.16 (Maven, с wrapper — `mvnw`/`mvnw.cmd`, локальная сборка не требует установленного Maven), PostgreSQL (JDBC + Spring Data JPA, без сущностей), Liquibase (master changelog + один baseline-changeset `tagDatabase`, реальных таблиц нет), Actuator (`/actuator/health`, `/actuator/info`). Docker: `backend/Dockerfile` (multi-stage, `eclipse-temurin:21-jre-alpine` runtime) + `docker/docker-compose.yml` (postgres + backend) + `docker/.env.example`. Единственный подготовленный пакет — `integration.jira` (пустой `package-info.java`) как явная точка старта следующей задачи; GitLab/Jenkins и бизнес-сущности не создавались. Сборка и smoke-тест контекста (Spring context + Liquibase поднимаются, embedded H2 в PostgreSQL-режиме, без Docker) проверены локально через `mvnw clean verify` — успешно (JDK 21 из Android Studio JBR, т.к. системный JDK — 17). Реальный `docker compose up` не проверялся в этой среде (Docker не установлен) — рекомендуется проверить на стенде/CI.

**Decisions:**

- Версия Spring Boot: **3.5.16** (последний патч ветки 3.5 на дату сессии).
- Package root: `ru.eltc.deliverymonitor` (по домену `eltc.ru`), артефакт `delivery-monitor`.
- Liquibase master-changelog использует `include` на отдельные файлы в `db/changelog/changes/` — задел на будущие миграции (Jira-домен, Phase 2).
- Hibernate `ddl-auto: validate` — схему меняет только Liquibase.
- Тесты не требуют Docker (embedded H2 в PostgreSQL-режиме) — Testcontainers сознательно не добавлены на этом этапе, чтобы не тянуть Docker-зависимость в unit/context-тесты Skeleton.
- Архитектура не изменилась относительно принятых ADR (Spring Boot + PostgreSQL modular monolith, REST, без CQRS/брокеров) → новый ADR **не создавался**.

**Docs touched:**

- `docs/session_log.md` (this entry)
- `docs/changelog.md`
- `backend/README.md`, `docker/README.md` (обновлены с placeholder на инструкции)
- `.gitignore` (исключение для `.mvn/wrapper/maven-wrapper.jar`)

**Code touched (new):**

- `backend/pom.xml`, `backend/mvnw(.cmd)`, `backend/.mvn/wrapper/**`
- `backend/src/main/java/ru/eltc/deliverymonitor/DeliveryMonitorApplication.java`
- `backend/src/main/java/ru/eltc/deliverymonitor/integration/jira/package-info.java`
- `backend/src/main/resources/application.yml`
- `backend/src/main/resources/db/changelog/db.changelog-master.yaml`, `.../changes/0001-skeleton-baseline.yaml`
- `backend/src/test/java/ru/eltc/deliverymonitor/DeliveryMonitorApplicationTests.java`
- `backend/Dockerfile`, `backend/.dockerignore`
- `docker/docker-compose.yml`, `docker/.env.example`

**Next:**

1. Проверить `docker compose up --build` на машине с установленным Docker (health-check `/actuator/health` через compose).
2. Начать Phase 2 — Jira: клиент/поллер в `integration.jira`, конфиг из `docs/discovery.md` §9.1 (`MPTPSUPP`, board 718, filter 30532), первые Liquibase-changesets для `issues`/`sprints` ([database.md](./database.md)), события `JIRA_STATUS`/`JIRA_COMMENT` в `activity_events`.
3. Не уходить дальше границ Phase 2 без явного go-ahead.

---

## 2026-07-13 — Phase 0 (Discovery) started: discovery checklist

**Stage:** Phase 0 — Discovery ([roadmap.md](./roadmap.md))

**Summary:**  
Начат этап Discovery. Создан `docs/discovery.md` — checklist всей информации, необходимой для старта реализации. Разделы: Jira, GitLab, Jenkins, Oracle, Workstream Types, Naming Convention, Service Accounts, Open Questions. Для каждого пункта зафиксировано: что известно, что нужно узнать, почему важно, как проверить. Известные факты взяты из docs (`integrations.md`, `database.md`, ADR-001/002); недостающие данные помечены `TODO` (ничего не выдумывалось). Код приложения не пишем — Discovery в процессе.

**Decisions:**

- Discovery-документ = единый источник checklist для Phase 0; критичных данных пока нет (проектный ключ Jira, repo→type map, Jenkins jobs, реальный naming, доступы — все `TODO`).
- Статусы пунктов размечены как `[known]` / `[assumed]` / `[TODO]`.
- Oracle трактуется как Workstream Type поверх Git (без отдельного коннектора), согласно `integrations.md`.

**Docs touched:**

- `docs/discovery.md` (new)
- `docs/session_log.md` (this entry)

**Next:**

1. Собрать реальные данные по `TODO`: проектный ключ Jira + JQL, список GitLab проектов и repo→type map, список Jenkins jobs, naming convention команды.
2. Оформить и проверить service accounts / tokens + webhook whitelist.
3. Закрыть Open Questions; довести discovery.md до состояния без критичных `TODO`.
4. Не начинать Skeleton/код до завершения Discovery и явного go-ahead.

---

## 2026-07-10 — Repository bootstrap & ADR formalization

**Stage:** Pre-development / documentation

**Summary:**  
Репозиторий приведён к профессиональной структуре перед разработкой. Документация v2.1 сохранена и дополнена. ADR вынесены в `docs/adr/`. Добавлены AI context, session log, changelog, CONTRIBUTING_AI, корневой README, GitHub templates, `.gitignore`. Рабочий корень рекомендован: `C:\Users\repin.ea\Projects\AI-Engineering-Delivery-Monitor` (не Program Files).

**Decisions:**

- Source of Truth = Markdown в `docs/` ([ADR-011](./adr/0011-docs-markdown-source-of-truth.md)).
- Структура каталогов: `docs/`, `backend/`, `frontend/`, `docker/`, `scripts/`, `.github/`.
- Код приложения пока не пишем; следующий фокус — Discovery.
- ADR-001…0011 зафиксированы как Accepted (0009 — post-MVP).

**Docs touched:**

- `docs/ai_context.md` (new)
- `docs/session_log.md` (new)
- `docs/changelog.md` (new)
- `docs/adr/*` (new)
- `docs/decisions.md` (index only)
- `docs/README.md`, `docs/structure.md`
- `README.md`, `CONTRIBUTING_AI.md`, `CHANGELOG.md`
- `.gitignore`, `.github/**`
- существующие vision/architecture/… сверены и подчищены

**Next:**

1. Привязать локальную папку к GitHub remote (clone/push).
2. Этап Discovery: seed Workstream Types, repo→type map, Jenkins jobs, service accounts ([roadmap.md](./roadmap.md) phase 0).
3. Открыть Cursor workspace на `C:\Users\repin.ea\Projects\AI-Engineering-Delivery-Monitor`.
4. Не начинать Skeleton/код до завершения Discovery и явного go-ahead.

---

## 2026-07-09 — Architecture v2.1 (docs + Workstream Type)

**Stage:** Architecture design

**Summary:**  
Спроектирован Delivery Monitor: Spring Boot monolith, PostgreSQL, scheduler/webhooks, REST, Timeline-first UX, Release Health, Activity Feed. Отказ от CQRS/очередей/Redis/GraphQL в MVP. Введена абстракция Workstream Type. Документы разложены по `docs/*.md`. Добавлен `JIRA_COMMENT`. Команда: 7 dev + 2 QA.

**Decisions:** См. ADR-001…011 (формализованы 2026-07-10).

**Docs touched:** `vision`, `architecture`, `architecture-overview`, `database`, `api`, `integrations`, `ux`, `roadmap`, `glossary`, ранний `decisions`.

**Next (на тот момент):** bootstrap репозитория, ADR files, AI context — выполнено 2026-07-10.
