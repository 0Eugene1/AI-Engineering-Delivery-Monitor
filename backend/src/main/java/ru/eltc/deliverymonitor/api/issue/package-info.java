/**
 * Read-only issue API (Phase 2 Read API + Phase 3.7 Timeline).
 *
 * <p>Dependency direction:
 *
 * <pre>
 * PostgreSQL -&gt; domain.issue -&gt; api.issue          (issues list/detail)
 * PostgreSQL -&gt; domain.timeline (+ workstream_type) -&gt; api.issue  (timeline)
 * </pre>
 *
 * <p>Never calls {@code sync.*} / {@code integration.*} / live Jira or GitLab.
 *
 * <ul>
 *   <li>{@link ru.eltc.deliverymonitor.api.issue.IssueController} /
 *       {@link ru.eltc.deliverymonitor.api.issue.IssueQueryService} /
 *       {@link ru.eltc.deliverymonitor.api.issue.IssueResponse} — {@code GET /api/issues},
 *       {@code GET /api/issues/{key}} ({@code 404} if missing).</li>
 *   <li>{@link ru.eltc.deliverymonitor.api.issue.TimelineController} /
 *       {@link ru.eltc.deliverymonitor.api.issue.TimelineQueryService} /
 *       {@link ru.eltc.deliverymonitor.api.issue.TimelineResponse} — {@code GET
 *       /api/issues/{key}/timeline} (always {@code 200}, empty {@code events} allowed; does not
 *       require {@code IssueEntity}).</li>
 *   <li>{@link ru.eltc.deliverymonitor.api.issue.ErrorResponse} — {@code { "error", "code" }} for
 *       issue {@code 404}.</li>
 * </ul>
 *
 * <p><b>Out of scope:</b> {@code GET /api/sprints/current}; optional nested {@code workstreams[]}
 * on issue detail; Activity Feed; write API; security changes.
 */
package ru.eltc.deliverymonitor.api.issue;
