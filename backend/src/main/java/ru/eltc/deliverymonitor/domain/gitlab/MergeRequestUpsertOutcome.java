package ru.eltc.deliverymonitor.domain.gitlab;

/**
 * Counts of rows created vs updated by one merge-request upsert call.
 *
 * @param created number of new {@code merge_requests} rows inserted
 * @param updated number of existing rows matched by {@code (repositoryId, gitlabIid)} and updated
 */
public record MergeRequestUpsertOutcome(int created, int updated) {

    public static MergeRequestUpsertOutcome empty() {
        return new MergeRequestUpsertOutcome(0, 0);
    }

    public int saved() {
        return created + updated;
    }
}
