# Discovery (Phase 0)

| | |
|---|---|
| **Status** | In progress |
| **Version** | 0.3 |
| **Stage** | Phase 0 — Discovery ([roadmap.md](./roadmap.md)) |
| **Related** | [integrations.md](./integrations.md), [database.md](./database.md), [architecture.md](./architecture.md), [glossary.md](./glossary.md) |
| **Last updated** | 2026-07-15 (task 2.2 Auth check) |

## Цель этапа

Собрать и **подтвердить** фактическую информацию для старта реализации: маппинги (repo → Workstream Type, Jenkins jobs), реальный naming convention команды, доступы (service accounts / tokens). **Код приложения не пишем**, пока Discovery не завершён ([ai_context.md](./ai_context.md) §2, §7).

**Done when:** документ маппингов заполнен (нет критичных `TODO`) + доступы выданы и проверены ([roadmap.md](./roadmap.md), Phase 0).

**Правило:** ничего не выдумываем. Там, где данных нет — `TODO`. Подтверждённое переносим в §9 «Результаты».

### Легенда статусов

| Статус | Значение |
|---|---|
| `[known]` | Подтверждено в docs или фактически проверено |
| `[assumed]` | Есть в docs как пример; нужно подтвердить |
| `[TODO]` | Данных нет; нужно собрать |

---

## Как пользоваться этим документом

1. Пройдите **порядок сбора** (§0) — сверху вниз, не перескакивая блоки.
2. Для каждого `TODO` смотрите колонку **«Откуда взять»** в таблице раздела.
3. После подтверждения — запишите факт в **§9 Результаты** и смените статус на `[known]`.
4. Когда §9 заполнен и доступы проверены — обновите [integrations.md](./integrations.md), запись в [session_log.md](./session_log.md).

---

## 0. Порядок сбора (рекомендуемый)

| Шаг | Что делаем | Раздел | Кто вовлечён | Время |
|---|---|---|---|---|
| 1 | Узнать Jira project key, board, JQL | §1 | Тимлид, любой разработчик | 30 мин |
| 2 | Согласовать Workstream Types | §5 | Тимлид | 30 мин |
| 3 | Выписать GitLab-проекты + repo→type | §2, §4 | Тимлид, разработчики | 1–2 ч |
| 4 | Снять naming convention с реальных веток/MR | §6 | Разработчики (git log) | 1 ч |
| 5 | Выписать Jenkins jobs + параметры билдов | §3 | DevOps / CI-владелец | 1–2 ч |
| 6 | Запросить service accounts + webhook whitelist | §7 | Jira/GitLab/Jenkins админы | 1–3 дня (ожидание) |
| 7 | Smoke-тесты API под выданными токенами | §1–3, §7 | Вы + админы | 2–4 ч |
| 8 | Заполнить §9, закрыть Open Questions | §8, §9 | Тимлид | 1 ч |

---

## Индекс: откуда что берётся

| Что нужно | Где посмотреть самому | К кому идти | Куда записать |
|---|---|---|---|
| Jira project key | Любая задача команды в Jira → префикс ключа (`ABC-123` → `ABC`) | Тимлид | §9.1 |
| Jira board / sprint | Jira → Boards → ваш Scrum board; URL содержит `rapidView=` | Тимлид | §9.1 |
| JQL фильтр | Board → «…» → Board settings → Filter; или Jira → Filters | Тимлид / Jira-админ | §9.1 |
| Custom fields | Jira → ⚙ → Issues → Custom fields; или REST `GET /rest/api/2/field` | Jira-админ | §9.1 |
| Workflow statuses | Jira → Project settings → Workflows; или REST `GET /rest/api/2/status` | Jira-админ | §9.1 |
| GitLab URL + проекты | GitLab → ваша Group → Projects | Тимлид / DevOps | §9.2 |
| GitLab project id | Project → Settings → General → Project ID | — | §9.2 |
| GitLab edition (CE/EE) | Admin → Help; или `GET /api/v4/version` | GitLab-админ | §9.2 |
| Naming convention | `git branch -a`, GitLab → Merge requests за 2 спринта | Разработчики | §9.3 |
| Jenkins jobs | Jenkins → Dashboard / папки команды | DevOps / CI-владелец | §9.4 |
| Параметры билда | Job → последний build → Parameters / Console log | DevOps | §9.4 |
| Oracle-репозитории | Спросить Oracle-разработчиков; сверить с §9.2 | Тимлид + Oracle dev | §9.2, §4 |
| Service accounts | Заявка в IT / DevOps | Админы Jira, GitLab, Jenkins | §9.5 |
| Webhook URL | После выбора стенда Monitor (Phase 1) | DevOps + сетевики | §9.5 |

---

## 1. Jira

### Уже известно

| Статус | Факт |
|---|---|
| `[known]` | URL: `https://jira.eltc.ru` |
| `[known]` | Версия: Jira Server **8.20.30** |
| `[known]` | Режим: **primary polling** 2–5 мин; опц. webhook `issue_updated` ([integrations.md](./integrations.md)) |
| `[known]` | Auth: service account / PAT |
| `[known]` | Синхронизируем: issues, changelog → `JIRA_STATUS`, comments → `JIRA_COMMENT`, issue links (blocks) |
| `[known]` | Проектный ключ **`MPTPSUPP`** — подтверждён ветками (`feature/MPTPSUPP-39033`, …) |
| `[known]` | Board **718** — **«МПЛК доска»**, тип **`kanban`** (не Scrum!) |
| `[known]` | Filter **30532**: «**все задачи для МПЛК, только головные**» — owner **kleymenova.vs** (Клейменова Василиса) |
| `[known]` | **JQL filter 30532** — см. §9.1; требует **ScriptRunner** (`issueFunction in parentsOf(...)`) |
| `[known]` | Roster команды на доске (из JQL `assignee in`): repin.ea, gutov.as, lisov.kv, gungaev.zt, knyazev.vy, semenychev.di, gumenyuk.oa, eftifanov.dv, iskandirov.mr, lazarev.mv, nikulina.vs, gevorgyan.gk, khrisanov.vv, backlog.mpgg |
| `[assumed]` | **Polling Monitor Phase 2** = JQL filter **30532** (совпадает с доской) |
| `[known]` | Swimlanes (дорожки) — отдельные JQL; см. таблицу ниже |
| `[known]` | Колонки board → mapping status id; см. §9.1 `column_config` |
| `[known]` | UI board: card width **190px**; search bar: `issuetype`, `status`, `assignee` |

