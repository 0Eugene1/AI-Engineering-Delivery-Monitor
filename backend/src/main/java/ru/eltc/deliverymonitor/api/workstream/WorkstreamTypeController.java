package ru.eltc.deliverymonitor.api.workstream;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * HTTP entry point for {@code GET /api/workstream-types} (Phase 3.7). Thin adapter over
 * {@link WorkstreamTypeQueryService}; reads only the seeded {@code workstream_types} table.
 */
@RestController
@RequestMapping("/api/workstream-types")
public class WorkstreamTypeController {

    private final WorkstreamTypeQueryService workstreamTypeQueryService;

    public WorkstreamTypeController(WorkstreamTypeQueryService workstreamTypeQueryService) {
        this.workstreamTypeQueryService = workstreamTypeQueryService;
    }

    @GetMapping
    public List<WorkstreamTypeResponse> listActive() {
        return workstreamTypeQueryService.findActive();
    }
}
