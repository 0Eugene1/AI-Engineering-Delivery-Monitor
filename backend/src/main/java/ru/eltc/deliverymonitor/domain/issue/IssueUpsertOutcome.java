package ru.eltc.deliverymonitor.domain.issue;

/**
 * Result of a single {@link IssuePersistencePort#upsertPage(java.util.List)} call: how many of the
 * commands in that page created a new row versus updated an existing one.
 *
 * @param created number of new {@code issues} rows inserted
 * @param updated number of existing {@code issues} rows updated
 */
public record IssueUpsertOutcome(int created, int updated) {

    public static IssueUpsertOutcome empty() {
        return new IssueUpsertOutcome(0, 0);
    }
}
