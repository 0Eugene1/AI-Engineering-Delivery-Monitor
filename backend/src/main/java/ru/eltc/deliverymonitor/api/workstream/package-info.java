/**
 * Read-only Workstream Type API (Phase 3.7, docs/api.md {@code GET /api/workstream-types}).
 *
 * <p>Dependency direction:
 *
 * <pre>
 * PostgreSQL -&gt; domain.workstream_type -&gt; api.workstream
 * </pre>
 *
 * <p>No live Jira/GitLab calls; uses the seeded {@code workstream_types} dictionary only.
 *
 * <ul>
 *   <li>{@link ru.eltc.deliverymonitor.api.workstream.WorkstreamTypeController} — thin HTTP adapter</li>
 *   <li>{@link ru.eltc.deliverymonitor.api.workstream.WorkstreamTypeQueryService} — read-only mapping</li>
 *   <li>{@link ru.eltc.deliverymonitor.api.workstream.WorkstreamTypeResponse} — external DTO</li>
 * </ul>
 */
package ru.eltc.deliverymonitor.api.workstream;
