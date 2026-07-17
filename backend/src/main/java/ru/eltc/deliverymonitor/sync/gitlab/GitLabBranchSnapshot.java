package ru.eltc.deliverymonitor.sync.gitlab;

/**
 * Internal, normalized view of a GitLab branch produced by {@link GitLabSyncService}.
 *
 * <p>Sync-layer contract towards future {@code branches} persistence — decoupled from wire
 * {@code GitLabBranchDto}. {@code issueKey} is intentionally absent here: extraction lands in
 * Phase 3.5.
 *
 * @param projectId          GitLab project id
 * @param name               branch name
 * @param commitSha          tip commit sha, or {@code null} if missing
 * @param merged             whether GitLab reports the branch as merged
 * @param protectedBranch    whether the branch is protected
 * @param defaultBranch      whether this is the default branch
 * @param webUrl             GitLab web URL, or {@code null}
 * @param lastCommitAt       tip commit time, or {@code null} if unparsable/missing
 * @param workstreamTypeCode configured type from {@code gitlab.sync.repositories}
 */
public record GitLabBranchSnapshot(
        long projectId,
        String name,
        String commitSha,
        Boolean merged,
        Boolean protectedBranch,
        Boolean defaultBranch,
        String webUrl,
        java.time.Instant lastCommitAt,
        String workstreamTypeCode
) {
}
