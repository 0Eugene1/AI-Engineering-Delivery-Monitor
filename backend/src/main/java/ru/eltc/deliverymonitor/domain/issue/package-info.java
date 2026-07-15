/**
 * Issue persistence — Phase 2.3 "Persistence" (docs/roadmap.md).
 *
 * <p>{@code domain.issue} is the sole owner of its persistence contracts: {@link
 * ru.eltc.deliverymonitor.domain.issue.IssueEntity}, {@link
 * ru.eltc.deliverymonitor.domain.issue.IssueRepository}, {@link
 * ru.eltc.deliverymonitor.domain.issue.IssuePersistencePort} (+ {@link
 * ru.eltc.deliverymonitor.domain.issue.IssueUpsertCommand}, {@link
 * ru.eltc.deliverymonitor.domain.issue.IssueUpsertOutcome}) and its default implementation {@link
 * ru.eltc.deliverymonitor.domain.issue.IssueUpsertService}.
 *
 * <p>Dependency direction is strictly {@code sync.jira -> domain.issue}: this package never
 * imports anything from {@code sync.jira} (not {@code JiraIssueSnapshot}, not {@code
 * JiraSyncService}). Callers map their own data into {@link
 * ru.eltc.deliverymonitor.domain.issue.IssueUpsertCommand} before calling {@link
 * ru.eltc.deliverymonitor.domain.issue.IssuePersistencePort}.
 *
 * <p>Upsert matches an existing row by {@code jiraId} (Jira's immutable internal id), never by
 * {@code key} (which can change if an issue is moved between Jira projects). {@code fixVersions}
 * and {@code labels} are stored via {@code @ElementCollection} value tables ({@code
 * issue_fix_versions}/{@code issue_labels}).
 *
 * <p><b>Deliberately out of scope in this phase</b> (docs/roadmap.md, docs/decisions.md): the
 * {@code sprints} and {@code sync_state} tables (no sprint data on the observed Kanban board, no
 * incremental sync/watermark yet), the {@code @Scheduled} poller (Phase 2.5), and GitLab/Jenkins.
 * The {@code POST /api/admin/sync/jira} REST controller and the admin Spring Security baseline
 * were added in Phase 2.4 ({@code ru.eltc.deliverymonitor.api.admin}, {@code
 * ru.eltc.deliverymonitor.api.security}) — neither imports from this package directly; both go
 * through {@code sync.jira.JiraSyncService}.
 */
package ru.eltc.deliverymonitor.domain.issue;
