package ru.eltc.deliverymonitor.domain.issue;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * JPA entity backing the {@code issues} table (docs/database.md, Phase 2.3 Persistence).
 *
 * <p>{@code jiraId} (Jira's immutable internal id) is the upsert matching key, <b>not</b>
 * {@code key} — an issue's {@code key} can change if it is moved between Jira projects, while
 * {@code jiraId} never does. {@code key} remains the business anchor (ADR-001) and a unique
 * index, used for future joins with GitLab/Jenkins, but is not used to look up an existing row.
 *
 * <p>{@code fixVersions}/{@code labels} are stored via {@code @ElementCollection} (own value
 * tables {@code issue_fix_versions}/{@code issue_labels}, unique on {@code (issue_id, value)}) so
 * multiplicity is not lost.
 */
@Entity
@Table(name = "issues", uniqueConstraints = {
        @UniqueConstraint(name = "uk_issues_jira_id", columnNames = "jira_id"),
        @UniqueConstraint(name = "uk_issues_issue_key", columnNames = "issue_key")
})
public class IssueEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Jira internal id, e.g. {@code "10001"} — immutable, the upsert matching key. */
    @Column(name = "jira_id", nullable = false, updatable = false)
    private String jiraId;

    /**
     * Jira issue key, e.g. {@code MPTPSUPP-1234} — business anchor (ADR-001), not the matching key.
     *
     * <p>Physical column is {@code issue_key}, not {@code key}: {@code KEY} is a reserved SQL
     * keyword that some databases (e.g. H2, the test database) accept unquoted in {@code CREATE
     * TABLE} but reject unquoted in ordinary {@code SELECT} expressions. The Java-level contract
     * ({@link #getKey()}) is unaffected — only the physical column name differs from the original
     * docs/database.md sketch (see docs/session_log.md for this implementation-detail deviation).
     */
    @Column(name = "issue_key", nullable = false)
    private String key;

    @Column(name = "summary")
    private String summary;

    @Column(name = "status_name")
    private String statusName;

    @Column(name = "status_category")
    private String statusCategory;

    @Column(name = "assignee_username")
    private String assigneeUsername;

    @Column(name = "assignee_display_name")
    private String assigneeDisplayName;

    @Column(name = "issue_type")
    private String issueType;

    @Column(name = "jira_created")
    private Instant jiraCreated;

    @Column(name = "jira_updated")
    private Instant jiraUpdated;

    /** Timestamp of the last successful upsert of this row. */
    @Column(name = "synced_at", nullable = false)
    private Instant syncedAt;

    @ElementCollection
    @CollectionTable(
            name = "issue_fix_versions",
            joinColumns = @JoinColumn(name = "issue_id"),
            uniqueConstraints = @UniqueConstraint(columnNames = {"issue_id", "fix_version_name"}))
    @Column(name = "fix_version_name", nullable = false)
    private Set<String> fixVersions = new HashSet<>();

    @ElementCollection
    @CollectionTable(
            name = "issue_labels",
            joinColumns = @JoinColumn(name = "issue_id"),
            uniqueConstraints = @UniqueConstraint(columnNames = {"issue_id", "label"}))
    @Column(name = "label", nullable = false)
    private Set<String> labels = new HashSet<>();

    /** JPA. */
    protected IssueEntity() {
    }

    public IssueEntity(String jiraId) {
        this.jiraId = jiraId;
    }

    /**
     * Applies an upsert command to this entity (new or existing) and stamps {@code syncedAt}.
     * Collections are cleared and repopulated in place rather than replaced, so Hibernate updates
     * the {@code issue_fix_versions}/{@code issue_labels} tables correctly for a managed entity.
     */
    public void applyUpsert(IssueUpsertCommand command, Instant syncedAt) {
        this.key = command.key();
        this.summary = command.summary();
        this.statusName = command.statusName();
        this.statusCategory = command.statusCategory();
        this.assigneeUsername = command.assigneeUsername();
        this.assigneeDisplayName = command.assigneeDisplayName();
        this.issueType = command.issueType();
        this.jiraCreated = command.jiraCreated();
        this.jiraUpdated = command.jiraUpdated();
        this.syncedAt = syncedAt;

        this.fixVersions.clear();
        this.fixVersions.addAll(command.fixVersions() != null ? command.fixVersions() : List.of());

        this.labels.clear();
        this.labels.addAll(command.labels() != null ? command.labels() : List.of());
    }

    public Long getId() {
        return id;
    }

    public String getJiraId() {
        return jiraId;
    }

    public String getKey() {
        return key;
    }

    public String getSummary() {
        return summary;
    }

    public String getStatusName() {
        return statusName;
    }

    public String getStatusCategory() {
        return statusCategory;
    }

    public String getAssigneeUsername() {
        return assigneeUsername;
    }

    public String getAssigneeDisplayName() {
        return assigneeDisplayName;
    }

    public String getIssueType() {
        return issueType;
    }

    public Instant getJiraCreated() {
        return jiraCreated;
    }

    public Instant getJiraUpdated() {
        return jiraUpdated;
    }

    public Instant getSyncedAt() {
        return syncedAt;
    }

    public Set<String> getFixVersions() {
        return fixVersions;
    }

    public Set<String> getLabels() {
        return labels;
    }
}
