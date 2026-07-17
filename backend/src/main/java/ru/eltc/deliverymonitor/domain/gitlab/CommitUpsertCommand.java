package ru.eltc.deliverymonitor.domain.gitlab;

import java.time.Instant;

/**
 * Input contract for upserting a single commit into {@code commits}.
 *
 * @param repositoryId internal {@code repositories.id} (FK), part of matching key
 * @param sha          full commit sha, part of matching key
 * @param branchId     optional FK to {@code branches.id}, or {@code null}
 * @param issueKey     nullable Jira key (orphan allowed; extraction is Phase 3.5)
 * @param shortId      short commit id, or {@code null}
 * @param title        commit title, or {@code null}
 * @param message      full message, or {@code null}
 * @param authorName   author name, or {@code null}
 * @param authorEmail  author email, or {@code null}
 * @param committedAt  commit time, or {@code null}
 * @param webUrl       GitLab web URL, or {@code null}
 */
public record CommitUpsertCommand(
        Long repositoryId,
        String sha,
        Long branchId,
        String issueKey,
        String shortId,
        String title,
        String message,
        String authorName,
        String authorEmail,
        Instant committedAt,
        String webUrl
) {
}
