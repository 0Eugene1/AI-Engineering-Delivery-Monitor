/**
 * GitLab sync — application layer (docs/roadmap.md Phase 3.2 + 3.4.1 + 3.5 + 3.6).
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
 * <p><b>Deliberately out of scope:</b> timeline/admin HTTP, dashboard, IssueEntity lookup,
 * scheduler, webhooks, pipelines.
 */
package ru.eltc.deliverymonitor.sync.gitlab;
