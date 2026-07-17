package ru.eltc.deliverymonitor.sync.gitlab;

import java.time.Instant;

/**
 * Internal, normalized view of a GitLab merge request produced by {@link GitLabSyncService}.
 *
 * <p>Sync-layer contract towards future {@code merge_requests} persistence — decoupled from wire
 * {@code GitLabMergeRequestDto}. {@code issueKey} is intentionally absent here: extraction lands
 * in Phase 3.5. Approvals API (EE) is out of Phase 3.1/3.2 scope.
 *
 * @param projectId           GitLab project id
 * @param id                  GitLab global MR id, or {@code null}
 * @param iid                 project-local MR iid
 * @param title               MR title, or {@code null}
 * @param state               GitLab state ({@code opened}/{@code merged}/{@code closed}/…), or {@code null}
 * @param sourceBranch        source branch name, or {@code null}
 * @param targetBranch        target branch name, or {@code null}
 * @param authorUsername      author username, or {@code null}
 * @param authorDisplayName   author display name, or {@code null}
 * @param createdAt           created time, or {@code null} if unparsable
 * @param updatedAt           updated time, or {@code null} if unparsable
 * @param mergedAt            merged time, or {@code null} if not merged / unparsable
 * @param webUrl              GitLab web URL, or {@code null}
 * @param workstreamTypeCode  configured type from {@code gitlab.sync.repositories}
 */
public record GitLabMergeRequestSnapshot(
        long projectId,
        Long id,
        long iid,
        String title,
        String state,
        String sourceBranch,
        String targetBranch,
        String authorUsername,
        String authorDisplayName,
        Instant createdAt,
        Instant updatedAt,
        Instant mergedAt,
        String webUrl,
        String workstreamTypeCode
) {
}
