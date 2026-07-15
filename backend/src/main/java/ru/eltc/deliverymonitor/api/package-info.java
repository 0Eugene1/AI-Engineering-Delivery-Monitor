/**
 * REST API layer (docs/architecture.md "Backend packages" — {@code api}) — HTTP controllers plus
 * the minimal Spring Security enforcement layer that gates {@code /api/admin/**} (Phase 2.4,
 * ADR-012).
 *
 * <p>Sub-packages:
 * <ul>
 *   <li>{@code api.admin} — admin-only controllers, e.g. {@link
 *       ru.eltc.deliverymonitor.api.admin.JiraSyncController}. Pure HTTP adapters: they translate
 *       a request into a call on an existing application-layer service ({@code sync.jira}, …) and
 *       return its result. No business logic lives here.</li>
 *   <li>{@code api.security} — {@link ru.eltc.deliverymonitor.api.security.SecurityConfig} and the
 *       admin Bearer-token filter. Not a full authentication system: see the package's own
 *       Javadoc and ADR-012.</li>
 *   <li>{@code api.issue} — read-only issue API ({@code GET /api/issues}, {@code GET
 *       /api/issues/{key}}), depending only on {@code domain.issue} (never {@code sync.jira} or
 *       {@code integration.jira} — no live Jira calls). See the package's own Javadoc.</li>
 * </ul>
 */
package ru.eltc.deliverymonitor.api;
