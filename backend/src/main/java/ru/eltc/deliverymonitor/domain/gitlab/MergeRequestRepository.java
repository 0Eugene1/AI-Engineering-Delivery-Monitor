package ru.eltc.deliverymonitor.domain.gitlab;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA access for {@link MergeRequestEntity}.
 */
public interface MergeRequestRepository extends JpaRepository<MergeRequestEntity, Long> {

    Optional<MergeRequestEntity> findByRepositoryIdAndGitlabIid(Long repositoryId, Long gitlabIid);

    /** Batch lookup for upsert matching by {@code (repositoryId, gitlabIid)}. */
    List<MergeRequestEntity> findAllByRepositoryIdAndGitlabIidIn(
            Long repositoryId, Collection<Long> gitlabIids);

    /**
     * Linked MRs for a set of issue keys (Phase 4.2 {@code OPEN_MR_STALE} / {@code NO_MR}).
     * Orphan MRs ({@code issue_key} null) are never returned.
     */
    List<MergeRequestEntity> findAllByIssueKeyIn(Collection<String> issueKeys);
}
