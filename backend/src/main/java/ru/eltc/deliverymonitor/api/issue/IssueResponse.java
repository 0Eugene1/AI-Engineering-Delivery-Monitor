package ru.eltc.deliverymonitor.api.issue;

import ru.eltc.deliverymonitor.domain.issue.IssueEntity;

import java.time.Instant;
import java.util.List;

/**
 * External API contract for a single issue — deliberately not {@link IssueEntity} itself, so the
 * REST contract does not couple to the JPA entity (internal id, {@code jiraId}, LAZY collection
 * proxies) and can evolve independently of the persistence schema.
 *
 * @param issueKey             public Jira key (business anchor, ADR-001), e.g. {@code MPTPSUPP-1234}
 * @param summary              issue summary
 * @param status               current status name
 * @param statusCategory       status category name
 * @param assigneeUsername     assignee username, or {@code null} if unassigned
 * @param assigneeDisplayName  assignee display name, or {@code null} if unassigned
 * @param issueType            issue type name
 * @param jiraCreated          Jira {@code created} timestamp, or {@code null} if unavailable
 * @param jiraUpdated          Jira {@code updated} timestamp, or {@code null} if unavailable
 * @param fixVersions          fix version names (never {@code null})
 * @param labels               issue labels (never {@code null})
 */
public record IssueResponse(
        String issueKey,
        String summary,
        String status,
        String statusCategory,
        String assigneeUsername,
        String assigneeDisplayName,
        String issueType,
        Instant jiraCreated,
        Instant jiraUpdated,
        List<String> fixVersions,
        List<String> labels
) {

    /**
     * Maps a persisted {@link IssueEntity} to this DTO. Must be called while the entity's
     * persistence-context session is still open ({@code fixVersions}/{@code labels} are LAZY
     * {@code @ElementCollection}s) — see {@link IssueQueryService}.
     */
    public static IssueResponse from(IssueEntity entity) {
        return new IssueResponse(
                entity.getKey(),
                entity.getSummary(),
                entity.getStatusName(),
                entity.getStatusCategory(),
                entity.getAssigneeUsername(),
                entity.getAssigneeDisplayName(),
                entity.getIssueType(),
                entity.getJiraCreated(),
                entity.getJiraUpdated(),
                List.copyOf(entity.getFixVersions()),
                List.copyOf(entity.getLabels()));
    }
}
