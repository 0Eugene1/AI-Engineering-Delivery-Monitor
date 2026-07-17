package ru.eltc.deliverymonitor.api.issue;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP entry point for Issue Timeline (Phase 3.7, docs/api.md):
 * {@code GET /api/issues/{key}/timeline}.
 *
 * <p>Thin HTTP adapter over {@link TimelineQueryService}. Reads only PostgreSQL
 * {@code activity_events} — no Jira/GitLab live calls. Always returns {@code 200} with
 * {@code events: []} when nothing is linked to the key (does <b>not</b> require an
 * {@code IssueEntity}; does not change {@code GET /api/issues/{key}} 404 semantics).
 */
@RestController
@RequestMapping("/api/issues")
public class TimelineController {

    private final TimelineQueryService timelineQueryService;

    public TimelineController(TimelineQueryService timelineQueryService) {
        this.timelineQueryService = timelineQueryService;
    }

    @GetMapping("/{key}/timeline")
    public TimelineResponse getTimeline(@PathVariable String key) {
        return timelineQueryService.findTimeline(key);
    }
}
