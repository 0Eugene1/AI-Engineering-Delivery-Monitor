package ru.eltc.deliverymonitor.domain.repository;

import java.util.List;
import java.util.Optional;

/**
 * {@code domain.repository}'s own persistence contract. Callers (future {@code sync.gitlab},
 * read API) depend on this interface; {@code domain.repository} does not import upper layers
 * (docs/architecture.md package dependency direction).
 *
 * <p>Upsert matches existing rows by {@link RepositoryUpsertCommand#gitlabProjectId()}, never by
 * {@code path}/{@code name}.
 */
public interface RepositoryPersistencePort {

    /** Upserts repositories, matching existing rows by GitLab project id. */
    RepositoryUpsertOutcome upsertAll(List<RepositoryUpsertCommand> commands);

    Optional<RepositoryEntity> findByGitlabProjectId(long gitlabProjectId);

    List<RepositoryEntity> findAllOrdered();
}
