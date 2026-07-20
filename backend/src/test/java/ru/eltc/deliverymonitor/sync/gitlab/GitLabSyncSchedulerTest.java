package ru.eltc.deliverymonitor.sync.gitlab;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Unit tests for {@link GitLabSyncScheduler} — no Spring context, no real GitLab, no database.
 * {@link GitLabSyncService} is a Mockito mock: these tests only verify the scheduler's own
 * responsibilities (conditional task registration, delegating to {@code syncAll()},
 * logging/error handling), not {@code GitLabSyncService}'s sync logic (already covered by
 * {@link GitLabSyncServiceTest}).
 */
class GitLabSyncSchedulerTest {

    private static GitLabSyncProperties properties(boolean enabled, Duration interval) {
        GitLabSyncProperties properties = new GitLabSyncProperties();
        properties.setEnabled(enabled);
        properties.setInterval(interval);
        return properties;
    }

    @Test
    void disabledSchedulerRegistersNoTask() {
        GitLabSyncService gitLabSyncService = mock(GitLabSyncService.class);
        GitLabSyncScheduler scheduler =
                new GitLabSyncScheduler(gitLabSyncService, properties(false, Duration.ofMinutes(10)));
        ScheduledTaskRegistrar registrar = mock(ScheduledTaskRegistrar.class);

        scheduler.configureTasks(registrar);

        verifyNoInteractions(registrar);
        verifyNoInteractions(gitLabSyncService);
    }

    @Test
    void enabledSchedulerRegistersFixedDelayTaskWithConfiguredInterval() {
        GitLabSyncService gitLabSyncService = mock(GitLabSyncService.class);
        Duration interval = Duration.ofMinutes(2);
        GitLabSyncScheduler scheduler =
                new GitLabSyncScheduler(gitLabSyncService, properties(true, interval));
        ScheduledTaskRegistrar registrar = mock(ScheduledTaskRegistrar.class);

        scheduler.configureTasks(registrar);

        // fixedDelay, never fixedRate: the next run only starts after the previous one finishes.
        verify(registrar).addFixedDelayTask(any(Runnable.class), eq(interval));
        verify(registrar, never()).addFixedRateTask(any(Runnable.class), any(Duration.class));
    }

    @Test
    void scheduledRunCallsGitLabSyncService() {
        GitLabSyncService gitLabSyncService = mock(GitLabSyncService.class);
        Instant now = Instant.now();
        given(gitLabSyncService.syncAll()).willReturn(
                new GitLabSyncResult(now, now, 1, 2, 3, 1, 3, false, 4, 0, List.of()));
        GitLabSyncScheduler scheduler =
                new GitLabSyncScheduler(gitLabSyncService, properties(true, Duration.ofMinutes(10)));

        scheduler.runScheduledSync();

        verify(gitLabSyncService).syncAll();
    }

    @Test
    void scheduledRunSwallowsExceptionSoFutureRunsAreNotBroken() {
        GitLabSyncService gitLabSyncService = mock(GitLabSyncService.class);
        given(gitLabSyncService.syncAll()).willThrow(new RuntimeException("boom"));
        GitLabSyncScheduler scheduler =
                new GitLabSyncScheduler(gitLabSyncService, properties(true, Duration.ofMinutes(10)));

        // Must not throw: a failed run must not prevent the next fixedDelay-scheduled run.
        scheduler.runScheduledSync();

        verify(gitLabSyncService).syncAll();
    }
}