> **⚠️ Kanban, не Scrum:** `sprint in openSprints()` **может не подходить**. Scope задач на board задаёт **filter 30532** + swimlanes. Для «спринтовых» задач есть swimlane **«Спринт»**; для релиза МП — swimlanes с `fixVersion` и `MPTPSUPP`.

### Board 718 — колонки (из REST configuration)

| Колонка | Status IDs |
|---|---|
| Список задач | 12557 |
| preDelivery | 11951, 16053, 15260, 1 |
| Требования → ТЗ | 14756, 11259, 14558 |
| Спланировать | 15353 |
| Планирование | 10027 |
| Бэклог | 14555, 11260, 14556 |
| **В работе** | 10553, 3, 10028, 10056, 17056, 17055 |
| **Ревью** | 14560, 14757 |
| Тестирование на тестовой | 16252, 14562 |
| Дизайн-ревью | 18653 |
| Тестирование на боевой | 16254 |
| Релиз | 11051 |
| тест | 15152, 10000, 10001 |
| Приемка | 14152, 11251, 15358, 14481 |

Rank field (порядок карточек): custom field **11350**.

### Swimlanes (дорожки) — JQL

| Имя | JQL |
|---|---|
| Блокеры | `priority = Blocker and issuetype not in (Совещание, "Совещание (бот)") AND category not in (КСУП, "КСУП (завершенные проекты)") AND project not in ("Обучение СИТ", SEonboarding)` |
| **Идёт в релиз МП (ближайший поезд)** | `fixVersion in earliestUnreleasedVersionByReleaseDate(MPTPSUPP)` |
| **Планируется в следующий релиз МП** | `fixVersion in unreleasedVersions() AND fixVersion in releaseDate("after 1970/01/01")` |
| Важно и срочно (такси) | `priority = Critical and issuetype not in (Совещание, "Совещание (бот)") AND category not in (КСУП, "КСУП (завершенные проекты)") AND project not in ("Обучение СИТ", SEonboarding)` |
| **Спринт** | `(summary ~ "Спринт" OR issueFunction in issuesInEpics("'Epic Name' ~ 'Спринт'"))` |
| Проекты | `category in (КСУП, "КСУП (завершенные проекты)") AND issuetype not in ("Совещание (бот)", Совещание)` |
| Рефакторинг МПГГ | `labels = РефакторингМПГГ` |
| Инциденты | `type in (Инцидент, "Расследование инцидента") or project in ("Технические TroubleTiket'ы", "Расследование инцидентов")` |
| Стандартный | `priority = Major and issuetype not in (Совещание, "Совещание (бот)", Ошибка, Баготряска) AND category not in (КСУП, "КСУП (завершенные проекты)") AND project not in ("Обучение СИТ", SEonboarding)` |
| Пониженный приоритет | `priority in (High, Medium, Minor, Стандартный, Low) AND issuetype not in (Совещание, "Совещание (бот)", Ошибка, Баготряска) and category not in (КСУП, "КСУП (завершенные проекты)") and project not in ("Обучение СИТ", SEonboarding)` |
| Sentry | `labels = sentry` |
| Ошибки и баготряски | `issuetype = Ошибка OR issuetype = Баготряска AND priority != Trivial AND labels != sentry` |
| Техдолг, нематериальный | `priority = Trivial and issuetype not in (Совещание, "Совещание (бот)") AND project not in ("Обучение СИТ", SEonboarding)` |
| ПиРы | `project in ("Обучение СИТ", SEonboarding)` |
| Фреймворк | `summary ~ "Совещание: Daily-митинг" OR summary ~ "Работа в двойках" OR summary ~ "Прочие работы" OR summary ~ "Совещание: 1-1" OR summary ~ "Еженедельное закрытие задач"` |
| Эпики | `type = Epic` |
| Совещания | `issuetype in (Совещание, "Совещание (бот)", "Совещание (бот) (подзадача)", "Совещание (Подзадача)") AND project not in ("Обучение СИТ", SEonboarding) AND NOT (summary ~ "Совещание: Daily-митинг" OR ...)` |
| Все остальное | *(пустой JQL — catch-all)* |

### Filter 30532 — JQL board (confirmed 2026-07-14)

| Поле | Значение |
|---|---|
| ID | 30532 |
| Имя | все задачи для МПЛК, только головные |
| Owner | kleymenova.vs (Клейменова Василиса) |
| View | https://jira.eltc.ru/issues/?filter=30532 |

**Логика (кратко):**

- Стандартные типы задач, **не** Баготряска (с исключениями)
- **MPTPSUPP** ИЛИ assignee из roster команды ИЛИ **родитель** задачи, назначенной на команду (`parentsOf`)
- Исключает «To Do» без assignee из команды; закрытые старше 90 дней — out
- Исключает `labels = sentry` + status `ОТКРЫТА`
- Сортировка: `ORDER BY Rank ASC`

Полный JQL — в §9.1 `board.filter_jql`.

### Что собрать

