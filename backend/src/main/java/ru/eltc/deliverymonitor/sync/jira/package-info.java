/**
 * Jira sync — the application-level layer (docs/roadmap.md Phase 2.2 "Jira Sync", Phase 2.3
 * "Persistence").
 *
 * <p>Sits <em>above</em> {@code integration.jira}: it orchestrates fetching the observed board's
 * issues through the {@code JiraContextProvider} seam (never the raw {@code JiraClient}), paginates
 * across all pages, normalizes each wire {@code JiraIssueDto} into an internal {@link
 * ru.eltc.deliverymonitor.sync.jira.JiraIssueSnapshot}, maps it into {@code domain.issue}'s own
 * {@code IssueUpsertCommand} and upserts each page immediately through {@code
 * domain.issue.IssuePersistencePort}. Returns a {@link
 * ru.eltc.deliverymonitor.sync.jira.JiraSyncResult} describing the run (aggregates only).
 *
 * <p>{@code JiraIssueSnapshot} is the deliberate seam towards persistence: upper layers depend on
 * this stable internal shape, not on the external Jira REST API structure, so changes to the Jira
 * API cannot ripple into the schema. Dependency direction is strictly {@code sync.jira ->
 * domain.issue} — this package depends on {@code domain.issue}'s contracts, never the reverse (see
 * docs/architecture.md "Package dependency direction").
 *
 * <p><b>Deliberately out of scope in this phase</b> (docs/roadmap.md): the {@code POST
 * /api/admin/sync/jira} REST controller (Phase 2.4), {@code sprints}/{@code sync_state} tables,
 * incremental sync by {@code updated}, the {@code @Scheduled} poller (Phase 2.5), Spring Security,
 * and GitLab/Jenkins.
 */
package ru.eltc.deliverymonitor.sync.jira;
