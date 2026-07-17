package ru.eltc.deliverymonitor.domain.repository;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RepositoryUpsertService} against a mocked {@link RepositoryJpaRepository}.
 * Covers create-vs-update matching by {@code gitlabProjectId} (not {@code path}/{@code name}).
 */
class RepositoryUpsertServiceTest {

    private final RepositoryJpaRepository jpaRepository = mock(RepositoryJpaRepository.class);
    private final RepositoryUpsertService service = new RepositoryUpsertService(jpaRepository);

    @Test
    void emptyListDoesNothing() {
        RepositoryUpsertOutcome outcome = service.upsertAll(List.of());

        assertThat(outcome).isEqualTo(RepositoryUpsertOutcome.empty());
        verifyNoMoreInteractions(jpaRepository);
    }

    @Test
    void createsNewRepositoryWhenNoExistingRowMatchesByGitlabProjectId() {
        when(jpaRepository.findAllByGitlabProjectIdIn(any())).thenReturn(List.of());
        RepositoryUpsertCommand command = command(2159L, "mptp/mptp8", "mptp8", "backend");

        RepositoryUpsertOutcome outcome = service.upsertAll(List.of(command));

        assertThat(outcome).isEqualTo(new RepositoryUpsertOutcome(1, 0));
        List<RepositoryEntity> saved = captureSavedEntities();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getGitlabProjectId()).isEqualTo(2159L);
        assertThat(saved.get(0).getPath()).isEqualTo("mptp/mptp8");
        assertThat(saved.get(0).getWorkstreamTypeCode()).isEqualTo("backend");
    }

    @Test
    void updatesExistingRepositoryMatchedByGitlabProjectIdEvenIfPathChanged() {
        RepositoryEntity existing = new RepositoryEntity(2159L);
        existing.applyUpsert(command(2159L, "mptp/old-name", "old-name", "backend"));
        when(jpaRepository.findAllByGitlabProjectIdIn(any())).thenReturn(List.of(existing));

        RepositoryUpsertCommand renamed = command(2159L, "mptp/mptp8", "mptp8", "backend");

        RepositoryUpsertOutcome outcome = service.upsertAll(List.of(renamed));

        assertThat(outcome).isEqualTo(new RepositoryUpsertOutcome(0, 1));
        assertThat(existing.getPath()).isEqualTo("mptp/mptp8");
        assertThat(existing.getName()).isEqualTo("mptp8");
        assertThat(existing.getGitlabProjectId()).isEqualTo(2159L);
    }

    @Test
    void aggregatesCreatedAndUpdatedAcrossAMixedBatch() {
        RepositoryEntity existing = new RepositoryEntity(760L);
        when(jpaRepository.findAllByGitlabProjectIdIn(any())).thenReturn(List.of(existing));

        RepositoryUpsertOutcome outcome = service.upsertAll(List.of(
                command(2159L, "mptp/mptp8", "mptp8", "backend"),
                command(760L, "mptp/mptp-react-native", "mptp-react-native", "frontend")));

        assertThat(outcome).isEqualTo(new RepositoryUpsertOutcome(1, 1));
        assertThat(outcome.saved()).isEqualTo(2);
    }

    @Test
    void findByGitlabProjectIdDelegatesToJpaRepository() {
        RepositoryEntity entity = new RepositoryEntity(2159L);
        when(jpaRepository.findByGitlabProjectId(2159L)).thenReturn(Optional.of(entity));

        assertThat(service.findByGitlabProjectId(2159L)).contains(entity);
    }

    private static RepositoryUpsertCommand command(
            Long gitlabProjectId, String path, String name, String workstreamTypeCode) {
        return new RepositoryUpsertCommand(gitlabProjectId, path, name, workstreamTypeCode);
    }

    @SuppressWarnings("unchecked")
    private List<RepositoryEntity> captureSavedEntities() {
        ArgumentCaptor<List<RepositoryEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(jpaRepository).saveAll(captor.capture());
        return captor.getValue();
    }
}
