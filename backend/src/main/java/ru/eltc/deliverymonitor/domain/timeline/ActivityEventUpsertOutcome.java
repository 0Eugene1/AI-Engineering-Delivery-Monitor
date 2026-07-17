package ru.eltc.deliverymonitor.domain.timeline;

/**
 * Counts of rows created vs updated by one {@link ActivityEventPersistencePort#upsertAll} call.
 *
 * @param created number of new {@code activity_events} rows inserted
 * @param updated number of existing rows matched by {@code (source, sourceRef)} and updated
 */
public record ActivityEventUpsertOutcome(int created, int updated) {

    public static ActivityEventUpsertOutcome empty() {
        return new ActivityEventUpsertOutcome(0, 0);
    }

    public int saved() {
        return created + updated;
    }
}
