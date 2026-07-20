package ru.eltc.deliverymonitor.api.workstream;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP entry for {@code GET /api/workstreams/progress} (Phase 4.3 Dashboard Projects bars).
 */
@RestController
@RequestMapping("/api/workstreams/progress")
public class WorkstreamProgressController {

    private final WorkstreamProgressQueryService workstreamProgressQueryService;

    public WorkstreamProgressController(WorkstreamProgressQueryService workstreamProgressQueryService) {
        this.workstreamProgressQueryService = workstreamProgressQueryService;
    }

    @GetMapping
    public WorkstreamProgressResponse progress() {
        return workstreamProgressQueryService.progress();
    }
}
