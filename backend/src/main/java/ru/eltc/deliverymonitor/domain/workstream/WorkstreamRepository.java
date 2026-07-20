package ru.eltc.deliverymonitor.domain.workstream;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /**
     * Issue keys among {@code keys} that already have at least one workstream
     * (Phase 4.2 {@code JIRA_ACTIVE_NO_GIT} anti-join).
     */
    @Query("""
            SELECT DISTINCT w.issueKey FROM WorkstreamEntity w
            WHERE w.issueKey IN :keys
            """)
    List<String> findDistinctIssueKeysIn(@Param("keys") Collection<String> keys);

    /**
     * Counts per type for Dashboard progress bars (Phase 4.3):
     * {@code [0]=workstreamTypeCode}, {@code [1]=total}, {@code [2]=mergedCount}.
     */
    @Query("""
            SELECT w.workstreamTypeCode,
                   COUNT(w),
                   SUM(CASE WHEN w.derivedStatus = 'merged' THEN 1L ELSE 0L END)
            FROM WorkstreamEntity w
            GROUP BY w.workstreamTypeCode
            """)
    List<Object[]> countTotalsAndMergedByType();
}
