package ru.eltc.deliverymonitor.domain.issue;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link IssueUpsertService} against a mocked {@link IssueRepository} — no
 * database, no Spring context. Covers create-vs-update matching by {@code jiraId} (not {@code
 * key}), the created/updated counts and fixVersions/labels replacement.
 */
class IssueUpsertServiceTest {

    private final IssueRepository repository = mock(IssueRepository.class);
    private final IssueUpsertService service = new IssueUpsertService(repository);

    @Test
    void emptyPageDoesNothing() {
        IssueUpsertOutcome outcome = service.upsertPage(List.of());

        assertThat(outcome).isEqualTo(IssueUpsertOutcome.empty());
        verifyNoMoreInteractions(repository);
    }

    @Test
    void createsNewIssueWhenNoExistingRowMatchesByJiraId() {
        when(repository.findAllByJiraIdIn(any())).thenReturn(List.of());
        IssueUpsertCommand command = command("10001", "MPTPSUPP-1");

        IssueUpsertOutcome outcome = service.upsertPage(List.of(command));

        assertThat(outcome).isEqualTo(new IssueUpsertOutcome(1, 0));
        List<IssueEntity> saved = captureSavedEntities();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getJiraId()).isEqualTo("10001");
        assertThat(saved.get(0).getKey()).isEqualTo("MPTPSUPP-1");
    }

    @Test
    void updatesExistingIssueMatchedByJiraIdEvenIfKeyChanged() {
        IssueEntity existing = new IssueEntity("10001");
        existing.applyUpsert(command("10001", "OLDPROJ-1"), Instant.parse("2026-01-01T00:00:00Z"));
        when(repository.findAllByJiraIdIn(any())).thenReturn(List.of(existing));

        // Same jiraId, but the key moved to a different Jira project — matching must still hit.
        IssueUpsertCommand movedCommand = command("10001", "NEWPROJ-99");

        IssueUpsertOutcome outcome = service.upsertPage(List.of(movedCommand));

        assertThat(outcome).isEqualTo(new IssueUpsertOutcome(0, 1));
        assertThat(existing.getKey()).isEqualTo("NEWPROJ-99");
    }

    @Test
    void replacesFixVersionsAndLabelsInPlaceOnUpdate() {
        IssueEntity existing = new IssueEntity("10001");
        existing.applyUpsert(
                new IssueUpsertCommand("10001", "MPTPSUPP-1", "s", null, null, null, null, null,
                        List.of("1.0.0"), List.of("old-label"), null, null),
                Instant.now());
        when(repository.findAllByJiraIdIn(any())).thenReturn(List.of(existing));

        IssueUpsertCommand updated = new IssueUpsertCommand("10001", "MPTPSUPP-1", "s", null, null,
                null, null, null, List.of("2.0.0"), List.of("new-label"), null, null);

        service.upsertPage(List.of(updated));

        assertThat(existing.getFixVersions()).containsExactly("2.0.0");
        assertThat(existing.getLabels()).containsExactly("new-label");
    }

    @Test
    void stampsSyncedAtOnEveryUpsertedRow() {
        when(repository.findAllByJiraIdIn(any())).thenReturn(List.of());
        Instant before = Instant.now();

        service.upsertPage(List.of(command("10001", "MPTPSUPP-1")));

        List<IssueEntity> saved = captureSavedEntities();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getSyncedAt()).isNotNull().isAfterOrEqualTo(before);
    }

    @Test
    void aggregatesCreatedAndUpdatedAcrossAMixedPage() {
        IssueEntity existing = new IssueEntity("10002");
        when(repository.findAllByJiraIdIn(any())).thenReturn(List.of(existing));

        IssueUpsertCommand toCreate = command("10001", "MPTPSUPP-1");
        IssueUpsertCommand toUpdate = command("10002", "MPTPSUPP-2");

        IssueUpsertOutcome outcome = service.upsertPage(List.of(toCreate, toUpdate));

        assertThat(outcome).isEqualTo(new IssueUpsertOutcome(1, 1));
    }

    private static IssueUpsertCommand command(String jiraId, String key) {
        return new IssueUpsertCommand(jiraId, key, "summary", "In Progress", "In Progress",
                "j.doe", "John Doe", "Bug", List.of("1.0.0"), List.of("backend"),
                Instant.parse("2026-07-01T09:00:00Z"), Instant.parse("2026-07-10T12:30:00Z"));
    }

    @SuppressWarnings("unchecked")
    private List<IssueEntity> captureSavedEntities() {
        ArgumentCaptor<List<IssueEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());
        return captor.getValue();
    }
}
