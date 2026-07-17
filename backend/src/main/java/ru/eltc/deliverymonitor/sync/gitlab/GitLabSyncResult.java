package ru.eltc.deliverymonitor.sync.gitlab;

import java.time.Instant;
import java.util.List;

/**
 * Outcome of a single {@link GitLabSyncService#syncAll()} / {@link GitLabSyncService#syncProject(String)}
 * run — a truthful report of what the sync did. Designed to be reused later by
 * {@code POST /api/admin/sync/gitlab} (Phase 3.8), the reconcile scheduler (Phase 3.9) and
 * logging/monitoring.
 *
 * <p>After Phase 3.4.1 persistence wiring there is no stored snapshot list: the run upserts
 * branches/commits/MRs via {@code domain.gitlab} ports and reports aggregates only (same pattern
 * as {@code JiraSyncResult} after Phase 2.3). {@link #saved()} is derived ({@code created +
 * updated}).
 *
 * @param startedAt            when the run started
 * @param finishedAt           when the run finished
 * @param projectsSynced       number of repositories successfully fetched and upserted end-to-end
 * @param branchesFetched      total branches normalized across all projects
 * @param commitsFetched       total commits normalized across all projects
 * @param mergeRequestsFetched total merge requests normalized across all projects
 * @param pages                total list-endpoint pages read (branches + commits + MRs)
 * @param mocked               {@code true} if data came from {@code gitlab.mode=mock}
 * @param created              new {@code branches}/{@code commits}/{@code merge_requests} rows
 * @param updated              existing git-entity rows updated
 * @param errors               human-readable error messages ({@code empty} on a clean run); a
 *                             partially failed multi-repo run still returns what it managed
 */
public record GitLabSyncResult(
        Instant startedAt,
        Instant finishedAt,
        int projectsSynced,
        int branchesFetched,
        int commitsFetched,
        int mergeRequestsFetched,
        int pages,
        boolean mocked,
        int created,
        int updated,
        List<String> errors
) {

    /** Total number of git entities (branches + commits + MRs) fetched and normalized. */
    public int fetched() {
        return branchesFetched + commitsFetched + mergeRequestsFetched;
    }

    /** Total number of git-entity rows created or updated across the run. */
    public int saved() {
        return created + updated;
    }
}
