package ru.eltc.deliverymonitor.api.activity;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * HTTP entry point for Activity Feed (Phase 4.1, docs/api.md): {@code GET /api/activity}.
 *
 * <p>Thin HTTP adapter over {@link ActivityQueryService}. Reads only PostgreSQL
 * {@code activity_events} — no Jira/GitLab live calls, no new table, no {@code domain.activity}.
 * Always returns {@code 200} with {@code events: []} when nothing matches.
 */
@RestController
@RequestMapping("/api/activity")
public class ActivityController {

    private final ActivityQueryService activityQueryService;

    public ActivityController(ActivityQueryService activityQueryService) {
        this.activityQueryService = activityQueryService;
    }

    @GetMapping
    public ActivityFeedResponse getActivity(
            @RequestParam(required = false) Instant since,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String workstreamType,
            @RequestParam(required = false, defaultValue = "true") boolean orphans) {
        return activityQueryService.findFeed(since, limit, workstreamType, orphans);
    }
}
