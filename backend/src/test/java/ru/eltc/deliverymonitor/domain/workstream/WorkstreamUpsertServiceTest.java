package ru.eltc.deliverymonitor.domain.workstream;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link WorkstreamUpsertService} — matching by {@code (issueKey, workstreamTypeCode)}.
 */
class WorkstreamUpsertServiceTest {

    private final WorkstreamRepository repository = mock(WorkstreamRepository.class);
    private final WorkstreamUpsertService service = new WorkstreamUpsertService(repository);

    @Test
    void emptyListDoesNothing() {
        WorkstreamUpsertOutcome outcome = service.upsertAll(List.of());

        assertThat(outcome).isEqualTo(WorkstreamUpsertOutcome.empty());
        verifyNoMoreInteractions(repository);
    }

    @Test
    void createsNewWorkstreamWhenNoExistingRowMatches() {
        when(repository.findAllByIssueKeyInAndWorkstreamTypeCodeIn(anyCollection(), anyCollection()))
                .thenReturn(List.of());
        WorkstreamUpsertCommand command = command(
                "MPTPSUPP-1", "backend", 10L, WorkstreamDerivedStatuses.IN_PROGRESS);

        WorkstreamUpsertOutcome outcome = service.upsertAll(List.of(command));

        assertThat(outcome).isEqualTo(new WorkstreamUpsertOutcome(1, 0));
        List<WorkstreamEntity> saved = captureSaved();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getIssueKey()).isEqualTo("MPTPSUPP-1");
        assertThat(saved.get(0).getWorkstreamTypeCode()).isEqualTo("backend");
        assertThat(saved.get(0).getRepositoryId()).isEqualTo(10L);
        assertThat(saved.get(0).getIssueId()).isNull();
        assertThat(saved.get(0).getDerivedStatus()).isEqualTo(WorkstreamDerivedStatuses.IN_PROGRESS);
    }

    @Test
    void updatesExistingMatchedByIssueKeyAndTypeWithoutDuplicating() {
        WorkstreamEntity existing = new WorkstreamEntity("MPTPSUPP-1", "backend");
        existing.applyUpsert(command(
                "MPTPSUPP-1", "backend", 10L, WorkstreamDerivedStatuses.IN_PROGRESS));
        when(repository.findAllByIssueKeyInAndWorkstreamTypeCodeIn(anyCollection(), anyCollection()))
                .thenReturn(List.of(existing));

        WorkstreamUpsertOutcome outcome = service.upsertAll(List.of(
                command("MPTPSUPP-1", "backend", 10L, WorkstreamDerivedStatuses.IN_REVIEW)));

        assertThat(outcome).isEqualTo(new WorkstreamUpsertOutcome(0, 1));
        assertThat(existing.getDerivedStatus()).isEqualTo(WorkstreamDerivedStatuses.IN_REVIEW);
    }

    @Test
    void doesNotDowngradeDerivedStatusOnWeakerSignal() {
        WorkstreamEntity existing = new WorkstreamEntity("MPTPSUPP-1", "backend");
        existing.applyUpsert(command(
                "MPTPSUPP-1", "backend", 10L, WorkstreamDerivedStatuses.MERGED));
        when(repository.findAllByIssueKeyInAndWorkstreamTypeCodeIn(anyCollection(), anyCollection()))
                .thenReturn(List.of(existing));

        service.upsertAll(List.of(
                command("MPTPSUPP-1", "backend", 10L, WorkstreamDerivedStatuses.IN_PROGRESS)));

        assertThat(existing.getDerivedStatus()).isEqualTo(WorkstreamDerivedStatuses.MERGED);
    }

    @Test
    void repositoryIdIsProvenanceNotIdentity_sameIssueTypeDifferentReposStillOneRow() {
        WorkstreamEntity existing = new WorkstreamEntity("MPTPSUPP-1", "backend");
        existing.applyUpsert(command(
                "MPTPSUPP-1", "backend", 10L, WorkstreamDerivedStatuses.IN_PROGRESS));
        when(repository.findAllByIssueKeyInAndWorkstreamTypeCodeIn(anyCollection(), anyCollection()))
                .thenReturn(List.of(existing));

        WorkstreamUpsertOutcome outcome = service.upsertAll(List.of(
                command("MPTPSUPP-1", "backend", 99L, WorkstreamDerivedStatuses.IN_PROGRESS)));

        assertThat(outcome).isEqualTo(new WorkstreamUpsertOutcome(0, 1));
        assertThat(existing.getRepositoryId()).isEqualTo(99L);
        verify(repository).saveAll(any());
    }

    @Test
    void collapsesDuplicateIdentityKeysInBatchKeepingHigherStatus() {
        when(repository.findAllByIssueKeyInAndWorkstreamTypeCodeIn(anyCollection(), anyCollection()))
                .thenReturn(List.of());

        WorkstreamUpsertOutcome outcome = service.upsertAll(List.of(
                command("MPTPSUPP-1", "backend", 10L, WorkstreamDerivedStatuses.IN_PROGRESS),
                command("MPTPSUPP-1", "backend", 10L, WorkstreamDerivedStatuses.MERGED)));

        assertThat(outcome).isEqualTo(new WorkstreamUpsertOutcome(1, 0));
        assertThat(captureSaved().get(0).getDerivedStatus()).isEqualTo(WorkstreamDerivedStatuses.MERGED);
    }

    @Test
    void allowsNullRepositoryIdForNonGitWorkstreams() {
        when(repository.findAllByIssueKeyInAndWorkstreamTypeCodeIn(anyCollection(), anyCollection()))
                .thenReturn(List.of());

        WorkstreamUpsertOutcome outcome = service.upsertAll(List.of(
                new WorkstreamUpsertCommand(
                        "MPTPSUPP-1",
                        "qa",
                        null,
                        null,
                        WorkstreamDerivedStatuses.IN_PROGRESS)));

        assertThat(outcome.created()).isEqualTo(1);
        assertThat(captureSaved().get(0).getRepositoryId()).isNull();
        assertThat(captureSaved().get(0).getWorkstreamTypeCode()).isEqualTo("qa");
    }

    @Test
    void aggregatesCreatedAndUpdatedAcrossAMixedBatch() {
        WorkstreamEntity existing = new WorkstreamEntity("MPTPSUPP-1", "backend");
        when(repository.findAllByIssueKeyInAndWorkstreamTypeCodeIn(anyCollection(), anyCollection()))
                .thenReturn(List.of(existing));

        WorkstreamUpsertOutcome outcome = service.upsertAll(List.of(
                command("MPTPSUPP-2", "frontend", 1L, WorkstreamDerivedStatuses.IN_PROGRESS),
                command("MPTPSUPP-1", "backend", 10L, WorkstreamDerivedStatuses.IN_REVIEW)));

        assertThat(outcome).isEqualTo(new WorkstreamUpsertOutcome(1, 1));
        assertThat(outcome.saved()).isEqualTo(2);
    }

    private static WorkstreamUpsertCommand command(
            String issueKey, String type, Long repositoryId, String status) {
        return new WorkstreamUpsertCommand(issueKey, type, repositoryId, null, status);
    }

    @SuppressWarnings("unchecked")
    private List<WorkstreamEntity> captureSaved() {
        ArgumentCaptor<List<WorkstreamEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());
        return captor.getValue();
    }
}
