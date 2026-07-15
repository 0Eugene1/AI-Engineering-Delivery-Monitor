# ADR-012: Minimal authentication baseline for admin endpoints

| Field | Value |
|---|---|
| **ADR** | ADR-012 |
| **Title** | Minimal authentication baseline for admin endpoints |
| **Status** | Accepted |
| **Date** | 2026-07-15 |
| **Deciders** | Architecture working group |
| **Related** | [security.md](../security.md), [api.md](../api.md), [architecture.md](../architecture.md), [roadmap.md](../roadmap.md), [ADR-003](./0003-modular-monolith.md), [ADR-007](./0007-rest-only.md) |

## Context

Phase 2.4 (REST API, [roadmap.md](./../roadmap.md)) впервые делает HTTP-доступным
привилегированный, **мутирующий** endpoint `POST /api/admin/sync/jira` — он инициирует
исходящие вызовы к Jira под сервисным аккаунтом и пишет в PostgreSQL. До этого момента всё
приложение состояло из offline-слоёв (`integration.jira`, `sync.jira`, `domain.issue`) и
read-only actuator; никакого `spring-boot-starter-security` и ни одного контроллера в коде нет.

[security.md](./../security.md) задаёт целевую модель: аутентификация пользователей — только
через корпоративный SSO/LDAP/OIDC (§2), локальных пользователей нет, `/api/admin/*` — только для
роли Admin (§3), привилегированные действия аудируются (§7). При этом §2 в редакции v1.0 писался,
когда HTTP-endpoint'ов не было вообще, и содержал фразу «добавление Spring Security на данном
этапе не предполагается». [api.md](./../api.md) у admin-endpoint фиксирует «admin-only (auth TBD),
минимум — не публичный без auth на prod».

Security Assumptions ([security.md](./../security.md)) на текущем этапе: внутренняя сеть,
корпоративный VPN, сервис не публикуется в интернет, интеграции через сервисные аккаунты.

## Problem

С момента появления `POST /api/admin/sync/jira` опора только на сетевой периметр (VPN)
недостаточна: внутри периметра находятся все сотрудники и другие сервисы, а endpoint —
привилегированное мутирующее действие. Требование §3 «admin-only» и §7 «audit привилегированных
операций» становятся реальными. Одновременно полноценный корпоративный SSO/OIDC сейчас
недоступен: провайдер/поток на `eltc.ru` не выбран (это решается при развёртывании), а
пользовательского фронтенда ещё нет. Нужен способ защитить admin-направление **сейчас**, не
затаскивая преждевременно OIDC-инфраструктуру.

## Decision

1. **Ввести Spring Security только как минимальный enforcement-слой.** Его назначение строго
   ограничено: защита привилегированных endpoint, проверка admin-доступа и подготовка основы под
   будущий OIDC. Spring Security **не** вводится как полная система аутентификации пользователей.

2. **Auth baseline — статичный Bearer API-token из environment variable для `/api/admin/**`.**
   Явные ограничения baseline:
   - нет локальных пользователей;
   - нет user storage;
   - нет ролей PM/QA/Developer в коде (только admin-gate);
   - нет OIDC implementation.

   API-token — **временный machine/admin baseline** до выбора корпоративного SSO/OIDC, а **не**
   финальная система аутентификации пользователей.

3. **Отдельный токен для входящего admin API — не переиспользовать `JIRA_TOKEN`.** Вводится
   отдельная переменная (напр. `DELIVERY_MONITOR_ADMIN_TOKEN`). Это разные границы доверия:

   ```text
   JIRA_TOKEN                       →  исходящие вызовы Monitor → Jira (Monitor как клиент)
   DELIVERY_MONITOR_ADMIN_TOKEN     →  входящие вызовы клиент → admin API (Monitor как сервер)
   ```

   Утечка одного токена **не** должна автоматически давать доступ ко второму направлению.
   Оба — только из env, не в Git и не в логах ([security.md](./../security.md) §5, §8).

4. **Endpoint policy:**

   | Endpoint | Политика | Причина |
   |---|---|---|
   | `POST /api/admin/sync/jira` | **Protected** (admin token) | Привилегированное мутирующее действие + внешние вызовы |
   | actuator, кроме `health`/liveness | **Protected** | Может раскрывать конфигурацию |
   | `GET /api/issues` | **Currently open** | Internal read-only API внутри VPN |
   | `GET /api/sprints/current` | **Currently open** | Internal read-only API внутри VPN |

   **Защита read-эндпоинтов будет пересмотрена при появлении UI/SSO** — тогда чтение переходит под
   аутентификацию корпоративного провайдера, а не остаётся открытым за VPN.

## Alternatives considered

| Option | Why rejected / deferred |
|---|---|
| **OIDC now** | Провайдер/поток на `eltc.ru` не выбран (решается при развёртывании), пользовательского UI ещё нет. Полноценный OIDC ради одного admin-POST — преждевременная инфраструктура. Остаётся **целевой** моделью для пользовательской аутентификации. |
| **Basic auth** | Семантика username/password конфликтует с §2 «локальных пользователей нет»; для machine/скриптового триггера admin-sync Bearer-токен чище и не создаёт иллюзии пользовательской БД. |
| **VPN only (network-only)** | Текущий де-факто. Недостаточно с момента появления мутирующего admin-endpoint: внутри периметра — все сотрудники и сервисы; не выполняет §3 (admin-only) и §7 (audit). |

## Consequences

### Positive

- Привилегированное направление (`/api/admin/**`) защищено уже в Phase 2.4; выполняются §3 и §7.
- Малый, обратимый шаг: тонкая security-конфигурация без user storage и без OIDC-инфраструктуры.
- Разделение токенов ограничивает радиус поражения: компрометация одного направления не открывает
  второе.
- Заложена основа (Spring Security на месте) для последующей замены token-фильтра на OIDC —
  без переписывания слоёв.

### Negative / Trade-offs

- Нет идентичности конкретного пользователя для admin-действий: аудит фиксирует факт/источник и
  результат, но не персону (это приемлемо до SSO).
- Read-эндпоинты остаются открытыми внутри VPN — осознанный временный компромисс, подлежащий
  пересмотру при появлении UI/SSO.
- Появляется ещё один секрет для ротации (`DELIVERY_MONITOR_ADMIN_TOKEN`) —
  владелец ротации фиксируется по общему правилу ([discovery.md](./../discovery.md) §9.5).

### Follow-ups

- Обновить [security.md](./../security.md) (§2 Authentication, §5 Secrets, §9 Checklist),
  [api.md](./../api.md) (Auth conventions + примечание у admin-endpoint), [architecture.md](./../architecture.md)
  (пакет `api` — security config), индекс [decisions.md](./../decisions.md), [changelog.md](./../changelog.md).
- **Не меняются** ADR-001/003/011 и [roadmap.md](./../roadmap.md).
- Будущий ADR при переходе на корпоративный SSO/OIDC (замена token-baseline пользовательской
  аутентификацией и включение ролей PM/QA/Developer + защита read-эндпоинтов).

## Notes

- Реализация (конкретный класс `SecurityFilterChain`, формат фильтра, имя bean) — вне ADR; ADR
  фиксирует границы и решение, не код.
- HTTPS/TLS остаётся эксплуатационным контролем ([security.md](./../security.md) §6), не частью
  этого решения.
