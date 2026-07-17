package ru.eltc.deliverymonitor.api.issue;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;

/**
 * External contract for {@code GET /api/issues/{key}/timeline} (docs/api.md Phase 3.7).
 *
 * <p>Always returned with HTTP {@code 200}, including when {@code events} is empty — absence of
 * Git activity is not a missing resource (docs/decisions.md Timeline read contract).
 *
 * @param issueKey path key echoed back (not validated against {@code issues})
 * @param events   activity events for the key, newest first ({@code occurred_at DESC})
 */
public record TimelineResponse(String issueKey, List<TimelineEvent> events) {

    /**
     * One {@code activity_events} row as exposed to the UI (docs/api.md "Timeline item" sketch).
     *
     * @param id             database id (string in JSON for forward-compat with opaque ids)
     * @param occurredAt     when the fact happened
     * @param type           event type ({@code BRANCH_CREATED}, {@code COMMIT}, …)
     * @param workstreamType type code + display name from {@code workstream_types}, or {@code null}
     * @param actor          actor username/display name, or {@code null} when both absent
     * @param summary        short human-readable line derived from type + payload
     * @param payload        parsed JSON payload, or {@code null}
     */
    public record TimelineEvent(
            String id,
            Instant occurredAt,
            String type,
            WorkstreamTypeRef workstreamType,
            ActorRef actor,
            String summary,
            JsonNode payload
    ) {
    }

    /**
     * Workstream Type pill in timeline items — {@code code} + {@code displayName} from the
     * dictionary (docs/api.md Conventions), not a hardcoded platform name.
     */
    public record WorkstreamTypeRef(String code, String displayName) {
    }

    /**
     * Actor on a timeline item. Mapped from {@code actor_username}/{@code actor_display_name};
     * sketch field {@code id} is the username (no separate people id in Phase 3).
     */
    public record ActorRef(String id, String name) {
    }
}
