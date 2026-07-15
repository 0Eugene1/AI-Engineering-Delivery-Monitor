package ru.eltc.deliverymonitor.sync.jira;

import org.junit.jupiter.api.Test;
import ru.eltc.deliverymonitor.domain.issue.IssuePersistencePort;
import ru.eltc.deliverymonitor.domain.issue.IssueUpsertCommand;
import ru.eltc.deliverymonitor.domain.issue.IssueUpsertOutcome;
import ru.eltc.deliverymonitor.integration.jira.dto.JiraFixVersionDto;
import ru.eltc.deliverymonitor.integration.jira.dto.JiraIssueDto;
import ru.eltc.deliverymonitor.integration.jira.dto.JiraIssueFieldsDto;
import ru.eltc.deliverymonitor.integration.jira.dto.JiraIssueTypeDto;
import ru.eltc.deliverymonitor.integration.jira.dto.JiraStatusCategoryDto;
import ru.eltc.deliverymonitor.integration.jira.dto.JiraStatusDto;
import ru.eltc.deliverymonitor.integration.jira.dto.JiraUserDto;
import ru.eltc.deliverymonitor.integration.jira.exception.JiraClientException;
import ru.eltc.deliverymonitor.integration.jira.provider.JiraBoardContext;
import ru.eltc.deliverymonitor.integration.jira.provider.JiraContextProvider;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link JiraSyncService} against a fake {@link JiraContextProvider} and a
 * recording {@link IssuePersistencePort} — no real Jira, no HTTP, no database, no Spring context.
 * Covers pagination, per-page persistence (not full-run accumulation), field normalization, the
 * {@code mocked} flag, {@link JiraClientException} handling and the in-process concurrency guard
 * (Phase 2.5) that stops a manual and a scheduled sync from running at the same time.
 */
class JiraSyncServiceTest {

    private static JiraSyncProperties pageSize(int size) {
        JiraSyncProperties properties = new JiraSyncProperties();
        properties.setPageSize(size);
        return properties;
    }

    @Test
    void paginatesAcrossMultiplePagesAndPersistsEachPageSeparately() {
        List<JiraIssueDto> issues = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            issues.add(issue("MPTPSUPP-" + i, "Summary " + i));
        }
        RecordingPersistencePort port = new RecordingPersistencePort();
        JiraSyncService service = new JiraSyncService(new FakeProvider(issues, false), port, pageSize(2));

        JiraSyncResult result = service.syncBoard();

        assertThat(result.fetched()).isEqualTo(5);
        assertThat(result.pages()).isEqualTo(3); // 2 + 2 + 1
        assertThat(result.errors()).isEmpty();
        assertThat(result.mocked()).isFalse();
        assertThat(result.created()).isEqualTo(5);
        assertThat(result.updated()).isZero();
        assertThat(result.saved()).isEqualTo(5);
        assertThat(result.startedAt()).isBeforeOrEqualTo(result.finishedAt());

