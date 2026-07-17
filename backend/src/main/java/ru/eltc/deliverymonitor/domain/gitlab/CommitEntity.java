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
 * JPA entity backing the {@code commits} table (docs/database.md, Phase 3.4).
 *
 * <p>Upsert matching key is {@code (repositoryId, sha)}. {@code branchId} is optional —
 * commit list sync is often not branch-scoped. {@code issueKey} is nullable (orphan /
 * Phase 3.5 linking).
 */
@Entity
@Table(name = "commits", uniqueConstraints = {
        @UniqueConstraint(name = "uk_commits_repository_id_sha", columnNames = {"repository_id", "sha"})
})
public class CommitEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "repository_id", nullable = false, updatable = false)
    private Long repositoryId;

    @Column(name = "sha", nullable = false, updatable = false)
    private String sha;

    @Column(name = "branch_id")
    private Long branchId;

    @Column(name = "issue_key")
    private String issueKey;

    @Column(name = "short_id")
    private String shortId;

    @Column(name = "title")
    private String title;

    @Column(name = "message")
    private String message;

    @Column(name = "author_name")
    private String authorName;

    @Column(name = "author_email")
    private String authorEmail;

    @Column(name = "committed_at")
    private Instant committedAt;

    @Column(name = "web_url")
    private String webUrl;

    @Column(name = "synced_at", nullable = false)
    private Instant syncedAt;

    /** JPA. */
    protected CommitEntity() {
    }

    public CommitEntity(Long repositoryId, String sha) {
        this.repositoryId = repositoryId;
        this.sha = sha;
    }

    public void applyUpsert(CommitUpsertCommand command, Instant syncedAt) {
        this.branchId = command.branchId();
        this.issueKey = command.issueKey();
        this.shortId = command.shortId();
        this.title = command.title();
        this.message = command.message();
        this.authorName = command.authorName();
        this.authorEmail = command.authorEmail();
        this.committedAt = command.committedAt();
        this.webUrl = command.webUrl();
        this.syncedAt = syncedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getRepositoryId() {
        return repositoryId;
    }

    public String getSha() {
        return sha;
    }

    public Long getBranchId() {
        return branchId;
    }

    public String getIssueKey() {
        return issueKey;
    }

    public String getShortId() {
        return shortId;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public String getAuthorName() {
        return authorName;
    }

    public String getAuthorEmail() {
        return authorEmail;
    }

    public Instant getCommittedAt() {
        return committedAt;
    }

    public String getWebUrl() {
        return webUrl;
    }

    public Instant getSyncedAt() {
        return syncedAt;
    }
}
