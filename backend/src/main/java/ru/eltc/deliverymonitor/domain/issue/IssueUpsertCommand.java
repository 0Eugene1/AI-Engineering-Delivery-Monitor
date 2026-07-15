package ru.eltc.deliverymonitor.domain.issue;

import java.time.Instant;
import java.util.List;

/**
 * {@code domain.issue}'s own input contract for upserting a single issue — deliberately not the
 * sync layer's {@code JiraIssueSnapshot}. The caller ({@code sync.jira}) maps its snapshot into
 * this command before calling {@link IssuePersistencePort}; {@code domain.issue} never imports
 * anything from {@code sync.jira} (Dependency Rule — see docs/architecture.md).
 *
 * @param jiraId              Jira internal id, immutable — the upsert matching key
 * @param key                 Jira issue key, business anchor (ADR-001)
 * @param summary             issue summary
 * @param statusName          current status name
 * @param statusCategory      status category name
 * @param assigneeUsername    assignee username, or {@code null} if unassigned
 * @param assigneeDisplayName assignee display name, or {@code null} if unassigned
 * @param issueType           issue type name
 * @param fixVersions         fix version names (never {@code null})
 * @param labels              issue labels (never {@code null})
 * @param jiraCreated         Jira {@code created} timestamp, or {@code null} if unparsable
 * @param jiraUpdated         Jira {@code updated} timestamp, or {@code null} if unparsable
 */
public record IssueUpsertCommand(
        String jiraId,
        String key,
        String summary,
        String statusName,
        String statusCategory,
        String assigneeUsername,
        String assigneeDisplayName,
        String issueType,
        List<String> fixVersions,
        List<String> labels,
        Instant jiraCreated,
        Instant jiraUpdated
) {
}