| Статус | Вопрос | Откуда взять | Как проверить |
|---|---|---|---|
| `[known]` | Board 718, kanban, filter 30532 + JQL | REST filter/30532 | §9.1 |
| `[known]` | Колонки + status ids | REST configuration | §9.1 `column_config` |
| `[known]` | Swimlanes JQL | Board swimlane config | §9.1 `swimlanes` |
| `[known]` | Roster `jira_user` команды | Filter 30532 assignee list | §9.1 `team_roster` |
| `[assumed]` | Monitor MVP polling = filter 30532 | Согласовать с тимлидом | Issue count = карточки на board |
| `[TODO]` | Доп. фильтр для Release Health (fixVersion swimlane?) | Тимлид | Phase 5 |
| `[TODO]` | **`GET /api/sprints/current` заблокирован отсутствием sprint persistence.** Архитектурное решение (2026-07-15, перед реализацией Read API): не создавать таблицу `sprints`, не делать mock/stub/фиктивные данные, не получать sprint напрямую из Jira для этого endpoint. Реализовать после того, как появится sprint persistence (новая таблица `sprints`, Liquibase-миграция, дизайн matching key — отдельная задача) | Тимлид / архитектурный review | `docs/database.md` (`sprints` — Planned/future), `docs/roadmap.md` |
| `[TODO]` | PAT vs basic auth на 8.20.30 — **сетевая доступность и версия подтверждены** 2026-07-14 (см. «Gate-check» ниже), **код/конфиг auth и smoke test повторно проверены** 2026-07-15 (см. «Task 2.2 (Auth) check» ниже), **сам auth ещё не подтверждён** — нужен реальный токен | Jira-админ; [Atlassian docs](https://confluence.atlassian.com/enterprise/using-personal-access-tokens-1026032365.html) | `GET /rest/api/2/myself` под выданным аккаунтом — прогнать `backend/.../jira/JiraSmokeTest.java` с реальным `JIRA_TOKEN` (инструкция ниже) |
| `[TODO]` | Webhook `issue_updated` — да/нет, URL | Jira-админ (System → WebHooks) | Тестовый POST на endpoint Monitor |
| `[TODO]` | Rate limits при polling | Jira-админ / нагрузочная политика | Согласовать интервал 2–5 мин |

### Gate-check Phase 2.1 → Phase 2.2 (2026-07-14)

Перед переходом к Phase 2.2 выполнена частичная проверка `JiraClient` (Phase 2.1) против **настоящего** `https://jira.eltc.ru` (не mock) — без реальных credentials (их не было в этой сессии), поэтому это **не** полный smoke-тест из `roadmap.md`, а только его сетевая/протокольная часть:

| Проверка | Результат |
|---|---|
| Сетевая доступность (`https://jira.eltc.ru:443`, TLS) | ✅ Доступен из рабочей сети |
| `GET /rest/api/2/serverInfo` (без auth, публичный) | ✅ `200`, `version: "8.20.30"`, `deploymentType: "Server"`, `serverTitle: "Goodline JIRA"` — подтверждает версию из «Уже известно» |
| `GET /rest/api/2/myself` без валидных credentials (через реальный `JiraClient`/`WebClient`, тот же код, что в проде) | `401 Unauthorized`, пустое тело — корректно обёрнуто в `JiraClientException` (см. `JiraClientTest#wrapsUnauthorizedResponseWithoutBody`, тот же путь кода отработал и на реальном сервере) |
| `GET /rest/api/2/search?jql=filter=30532` без валидных credentials | `400 Bad Request`, тело `{"errorMessages":["Фильтр с ID '30532' не существует или у вас нет прав для просмотра его данных."]}` — стандартное generic-сообщение Jira для анонимного/невалидного доступа (не значит, что filter 30532 не существует — Jira намеренно не различает «нет фильтра» и «нет прав» для неавторизованных запросов); тело корректно распарсилось в `JiraErrorResponseDto`/`JiraClientException` |

**Вывод:** транспортный уровень (TLS, таймауты, парсинг реальных Jira-ответов на ошибки) подтверждён на настоящем сервере и совпадает с поведением, которое уже покрыто unit-тестами на mock-сервере — расхождений не найдено. **Открытый TODO** — авторизованный прогон (`PAT` или `Basic`) с реальным сервисным аккаунтом, чтобы подтвердить: (а) какой auth type реально работает на этой инсталляции, (б) что filter 30532 действительно возвращает issues. Для этого добавлен `backend/src/test/java/ru/eltc/deliverymonitor/integration/jira/JiraSmokeTest.java` — отключён по умолчанию (`@EnabledIfEnvironmentVariable(JIRA_TOKEN)`), не входит в обычный `mvnw verify`; инструкция запуска — в Javadoc класса. После первого успешного прогона с реальным токеном — обновить эту секцию и сменить статус строки выше на `[known]`.

### Task 2.2 (Auth) check — 2026-07-15

Roadmap task **2.2 Auth** ([roadmap.md](./roadmap.md), «Phase 2 — детализация»): "Конфиг credentials (env), basic auth / PAT; smoke `GET /rest/api/2/myself`". **Done when:** реальный Jira token из env → `/myself` 200. В этой сессии повторно проверена готовность к этому шагу, **без** реального токена (недоступен в этой среде — не в env, не в файлах `.env*`) и **без** написания нового кода (sync/БД/entity/repository/scheduler/REST API сознательно не добавлялись — вне scope задачи):

| Проверка | Результат |
|---|---|
| `JiraSmokeTest` существует и покрывает нужные вызовы | ✅ `backend/src/test/java/.../integration/jira/JiraSmokeTest.java` — `myselfAuthenticatesAgainstRealJira()` (`GET /rest/api/2/myself`) и `searchByDefaultFilterReturnsIssues()` (`GET /rest/api/2/search`, filter из `JIRA_DEFAULT_FILTER_ID`, по умолчанию 30532) |
| Использует **production** `JiraClient` | ✅ Тест собирает `JiraClient` через те же production-бины, что и приложение — `JiraClientConfig#jiraAuthenticationStrategy` + `JiraClientConfig#jiraWebClient` (никакой отдельной/тестовой реализации auth или HTTP-вызовов нет) |
| Конфигурация env variables подтверждена в `backend/src/main/resources/application.yml` | ✅ `JIRA_BASE_URL` (`jira.base-url`, default `https://jira.eltc.ru`), `JIRA_AUTH_TYPE` (`jira.auth.type`, default `bearer`), `JIRA_USERNAME` (`jira.auth.username`, default пусто), `JIRA_TOKEN` (`jira.auth.token`, default пусто, `@NotBlank` — fail-fast), `JIRA_DEFAULT_FILTER_ID` (`jira.default-filter-id`, default `30532`) — все пять читаются `JiraSmokeTest` напрямую из `System.getenv`, и те же переменные разрешает Spring через `application.yml` в production |
| Поведение без токена — build не ломается | ✅ `.\mvnw.cmd clean verify` (JDK 21, Android Studio JBR): `Tests run: 25, Failures: 0, Errors: 0, Skipped: 2` (2 skipped = оба метода `JiraSmokeTest`, `@EnabledIfEnvironmentVariable(JIRA_TOKEN)` не выполнено) → `BUILD SUCCESS` |
| Реальный авторизованный прогон (`/myself` → 200) | ❌ **Не выполнен** — `JIRA_TOKEN` недоступен в этой сессии. Остаётся открытым `TODO` (см. таблицу выше и §8) |

**Как запустить smoke test, когда появится реальный токен** (PowerShell, Windows):

```powershell
cd backend
$env:JIRA_TOKEN = "<реальный PAT или пароль сервисного аккаунта>"
$env:JIRA_AUTH_TYPE = "bearer"          # или "basic" — см. TODO выше, auth type ещё не подтверждён
$env:JIRA_USERNAME = "<нужно только для JIRA_AUTH_TYPE=basic>"
$env:JIRA_BASE_URL = "https://jira.eltc.ru"          # опционально — это default
$env:JIRA_DEFAULT_FILTER_ID = "30532"                # опционально — это default
.\mvnw.cmd test -Dtest=JiraSmokeTest
```

Ожидаемый результат при успехе: оба теста зелёные (не skipped), в выводе — `[JiraSmokeTest] /myself OK -> name=...` и `[JiraSmokeTest] /search?jql=filter=30532 OK -> total=...`. После прогона — обновить статус auth в §1 (сменить `[TODO]` на `[known]`, зафиксировать, какой `JIRA_AUTH_TYPE` реально работает) и сделать запись в [session_log.md](./session_log.md).

**Вывод:** Task 2.2 (Auth) с точки зрения **кода и конфигурации** готова полностью — production `JiraClient`/`JiraClientConfig` уже поддерживают auth через env (Basic и Bearer), fail-fast валидация не даёт стартовать без `JIRA_TOKEN`, smoke-тест написан и корректно skip-ается без credentials. Единственное, что блокирует формальное закрытие task 2.2 (`Done when: реальный token → /myself 200`) — отсутствие реального токена в этой среде. Ничего не выдумано и не сымитировано взамен реального прогона.

### Офлайн-разработка без реального аккаунта (`jira.mode=mock`) — 2026-07-15 (Task 2.3)

Пока реальный сервисный аккаунт Jira не выдан, разработку следующих слоёв можно вести офлайн: Task 2.3 добавил `JiraContextProvider` с двумя реализациями, переключаемыми конфигом `jira.mode` (default `rest`):

- `jira.mode=rest` — реальная Jira через `JiraClient` (нужен `JIRA_TOKEN`);
- `jira.mode=mock` — **санитизированные demo-данные** из `classpath:jira/mock/board-718-filter-30532.json` (fake-пользователи `demo.*`, форма ответа как у filter 30532). Поднимается профилем `jira-mock`:

```powershell
cd backend
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=jira-mock"
```

**Mock никогда не используется в production:** `MockJiraContextProvider` отказывается стартовать при активном профиле `prod`/`production`. Переход на реальную Jira при появлении токена — задать `JIRA_TOKEN` и оставить `jira.mode=rest`; **код менять не нужно**. Реальный авторизованный прогон (`filter 30532` → issues) остаётся тем же открытым `TODO`, что и для auth выше.

### Почему важно

Jira issue key — **якорь всех связей** ([ADR-001](./adr/0001-jira-source-of-truth.md)). Board — **Kanban** с swimlanes по приоритету/релизу/спринту; при реализации Phase 2 нужно согласовать **какой JQL = «наша команда на доске»**, а не предполагать Scrum open sprint.

---

## 2. GitLab

### Уже известно

| Статус | Факт |
|---|---|
| `[known]` | Режим: **webhook preferred** + reconcile 15–30 мин |
| `[known]` | Auth: project/group token |
| `[known]` | Маппинг: `gitlab project → workstream_type_code` ([ADR-002](./adr/0002-workstream-type.md)) |
| `[known]` | Синхронизируем: push, commits, MR, notes/approvals (если API доступен) |
| `[known]` | URL: **`https://git.eltc.ru`**, groups: **`mptp`**, **`SOFTCOMPANY`** |
| `[known]` | **3 репозитория** (подтверждено 2026-07-14): |

| path | Описание | workstream_type |
|---|---|---|
| `mptp/mptp-react-native` | React Native, приложение Goodline (SuperApp) | `frontend` |
| `mptp/mptp8` | **МП Техподдержка Goodline**, Laravel 8 + SuperApp, бекенд ЛК | `backend` |
| `SOFTCOMPANY/eltcbackend` | Oracle / ELTC backend (процедуры) | `oracle` |

| `[known]` | Контакты по проектам (разные!): |
| | `mptp-react-native` — руководитель **Макс Рыльцев** |
| | `mptp8` — руководитель **Замалдинова Виктория**, ответственный разработчик **Горбунов Яков** |
| `[known]` | **`mptp8` CI/CD — GitLab Pipelines**, не Jenkins: `https://git.eltc.ru/mptp/mptp8/-/pipelines` |
| `[known]` | `mptp8` deploy: `develop` → test, `master` → prod, `project/*` → project env (K8s ns `mptp8`) |
| `[known]` | Последний merge в `mptp8`: `feature/MPTPSUPP-46098_text-fixes` → `master` (подтверждает naming) |
| `[known]` | **`gitlab_id`:** `mptp8` = **2159**, `mptp-react-native` = **760**, `eltcbackend` = **3494** (2026-07-14) |

### Детали `mptp/mptp8` (из README проекта)

| Поле | Значение |
|---|---|
| Jira | **MPTPSUPP** |
| Prod | `https://mptp8.k8s.eltc.ru` |
| Staging | `https://mptp8.dev.k8s.eltc.ru` |
| Стек | PHP 8.1+, Laravel 8, MySQL 8, Redis, **ext-oci8** (вызов Oracle из PHP) |
| Harbor | `harbor.eltc.ru/mptp8` |
| Мониторинг | Graylog, Grafana, Zabbix (вне scope Monitor MVP) |

> **Важно для Oracle workstream:** `mptp8` **читает** Oracle через PHP-репозитории (`OracleRepositories` trait), но **разработка процедур** — в `SOFTCOMPANY/eltcbackend`. Процедуры могут выкатываться на snapshot вне Git → потенциальная слепая зона (уточнить у Oracle-команды).

### Как получить `gitlab_id`, если Settings не видно

На вашем скрине sidebar: Manage, Plan, Code, Build… — **без Settings внизу**. Возможные причины:

1. **Роль Guest/Reporter** — Settings видят только Maintainer+  
2. **GitLab 17+** — настройки могли переехать: **Manage** → прокрутить подменю → **General**  
3. Sidebar **не прокручен до низа** — Settings часто в самом низу

**Что сделать по порядку:**

| # | Действие |
|---|---|
| 1 | Прокрутить левое меню вниз — ищите **Settings** (шестерёнка) |
| 2 | Открыть **Manage** → искать **General** / **Project information** |
| 3 | Написать **Горбунову Якову** или **Замалдиновой Виктории** — попросить Project ID или роль Developer |
| 4 | **Personal Access Token** + API (работает без Settings): |

```powershell
# GitLab → аватар → Preferences → Access Tokens → read_api
$token = "ваш_token"
$h = @{ "PRIVATE-TOKEN" = $token }
Invoke-RestMethod -Uri "https://git.eltc.ru/api/v4/projects/mptp%2Fmptp8" -Headers $h | Select-Object id, name, path_with_namespace
```

| 5 | **DevTools** (F12) → Network → обновить страницу проекта → в ответах API искать `"id":` |

**Для старта Monitor числовой `gitlab_id` не блокер** — достаточно `path` (`mptp/mptp8`), API резолвит: `GET /projects/mptp%2Fmptp8`.

### Что собрать

| Статус | Вопрос | Откуда взять | Как проверить |
|---|---|---|---|
| `[known]` | Базовый URL `https://git.eltc.ru` | Ссылки на репо | — |
| `[known]` | 3 репо + маппинг workstream type | Команда 2026-07-14 | §9.2 |
| `[known]` | `gitlab_id` всех 3 проектов | Settings / команда | §9.2 |
| `[TODO]` | Ещё репозитории кроме этих трёх? | Замалдинова / Рыльцев | Сверить с §9.2 |
| `[TODO]` | Group id `mptp`, `SOFTCOMPANY` + group-level token | GitLab → Groups; админ | `GET /api/v4/groups/mptp` |
| `[TODO]` | Разрешённые webhook-события | Group/Project → Webhooks; админ | Тест push/MR → доставка на endpoint |
| `[TODO]` | Whitelisted URL для webhooks | DevOps / сеть (после стенда Monitor) | `curl` с GitLab runner/сети |
| `[TODO]` | Edition CE/EE (approvals API) | `GET /api/v4/version`; админ | `GET /projects/{id}/merge_requests/{iid}/approvals` |
| `[TODO]` | Проекты вне спринта (исключить) | Тимлид | Явный список exclude в §9.2 |

### Почему важно

Repo без маппинга = **orphan / слепая зона** ([ADR-002](./adr/0002-workstream-type.md)). GitLab — первая ценность (Phase 3).

---

## 3. Jenkins

### Уже известно

| Статус | Факт |
|---|---|
| `[known]` | Режим: webhook on build finished **или** poll jobs |
| `[known]` | Маппинг: job/branch → issue key; type через repo/job map |
| `[known]` | Синхронизируем: build started/finished, result, duration, branch params |
| `[known]` | **`mptp8` использует GitLab CI/CD**, не Jenkins — pipeline events как альтернатива Phase 5 |
| `[TODO]` | **Заблокировано** — доступ к Jenkins пока нет; уточнить, нужен ли Jenkins для frontend/oracle |

### Что собрать

| Статус | Вопрос | Откуда взять | Как проверить |
|---|---|---|---|
| `[TODO]` | Базовый URL Jenkins | Dashboard в браузере; DevOps | `GET {url}/api/json` |
| `[TODO]` | Список jobs (name, folder) | Jenkins UI; REST `jobs[name,url]` | Список согласован с тимлидом |
| `[TODO]` | Маппинг job → `workstream_type_code` / repo | DevOps + тимлид | Записать в §9.4 |
| `[TODO]` | Имя параметра ветки (`BRANCH_NAME` / `GIT_BRANCH` / …) | Last build → Parameters | `GET /job/{name}/lastBuild/api/json` |
| `[TODO]` | Как извлекать issue key (multibranch? git plugin?) | Console log + build metadata | 3–5 последних билдов одного job |
| `[TODO]` | Webhook plugin vs только polling | DevOps; Notification plugin | Тестовый build → webhook |
| `[TODO]` | Service user + API token | Jenkins-админ | Auth на `api/json` |

### Почему важно

Без надёжного извлечения ветки из билда нельзя связать `BUILD_*` с issue key и Workstream Type.

---

## 4. Oracle

> Oracle — **Workstream Type**, не отдельный коннектор. Отслеживается как Git-проект с `workstream_type_code = oracle` ([integrations.md](./integrations.md)).

### Уже известно

| Статус | Факт |
|---|---|
| `[known]` | `oracle` в seed Workstream Types |
| `[known]` | Изменения вне Git = слепая зона |

### Что собрать

| Статус | Вопрос | Откуда взять | Как проверить |
|---|---|---|---|
| `[known]` | Репозиторий Oracle: **`SOFTCOMPANY/eltcbackend`** → `workstream_type_code: oracle` |
| `[known]` | `mptp8` вызывает Oracle из PHP (oci8), но **не заменяет** мониторинг eltcbackend |
| `[known]` | Риск: Oracle-процедуры могут выкатываться на snapshot **вне Git** (из README mptp8) |
| `[TODO]` | Как ведётся код в eltcbackend (PL/SQL, миграции, …) | Oracle dev / Горбунов | Интервью 15 мин |
| `[TODO]` | Отдельный CI для oracle-репо | GitLab pipelines eltcbackend? | §9.2 |
| `[TODO]` | Нужен ли мониторинг на уровне БД | Тимлид (по архитектуре — **нет**, только Git) | Зафиксировать решение |

---

## 5. Workstream Types

### Уже известно

| Статус | Факт |
|---|---|
| `[known]` | Seed (данные, не доменный код): `backend`, `frontend`, `oracle`, `qa` |
| `[known]` | `Workstream = Issue × Workstream Type` |

### Что собрать

| Статус | Вопрос | Откуда взять | Как проверить |
|---|---|---|---|
| `[TODO]` | Финальный набор типов | Тимлид: «есть ли devops, analytics, …» | Согласование в §9.5 |
| `[TODO]` | `display_name` и `sort_order` | Тимлид + UX на Board | Порядок колонок на mock/wireframe |
| `[TODO]` | Неактивные типы (`is_active = false`) | Тимлид | Нужны ли сейчас |
| `[TODO]` | Как детектировать `qa` без Git-веток | Тимлид + QA: Jira status / assignee? | Источник события `WORKSTREAM_STARTED` |

---

## 6. Naming Convention

### Уже известно

| Статус | Факт |
|---|---|
| `[known]` | **Git Flow `mptp8`:** `develop` → test, `master` → prod, `project/*` → project env |
| `[known]` | **Git Flow `mptp-react-native`:** короткие фичи → `master`, крупные → `develop` |
| `[known]` | Husky: TypeScript, ESLint, Prettier, Jest на commit/push (`mptp-react-native`) |
| `[known]` | Основной паттерн: **`feature/MPTPSUPP-{number}`** (+ суффикс `_text-fixes` и т.п.) |
| `[known]` | Подтверждено в `mptp8`: `feature/MPTPSUPP-46098_text-fixes` |
| `[known]` | Варианты с суффиксом: `feature/MPTPSUPP-41439-clean`, `feature/MPTPSUPP-42193-clean` |
| `[known]` | Опечатка в префиксе встречается: `feture/MPTPSUPP-39491_...` |
| `[known]` | Legacy без префикса: `MPTPSUPP-28609_delite_mobile`, `MPTPSUPP-39632-banner-on-payscreen` |
| `[known]` | `bugfix/` **без** issue key возможен: `bugfix/fix-dozor-zero-subscription-chat-deeplink` |
| `[known]` | Другой project key в старых ветках: `DODD-114`, `DODD-586-*` |
| `[known]` | Release/infra-ветки без ключа: `5_7_20_prod`, `Golovin`, `ab_test_*` — не спринтовые |
| `[assumed]` | Commit message: `MPTPSUPP-1234: …` — не проверено на выгрузке |
| `[known]` | Join key — Jira issue key; regex должен ловить ключ **в любом месте** имени ветки |

### Снимок naming (2026-07-14, один репо)

| Категория | Примеры | С issue key? |
|---|---|---|
| Текущий стандарт | `feature/MPTPSUPP-39033` | да |
| С суффиксом | `feature/MPTPSUPP-41439-clean` | да |
| Опечатка prefix | `feture/MPTPSUPP-39491_redesigen_main_vidget` | да |
| Bugfix без ключа | `bugfix/fix-dozor-zero-subscription-chat-deeplink` | нет → orphan |
| Legacy | `MPTPSUPP-39632-banner-on-payscreen` | да (ключ в начале) |
| Другой проект | `DODD-586-fix-show-null-on-dog-num` | да, но не MPTPSUPP |
| Release / личные | `5_7_20_prod`, `Golovin-search-clean` | нет → исключить |

**Вывод:** для спринтовых `feature/*` веток ключ есть почти всегда. Orphan — в основном `bugfix/` без ключа и release-ветки. Рекомендуемый regex — искать `(?<key>[A-Z]+-\d+)` в имени ветки, не только после `feature/`.

### Что собрать

| Статус | Вопрос | Откуда взять | Как проверить |
|---|---|---|---|
| `[known]` | Префиксы: `feature/` (основной), `bugfix/` (иногда без ключа) | `git branch -a` | §9.3 |
| `[TODO]` | Issue key в commit / MR title — всегда? | `git log`, GitLab MR | Посчитать % |
| `[known]` | Формат ключа: `[A-Z]+-\d+` (`MPTPSUPP-39033`, `DODD-114`) | Ветки | §9.3 |
| `[assumed]` | Финальные regex — черновик в §9.3; coverage % ещё не посчитан | MR за 2 спринта | Цель ≥90% |
| `[TODO]` | Политика orphan branches | Тимлид | Risk flag / ignore |

### Почему важно

Критерий Phase 3: **≥90%** задач спринта с корректным naming имеют события в Timeline ([roadmap.md](./roadmap.md)).

---

## 7. Service Accounts & Webhooks

### Уже известно

| Статус | Факт |
|---|---|
| `[known]` | Нужны tokens для Jira, GitLab, Jenkins |
| `[known]` | Webhook URL whitelist для GitLab / Jenkins / опц. Jira |
| `[known]` | Секреты **не коммитим** ([ai_context.md](./ai_context.md) §7) |

### Что собрать

| Статус | Вопрос | Откуда взять | Как проверить |
|---|---|---|---|
| `[TODO]` | Jira: login/PAT + permissions (read) | Jira-админ; заявка в IT | `GET /rest/api/2/myself` |
| `[TODO]` | GitLab: group/project token, scopes | GitLab-админ | `GET /api/v4/user` + чтение MR |
| `[TODO]` | Jenkins: user + API token | Jenkins-админ | `GET /api/json` |
| `[TODO]` | Где хранить секреты (env / vault) | DevOps / политика ИБ | Вне Git; `.env` в `.gitignore` |
| `[TODO]` | Hostname Monitor для webhooks | После Phase 1 (стенд) | Ping/curl из GitLab/Jenkins сети |
| `[TODO]` | Владелец ротации токенов | Тимлид + админ | Имя/роль в §9.5 |

### Smoke-тесты (после выдачи доступов)

```bash
# Jira
curl -u "USER:TOKEN" "https://jira.eltc.ru/rest/api/2/myself"

# GitLab
curl -H "PRIVATE-TOKEN: TOKEN" "https://GITLAB_HOST/api/v4/user"

# Jenkins
curl -u "USER:TOKEN" "https://JENKINS_HOST/api/json?tree=jobs[name]"
```

---

## 8. Open Questions

Вопросы закрываются по мере заполнения §9. Переносите в §9 с `[known]` и вычёркивайте здесь.

- [x] Project key Jira — **`MPTPSUPP` подтверждён** (ветки + board 718)
- [x] **JQL filter 30532** — получен («все задачи для МПЛК, только головные»)
- [ ] **Release Health:** дополнительно фильтровать по fixVersion swimlane или достаточно filter 30532?
- [ ] Jira webhooks — да или только polling?
- [ ] GitLab CE/EE — approvals через API?
- [ ] Jenkins нужен для frontend/oracle или достаточно **GitLab pipeline webhooks**?
- [ ] Oracle вне Git — размер слепой зоны?
- [ ] Источник `WORKSTREAM_STARTED` для `qa`?
- [ ] Стенд Monitor + доступность для webhooks?
- [ ] Владелец доступов (имена/роли админов)?
- [ ] Workstream Types подтверждены тимлидом?
- [ ] Naming coverage ≥90% на прошлых спринтах? *(черновик regex в §9.3; нужен подсчёт по MR спринта)*
- [x] `mptp8` — **backend** (Laravel 8, МП техподдержка)
- [x] Репозитории: `mptp-react-native` (frontend), `mptp8` (backend), `eltcbackend` (oracle)
- [ ] Полный список репо — только эти 3 или есть ещё?

---

## 9. Результаты Discovery (заполнять по мере сбора)

> Сюда переносятся **подтверждённые** факты. Это будущий seed-конфиг приложения ([database.md](./database.md)). Секреты — только в env/vault, не здесь.

### 9.1 Jira

```yaml
jira:
  url: https://jira.eltc.ru
  project_keys: [MPTPSUPP]
  board:
    id: 718
    name: "МПЛК доска"
    type: kanban
    filter:
      id: 30532
      name: "все задачи для МПЛК, только головные"
      owner: kleymenova.vs          # Клейменова Василиса
      view_url: "https://jira.eltc.ru/issues/?filter=30532"
      requires_scriptrunner: true   # issueFunction in parentsOf(...)
      jql: >-
        (issuetype in standardIssueTypes() AND issuetype != Баготряска AND (project = MPTPSUPP OR assignee in (repin.ea, gutov.as, lisov.kv, gungaev.zt, knyazev.vy, semenychev.di, gumenyuk.oa, eftifanov.dv, iskandirov.mr, lazarev.mv, nikulina.vs, gevorgyan.gk, khrisanov.vv, backlog.mpgg) OR issueFunction in parentsOf("assignee in (\n repin.ea,\n lisov.kv,\n gutov.as,\n gungaev.zt,\n knyazev.vy,\n semenychev.di,\n gumenyuk.oa,\n eftifanov.dv,\n iskandirov.mr,\n lazarev.mv,\n nikulina.vs,\n gevorgyan.gk,\n khrisanov.vv,\n backlog.mpgg\n )")) OR project = MPTPSUPP AND issuetype = Баготряска AND statusCategory != "To Do") AND (statusCategory != "To Do" OR assignee in (repin.ea, gutov.as, lisov.kv, gungaev.zt, knyazev.vy, semenychev.di, gumenyuk.oa, eftifanov.dv, iskandirov.mr, lazarev.mv, nikulina.vs, gevorgyan.gk, khrisanov.vv, backlog.mpgg)) AND (resolution is EMPTY AND statusCategory != Done OR resolutiondate >= startOfDay(-90d)) AND NOT (labels = sentry AND status = ОТКРЫТА) ORDER BY Rank ASC
    rank_custom_field_id: 11350
    config_url: "https://jira.eltc.ru/secure/RapidView.jspa?rapidView=718&tab=agile-tools-configuration"
  jql_polling_recommended: filter_30532   # matches board cards
  team_roster_jira_users:
    - repin.ea
    - gutov.as
    - lisov.kv
    - gungaev.zt
    - knyazev.vy
    - semenychev.di
    - gumenyuk.oa
    - eftifanov.dv
    - iskandirov.mr
    - lazarev.mv
    - nikulina.vs
    - gevorgyan.gk
    - khrisanov.vv
    - backlog.mpgg
  jql_candidates_optional:
    current_release_mp: 'fixVersion in earliestUnreleasedVersionByReleaseDate(MPTPSUPP)'
    next_release_mp: 'fixVersion in unreleasedVersions() AND fixVersion in releaseDate("after 1970/01/01")'
    sprint_swimlane: '(summary ~ "Спринт" OR issueFunction in issuesInEpics(''Epic Name'' ~ ''Спринт''))'
  column_config:
    - { name: "Список задач", status_ids: [12557] }
    - { name: "preDelivery", status_ids: [11951, 16053, 15260, 1] }
    - { name: "Требования -> ТЗ", status_ids: [14756, 11259, 14558] }
    - { name: "Спланировать", status_ids: [15353] }
    - { name: "Планирование", status_ids: [10027] }
    - { name: "Бэклог", status_ids: [14555, 11260, 14556] }
    - { name: "В работе", status_ids: [10553, 3, 10028, 10056, 17056, 17055] }
    - { name: "Ревью", status_ids: [14560, 14757] }
    - { name: "Тестирование на тестовой", status_ids: [16252, 14562] }
    - { name: "Дизайн-ревью", status_ids: [18653] }
    - { name: "Тестирование на боевой", status_ids: [16254] }
    - { name: "Релиз", status_ids: [11051] }
    - { name: "тест", status_ids: [15152, 10000, 10001] }
    - { name: "Приемка", status_ids: [14152, 11251, 15358, 14481] }
  swimlanes:
    - { name: "Блокеры", jql: 'priority = Blocker and issuetype not in (Совещание, "Совещание (бот)") AND category not in (КСУП, "КСУП (завершенные проекты)") AND project not in ("Обучение СИТ", SEonboarding)' }
    - { name: "Идёт в релиз МП (ближайший поезд)", jql: "fixVersion in earliestUnreleasedVersionByReleaseDate(MPTPSUPP)" }
    - { name: "Планируется в следующий релиз МП", jql: 'fixVersion in unreleasedVersions() AND fixVersion in releaseDate("after 1970/01/01")' }
    - { name: "Спринт", jql: '(summary ~ "Спринт" OR issueFunction in issuesInEpics(''Epic Name'' ~ ''Спринт''))' }
    - { name: "Рефакторинг МПГГ", jql: "labels = РефакторингМПГГ" }
    - { name: "Sentry", jql: "labels = sentry" }
    # full list in §1 Jira swimlanes table
  board_ui:
    card_width_px: 190
    search_bar_default_fields: [issuetype, status, assignee]
  quick_filters_noted:
    - "Продажный трек: labels = Команда_продажи"
    - "Сервисный трек: labels = Команда_сервис"
  contacts:
    board_filter_owner: { jira: kleymenova.vs, name: "Клейменова Василиса" }
    mptp_react_native: { lead: "Макс Рыльцев" }
    mptp8: { lead: "Замалдинова Виктория", dev: "Горбунов Яков" }
  custom_fields: {}
  webhook_enabled: false
  polling_interval_sec: 180
```

### 9.2 GitLab repositories

```yaml
gitlab:
  url: https://git.eltc.ru
  edition: ""                         # TODO — GET /api/v4/version
  groups:
    - path: mptp
      id: null                        # TODO
    - path: SOFTCOMPANY
      id: null                        # TODO
repositories:
  - gitlab_id: 760
    path: mptp/mptp-react-native
    url: https://git.eltc.ru/mptp/mptp-react-native
    description: "React Native, SuperApp Goodline"
    workstream_type_code: frontend
    contacts: { lead: "Макс Рыльцев" }
    confirmed: true
  - gitlab_id: 2159
    path: mptp/mptp8
    url: https://git.eltc.ru/mptp/mptp8
    description: "МП Техподдержка Goodline, Laravel 8 + SuperApp, бекенд ЛК"
    workstream_type_code: backend
    jira_project: MPTPSUPP
    environments:
      prod: https://mptp8.k8s.eltc.ru
      staging: https://mptp8.dev.k8s.eltc.ru
    cicd:
      type: gitlab_pipeline
      url: https://git.eltc.ru/mptp/mptp8/-/pipelines
      deploy_rules:
        develop: test
        master: prod
        "project/*": project_env
      k8s_namespace: mptp8
    contacts: { lead: "Замалдинова Виктория", dev: "Горбунов Яков" }
    confirmed: true
  - gitlab_id: 3494
    path: SOFTCOMPANY/eltcbackend
    url: https://git.eltc.ru/SOFTCOMPANY/eltcbackend
    description: "Oracle procedures / ELTC backend"
    workstream_type_code: oracle
    confirmed: true
exclude_projects: []
```

### 9.3 Naming patterns

```yaml
# Draft 2026-07-14 — based on git branch -a (mptp mobile repo)
naming:
  primary_project_key: MPTPSUPP
  branch_patterns:
    # Primary: feature/MPTPSUPP-12345
    - '(?<prefix>feature|bugfix|feture)/(?<key>[A-Z]+-\d+)'
    # Suffixed: feature/MPTPSUPP-41439-clean
    - '(?<prefix>feature)/(?<key>[A-Z]+-\d+)[-_].+'
    # Legacy: MPTPSUPP-39632-banner-on-payscreen
    - '^(?<key>[A-Z]+-\d+)[-_].+'
    # Fallback: key anywhere in branch name
    - '(?<key>[A-Z]+-\d+)'
  commit_pattern: '(?<key>[A-Z]+-\d+)'       # assumed — verify git log
  mr_title_pattern: '(?<key>[A-Z]+-\d+)'     # TODO — check GitLab MRs
  orphan_examples:
    - bugfix/fix-dozor-zero-subscription-chat-deeplink
    - 5_7_20_prod
    - Golovin-search-clean
  coverage_last_sprint_pct: null             # TODO — count MRs in last sprint
  notes:
    - "feture" typo seen — consider tolerant prefix or fallback regex
    - DODD-* branches exist — confirm if in sprint scope
    - "Короткие фичи" идут в master, "крупные" в develop — оба потока нужно мониторить
  git_flow:
    mptp_react_native:
      short_product: "master → master → sync develop"
      large_tech: "develop → develop"
    mptp8:
      develop: test_deploy
      master: prod_deploy
      "project/*": project_env
  examples_mptp8:
    - feature/MPTPSUPP-46098_text-fixes
```

### 9.4 CI / Jenkins

```yaml
# mptp8 — GitLab CI (confirmed), Jenkins not used for this repo
gitlab_ci:
  mptp/mptp8:
    pipelines_url: https://git.eltc.ru/mptp/mptp8/-/pipelines
    webhook_events: [pipeline]   # candidate for BUILD_* instead of Jenkins

jenkins:
  url: ""
  status: blocked                # 2026-07-14 — no access; TBD if needed for react-native / oracle
  jobs: []
```

### 9.5 Workstream types & access owners

```yaml
workstream_types:
  - { code: backend,  display_name: Backend,  sort_order: 1, is_active: true }
  - { code: frontend, display_name: Frontend, sort_order: 2, is_active: true }
  - { code: oracle,   display_name: Oracle,   sort_order: 3, is_active: true }
  - { code: qa,       display_name: QA,       sort_order: 4, is_active: true }

access_owners:
  mptp_react_native: "Макс Рыльцев"
  mptp8: "Замалдинова Виктория"
  mptp8_dev: "Горбунов Яков"
  jira: ""
  gitlab: ""
  jenkins: ""
  monitor_host: ""
```

---

## 10. Контакты и доступы (чеклист)

| Система | Что запросить | У кого | Запрошено | Выдано | Проверено |
|---|---|---|---|---|---|
| Jira | Read-only service account или PAT | Jira-админ | ☐ | ☐ | ☐ |
| GitLab | Group token: `read_api`, `read_repository` | GitLab-админ | ☐ | ☐ | ☐ |
| Jenkins | API token, read jobs/builds | Jenkins-админ | ☐ | ☐ | ☐ |
| Сеть | Whitelist URL Monitor для webhooks | DevOps / сеть | ☐ | ☐ | ☐ |

---

## Change policy

- Подтверждённые данные → §9 + при необходимости [integrations.md](./integrations.md), [database.md](./database.md).
- По завершении Phase 0 → запись в [session_log.md](./session_log.md) и [changelog.md](./changelog.md).
- Секреты никогда не попадают в этот файл или Git.
