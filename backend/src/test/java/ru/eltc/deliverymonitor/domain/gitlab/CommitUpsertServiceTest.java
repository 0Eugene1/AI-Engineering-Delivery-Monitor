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
 * Unit tests for {@link CommitUpsertService} — matching by {@code (repositoryId, sha)}.
 */
class CommitUpsertServiceTest {

    private final CommitRepository repository = mock(CommitRepository.class);
    private final CommitUpsertService service = new CommitUpsertService(repository);

    @Test
    void emptyListDoesNothing() {
        CommitUpsertOutcome outcome = service.upsertAll(List.of());

        assertThat(outcome).isEqualTo(CommitUpsertOutcome.empty());
        verifyNoMoreInteractions(repository);
    }

    @Test
    void createsNewCommitWhenNoExistingRowMatches() {
        when(repository.findAllByRepositoryIdAndShaIn(eq(10L), any())).thenReturn(List.of());
        CommitUpsertCommand command = command(10L, "sha-aaa");

        CommitUpsertOutcome outcome = service.upsertAll(List.of(command));

        assertThat(outcome).isEqualTo(new CommitUpsertOutcome(1, 0));
        List<CommitEntity> saved = captureSaved();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getRepositoryId()).isEqualTo(10L);
        assertThat(saved.get(0).getSha()).isEqualTo("sha-aaa");
        assertThat(saved.get(0).getMessage()).isEqualTo("fix bug");
        assertThat(saved.get(0).getIssueKey()).isNull();
    }

    @Test
    void updatesExistingCommitMatchedByRepositoryIdAndSha() {
        CommitEntity existing = new CommitEntity(10L, "sha-aaa");
        existing.applyUpsert(command(10L, "sha-aaa"), Instant.parse("2026-01-01T00:00:00Z"));
        when(repository.findAllByRepositoryIdAndShaIn(eq(10L), any())).thenReturn(List.of(existing));

        CommitUpsertCommand updated = new CommitUpsertCommand(
                10L, "sha-aaa", null, null, "short", "new title", "new message",
                "Bob", "b@example.com", Instant.parse("2026-07-17T11:00:00Z"), null);

        CommitUpsertOutcome outcome = service.upsertAll(List.of(updated));

        assertThat(outcome).isEqualTo(new CommitUpsertOutcome(0, 1));
        assertThat(existing.getTitle()).isEqualTo("new title");
        assertThat(existing.getAuthorName()).isEqualTo("Bob");
    }

    @Test
    void aggregatesCreatedAndUpdatedAcrossAMixedBatch() {
        CommitEntity existing = new CommitEntity(10L, "sha-old");
        when(repository.findAllByRepositoryIdAndShaIn(eq(10L), any())).thenReturn(List.of(existing));

        CommitUpsertOutcome outcome = service.upsertAll(List.of(
                command(10L, "sha-new"),
                command(10L, "sha-old")));

        assertThat(outcome).isEqualTo(new CommitUpsertOutcome(1, 1));
        assertThat(outcome.saved()).isEqualTo(2);
    }

    private static CommitUpsertCommand command(Long repositoryId, String sha) {
        return new CommitUpsertCommand(
                repositoryId, sha, null, null, "short", "title", "fix bug",
                "Alice", "a@example.com", Instant.parse("2026-07-10T12:00:00Z"), null);
    }

    @SuppressWarnings("unchecked")
    private List<CommitEntity> captureSaved() {
        ArgumentCaptor<List<CommitEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());
        return captor.getValue();
    }
}
