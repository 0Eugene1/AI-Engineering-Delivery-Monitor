package ru.eltc.deliverymonitor.domain.gitlab;

/**
 * Counts of rows created vs updated by one commit upsert call.
 *
 * @param created number of new {@code commits} rows inserted
 * @param updated number of existing rows matched by {@code (repositoryId, sha)} and updated
 */
public record CommitUpsertOutcome(int created, int updated) {

    public static CommitUpsertOutcome empty() {
        return new CommitUpsertOutcome(0, 0);
    }

    public int saved() {
        return created + updated;
    }
}
