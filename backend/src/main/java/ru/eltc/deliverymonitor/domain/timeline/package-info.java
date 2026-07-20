/**
 * Timeline / {@code activity_events} — Phase 3.5 write + 3.7 / 4.1 read
 * (docs/roadmap.md, docs/database.md, ADR-008).
 *
 * <p>{@code domain.timeline} owns:
 * <ul>
 *   <li>{@link ru.eltc.deliverymonitor.domain.timeline.IssueKeyExtractor} — pure
 *       {@code String → Optional<String>} regex extraction (no DB, no Jira lookup);</li>
 *   <li>persistence for {@code activity_events}: entity, repository, upsert port + service;</li>
 *   <li>read queries used by Issue Timeline and Activity Feed (same table).</li>
 * </ul>
 *
 * <p>Idempotency: UNIQUE {@code (source, source_ref)}. Callers ({@code sync.gitlab}) stamp
 * {@code issue_key} on git entities via the extractor and write events through
 * {@link ru.eltc.deliverymonitor.domain.timeline.ActivityEventPersistencePort}.
 *
 * <p>HTTP mapping lives in {@code api.issue} (Timeline) and {@code api.activity} (Feed) — not here.
 * No separate {@code domain.activity} package in Phase 4.
 *
 * <p><b>Deliberately out of scope here:</b> workstreams, Jira resolution ({@code IssueEntity}
 * lookup), Jenkins, scheduler, UI, Risks.
 */
package ru.eltc.deliverymonitor.domain.timeline;
