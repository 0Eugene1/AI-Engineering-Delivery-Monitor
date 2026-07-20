/**
 * Risks Read API (Phase 4.2).
 *
 * <p>Dependency direction:
 *
 * <pre>
 * PostgreSQL -&gt; domain.{workstream,timeline,gitlab,issue} -&gt; domain.risk -&gt; api.risk
 * </pre>
 *
 * <p>Evaluate-on-read — no {@code risk_flags} table, no JPA risk entity. Empty result is
 * always {@code 200} + {@code risks: []}.
 *
 * <p><b>Out of scope:</b> UI; AI; Kafka/Redis/CQRS; Jenkins; dismiss/ack persistence.
 */
package ru.eltc.deliverymonitor.api.risk;
