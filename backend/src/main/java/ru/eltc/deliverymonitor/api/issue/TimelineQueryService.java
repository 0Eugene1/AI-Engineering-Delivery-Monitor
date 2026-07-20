package ru.eltc.deliverymonitor.api.issue;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.eltc.deliverymonitor.api.ActivityEventMapper;
import ru.eltc.deliverymonitor.domain.timeline.ActivityEventEntity;
import ru.eltc.deliverymonitor.domain.timeline.ActivityEventRepository;
import ru.eltc.deliverymonitor.domain.workstream_type.WorkstreamTypeEntity;
import ru.eltc.deliverymonitor.domain.workstream_type.WorkstreamTypeRepository;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Read-only query layer for Issue Timeline (Phase 3.7): loads {@code activity_events} from
 * PostgreSQL only and maps them to {@link TimelineResponse} via shared
 * {@link ActivityEventMapper}. No Jira/GitLab live calls; does not require a row in
 * {@code issues}.
 *
 * <p>Dependency direction: {@code PostgreSQL -> domain.timeline (+ domain.workstream_type) ->
 * api.issue}.
 */
@Service
@Transactional(readOnly = true)
public class TimelineQueryService {

    private final ActivityEventRepository activityEventRepository;
    private final WorkstreamTypeRepository workstreamTypeRepository;
    private final ActivityEventMapper activityEventMapper;

    public TimelineQueryService(
            ActivityEventRepository activityEventRepository,
            WorkstreamTypeRepository workstreamTypeRepository,
            ActivityEventMapper activityEventMapper) {
        this.activityEventRepository = activityEventRepository;
        this.workstreamTypeRepository = workstreamTypeRepository;
        this.activityEventMapper = activityEventMapper;
    }

    /**
     * Returns timeline for {@code issueKey}. Always a present response with possibly empty
     * {@code events} — never signals "not found".
     */
    public TimelineResponse findTimeline(String issueKey) {
        List<ActivityEventEntity> rows =
                activityEventRepository.findAllByIssueKeyOrderByOccurredAtDesc(issueKey);
        Map<String, String> displayNames = workstreamTypeRepository.findAll().stream()
                .collect(Collectors.toMap(
                        WorkstreamTypeEntity::getCode,
                        WorkstreamTypeEntity::getDisplayName,
                        (a, b) -> a));
        List<TimelineResponse.TimelineEvent> events = rows.stream()
                .map(entity -> activityEventMapper.toTimelineEvent(entity, displayNames))
                .toList();
        return new TimelineResponse(issueKey, events);
    }
}
