/**
 * Jira sync — the application-level layer (docs/roadmap.md Phase 2.2 "Jira Sync", Phase 2.3
 * "Persistence", Phase 2.5 "Scheduler").
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
 * <p>Two callers trigger a run through the exact same {@link
 * ru.eltc.deliverymonitor.sync.jira.JiraSyncService#syncBoard()} method, and neither bypasses it:
 * the {@code POST /api/admin/sync/jira} REST controller (Phase 2.4, {@code
 * ru.eltc.deliverymonitor.api.admin}) for manual runs, and {@link
 * ru.eltc.deliverymonitor.sync.jira.JiraSyncScheduler} (Phase 2.5, {@code
 * jira.sync.enabled}/{@code jira.sync.interval}, {@code fixedDelay} semantics) for background
 * runs. {@link ru.eltc.deliverymonitor.sync.jira.JiraSyncService} itself carries a small
 * in-process guard (an {@link java.util.concurrent.atomic.AtomicBoolean}) so the two can never run
 * concurrently — no distributed lock, no {@code sync_state} row. This package stays HTTP-agnostic
 * either way. <b>Deliberately out of scope</b> (docs/roadmap.md): a {@code sync_state} table, a
 * distributed lock (ShedLock/Redis), incremental sync by {@code updated}, a retry framework, and
 * GitLab/Jenkins.
 */
package ru.eltc.deliverymonitor.sync.jira;
