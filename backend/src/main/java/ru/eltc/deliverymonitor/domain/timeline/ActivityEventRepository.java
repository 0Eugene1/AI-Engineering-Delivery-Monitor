package ru.eltc.deliverymonitor.domain.timeline;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
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

    /**
     * Activity Feed read (Phase 4.1): global events with optional filters. Sort/limit via
     * {@link Pageable} (callers use {@code occurredAt DESC}).
     *
     * <p>Optional filters use Spring Data SpEL ({@code :#{#param == null}}) instead of
     * {@code :param IS NULL} — PostgreSQL cannot infer JDBC types for null bind parameters
     * in {@code ? IS NULL} (SQLState {@code 42P18}).
     *
     * @param since          inclusive lower bound on {@code occurredAt}, or {@code null}
     * @param workstreamType exact {@code workstreamTypeCode}, or {@code null} for any
     * @param includeOrphans {@code true} include rows with null {@code issueKey}; {@code false}
     *                       only linked rows
     */
    @Query("""
            SELECT e FROM ActivityEventEntity e
            WHERE (:#{#since == null} = true OR e.occurredAt >= :since)
              AND (:#{#workstreamType == null} = true OR e.workstreamTypeCode = :workstreamType)
              AND (:includeOrphans = TRUE OR e.issueKey IS NOT NULL)
            """)
    List<ActivityEventEntity> findFeed(
            @Param("since") Instant since,
            @Param("workstreamType") String workstreamType,
            @Param("includeOrphans") boolean includeOrphans,
            Pageable pageable);

    /**
     * Latest {@code occurred_at} per {@code (issue_key, workstream_type_code)} for linked events
     * (Phase 4.2 {@code STALE_ACTIVITY}). Orphans ({@code issue_key} null) are excluded.
     */
    @Query("""
            SELECT e.issueKey, e.workstreamTypeCode, MAX(e.occurredAt)
            FROM ActivityEventEntity e
            WHERE e.issueKey IS NOT NULL
            GROUP BY e.issueKey, e.workstreamTypeCode
            """)
    List<Object[]> findLastOccurredAtGroupedByIssueKeyAndWorkstreamType();

    /**
     * Issue keys among {@code keys} that have at least one linked activity event
     * (Phase 4.2 {@code JIRA_ACTIVE_NO_GIT} anti-join).
     */
    @Query("""
            SELECT DISTINCT e.issueKey FROM ActivityEventEntity e
            WHERE e.issueKey IN :keys
            """)
    List<String> findDistinctIssueKeysIn(@Param("keys") Collection<String> keys);
}
