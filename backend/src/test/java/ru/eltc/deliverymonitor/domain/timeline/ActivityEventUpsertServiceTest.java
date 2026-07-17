package ru.eltc.deliverymonitor.domain.timeline;

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
 * Unit tests for {@link ActivityEventUpsertService} — matching by {@code (source, sourceRef)}.
 */
class ActivityEventUpsertServiceTest {

    private final ActivityEventRepository repository = mock(ActivityEventRepository.class);
    private final ActivityEventUpsertService service = new ActivityEventUpsertService(repository);

    @Test
    void emptyListDoesNothing() {
        ActivityEventUpsertOutcome outcome = service.upsertAll(List.of());

        assertThat(outcome).isEqualTo(ActivityEventUpsertOutcome.empty());
        verifyNoMoreInteractions(repository);
    }

    @Test
    void createsNewEventWhenNoExistingRowMatches() {
        when(repository.findAllBySourceAndSourceRefIn(eq(ActivityEventTypes.SOURCE_GITLAB), any()))
                .thenReturn(List.of());
        ActivityEventUpsertCommand command = command("2159:abc", "MPTPSUPP-1", ActivityEventTypes.COMMIT);

        ActivityEventUpsertOutcome outcome = service.upsertAll(List.of(command));

        assertThat(outcome).isEqualTo(new ActivityEventUpsertOutcome(1, 0));
        List<ActivityEventEntity> saved = captureSaved();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getSource()).isEqualTo(ActivityEventTypes.SOURCE_GITLAB);
        assertThat(saved.get(0).getSourceRef()).isEqualTo("2159:abc");
        assertThat(saved.get(0).getIssueKey()).isEqualTo("MPTPSUPP-1");
        assertThat(saved.get(0).getType()).isEqualTo(ActivityEventTypes.COMMIT);
    }

    @Test
    void updatesExistingEventMatchedBySourceAndSourceRef() {
        ActivityEventEntity existing = new ActivityEventEntity(ActivityEventTypes.SOURCE_GITLAB, "2159:mr:88");
        existing.applyUpsert(command("2159:mr:88", "MPTPSUPP-1", ActivityEventTypes.MR_OPENED));
        when(repository.findAllBySourceAndSourceRefIn(eq(ActivityEventTypes.SOURCE_GITLAB), any()))
                .thenReturn(List.of(existing));

        ActivityEventUpsertCommand updated = command("2159:mr:88", "MPTPSUPP-1", ActivityEventTypes.MR_MERGED);
        ActivityEventUpsertOutcome outcome = service.upsertAll(List.of(updated));

        assertThat(outcome).isEqualTo(new ActivityEventUpsertOutcome(0, 1));
        assertThat(existing.getType()).isEqualTo(ActivityEventTypes.MR_MERGED);
    }

    @Test
    void orphanEventWithNullIssueKeyIsPersisted() {
        when(repository.findAllBySourceAndSourceRefIn(eq(ActivityEventTypes.SOURCE_GITLAB), any()))
                .thenReturn(List.of());

        ActivityEventUpsertOutcome outcome = service.upsertAll(List.of(
                command("2159:branch:orphan", null, ActivityEventTypes.BRANCH_CREATED)));

        assertThat(outcome.created()).isEqualTo(1);
        assertThat(captureSaved().get(0).getIssueKey()).isNull();
    }

    @Test
    void aggregatesCreatedAndUpdatedAcrossAMixedBatch() {
        ActivityEventEntity existing = new ActivityEventEntity(ActivityEventTypes.SOURCE_GITLAB, "2159:aaa");
        when(repository.findAllBySourceAndSourceRefIn(eq(ActivityEventTypes.SOURCE_GITLAB), any()))
                .thenReturn(List.of(existing));

        ActivityEventUpsertOutcome outcome = service.upsertAll(List.of(
                command("2159:bbb", "MPTPSUPP-2", ActivityEventTypes.COMMIT),
                command("2159:aaa", "MPTPSUPP-1", ActivityEventTypes.COMMIT)));

        assertThat(outcome).isEqualTo(new ActivityEventUpsertOutcome(1, 1));
        assertThat(outcome.saved()).isEqualTo(2);
    }

    private static ActivityEventUpsertCommand command(String sourceRef, String issueKey, String type) {
        return new ActivityEventUpsertCommand(
                Instant.parse("2026-07-10T12:00:00Z"),
                issueKey,
                "backend",
                "demo.dev",
                "Demo Dev",
                type,
                "{\"demo\":true}",
                ActivityEventTypes.SOURCE_GITLAB,
                sourceRef);
    }

    @SuppressWarnings("unchecked")
    private List<ActivityEventEntity> captureSaved() {
        ArgumentCaptor<List<ActivityEventEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());
        return captor.getValue();
    }
}
