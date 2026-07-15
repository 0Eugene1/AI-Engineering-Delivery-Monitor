package ru.eltc.deliverymonitor.integration.jira.provider;

/**
 * Phase 2.3 seam: supplies the Jira <em>board context</em> (the issues shown by the
 * observed board's filter — docs/discovery.md §9.1, board 718 / filter 30532) as a
 * domain-meaningful operation, decoupled from the raw HTTP {@code JiraClient}.
 *
 * <p>Upper layers (future sync orchestration — Phase 2.2/2.4) depend on this interface,
 * never on {@code JiraClient} directly. Switching between the real Jira Server and the
 * offline mock is a configuration change ({@code jira.mode=rest|mock}), not a code change:
 * see {@code RestJiraContextProvider} and {@code MockJiraContextProvider}.
 *
 * <p><b>Scope (Phase 2.3):</b> only issue retrieval via the board filter. Board configuration
 * (columns/swimlanes) and sprint metadata are deliberately out of scope — the board is Kanban
 * and its column/status/swimlane config is already captured statically in docs/discovery.md
 * §9.1. Those remain a future, separate task; this interface can be extended then without
 * rewriting callers.
 */
public interface JiraContextProvider {

    /**
     * Fetches a page of the board's issues (via the configured board filter).
     *
     * @param startAt    paging offset (0-based)
     * @param maxResults page size
     * @return the board context: board/filter ids, paging echo, total and the issues page
     */
    JiraBoardContext getBoardContext(int startAt, int maxResults);
}
