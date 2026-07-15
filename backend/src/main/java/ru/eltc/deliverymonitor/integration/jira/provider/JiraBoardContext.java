package ru.eltc.deliverymonitor.integration.jira.provider;

import ru.eltc.deliverymonitor.integration.jira.dto.JiraIssueDto;

import java.time.Instant;
import java.util.List;

/**
 * Result of {@link JiraContextProvider#getBoardContext(int, int)}: the board's issues plus
 * enough context to interpret them. Reuses the existing {@link JiraIssueDto} so callers see
 * the same shape regardless of whether the data came from the real Jira or the mock.
 *
 * @param boardId     observed board id (docs/discovery.md §9.1)
 * @param filterId    board filter id used to select issues (e.g. 30532)
 * @param startAt     paging offset echoed back
 * @param maxResults  page size echoed back
 * @param total       total issues matching the filter (may exceed {@code issues.size()})
 * @param issues      the returned page of issues
 * @param fetchedAt   when this context was produced
 * @param mocked      {@code true} if served from sanitized demo data (mock), not real Jira
 */
public record JiraBoardContext(
        long boardId,
        long filterId,
        int startAt,
        int maxResults,
        int total,
        List<JiraIssueDto> issues,
        Instant fetchedAt,
        boolean mocked
) {
}
