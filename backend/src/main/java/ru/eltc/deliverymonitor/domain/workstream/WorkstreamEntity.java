package ru.eltc.deliverymonitor.domain.workstream;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * JPA entity backing {@code workstreams} (docs/database.md, ADR-002, Phase 3.6).
 *
 * <p>Upsert matching key is {@code (issueKey, workstreamTypeCode)}. {@code repositoryId} is
 * nullable Git provenance — not part of identity.
 */
@Entity
@Table(name = "workstreams", uniqueConstraints = {
        @UniqueConstraint(
                name = "uk_workstreams_issue_key_workstream_type_code",
                columnNames = {"issue_key", "workstream_type_code"})
})
public class WorkstreamEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "issue_key", nullable = false, updatable = false)
    private String issueKey;

    @Column(name = "issue_id")
    private Long issueId;

    @Column(name = "repository_id")
    private Long repositoryId;

    @Column(name = "workstream_type_code", nullable = false, updatable = false)
    private String workstreamTypeCode;

    @Column(name = "derived_status", nullable = false)
    private String derivedStatus;

    /** JPA. */
    protected WorkstreamEntity() {
    }

    public WorkstreamEntity(String issueKey, String workstreamTypeCode) {
        this.issueKey = issueKey;
        this.workstreamTypeCode = workstreamTypeCode;
    }

    /**
     * Applies an upsert command. Identity fields are never changed after construction.
     * {@code derivedStatus} is merged with {@link WorkstreamDerivedStatuses#max} (never downgrades).
     * {@code repositoryId}/{@code issueId} are set when the command provides a non-null value.
     */
    public void applyUpsert(WorkstreamUpsertCommand command) {
        if (command.repositoryId() != null) {
            this.repositoryId = command.repositoryId();
        }
        if (command.issueId() != null) {
            this.issueId = command.issueId();
        }
        this.derivedStatus = WorkstreamDerivedStatuses.max(this.derivedStatus, command.derivedStatus());
    }

    public Long getId() {
        return id;
    }

    public String getIssueKey() {
        return issueKey;
    }

    public Long getIssueId() {
        return issueId;
    }

    public Long getRepositoryId() {
        return repositoryId;
    }

    public String getWorkstreamTypeCode() {
        return workstreamTypeCode;
    }

    public String getDerivedStatus() {
        return derivedStatus;
    }
}
