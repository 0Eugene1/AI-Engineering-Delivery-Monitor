/**
 * Minimal Spring Security enforcement layer (Phase 2.4, ADR-012 — see docs/security.md §2).
 *
 * <p>Scope, by explicit design:
 * <ul>
 *   <li>{@link ru.eltc.deliverymonitor.api.security.SecurityConfig} — wires the {@code
 *       SecurityFilterChain}: {@code /actuator/health} stays public, every other actuator
 *       endpoint and {@code /api/admin/**} require authentication, every other endpoint is left
 *       as-is (currently open within the VPN perimeter). No JWT, no OAuth2 Resource Server, no
 *       OIDC, no LDAP.</li>
 *   <li>{@link ru.eltc.deliverymonitor.api.security.AdminTokenAuthenticationFilter} — validates
 *       the {@code Authorization: Bearer <token>} header against a single configured admin
 *       token.</li>
 *   <li>{@link ru.eltc.deliverymonitor.api.security.AdminTokenProperties} — binds {@code
 *       delivery-monitor.admin.token} from {@code DELIVERY_MONITOR_ADMIN_TOKEN} (env only, never
 *       committed).</li>
 * </ul>
 *
 * <p><b>This is intentionally not a user authentication system.</b> "Authenticated" here means
 * exactly one thing: <em>the request presented the admin token</em> — nothing more. There is no
 * {@code User} entity, no {@code Role} entity, no {@code UserRepository}, no principal model and
 * no permissions; the resulting {@code Authentication} carries a single generic {@code
 * ROLE_ADMIN} authority and no extracted identity (docs/security.md §2, §7 — "admin token
 * validation is intentionally stateless"). The target model — corporate SSO/OIDC with real user
 * identity and PM/QA/Developer roles — is deferred to a future ADR.
 */
package ru.eltc.deliverymonitor.api.security;
