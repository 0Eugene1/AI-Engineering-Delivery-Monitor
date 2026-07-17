package ru.eltc.deliverymonitor.domain.repository;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * JPA entity backing the {@code repositories} table (docs/database.md, Phase 3.3).
 *
 * <p>{@code gitlabProjectId} (GitLab's immutable numeric project id) is the upsert / lookup
 * matching key, <b>not</b> {@code path} or {@code name} — those may change on rename/move in
 * GitLab. {@code workstreamTypeCode} maps the project to a Workstream Type (ADR-002); it is a
 * FK to {@code workstream_types.code} at the database level.
 */
@Entity
@Table(name = "repositories", uniqueConstraints = {
        @UniqueConstraint(name = "uk_repositories_gitlab_project_id", columnNames = "gitlab_project_id")
})
public class RepositoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** GitLab project id — immutable external identifier, the matching key. */
    @Column(name = "gitlab_project_id", nullable = false, updatable = false)
    private Long gitlabProjectId;

    /** {@code path_with_namespace} — mutable; refreshed when GitLab renames/moves the project. */
    @Column(name = "path", nullable = false)
    private String path;

    /** Display name — mutable. */
    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "workstream_type_code", nullable = false)
    private String workstreamTypeCode;

    /** JPA. */
    protected RepositoryEntity() {
    }

    public RepositoryEntity(Long gitlabProjectId) {
        this.gitlabProjectId = gitlabProjectId;
    }

    /**
     * Applies an upsert command to this entity (new or existing). {@code gitlabProjectId} is
     * never changed after construction.
     */
    public void applyUpsert(RepositoryUpsertCommand command) {
        this.path = command.path();
        this.name = command.name();
        this.workstreamTypeCode = command.workstreamTypeCode();
    }

    public Long getId() {
        return id;
    }

    public Long getGitlabProjectId() {
        return gitlabProjectId;
    }

    public String getPath() {
        return path;
    }

    public String getName() {
        return name;
    }

    public String getWorkstreamTypeCode() {
        return workstreamTypeCode;
    }
}
