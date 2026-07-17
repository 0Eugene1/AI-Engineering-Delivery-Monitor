package ru.eltc.deliverymonitor.sync.gitlab;

import java.time.Instant;

/**
 * Internal, normalized view of a GitLab commit produced by {@link GitLabSyncService}.
 *
 * <p>Sync-layer contract towards future {@code commits} persistence — decoupled from wire
 * {@code GitLabCommitDto}. {@code issueKey} is intentionally absent here: extraction lands in
 * Phase 3.5.
 *
 * @param projectId          GitLab project id
 * @param sha                full commit id
 * @param shortId            short commit id, or {@code null}
 * @param title              commit title, or {@code null}
 * @param message            full message, or {@code null}
 * @param authorName         author name, or {@code null}
 * @param authorEmail        author email, or {@code null}
 * @param committedAt        commit time, or {@code null} if unparsable
 * @param webUrl             GitLab web URL, or {@code null}
 * @param workstreamTypeCode configured type from {@code gitlab.sync.repositories}
 */
public record GitLabCommitSnapshot(
        long projectId,
        String sha,
        String shortId,
        String title,
        String message,
        String authorName,
        String authorEmail,
        Instant committedAt,
        String webUrl,
        String workstreamTypeCode
) {
}
