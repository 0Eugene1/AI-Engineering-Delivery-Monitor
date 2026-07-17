package ru.eltc.deliverymonitor.domain.timeline;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA access for {@link ActivityEventEntity}.
 */
public interface ActivityEventRepository extends JpaRepository<ActivityEventEntity, Long> {

    Optional<ActivityEventEntity> findBySourceAndSourceRef(String source, String sourceRef);

    /** Batch lookup for upsert matching by {@code (source, sourceRef)}. */
    List<ActivityEventEntity> findAllBySourceAndSourceRefIn(String source, Collection<String> sourceRefs);

    /**
     * Issue Timeline read (Phase 3.7): linked events for a Jira key, newest first
     * ({@code ORDER BY occurred_at DESC}). Empty list when none exist — never a missing-resource
     * signal at the repository layer.
     */
    List<ActivityEventEntity> findAllByIssueKeyOrderByOccurredAtDesc(String issueKey);
}
