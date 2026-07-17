package ru.eltc.deliverymonitor.sync.gitlab;

import java.util.List;

/**
 * Normalized per-project outcome of one GitLab sync pass: project metadata from {@code getProject}
 * plus the branch/commit/MR snapshots collected for that project. Carried inside
 * {@link GitLabSyncResult} until persistence consumes these lists (Phase 3.3–3.4).
 *
 * <p>Lists are never {@code null} (empty instead).
 *
 * @param gitlabId           resolved GitLab project id
 * @param path               {@code path_with_namespace}, or configured path fallback
 * @param name               project name, or {@code null}
 * @param defaultBranch      default branch name, or {@code null}
 * @param workstreamTypeCode configured Workstream Type code (ADR-002)
 * @param branches           normalized branches
 * @param commits            normalized commits (already bounded by commit-history-days)
 * @param mergeRequests      normalized merge requests ({@code state=all})
 */
public record GitLabProjectSyncSnapshot(
        long gitlabId,
        String path,
        String name,
        String defaultBranch,
        String workstreamTypeCode,
        List<GitLabBranchSnapshot> branches,
        List<GitLabCommitSnapshot> commits,
        List<GitLabMergeRequestSnapshot> mergeRequests
) {
}
