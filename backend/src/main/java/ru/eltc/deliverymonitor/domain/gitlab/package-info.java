/**
 * GitLab git-entity persistence — Phase 3.4 (docs/roadmap.md, docs/database.md).
 *
 * <p>{@code domain.gitlab} owns persistence for {@code branches}, {@code commits}, and
 * {@code merge_requests}: JPA entities, Spring Data repositories, and upsert ports
 * ({@code *PersistencePort} + {@code *UpsertCommand}/{@code *UpsertOutcome} + default
 * services). Matching keys:
 *
 * <ul>
 *   <li>branches — {@code (repositoryId, name)}</li>
 *   <li>commits — {@code (repositoryId, sha)}</li>
 *   <li>merge_requests — {@code (repositoryId, gitlabIid)}</li>
 * </ul>
 *
 * <p>Rows link to observed projects via {@code repository_id} FK → {@code repositories.id}
 * (not by GitLab path/name). {@code issue_key} is nullable (orphan policy); callers
 * ({@code sync.gitlab}) stamp it via {@code domain.timeline.IssueKeyExtractor} (Phase 3.5).
 * This package does not import {@code sync.gitlab} or {@code domain.timeline}.
 *
 * <p><b>Deliberately out of scope here:</b> {@code activity_events} (owned by
 * {@code domain.timeline}), {@code workstreams}, REST API, scheduler, security.
 */
package ru.eltc.deliverymonitor.domain.gitlab;
