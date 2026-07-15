package ru.eltc.deliverymonitor.integration.jira.provider;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import ru.eltc.deliverymonitor.integration.jira.client.JiraClient;
import ru.eltc.deliverymonitor.integration.jira.config.JiraProperties;
import ru.eltc.deliverymonitor.integration.jira.dto.JiraIssueDto;
import ru.eltc.deliverymonitor.integration.jira.dto.JiraSearchResultDto;
import ru.eltc.deliverymonitor.integration.jira.exception.JiraClientException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Default {@link JiraContextProvider}: reads the board context from the real Jira Server via
 * the Phase 2.1 {@link JiraClient} (board filter search, docs/discovery.md §9.1). Active when
 * {@code jira.mode=rest} (the default).
 *
 * <p>Contains no persistence, no scheduler and no domain entities — it only adapts the reactive
 * {@code JiraClient} into the synchronous {@link JiraContextProvider} contract, blocking with a
 * bounded timeout so reactive types stay confined to the client layer.
 */
@Component
@ConditionalOnProperty(name = "jira.mode", havingValue = "rest", matchIfMissing = true)
public class RestJiraContextProvider implements JiraContextProvider {

    private final JiraClient jiraClient;
    private final JiraProperties properties;
    private final Duration blockTimeout;

    public RestJiraContextProvider(JiraClient jiraClient, JiraProperties properties) {
        this.jiraClient = jiraClient;
        this.properties = properties;
        // Bounded wait: connect + response budget plus a small buffer, so a stuck call surfaces
        // as an error instead of blocking the caller forever.
        this.blockTimeout = properties.getConnectTimeout()
                .plus(properties.getResponseTimeout())
                .plusSeconds(5);
    }

    @Override
    public JiraBoardContext getBoardContext(int startAt, int maxResults) {
        long filterId = properties.getDefaultFilterId();
        JiraSearchResultDto result = jiraClient.searchByFilter(filterId, startAt, maxResults)
                .block(blockTimeout);

        if (result == null) {
            throw new JiraClientException(
                    "Jira returned no board context for filter " + filterId,
                    JiraClientException.NO_HTTP_STATUS, List.of());
        }

        List<JiraIssueDto> issues = result.issues() != null ? result.issues() : List.of();
        return new JiraBoardContext(
                properties.getBoardId() != null ? properties.getBoardId() : 0L,
                filterId,
                result.startAt() != null ? result.startAt() : startAt,
                result.maxResults() != null ? result.maxResults() : maxResults,
                result.total() != null ? result.total() : issues.size(),
                issues,
                Instant.now(),
                false);
    }
}
