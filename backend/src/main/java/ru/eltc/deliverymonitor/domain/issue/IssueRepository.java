package ru.eltc.deliverymonitor.domain.issue;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/**
 * Spring Data JPA repository for {@link IssueEntity}. Lives directly in {@code domain.issue} —
 * a pragmatic simplification for the modular monolith (ADR-003), not a strict hexagonal
 * architecture with a separate adapter module.
 */
public interface IssueRepository extends JpaRepository<IssueEntity, Long> {

    /** Batch lookup used by page-level upsert to match existing rows by Jira's immutable internal id. */
    List<IssueEntity> findAllByJiraIdIn(Collection<String> jiraIds);
}
