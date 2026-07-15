package ru.eltc.deliverymonitor.sync.jira;

import java.time.Instant;
import java.util.List;

/**
 * Internal, normalized view of a Jira issue produced by {@link JiraSyncService}.
 *
 * <p>This is the sync layer's own contract towards persistence — intentionally decoupled from the
 * external {@code JiraIssueDto} (the raw Jira REST API v2 shape). Upper layers depend on <em>this</em>
 * stable shape, so a change in the Jira API cannot break the schema. {@code sync.jira} maps this
 * into {@code domain.issue}'s own {@code IssueUpsertCommand} before calling {@code
 * IssuePersistencePort} — {@code domain.issue} never sees this type.
 *
 * <p>{@code fixVersions} and {@code labels} are never {@code null} (empty list instead).
 * {@code created}/{@code updated} are {@code null} if the Jira timestamp could not be parsed
 * (logged as a warning, sync does not fail because of it).
 *
 * @param jiraId              Jira internal id (immutable), the future upsert matching key
 * @param key                 Jira issue key, the integration anchor (ADR-001), e.g. {@code MPTPSUPP-1234}
 * @param summary             issue summary
 * @param statusName          current status name, e.g. {@code In Review}
 * @param statusCategory      status category name, e.g. {@code In Progress}
 * @param assigneeUsername    assignee username, or {@code null} if unassigned
 * @param assigneeDisplayName assignee display name, or {@code null} if unassigned
 * @param issueType           issue type name, e.g. {@code Bug}
 * @param fixVersions         fix version names (never {@code null})
 * @param labels              issue labels (never {@code null})
 * @param created             Jira {@code created} timestamp, or {@code null} if unparsable
 * @param updated             Jira {@code updated} timestamp, or {@code null} if unparsable
 */
public record JiraIssueSnapshot(
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
        Instant created,
        Instant updated
) {
}
