package ru.eltc.deliverymonitor.api.activity;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.eltc.deliverymonitor.api.ActivityEventMapper;
import ru.eltc.deliverymonitor.domain.timeline.ActivityEventEntity;
import ru.eltc.deliverymonitor.domain.timeline.ActivityEventRepository;
import ru.eltc.deliverymonitor.domain.workstream_type.WorkstreamTypeEntity;
import ru.eltc.deliverymonitor.domain.workstream_type.WorkstreamTypeRepository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Read-only query layer for Activity Feed (Phase 4.1): loads {@code activity_events} from
 * PostgreSQL only and maps them via {@link ActivityEventMapper} (shared with Timeline).
 * No Jira/GitLab live calls; no {@code domain.activity} package.
 *
 * <p>Dependency direction: {@code PostgreSQL -> domain.timeline (+ domain.workstream_type) ->
 * api.activity}.
 */
@Service
@Transactional(readOnly = true)
@EnableConfigurationProperties(ActivityFeedProperties.class)
public class ActivityQueryService {

    private final ActivityEventRepository activityEventRepository;
    private final WorkstreamTypeRepository workstreamTypeRepository;
    private final ActivityEventMapper activityEventMapper;
    private final ActivityFeedProperties properties;

    public ActivityQueryService(
            ActivityEventRepository activityEventRepository,
            WorkstreamTypeRepository workstreamTypeRepository,
            ActivityEventMapper activityEventMapper,
            ActivityFeedProperties properties) {
        this.activityEventRepository = activityEventRepository;
        this.workstreamTypeRepository = workstreamTypeRepository;
        this.activityEventMapper = activityEventMapper;
        this.properties = properties;
    }

    /**
     * Returns the team activity feed. Always a present response with possibly empty
     * {@code events} — never signals "not found".
     *
     * @param since          optional lower bound on {@code occurred_at} (inclusive); {@code null} = none
     * @param limit          optional max rows; {@code null} → {@link ActivityFeedProperties#getDefaultLimit()};
     *                       clamped to {@link ActivityFeedProperties#getMaxLimit()}
     * @param workstreamType optional filter on {@code workstream_type_code}
     * @param orphans        {@code true} (default) include events with null {@code issue_key};
     *                       {@code false} only linked events
     */
    public ActivityFeedResponse findFeed(
            Instant since, Integer limit, String workstreamType, boolean orphans) {
        int pageSize = resolveLimit(limit);
        List<ActivityEventEntity> rows = activityEventRepository.findFeed(
                since,
                blankToNull(workstreamType),
                orphans,
                PageRequest.of(0, pageSize, Sort.by(Sort.Direction.DESC, "occurredAt")));
        Map<String, String> displayNames = workstreamTypeRepository.findAll().stream()
                .collect(Collectors.toMap(
                        WorkstreamTypeEntity::getCode,
                        WorkstreamTypeEntity::getDisplayName,
                        (a, b) -> a));
        List<ActivityFeedResponse.ActivityEvent> events = rows.stream()
                .map(entity -> activityEventMapper.toFeedEvent(entity, displayNames))
                .toList();
        return new ActivityFeedResponse(events);
    }

    private int resolveLimit(Integer limit) {
        int requested = limit == null ? properties.getDefaultLimit() : limit;
        if (requested < 1) {
            requested = properties.getDefaultLimit();
        }
        return Math.min(requested, properties.getMaxLimit());
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }
}
