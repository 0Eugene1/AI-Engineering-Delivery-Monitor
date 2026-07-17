package ru.eltc.deliverymonitor.domain.gitlab;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA access for {@link CommitEntity}.
 */
public interface CommitRepository extends JpaRepository<CommitEntity, Long> {

    Optional<CommitEntity> findByRepositoryIdAndSha(Long repositoryId, String sha);

    /** Batch lookup for upsert matching by {@code (repositoryId, sha)}. */
    List<CommitEntity> findAllByRepositoryIdAndShaIn(Long repositoryId, Collection<String> shas);
}
