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
 * Unit tests for {@link MergeRequestUpsertService} — matching by {@code (repositoryId, gitlabIid)}.
 */
class MergeRequestUpsertServiceTest {

    private final MergeRequestRepository repository = mock(MergeRequestRepository.class);
    private final MergeRequestUpsertService service = new MergeRequestUpsertService(repository);

    @Test
    void emptyListDoesNothing() {
        MergeRequestUpsertOutcome outcome = service.upsertAll(List.of());

        assertThat(outcome).isEqualTo(MergeRequestUpsertOutcome.empty());
        verifyNoMoreInteractions(repository);
    }

    @Test
    void createsNewMergeRequestWhenNoExistingRowMatches() {
        when(repository.findAllByRepositoryIdAndGitlabIidIn(eq(10L), any())).thenReturn(List.of());
        MergeRequestUpsertCommand command = command(10L, 42L);

        MergeRequestUpsertOutcome outcome = service.upsertAll(List.of(command));

        assertThat(outcome).isEqualTo(new MergeRequestUpsertOutcome(1, 0));
        List<MergeRequestEntity> saved = captureSaved();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getRepositoryId()).isEqualTo(10L);
        assertThat(saved.get(0).getGitlabIid()).isEqualTo(42L);
        assertThat(saved.get(0).getState()).isEqualTo("opened");
        assertThat(saved.get(0).getIssueKey()).isNull();
    }

    @Test
    void updatesExistingMergeRequestMatchedByRepositoryIdAndGitlabIid() {
        MergeRequestEntity existing = new MergeRequestEntity(10L, 42L);
        existing.applyUpsert(command(10L, 42L), Instant.parse("2026-01-01T00:00:00Z"));
        when(repository.findAllByRepositoryIdAndGitlabIidIn(eq(10L), any())).thenReturn(List.of(existing));

        MergeRequestUpsertCommand updated = new MergeRequestUpsertCommand(
                10L, 42L, 999L, null, "Merged title", "merged",
                "feature/X", "main", "alice", "Alice",
                Instant.parse("2026-07-01T00:00:00Z"), Instant.parse("2026-07-17T12:00:00Z"),
                Instant.parse("2026-07-17T12:00:00Z"), "https://git.example/mr/42");

        MergeRequestUpsertOutcome outcome = service.upsertAll(List.of(updated));

        assertThat(outcome).isEqualTo(new MergeRequestUpsertOutcome(0, 1));
        assertThat(existing.getState()).isEqualTo("merged");
        assertThat(existing.getMergedAt()).isEqualTo(Instant.parse("2026-07-17T12:00:00Z"));
        assertThat(existing.getGitlabId()).isEqualTo(999L);
    }

    @Test
    void aggregatesCreatedAndUpdatedAcrossAMixedBatch() {
        MergeRequestEntity existing = new MergeRequestEntity(10L, 1L);
        when(repository.findAllByRepositoryIdAndGitlabIidIn(eq(10L), any())).thenReturn(List.of(existing));

        MergeRequestUpsertOutcome outcome = service.upsertAll(List.of(
                command(10L, 2L),
                command(10L, 1L)));

        assertThat(outcome).isEqualTo(new MergeRequestUpsertOutcome(1, 1));
        assertThat(outcome.saved()).isEqualTo(2);
    }

    private static MergeRequestUpsertCommand command(Long repositoryId, Long gitlabIid) {
        return new MergeRequestUpsertCommand(
                repositoryId, gitlabIid, 100L + gitlabIid, null, "MR title", "opened",
                "feature/X", "main", "alice", "Alice",
                Instant.parse("2026-07-01T00:00:00Z"), Instant.parse("2026-07-10T00:00:00Z"),
                null, null);
    }

    @SuppressWarnings("unchecked")
    private List<MergeRequestEntity> captureSaved() {
        ArgumentCaptor<List<MergeRequestEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());
        return captor.getValue();
    }
}
