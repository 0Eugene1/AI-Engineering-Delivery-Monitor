package ru.eltc.deliverymonitor.domain.gitlab;

/**
 * Counts of rows created vs updated by one branch upsert call.
 *
 * @param created number of new {@code branches} rows inserted
 * @param updated number of existing rows matched by {@code (repositoryId, name)} and updated
 */
public record BranchUpsertOutcome(int created, int updated) {

    public static BranchUpsertOutcome empty() {
        return new BranchUpsertOutcome(0, 0);
    }

    public int saved() {
        return created + updated;
    }
}
