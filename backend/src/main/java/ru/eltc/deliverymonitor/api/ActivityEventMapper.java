package ru.eltc.deliverymonitor.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import ru.eltc.deliverymonitor.api.activity.ActivityFeedResponse;
import ru.eltc.deliverymonitor.api.issue.TimelineResponse;
import ru.eltc.deliverymonitor.domain.timeline.ActivityEventEntity;
import ru.eltc.deliverymonitor.domain.timeline.ActivityEventTypes;

import java.util.Map;

/**
 * Shared read-side mapping for {@code activity_events} → Timeline / Activity Feed DTOs
 * (Phase 3.7 + Phase 4.1). Owns summary generation, actor mapping, and payload parsing so
 * {@code api.issue} and {@code api.activity} do not duplicate presentation logic.
 */
@Component
public class ActivityEventMapper {

    private final ObjectMapper objectMapper;

    public ActivityEventMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public TimelineResponse.TimelineEvent toTimelineEvent(
            ActivityEventEntity entity, Map<String, String> displayNames) {
        JsonNode payload = parsePayload(entity.getPayload());
        return new TimelineResponse.TimelineEvent(
                idOf(entity),
                entity.getOccurredAt(),
                entity.getType(),
                toTimelineWorkstreamType(entity.getWorkstreamTypeCode(), displayNames),
                toTimelineActor(entity.getActorUsername(), entity.getActorDisplayName()),
                buildSummary(entity.getType(), payload),
                payload);
    }

    public ActivityFeedResponse.ActivityEvent toFeedEvent(
            ActivityEventEntity entity, Map<String, String> displayNames) {
        JsonNode payload = parsePayload(entity.getPayload());
        return new ActivityFeedResponse.ActivityEvent(
                idOf(entity),
                entity.getOccurredAt(),
                entity.getType(),
                entity.getSource(),
                entity.getIssueKey(),
                toFeedWorkstreamType(entity.getWorkstreamTypeCode(), displayNames),
                toFeedActor(entity.getActorUsername(), entity.getActorDisplayName()),
                buildSummary(entity.getType(), payload),
                payload);
    }

    private static String idOf(ActivityEventEntity entity) {
        return entity.getId() == null ? null : String.valueOf(entity.getId());
    }

    private TimelineResponse.WorkstreamTypeRef toTimelineWorkstreamType(
            String code, Map<String, String> displayNames) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return new TimelineResponse.WorkstreamTypeRef(code, displayNames.get(code));
    }

    private ActivityFeedResponse.WorkstreamTypeRef toFeedWorkstreamType(
            String code, Map<String, String> displayNames) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return new ActivityFeedResponse.WorkstreamTypeRef(code, displayNames.get(code));
    }

    private TimelineResponse.ActorRef toTimelineActor(String username, String displayName) {
        if (username == null && displayName == null) {
            return null;
        }
        return new TimelineResponse.ActorRef(username, displayName);
    }

    private ActivityFeedResponse.ActorRef toFeedActor(String username, String displayName) {
        if (username == null && displayName == null) {
            return null;
        }
        return new ActivityFeedResponse.ActorRef(username, displayName);
    }

    JsonNode parsePayload(String payload) {
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
    static String buildSummary(String type, JsonNode payload) {
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
