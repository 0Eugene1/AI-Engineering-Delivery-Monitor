package ru.eltc.deliverymonitor.domain.gitlab;

import java.time.Instant;

/**
 * Input contract for upserting a single branch into {@code branches}. Owned by
 * {@code domain.gitlab}; callers (future {@code sync.gitlab}) map their snapshots into this
 * command — this package never imports sync/integration types.
 *
 * @param repositoryId internal {@code repositories.id} (FK), part of matching key
 * @param name         branch name, part of matching key
 * @param issueKey     nullable Jira key (orphan allowed; extraction is Phase 3.5)
 * @param tipCommitSha tip commit sha, or {@code null}
 * @param lastCommitAt tip commit time, or {@code null}
 * @param authorName   tip author name, or {@code null}
 * @param authorEmail  tip author email, or {@code null}
 * @param webUrl       GitLab web URL, or {@code null}
 */
public record BranchUpsertCommand(
        Long repositoryId,
        String name,
        String issueKey,
        String tipCommitSha,
        Instant lastCommitAt,
        String authorName,
        String authorEmail,
        String webUrl
) {
}
