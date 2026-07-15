package ru.eltc.deliverymonitor.sync.jira;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import ru.eltc.deliverymonitor.domain.issue.IssuePersistencePort;
import ru.eltc.deliverymonitor.domain.issue.IssueUpsertCommand;
import ru.eltc.deliverymonitor.domain.issue.IssueUpsertOutcome;
import ru.eltc.deliverymonitor.integration.jira.dto.JiraFixVersionDto;
import ru.eltc.deliverymonitor.integration.jira.dto.JiraIssueDto;
import ru.eltc.deliverymonitor.integration.jira.dto.JiraIssueFieldsDto;
import ru.eltc.deliverymonitor.integration.jira.dto.JiraStatusDto;
import ru.eltc.deliverymonitor.integration.jira.dto.JiraUserDto;
import ru.eltc.deliverymonitor.integration.jira.exception.JiraClientException;
import ru.eltc.deliverymonitor.integration.jira.provider.JiraBoardContext;
import ru.eltc.deliverymonitor.integration.jira.provider.JiraContextProvider;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * First application-level layer over {@link JiraContextProvider} (docs/roadmap.md Phase 2.2
 * "Jira Sync"). Orchestrates a full sync of the observed board's issues:
 *
 * <ol>
 *   <li>pages through {@link JiraContextProvider#getBoardContext(int, int)} until every issue is
 *       read (page size from {@code jira.sync.page-size});</li>
 *   <li>normalizes each wire {@link JiraIssueDto} into a {@link JiraIssueSnapshot}, then maps it
 *       into {@code domain.issue}'s own {@link IssueUpsertCommand};</li>
 *   <li>upserts each page immediately via {@link IssuePersistencePort#upsertPage(List)} — no
 *       full-run accumulation in memory, so a mid-run failure still leaves already-processed
 *       pages persisted;</li>
 *   <li>returns a {@link JiraSyncResult} describing the run (aggregates only).</li>
 * </ol>
 *
 * <p>Depends only on the provider seam (never the raw {@code JiraClient}) and on {@code
 * domain.issue}'s persistence contract (never {@code JpaRepository}/entities directly). A {@link
 * JiraClientException} is not propagated raw: it is recorded in {@link JiraSyncResult#errors()}
 * and the run returns whatever it managed to fetch and persist so far. Database errors from
 * {@link IssuePersistencePort} are <b>not</b> caught here — they are not masked, and the run does
 * not continue past them.
 */
@Service
@EnableConfigurationProperties(JiraSyncProperties.class)
public class JiraSyncService {

    private static final Logger log = LoggerFactory.getLogger(JiraSyncService.class);

    /**
     * Defensive upper bound on pages read in a single run, so a provider that keeps returning
     * non-empty pages (e.g. a stale/over-reported total) can never spin forever.
     */
    private static final int MAX_PAGES = 10_000;

    /** Jira REST API v2 timestamp format, e.g. {@code 2026-07-10T12:30:00.000+0000}. */
    private static final DateTimeFormatter JIRA_TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

    private final JiraContextProvider contextProvider;
    private final IssuePersistencePort persistencePort;
    private final JiraSyncProperties properties;

    public JiraSyncService(
            JiraContextProvider contextProvider,
            IssuePersistencePort persistencePort,
            JiraSyncProperties properties) {
        this.contextProvider = contextProvider;
        this.persistencePort = persistencePort;
        this.properties = properties;
    }

    /**
     * Fetches every issue of the observed board (via the configured board filter) and upserts
     * each page into persistence as it is fetched.
     */
    public JiraSyncResult syncBoard() {
        Instant startedAt = Instant.now();
        int pageSize = properties.getPageSize();

        List<String> errors = new ArrayList<>();
        int pages = 0;
        int fetched = 0;
        int created = 0;
        int updated = 0;
        boolean mocked = false;

        int startAt = 0;
        int total = Integer.MAX_VALUE; // unknown until the first page reports it

        try {
            while (startAt < total && pages < MAX_PAGES) {
                JiraBoardContext context = contextProvider.getBoardContext(startAt, pageSize);
                pages++;
                mocked = context.mocked();
                total = context.total();

                List<JiraIssueDto> pageIssues = context.issues();
                if (pageIssues == null || pageIssues.isEmpty()) {
                    // No more data even though total may claim otherwise — stop instead of looping.
                    break;
                }

                List<IssueUpsertCommand> commands = new ArrayList<>(pageIssues.size());
                for (JiraIssueDto issue : pageIssues) {
                    commands.add(toCommand(toSnapshot(issue)));
                }

                IssueUpsertOutcome outcome = persistencePort.upsertPage(commands);
                created += outcome.created();
                updated += outcome.updated();
                fetched += pageIssues.size();

                // Advance by what was actually returned: robust if the server caps maxResults.
                startAt += pageIssues.size();
            }
            if (pages >= MAX_PAGES) {
                errors.add("Reached the safety limit of " + MAX_PAGES + " pages; sync stopped early");
            }
        } catch (JiraClientException e) {
            errors.add(describe(e));
            log.warn("Jira sync failed after {} page(s), {} issue(s) fetched/saved so far: {}",
                    pages, fetched, e.getMessage());
        }

        JiraSyncResult result = new JiraSyncResult(
                startedAt, Instant.now(), fetched, pages, mocked, created, updated, List.copyOf(errors));
        log.info("Jira sync done: fetched={} pages={} mocked={} created={} updated={} errors={}",
                result.fetched(), result.pages(), result.mocked(),
                result.created(), result.updated(), result.errors().size());
        return result;
    }

    private JiraIssueSnapshot toSnapshot(JiraIssueDto issue) {
        JiraIssueFieldsDto fields = issue.fields();
        if (fields == null) {
            return new JiraIssueSnapshot(
                    issue.id(), issue.key(), null, null, null, null, null, null, List.of(), List.of(), null, null);
        }

        JiraStatusDto status = fields.status();
        String statusName = status != null ? status.name() : null;
        String statusCategory = status != null && status.statusCategory() != null
                ? status.statusCategory().name()
                : null;

        JiraUserDto assignee = fields.assignee();
        String assigneeUsername = assignee != null ? assignee.name() : null;
        String assigneeDisplayName = assignee != null ? assignee.displayName() : null;

        String issueType = fields.issuetype() != null ? fields.issuetype().name() : null;

        List<String> fixVersions = fields.fixVersions() != null
                ? fields.fixVersions().stream()
                        .map(JiraFixVersionDto::name)
                        .filter(Objects::nonNull)
                        .toList()
                : List.of();

        List<String> labels = fields.labels() != null ? List.copyOf(fields.labels()) : List.of();

        return new JiraIssueSnapshot(
                issue.id(),
                issue.key(),
                fields.summary(),
                statusName,
                statusCategory,
                assigneeUsername,
                assigneeDisplayName,
                issueType,
                fixVersions,
                labels,
                parseJiraTimestamp(fields.created()),
                parseJiraTimestamp(fields.updated()));
    }

    private static IssueUpsertCommand toCommand(JiraIssueSnapshot snapshot) {
        return new IssueUpsertCommand(
                snapshot.jiraId(),
                snapshot.key(),
                snapshot.summary(),
                snapshot.statusName(),
                snapshot.statusCategory(),
                snapshot.assigneeUsername(),
                snapshot.assigneeDisplayName(),
                snapshot.issueType(),
                snapshot.fixVersions(),
                snapshot.labels(),
                snapshot.created(),
                snapshot.updated());
    }

    /**
     * Parses a Jira REST API v2 timestamp into an {@link Instant}. A malformed/unparsable value
     * does not fail the sync: it is logged as a warning and mapped to {@code null}, matching the
     * existing partial-result policy for Jira-side failures.
     */
    private Instant parseJiraTimestamp(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value, JIRA_TIMESTAMP_FORMAT).toInstant();
        } catch (DateTimeParseException e) {
            log.warn("Failed to parse Jira timestamp '{}': {}", value, e.getMessage());
            return null;
        }
    }

    private static String describe(JiraClientException e) {
        StringBuilder sb = new StringBuilder();
        if (e.getStatusCode() != JiraClientException.NO_HTTP_STATUS) {
            sb.append("HTTP ").append(e.getStatusCode()).append(": ");
        }
        sb.append(e.getMessage());
        if (!e.getJiraErrorMessages().isEmpty()) {
            sb.append(" (").append(String.join("; ", e.getJiraErrorMessages())).append(')');
        }
        return sb.toString();
    }
}
