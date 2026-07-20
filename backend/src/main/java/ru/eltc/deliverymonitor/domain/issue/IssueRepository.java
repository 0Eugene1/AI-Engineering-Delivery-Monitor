package ru.eltc.deliverymonitor.domain.issue;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link IssueEntity}. Lives directly in {@code domain.issue} —
 * a pragmatic simplification for the modular monolith (ADR-003), not a strict hexagonal
 * architecture with a separate adapter module.
 */
public interface IssueRepository extends JpaRepository<IssueEntity, Long> {

    /** Batch lookup used by page-level upsert to match existing rows by Jira's immutable internal id. */
    List<IssueEntity> findAllByJiraIdIn(Collection<String> jiraIds);

    /**
     * Looks up a single issue by its public Jira {@code key} (business anchor, ADR-001; physical
     * column {@code issue_key} — see {@link IssueEntity#getKey()}), for the read API
     * ({@code api.issue}). Not to be confused with {@link IssueEntity#getJiraId()} (internal Jira
     * id, upsert matching key) or the database-generated {@link IssueEntity#getId()}.
     */
    Optional<IssueEntity> findByKey(String key);

    /**
     * Issues in a given Jira status category <em>name</em> (Phase 4.2 {@code JIRA_ACTIVE_NO_GIT}).
     * Sync persists category {@code name} (e.g. {@code In Progress}), not the Jira key
     * {@code indeterminate}.
     */
    List<IssueEntity> findAllByStatusCategory(String statusCategory);
}
