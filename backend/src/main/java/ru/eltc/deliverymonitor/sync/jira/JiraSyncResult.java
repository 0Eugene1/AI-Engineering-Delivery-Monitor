package ru.eltc.deliverymonitor.sync.jira;

import java.time.Instant;
import java.util.List;

/**
 * Outcome of a single {@link JiraSyncService#syncBoard()} run — a truthful report of what the sync
 * did. Designed to be reused later by the {@code POST /api/admin/sync/jira} endpoint (Phase 2.4),
 * the scheduler (Phase 2.5) and logging/monitoring, aligned with the {@code api.md} sketch
 * ({@code startedAt}/{@code finishedAt}/{@code fetched}/{@code saved}/{@code errors}).
 *
 * <p>There is no stored {@code issues} list any more: persistence (Phase 2.3) upserts page by
 * page as they are fetched, so the run only reports aggregates. {@link #saved()} is a derived
 * method ({@code created + updated}), not a separately stored field, so it can never drift from
 * the sum.
 *
 * @param startedAt  when the run started
 * @param finishedAt when the run finished
 * @param fetched    number of issues fetched and normalized
 * @param pages      number of provider pages read
 * @param mocked     {@code true} if the data came from the offline mock, not real Jira
 * @param created    number of new {@code issues} rows inserted across all pages
 * @param updated    number of existing {@code issues} rows updated across all pages
 * @param errors     human-readable error messages ({@code empty} on a clean run); a partially
 *                   failed run still returns what it managed to fetch and persist
 */
public record JiraSyncResult(
        Instant startedAt,
        Instant finishedAt,
        int fetched,
        int pages,
        boolean mocked,
        int created,
        int updated,
        List<String> errors
) {

    /** Total number of {@code issues} rows created or updated across the run. */
    public int saved() {
        return created + updated;
    }
}
