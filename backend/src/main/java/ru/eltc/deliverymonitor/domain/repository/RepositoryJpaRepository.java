package ru.eltc.deliverymonitor.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA access for {@link RepositoryEntity}. Named {@code RepositoryJpaRepository}
 * (not {@code RepositoryRepository}) to avoid a confusing double-{@code Repository} type name
 * while staying in package {@code domain.repository}.
 */
public interface RepositoryJpaRepository extends JpaRepository<RepositoryEntity, Long> {

    /** Lookup by the immutable GitLab project id — the matching key for seed/sync. */
    Optional<RepositoryEntity> findByGitlabProjectId(Long gitlabProjectId);

    /** Batch lookup used by upsert to match existing rows by GitLab project id. */
    List<RepositoryEntity> findAllByGitlabProjectIdIn(Collection<Long> gitlabProjectIds);

    List<RepositoryEntity> findAllByOrderByIdAsc();
}
