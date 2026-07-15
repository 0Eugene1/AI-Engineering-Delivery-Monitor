package ru.eltc.deliverymonitor.domain.issue;

import java.util.List;

/**
 * {@code domain.issue}'s own persistence contract. Callers ({@code sync.jira}) depend on this
 * interface, never the other way around — {@code domain.issue} does not know {@code sync.jira}
 * exists (see docs/architecture.md "Package dependency direction").
 *
 * <p>Upserts happen page by page: the caller is expected to invoke this once per page of fetched
 * issues, rather than accumulating a full run in memory before calling it once.
 */
public interface IssuePersistencePort {

    /** Upserts one page of issues, matching existing rows by {@link IssueUpsertCommand#jiraId()}. */
    IssueUpsertOutcome upsertPage(List<IssueUpsertCommand> commands);
}