        // Persisted page by page, not accumulated into one call for the whole run.
        assertThat(port.pages()).hasSize(3);
        assertThat(port.pages().get(0)).extracting(IssueUpsertCommand::key)
                .containsExactly("MPTPSUPP-1", "MPTPSUPP-2");
        assertThat(port.pages().get(1)).extracting(IssueUpsertCommand::key)
                .containsExactly("MPTPSUPP-3", "MPTPSUPP-4");
        assertThat(port.pages().get(2)).extracting(IssueUpsertCommand::key)
                .containsExactly("MPTPSUPP-5");
    }

    @Test
    void normalizesIssueFieldsIntoUpsertCommand() {
        JiraIssueDto issue = new JiraIssueDto("10001", "MPTPSUPP-42", "self",
                new JiraIssueFieldsDto(
                        "Fix the thing",
                        new JiraStatusDto("3", "In Review", null,
                                new JiraStatusCategoryDto(4L, "indeterminate", "yellow", "In Progress")),
                        new JiraUserDto("j.doe", "j.doe", "John Doe", "j.doe@eltc.ru", true),
                        null,
                        new JiraIssueTypeDto("1", "Bug", false),
                        List.of(new JiraFixVersionDto("5", "5.7.27", false, false, null)),
                        List.of("backend", "urgent"),
                        "2026-07-01T09:00:00.000+0000",
                        "2026-07-10T12:30:00.000+0000"));
        RecordingPersistencePort port = new RecordingPersistencePort();
        JiraSyncService service = new JiraSyncService(new FakeProvider(List.of(issue), false), port, pageSize(50));

        service.syncBoard();

        IssueUpsertCommand command = port.pages().get(0).get(0);
        assertThat(command.jiraId()).isEqualTo("10001");
        assertThat(command.key()).isEqualTo("MPTPSUPP-42");
        assertThat(command.summary()).isEqualTo("Fix the thing");
        assertThat(command.statusName()).isEqualTo("In Review");
        assertThat(command.statusCategory()).isEqualTo("In Progress");
        assertThat(command.assigneeUsername()).isEqualTo("j.doe");
        assertThat(command.assigneeDisplayName()).isEqualTo("John Doe");
        assertThat(command.issueType()).isEqualTo("Bug");
        assertThat(command.fixVersions()).containsExactly("5.7.27");
        assertThat(command.labels()).containsExactly("backend", "urgent");
        assertThat(command.jiraCreated()).isEqualTo(Instant.parse("2026-07-01T09:00:00Z"));
        assertThat(command.jiraUpdated()).isEqualTo(Instant.parse("2026-07-10T12:30:00Z"));
    }

    @Test
    void unparsableTimestampBecomesNullWithoutFailingSync() {
        JiraIssueDto issue = new JiraIssueDto("10002", "MPTPSUPP-43", "self",
                new JiraIssueFieldsDto("s", null, null, null, null, null, null,
                        "not-a-date", "also-not-a-date"));
        RecordingPersistencePort port = new RecordingPersistencePort();
        JiraSyncService service = new JiraSyncService(new FakeProvider(List.of(issue), false), port, pageSize(50));

        JiraSyncResult result = service.syncBoard();

        assertThat(result.errors()).isEmpty();
        IssueUpsertCommand command = port.pages().get(0).get(0);
        assertThat(command.jiraCreated()).isNull();
        assertThat(command.jiraUpdated()).isNull();
    }

    @Test
    void handlesUnassignedAndMissingFieldsGracefully() {
        JiraIssueDto missingFields = new JiraIssueDto("1", "MPTPSUPP-100", "self", null);
        JiraIssueDto unassigned = new JiraIssueDto("2", "MPTPSUPP-101", "self",
                new JiraIssueFieldsDto("No assignee", null, null, null, null, null, null, null, null));
        RecordingPersistencePort port = new RecordingPersistencePort();
        JiraSyncService service = new JiraSyncService(
                new FakeProvider(List.of(missingFields, unassigned), false), port, pageSize(50));

        service.syncBoard();

        List<IssueUpsertCommand> commands = port.pages().get(0);
        assertThat(commands.get(0).key()).isEqualTo("MPTPSUPP-100");
        assertThat(commands.get(0).summary()).isNull();
        assertThat(commands.get(0).fixVersions()).isEmpty();
        assertThat(commands.get(0).labels()).isEmpty();
        assertThat(commands.get(1).assigneeUsername()).isNull();
        assertThat(commands.get(1).assigneeDisplayName()).isNull();
        assertThat(commands.get(1).statusName()).isNull();
        assertThat(commands.get(1).statusCategory()).isNull();
        assertThat(commands.get(1).fixVersions()).isEmpty();
        assertThat(commands.get(1).labels()).isEmpty();
    }

    @Test
    void propagatesMockedFlag() {
        RecordingPersistencePort port = new RecordingPersistencePort();
        JiraSyncService service = new JiraSyncService(
                new FakeProvider(List.of(issue("MPTPSUPP-1", "s")), true), port, pageSize(50));

        assertThat(service.syncBoard().mocked()).isTrue();
    }

    @Test
    void emptyBoardReturnsNoIssuesNoErrorsAndNeverCallsPersistence() {
        RecordingPersistencePort port = new RecordingPersistencePort();
        JiraSyncService service = new JiraSyncService(new FakeProvider(List.of(), false), port, pageSize(50));

        JiraSyncResult result = service.syncBoard();

        assertThat(result.fetched()).isZero();
        assertThat(result.created()).isZero();
        assertThat(result.updated()).isZero();
        assertThat(result.saved()).isZero();
        assertThat(result.errors()).isEmpty();
        assertThat(result.pages()).isEqualTo(1);
        assertThat(port.pages()).isEmpty();
    }

    @Test
    void recordsJiraClientExceptionWithoutPropagating() {
        JiraContextProvider failing = (startAt, maxResults) -> {
            throw new JiraClientException("boom", 401, List.of("Unauthorized"));
        };
        RecordingPersistencePort port = new RecordingPersistencePort();
        JiraSyncService service = new JiraSyncService(failing, port, pageSize(50));

        JiraSyncResult result = service.syncBoard();

        assertThat(result.fetched()).isZero();
        assertThat(result.saved()).isZero();
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0)).contains("HTTP 401", "boom", "Unauthorized");
    }

    @Test
    void returnsPartialPersistedResultsWhenAPageFails() {
        List<JiraIssueDto> firstPage = List.of(issue("MPTPSUPP-1", "s1"), issue("MPTPSUPP-2", "s2"));
        JiraContextProvider failingOnSecondPage = new JiraContextProvider() {
            @Override
            public JiraBoardContext getBoardContext(int startAt, int maxResults) {
                if (startAt == 0) {
                    return new JiraBoardContext(718L, 30532L, 0, maxResults, 10, firstPage, Instant.now(), false);
                }
                throw new JiraClientException("connection reset",
                        JiraClientException.NO_HTTP_STATUS, List.of());
            }
        };
        RecordingPersistencePort port = new RecordingPersistencePort();
        JiraSyncService service = new JiraSyncService(failingOnSecondPage, port, pageSize(2));

        JiraSyncResult result = service.syncBoard();

        assertThat(result.fetched()).isEqualTo(2);
        assertThat(result.created()).isEqualTo(2);
        assertThat(result.saved()).isEqualTo(2);
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0)).isEqualTo("connection reset");
        // The already-fetched first page was persisted before the failure on the second page.
        assertThat(port.pages()).hasSize(1);
        assertThat(port.pages().get(0)).extracting(IssueUpsertCommand::key)
                .containsExactly("MPTPSUPP-1", "MPTPSUPP-2");
    }

    @Test
    void guardSkipsAConcurrentSyncBoardCallWhileAnotherRunIsInProgress() throws InterruptedException {
        CountDownLatch firstRunEntered = new CountDownLatch(1);
        CountDownLatch releaseFirstRun = new CountDownLatch(1);
        JiraContextProvider blockingOnFirstPage = (startAt, maxResults) -> {
            firstRunEntered.countDown();
            try {
                releaseFirstRun.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return new JiraBoardContext(718L, 30532L, startAt, maxResults, 1,
                    List.of(issue("MPTPSUPP-1", "s")), Instant.now(), false);
        };
        RecordingPersistencePort port = new RecordingPersistencePort();
        JiraSyncService service = new JiraSyncService(blockingOnFirstPage, port, pageSize(50));

        JiraSyncResult[] firstRunResult = new JiraSyncResult[1];
        Thread firstRun = new Thread(() -> firstRunResult[0] = service.syncBoard());
        firstRun.start();

        assertThat(firstRunEntered.await(5, TimeUnit.SECONDS))
                .as("test setup: first run did not reach the provider in time")
                .isTrue();

        // Second call arrives while the first run is still blocked inside the provider above —
        // the in-process guard must skip it immediately rather than run concurrently.
        JiraSyncResult secondRunResult = service.syncBoard();
        assertThat(secondRunResult.fetched()).isZero();
        assertThat(secondRunResult.pages()).isZero();
        assertThat(secondRunResult.errors()).containsExactly("Sync already in progress; this run was skipped");
        assertThat(port.pages()).isEmpty();

        releaseFirstRun.countDown();
        firstRun.join(5_000);
        assertThat(firstRun.isAlive()).isFalse();
        assertThat(firstRunResult[0].fetched()).isEqualTo(1);
        assertThat(firstRunResult[0].errors()).isEmpty();
        assertThat(port.pages()).hasSize(1);

        // The guard is released after a run completes: a third call now runs normally.
        JiraSyncResult thirdRunResult = service.syncBoard();
        assertThat(thirdRunResult.errors()).isEmpty();
        assertThat(thirdRunResult.fetched()).isEqualTo(1);
    }

    private static JiraIssueDto issue(String key, String summary) {
        return new JiraIssueDto("id-" + key, key, "self",
                new JiraIssueFieldsDto(summary, null, null, null, null, null, null, null, null));
    }

    /** In-memory provider that pages over a fixed issue list, mirroring real paging semantics. */
    private static final class FakeProvider implements JiraContextProvider {

        private final List<JiraIssueDto> allIssues;
        private final boolean mocked;

        private FakeProvider(List<JiraIssueDto> allIssues, boolean mocked) {
            this.allIssues = allIssues;
            this.mocked = mocked;
        }

        @Override
        public JiraBoardContext getBoardContext(int startAt, int maxResults) {
            int total = allIssues.size();
            int from = Math.min(startAt, total);
            int to = Math.min(from + maxResults, total);
            List<JiraIssueDto> page = new ArrayList<>(allIssues.subList(from, to));
            return new JiraBoardContext(718L, 30532L, startAt, maxResults, total, page, Instant.now(), mocked);
        }
    }

    /** Records every page passed to {@link #upsertPage(List)}; treats every command as newly created. */
    private static final class RecordingPersistencePort implements IssuePersistencePort {

        private final List<List<IssueUpsertCommand>> pages = new ArrayList<>();

        @Override
        public IssueUpsertOutcome upsertPage(List<IssueUpsertCommand> commands) {
            pages.add(List.copyOf(commands));
            return new IssueUpsertOutcome(commands.size(), 0);
        }

        List<List<IssueUpsertCommand>> pages() {
            return pages;
        }
    }
}
