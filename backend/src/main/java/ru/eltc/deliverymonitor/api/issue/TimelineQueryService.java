package ru.eltc.deliverymonitor.api.issue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.eltc.deliverymonitor.domain.timeline.ActivityEventEntity;
import ru.eltc.deliverymonitor.domain.timeline.ActivityEventRepository;
import ru.eltc.deliverymonitor.domain.timeline.ActivityEventTypes;
import ru.eltc.deliverymonitor.domain.workstream_type.WorkstreamTypeEntity;
import ru.eltc.deliverymonitor.domain.workstream_type.WorkstreamTypeRepository;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Read-only query layer for Issue Timeline (Phase 3.7): loads {@code activity_events} from
 * PostgreSQL only and maps them to {@link TimelineResponse}. No Jira/GitLab live calls; does not
 * require a row in {@code issues}.
 *
 * <p>Dependency direction: {@code PostgreSQL -> domain.timeline (+ domain.workstream_type) ->
 * api.issue}.
 */
@Service
@Transactional(readOnly = true)
public class TimelineQueryService {

    private final ActivityEventRepository activityEventRepository;
    private final WorkstreamTypeRepository workstreamTypeRepository;
    private final ObjectMapper objectMapper;

    public TimelineQueryService(
            ActivityEventRepository activityEventRepository,
            WorkstreamTypeRepository workstreamTypeRepository,
            ObjectMapper objectMapper) {
        this.activityEventRepository = activityEventRepository;
        this.workstreamTypeRepository = workstreamTypeRepository;
        this.objectMapper = objectMapper;
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
                .map(entity -> toEvent(entity, displayNames))
                .toList();
        return new TimelineResponse(issueKey, events);
    }

    private TimelineResponse.TimelineEvent toEvent(
            ActivityEventEntity entity, Map<String, String> displayNames) {
        JsonNode payload = parsePayload(entity.getPayload());
        return new TimelineResponse.TimelineEvent(
                entity.getId() == null ? null : String.valueOf(entity.getId()),
                entity.getOccurredAt(),
                entity.getType(),
                toWorkstreamType(entity.getWorkstreamTypeCode(), displayNames),
                toActor(entity.getActorUsername(), entity.getActorDisplayName()),
                buildSummary(entity.getType(), payload),
                payload);
    }

    private TimelineResponse.WorkstreamTypeRef toWorkstreamType(
            String code, Map<String, String> displayNames) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return new TimelineResponse.WorkstreamTypeRef(code, displayNames.get(code));
    }

    private TimelineResponse.ActorRef toActor(String username, String displayName) {
        if (username == null && displayName == null) {
            return null;
        }
        return new TimelineResponse.ActorRef(username, displayName);
    }

    private JsonNode parsePayload(String payload) {
        if (payload == null || payload.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(payload);
        } catch (Exception e) {
            return objectMapper.getNodeFactory().textNode(payload);
        }
    }

    /**
     * Short UI line from event type + known payload fields. Not stored in DB — derived at read
     * time from data written by GitLab sync (Phase 3.5).
     */
    private static String buildSummary(String type, JsonNode payload) {
        if (type == null) {
            return "";
        }
        return switch (type) {
            case ActivityEventTypes.BRANCH_CREATED -> {
                String branch = text(payload, "branch");
                yield branch != null ? "created branch " + branch : "created branch";
            }
            case ActivityEventTypes.COMMIT -> {
                String title = text(payload, "title");
                if (title != null) {
                    yield title;
                }
                String shortId = text(payload, "shortId");
                yield shortId != null ? "commit " + shortId : "commit";
            }
            case ActivityEventTypes.MR_OPENED -> mrSummary(payload, "opened MR");
            case ActivityEventTypes.MR_APPROVED -> mrSummary(payload, "approved MR");
            case ActivityEventTypes.MR_MERGED -> mrSummary(payload, "merged MR");
            default -> type;
        };
    }

    private static String mrSummary(JsonNode payload, String verb) {
        JsonNode iid = payload == null ? null : payload.get("iid");
        if (iid != null && !iid.isNull()) {
            return verb + " !" + iid.asText();
        }
        return verb;
    }

    private static String text(JsonNode payload, String field) {
        if (payload == null || !payload.has(field) || payload.get(field).isNull()) {
            return null;
        }
        String value = payload.get(field).asText();
        return value.isBlank() ? null : value;
    }
}
