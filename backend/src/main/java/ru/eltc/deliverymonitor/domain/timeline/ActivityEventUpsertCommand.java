package ru.eltc.deliverymonitor.domain.timeline;

import java.time.Instant;

/**
 * {@code domain.timeline}'s own input contract for upserting a single activity event.
 * Callers ({@code sync.gitlab}) map snapshots into this command; this package never imports
 * sync/integration types.
 *
 * <p>Matching key is {@code (source, sourceRef)} — see {@link ActivityEventEntity}.
 *
 * @param occurredAt         when the fact happened
 * @param issueKey           nullable Jira key (orphan allowed)
 * @param workstreamTypeCode Workstream Type code, or {@code null}
 * @param actorUsername      actor login, or {@code null}
 * @param actorDisplayName   actor display name, or {@code null}
 * @param type               event type (e.g. {@link ActivityEventTypes#COMMIT})
 * @param payload            optional JSON/text payload, or {@code null}
 * @param source             origin system (e.g. {@link ActivityEventTypes#SOURCE_GITLAB})
 * @param sourceRef          stable external ref within {@code source} (idempotency)
 */
public record ActivityEventUpsertCommand(
        Instant occurredAt,
        String issueKey,
        String workstreamTypeCode,
        String actorUsername,
        String actorDisplayName,
        String type,
        String payload,
        String source,
        String sourceRef
) {
}
