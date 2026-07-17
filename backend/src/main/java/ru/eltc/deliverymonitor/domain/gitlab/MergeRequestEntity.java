package ru.eltc.deliverymonitor.domain.gitlab;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * JPA entity backing the {@code merge_requests} table (docs/database.md, Phase 3.4).
 *
 * <p>Upsert matching key is {@code (repositoryId, gitlabIid)} — project-local GitLab MR iid.
 * Physical column {@code gitlab_iid} (not bare {@code iid}) for SQL clarity. {@code issueKey}
 * is nullable (orphan / Phase 3.5 linking).
 */
@Entity
@Table(name = "merge_requests", uniqueConstraints = {
        @UniqueConstraint(
                name = "uk_merge_requests_repository_id_gitlab_iid",
                columnNames = {"repository_id", "gitlab_iid"})
})
public class MergeRequestEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "repository_id", nullable = false, updatable = false)
    private Long repositoryId;

    /** Project-local GitLab MR iid — part of the matching key. */
    @Column(name = "gitlab_iid", nullable = false, updatable = false)
    private Long gitlabIid;

    /** Global GitLab MR id (mutable metadata; not the matching key). */
    @Column(name = "gitlab_id")
    private Long gitlabId;

    @Column(name = "issue_key")
    private String issueKey;

    @Column(name = "title")
    private String title;

    @Column(name = "state")
    private String state;

    @Column(name = "source_branch")
    private String sourceBranch;

    @Column(name = "target_branch")
    private String targetBranch;

    @Column(name = "author_username")
    private String authorUsername;

    @Column(name = "author_display_name")
    private String authorDisplayName;

    @Column(name = "gitlab_created_at")
    private Instant gitlabCreatedAt;

    @Column(name = "gitlab_updated_at")
    private Instant gitlabUpdatedAt;

    @Column(name = "merged_at")
    private Instant mergedAt;

    @Column(name = "web_url")
    private String webUrl;

    @Column(name = "synced_at", nullable = false)
    private Instant syncedAt;

    /** JPA. */
    protected MergeRequestEntity() {
    }

    public MergeRequestEntity(Long repositoryId, Long gitlabIid) {
        this.repositoryId = repositoryId;
        this.gitlabIid = gitlabIid;
    }

    public void applyUpsert(MergeRequestUpsertCommand command, Instant syncedAt) {
        this.gitlabId = command.gitlabId();
        this.issueKey = command.issueKey();
        this.title = command.title();
        this.state = command.state();
        this.sourceBranch = command.sourceBranch();
        this.targetBranch = command.targetBranch();
        this.authorUsername = command.authorUsername();
        this.authorDisplayName = command.authorDisplayName();
        this.gitlabCreatedAt = command.gitlabCreatedAt();
        this.gitlabUpdatedAt = command.gitlabUpdatedAt();
        this.mergedAt = command.mergedAt();
        this.webUrl = command.webUrl();
        this.syncedAt = syncedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getRepositoryId() {
        return repositoryId;
    }

    public Long getGitlabIid() {
        return gitlabIid;
    }

    public Long getGitlabId() {
        return gitlabId;
    }

    public String getIssueKey() {
        return issueKey;
    }

    public String getTitle() {
        return title;
    }

    public String getState() {
        return state;
    }

    public String getSourceBranch() {
        return sourceBranch;
    }

    public String getTargetBranch() {
        return targetBranch;
    }

    public String getAuthorUsername() {
        return authorUsername;
    }

    public String getAuthorDisplayName() {
        return authorDisplayName;
    }

    public Instant getGitlabCreatedAt() {
        return gitlabCreatedAt;
    }

    public Instant getGitlabUpdatedAt() {
        return gitlabUpdatedAt;
    }

    public Instant getMergedAt() {
        return mergedAt;
    }

    public String getWebUrl() {
        return webUrl;
    }

    public Instant getSyncedAt() {
        return syncedAt;
    }
}
