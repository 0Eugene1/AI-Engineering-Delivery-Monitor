package ru.eltc.deliverymonitor.domain.workstream;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA access for {@link WorkstreamEntity}.
 */
public interface WorkstreamRepository extends JpaRepository<WorkstreamEntity, Long> {

    Optional<WorkstreamEntity> findByIssueKeyAndWorkstreamTypeCode(
            String issueKey, String workstreamTypeCode);

    /** Batch lookup for upsert matching by {@code (issueKey, workstreamTypeCode)}. */
    List<WorkstreamEntity> findAllByIssueKeyInAndWorkstreamTypeCodeIn(
            Collection<String> issueKeys, Collection<String> workstreamTypeCodes);
}
