/**
 * Read-only issue API ("Read API" phase, docs/roadmap.md; design decision recorded in
 * docs/session_log.md before this phase started).
 *
 * <p>Dependency direction, enforced by this package:
 *
 * <pre>
 * PostgreSQL -&gt; domain.issue -&gt; api.issue
 * </pre>
 *
 * <p>{@code api.issue} depends only on {@code domain.issue} ({@link
 * ru.eltc.deliverymonitor.domain.issue.IssueRepository}, {@link
 * ru.eltc.deliverymonitor.domain.issue.IssueEntity}). It never calls {@code sync.jira}, {@code
 * integration.jira}, or {@code JiraClient} — no live Jira requests happen from this package, and
 * the existing sync flow ({@code JiraSyncService}, {@code POST /api/admin/sync/jira}) is
 * untouched.
 *
 * <ul>
 *   <li>{@link ru.eltc.deliverymonitor.api.issue.IssueController} — pure HTTP adapter for
 *       {@code GET /api/issues} and {@code GET /api/issues/{key}}; no business logic.</li>
 *   <li>{@link ru.eltc.deliverymonitor.api.issue.IssueQueryService} — read-only
 *       ({@code @Transactional(readOnly = true)}) query layer; loads {@code IssueEntity} rows and
 *       maps them to {@link ru.eltc.deliverymonitor.api.issue.IssueResponse} while the JPA
 *       session is still open, so the LAZY {@code fixVersions}/{@code labels}
 *       {@code @ElementCollection}s never leak a {@code LazyInitializationException} across the
 *       API boundary.</li>
 *   <li>{@link ru.eltc.deliverymonitor.api.issue.IssueResponse} — external DTO, deliberately
 *       separate from the JPA entity (no {@code jiraId}, no database id, no entity/collection
 *       proxies leaked).</li>
 *   <li>{@link ru.eltc.deliverymonitor.api.issue.ErrorResponse} — the {@code { "error", "code" }}
 *       shape from docs/api.md, used for the {@code 404} response.</li>
 * </ul>
 *
 * <p><b>Deliberately out of scope in this phase</b> (architecture review before implementation):
 * {@code GET /api/sprints/current} — there is no {@code sprints} table yet (docs/database.md
 * marks it Planned/future, deferred from Phase 2.3); no {@code sprints} table, mock/stub response,
 * or live Jira call is substituted for it. TODO: implement once sprint persistence exists (see
 * docs/discovery.md). Also out of scope: pagination, sorting, filtering, search, and any change
 * to {@code sync.jira}, {@code integration.jira}, {@code JiraClient}, {@code
 * api.admin.JiraSyncController}, or {@code api.security.SecurityConfig}.
 */
package ru.eltc.deliverymonitor.api.issue;
