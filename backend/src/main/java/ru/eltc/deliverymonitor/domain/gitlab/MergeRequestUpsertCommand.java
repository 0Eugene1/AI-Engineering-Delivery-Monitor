package ru.eltc.deliverymonitor.domain.gitlab;

import java.time.Instant;

/**
 * Input contract for upserting a single merge request into {@code merge_requests}.
 *
 * @param repositoryId      internal {@code repositories.id} (FK), part of matching key
 * @param gitlabIid         project-local GitLab MR iid, part of matching key
 * @param gitlabId          global GitLab MR id, or {@code null}
 * @param issueKey          nullable Jira key (orphan allowed; extraction is Phase 3.5)
 * @param title             MR title, or {@code null}
 * @param state             GitLab state, or {@code null}
 * @param sourceBranch      source branch name, or {@code null}
 * @param targetBranch      target branch name, or {@code null}
 * @param authorUsername    author username, or {@code null}
 * @param authorDisplayName author display name, or {@code null}
 * @param gitlabCreatedAt   created time, or {@code null}
 * @param gitlabUpdatedAt   updated time, or {@code null}
 * @param mergedAt          merged time, or {@code null}
 * @param webUrl            GitLab web URL, or {@code null}
 */
public record MergeRequestUpsertCommand(
        Long repositoryId,
        Long gitlabIid,
        Long gitlabId,
        String issueKey,
        String title,
        String state,
        String sourceBranch,
        String targetBranch,
        String authorUsername,
        String authorDisplayName,
        Instant gitlabCreatedAt,
        Instant gitlabUpdatedAt,
        Instant mergedAt,
        String webUrl
) {
}
