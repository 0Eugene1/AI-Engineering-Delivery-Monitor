/**
 * Risk evaluation domain (Phase 4.2).
 *
 * <p>Logical risks only — <b>no</b> JPA entity and <b>no</b> {@code risk_flags} table.
 * Rules are evaluated on read from existing domains:
 *
 * <pre>
 * domain.risk
 *       ↓
 * workstream / timeline / gitlab / issue
 * </pre>
 *
 * <p>HTTP mapping lives in {@code api.risk} ({@code GET /api/risks}).
 */
package ru.eltc.deliverymonitor.domain.risk;
