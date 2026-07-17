package ru.eltc.deliverymonitor.domain.repository;

/**
 * Counts of rows created vs updated by one {@link RepositoryPersistencePort#upsertAll} call.
 *
 * @param created number of new {@code repositories} rows inserted
 * @param updated number of existing rows matched by {@code gitlabProjectId} and updated
 */
public record RepositoryUpsertOutcome(int created, int updated) {

    public static RepositoryUpsertOutcome empty() {
        return new RepositoryUpsertOutcome(0, 0);
    }

    /** Derived total of persisted rows touched in this call. */
    public int saved() {
        return created + updated;
    }
}
