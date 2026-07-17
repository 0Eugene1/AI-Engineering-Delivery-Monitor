package ru.eltc.deliverymonitor.domain.timeline;

import java.util.List;

/**
 * {@code domain.timeline}'s persistence contract for {@code activity_events}. Callers
 * ({@code sync.gitlab}) depend on this interface; this package does not import upper layers.
 *
 * <p>Upsert matches existing rows by {@code (source, sourceRef)} — UNIQUE constraint in Liquibase
 * {@code 0006-activity-events.yaml}.
 */
public interface ActivityEventPersistencePort {

    ActivityEventUpsertOutcome upsertAll(List<ActivityEventUpsertCommand> commands);
}
