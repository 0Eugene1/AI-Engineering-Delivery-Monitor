package ru.eltc.deliverymonitor.domain.gitlab;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link BranchUpsertService} — matching by {@code (repositoryId, name)}.
 */
class BranchUpsertServiceTest {

    private final BranchRepository repository = mock(BranchRepository.class);
    private final BranchUpsertService service = new BranchUpsertService(repository);

    @Test
    void emptyListDoesNothing() {
        BranchUpsertOutcome outcome = service.upsertAll(List.of());

        assertThat(outcome).isEqualTo(BranchUpsertOutcome.empty());
        verifyNoMoreInteractions(repository);
    }

    @Test
    void createsNewBranchWhenNoExistingRowMatches() {
        when(repository.findAllByRepositoryIdAndNameIn(eq(10L), any())).thenReturn(List.of());
        BranchUpsertCommand command = command(10L, "feature/MPTPSUPP-1");

        BranchUpsertOutcome outcome = service.upsertAll(List.of(command));

        assertThat(outcome).isEqualTo(new BranchUpsertOutcome(1, 0));
        List<BranchEntity> saved = captureSaved();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getRepositoryId()).isEqualTo(10L);
        assertThat(saved.get(0).getName()).isEqualTo("feature/MPTPSUPP-1");
        assertThat(saved.get(0).getTipCommitSha()).isEqualTo("abc123");
        assertThat(saved.get(0).getIssueKey()).isNull();
    }

    @Test
    void updatesExistingBranchMatchedByRepositoryIdAndName() {
        BranchEntity existing = new BranchEntity(10L, "feature/X");
        existing.applyUpsert(command(10L, "feature/X"), Instant.parse("2026-01-01T00:00:00Z"));
        when(repository.findAllByRepositoryIdAndNameIn(eq(10L), any())).thenReturn(List.of(existing));

        BranchUpsertCommand updated = new BranchUpsertCommand(
                10L, "feature/X", null, "def456", Instant.parse("2026-07-17T10:00:00Z"),
                "Alice", "a@example.com", "https://git.example/branch");

        BranchUpsertOutcome outcome = service.upsertAll(List.of(updated));

        assertThat(outcome).isEqualTo(new BranchUpsertOutcome(0, 1));
        assertThat(existing.getTipCommitSha()).isEqualTo("def456");
        assertThat(existing.getAuthorName()).isEqualTo("Alice");
    }

    @Test
    void sameNameInDifferentRepositoriesAreIndependent() {
        when(repository.findAllByRepositoryIdAndNameIn(eq(10L), any())).thenReturn(List.of());
        when(repository.findAllByRepositoryIdAndNameIn(eq(20L), any())).thenReturn(List.of());

        BranchUpsertOutcome outcome = service.upsertAll(List.of(
                command(10L, "main"),
                command(20L, "main")));

        assertThat(outcome).isEqualTo(new BranchUpsertOutcome(2, 0));
        assertThat(captureSaved()).hasSize(2);
    }

    @Test
    void aggregatesCreatedAndUpdatedAcrossAMixedBatch() {
        BranchEntity existing = new BranchEntity(10L, "main");
        when(repository.findAllByRepositoryIdAndNameIn(eq(10L), any())).thenReturn(List.of(existing));

        BranchUpsertOutcome outcome = service.upsertAll(List.of(
                command(10L, "feature/new"),
                command(10L, "main")));

        assertThat(outcome).isEqualTo(new BranchUpsertOutcome(1, 1));
        assertThat(outcome.saved()).isEqualTo(2);
    }

    private static BranchUpsertCommand command(Long repositoryId, String name) {
        return new BranchUpsertCommand(
                repositoryId, name, null, "abc123", Instant.parse("2026-07-10T12:00:00Z"),
                null, null, null);
    }

    @SuppressWarnings("unchecked")
    private List<BranchEntity> captureSaved() {
        ArgumentCaptor<List<BranchEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());
        return captor.getValue();
    }
}
