package ru.eltc.deliverymonitor.domain.workstream;

/**
 * Counts of rows created vs updated by one {@link WorkstreamPersistencePort#upsertAll} call.
 *
 * @param created number of new {@code workstreams} rows inserted
 * @param updated number of existing rows matched by {@code (issueKey, workstreamTypeCode)} and updated
 */
public record WorkstreamUpsertOutcome(int created, int updated) {

    public static WorkstreamUpsertOutcome empty() {
        return new WorkstreamUpsertOutcome(0, 0);
    }

    public int saved() {
        return created + updated;
    }
}
