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
 * JPA entity backing the {@code branches} table (docs/database.md, Phase 3.4).
 *
 * <p>Upsert matching key is {@code (repositoryId, name)} — not tip commit sha. {@code issueKey}
 * is nullable (orphan GitLab objects; linking in Phase 3.5).
 */
@Entity
@Table(name = "branches", uniqueConstraints = {
        @UniqueConstraint(name = "uk_branches_repository_id_name", columnNames = {"repository_id", "name"})
})
public class BranchEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Internal FK to {@code repositories.id} — not the GitLab project id. */
    @Column(name = "repository_id", nullable = false, updatable = false)
    private Long repositoryId;

    @Column(name = "name", nullable = false, updatable = false)
    private String name;

    @Column(name = "issue_key")
    private String issueKey;

    @Column(name = "tip_commit_sha")
    private String tipCommitSha;

    @Column(name = "last_commit_at")
    private Instant lastCommitAt;

    @Column(name = "author_name")
    private String authorName;

    @Column(name = "author_email")
    private String authorEmail;

    @Column(name = "web_url")
    private String webUrl;

    @Column(name = "synced_at", nullable = false)
    private Instant syncedAt;

    /** JPA. */
    protected BranchEntity() {
    }

    public BranchEntity(Long repositoryId, String name) {
        this.repositoryId = repositoryId;
        this.name = name;
    }

    public void applyUpsert(BranchUpsertCommand command, Instant syncedAt) {
        this.issueKey = command.issueKey();
        this.tipCommitSha = command.tipCommitSha();
        this.lastCommitAt = command.lastCommitAt();
        this.authorName = command.authorName();
        this.authorEmail = command.authorEmail();
        this.webUrl = command.webUrl();
        this.syncedAt = syncedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getRepositoryId() {
        return repositoryId;
    }

    public String getName() {
        return name;
    }

    public String getIssueKey() {
        return issueKey;
    }

    public String getTipCommitSha() {
        return tipCommitSha;
    }

    public Instant getLastCommitAt() {
        return lastCommitAt;
    }

    public String getAuthorName() {
        return authorName;
    }

    public String getAuthorEmail() {
        return authorEmail;
    }

    public String getWebUrl() {
        return webUrl;
    }

    public Instant getSyncedAt() {
        return syncedAt;
    }
}
