package ru.eltc.deliverymonitor.domain.gitlab;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA access for {@link BranchEntity}.
 */
public interface BranchRepository extends JpaRepository<BranchEntity, Long> {

    Optional<BranchEntity> findByRepositoryIdAndName(Long repositoryId, String name);

    /** Batch lookup for upsert matching by {@code (repositoryId, name)}. */
    List<BranchEntity> findAllByRepositoryIdAndNameIn(Long repositoryId, Collection<String> names);
}
