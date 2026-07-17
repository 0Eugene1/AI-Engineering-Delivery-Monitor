/**
 * Timeline / {@code activity_events} — Phase 3.5 (docs/roadmap.md, docs/database.md, ADR-008).
 *
 * <p>{@code domain.timeline} owns:
 * <ul>
 *   <li>{@link ru.eltc.deliverymonitor.domain.timeline.IssueKeyExtractor} — pure
 *       {@code String → Optional<String>} regex extraction (no DB, no Jira lookup);</li>
 *   <li>persistence for {@code activity_events}: entity, repository, upsert port + service.</li>
 * </ul>
 *
 * <p>Idempotency: UNIQUE {@code (source, source_ref)}. Callers ({@code sync.gitlab}) stamp
 * {@code issue_key} on git entities via the extractor and write events through
 * {@link ru.eltc.deliverymonitor.domain.timeline.ActivityEventPersistencePort}.
 *
 * <p><b>Deliberately out of scope here:</b> timeline read API, workstreams, Jira resolution
 * ({@code IssueEntity} lookup), Jenkins, scheduler, Activity Feed UI.
 */
package ru.eltc.deliverymonitor.domain.timeline;
