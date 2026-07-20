/**
 * GitLab sync — application layer (docs/roadmap.md Phase 3.2 + 3.4.1 + 3.5 + 3.6 + 3.8 + 3.9).
 *
 * <p>Sits <em>above</em> {@code integration.gitlab} and depends <em>down</em> on
 * {@code domain.repository} / {@code domain.gitlab} / {@code domain.timeline} /
 * {@code domain.workstream}. Orchestrates fetch → snapshot → upsert:
 *
 * <pre>
 *   repositories (PostgreSQL) → RepositoryPersistencePort
 *         → GitLabClient → snapshots → Branch/Commit/MergeRequest PersistencePort
 *         → activity_events → workstreams (when issue_key present)
 * </pre>
 *
 * <p><b>Single SoT for observed repos (docs/decisions.md):</b> in {@code gitlab.mode=rest}
 * the list comes only from {@code RepositoryPersistencePort} — yaml
 * {@code gitlab.sync.repositories} is ignored. In {@code gitlab.mode=mock} yaml selects which
 * projects to sync; each entry is resolved to a seeded {@code repositories} row by
 * {@code gitlab_project_id}.
 *
 * <p>Two callers trigger a full run through the exact same {@link
 * ru.eltc.deliverymonitor.sync.gitlab.GitLabSyncService#syncAll()} method, and neither bypasses
 * it: the {@code POST /api/admin/sync/gitlab} REST controller (Phase 3.8, {@code
 * ru.eltc.deliverymonitor.api.admin}) for manual runs, and {@link
 * ru.eltc.deliverymonitor.sync.gitlab.GitLabSyncScheduler} (Phase 3.9, {@code
 * gitlab.sync.enabled}/{@code gitlab.sync.interval}, {@code fixedDelay} semantics) for background
 * runs. {@link ru.eltc.deliverymonitor.sync.gitlab.GitLabSyncService} itself carries a small
 * in-process guard (an {@link java.util.concurrent.atomic.AtomicBoolean}) so the two can never run
 * concurrently — no distributed lock, no {@code sync_state} row. This package stays HTTP-agnostic
 * either way.
 *
 * <p><b>Deliberately out of scope:</b> dashboard, IssueEntity lookup, webhooks, pipelines,
 * {@code sync_state}, distributed lock, incremental sync, retry framework.
 */
package ru.eltc.deliverymonitor.sync.gitlab;
