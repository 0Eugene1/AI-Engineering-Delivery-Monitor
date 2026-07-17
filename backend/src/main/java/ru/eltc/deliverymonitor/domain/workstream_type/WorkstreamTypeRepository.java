package ru.eltc.deliverymonitor.domain.workstream_type;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link WorkstreamTypeEntity}. Lives directly in
 * {@code domain.workstream_type} — same pragmatic modular-monolith choice as
 * {@code domain.issue.IssueRepository} (ADR-003).
 */
public interface WorkstreamTypeRepository extends JpaRepository<WorkstreamTypeEntity, String> {

    Optional<WorkstreamTypeEntity> findByCode(String code);

    /** Active types in Board / Release Health display order. */
    List<WorkstreamTypeEntity> findAllByActiveTrueOrderBySortOrderAsc();
}
