package ru.eltc.deliverymonitor.api.workstream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.eltc.deliverymonitor.domain.workstream_type.WorkstreamTypeRepository;

import java.util.List;

/**
 * Read-only query layer for {@code GET /api/workstream-types}: active types from PostgreSQL
 * via {@link WorkstreamTypeRepository#findAllByActiveTrueOrderBySortOrderAsc()} — no duplicated
 * seed/filter logic.
 */
@Service
@Transactional(readOnly = true)
public class WorkstreamTypeQueryService {

    private final WorkstreamTypeRepository workstreamTypeRepository;

    public WorkstreamTypeQueryService(WorkstreamTypeRepository workstreamTypeRepository) {
        this.workstreamTypeRepository = workstreamTypeRepository;
    }

    /** Active Workstream Types in display order (Board badges, Timeline pills). */
    public List<WorkstreamTypeResponse> findActive() {
        return workstreamTypeRepository.findAllByActiveTrueOrderBySortOrderAsc().stream()
                .map(WorkstreamTypeResponse::from)
                .toList();
    }
}
