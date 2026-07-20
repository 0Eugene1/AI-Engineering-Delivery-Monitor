package ru.eltc.deliverymonitor.api.activity;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;

/**
 * External contract for {@code GET /api/activity} (docs/api.md Phase 4.1).
 *
 * <p>Always returned with HTTP {@code 200}, including when {@code events} is empty.
 *
 * @param events activity events, newest first ({@code occurred_at DESC})
 */
public record ActivityFeedResponse(List<ActivityEvent> events) {

    /**
     * One {@code activity_events} row for the team feed. Unlike Timeline items, {@code issueKey}
     * is present (nullable for orphans) and {@code source} is exposed.
     *
     * @param id             database id (string in JSON for forward-compat with opaque ids)
     * @param occurredAt     when the fact happened
     * @param type           event type ({@code BRANCH_CREATED}, {@code COMMIT}, …)
     * @param source         origin system ({@code GITLAB}, …)
     * @param issueKey       linked Jira key, or {@code null} for orphan events
     * @param workstreamType type code + display name from {@code workstream_types}, or {@code null}
     * @param actor          actor username/display name, or {@code null} when both absent
     * @param summary        short human-readable line derived from type + payload (same as Timeline)
     * @param payload        parsed JSON payload, or {@code null}
     */
    public record ActivityEvent(
            String id,
            Instant occurredAt,
            String type,
            String source,
            String issueKey,
            WorkstreamTypeRef workstreamType,
            ActorRef actor,
            String summary,
            JsonNode payload
    ) {
    }

    /**
     * Workstream Type pill — {@code code} + {@code displayName} from the dictionary
     * (docs/api.md Conventions), not a hardcoded platform name.
     */
    public record WorkstreamTypeRef(String code, String displayName) {
    }

    /**
     * Actor on a feed item. Mapped from {@code actor_username}/{@code actor_display_name};
     * field {@code id} is the username (no separate people id in Phase 4).
     */
    public record ActorRef(String id, String name) {
    }
}
