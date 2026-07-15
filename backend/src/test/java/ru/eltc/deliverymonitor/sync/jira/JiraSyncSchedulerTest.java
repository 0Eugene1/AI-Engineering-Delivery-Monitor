package ru.eltc.deliverymonitor.sync.jira;

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
 * Unit tests for {@link JiraSyncScheduler} — no Spring context, no real Jira, no database. {@link
 * JiraSyncService} is a Mockito mock: these tests only verify the scheduler's own responsibilities
 * (conditional task registration, delegating to {@code syncBoard()}, logging/error handling), not
 * {@code JiraSyncService}'s sync logic (already covered by {@link JiraSyncServiceTest}).
 */
class JiraSyncSchedulerTest {

    private static JiraSyncProperties properties(boolean enabled, Duration interval) {
        JiraSyncProperties properties = new JiraSyncProperties();
        properties.setEnabled(enabled);
        properties.setInterval(interval);
        return properties;
    }

    @Test
    void disabledSchedulerRegistersNoTask() {
        JiraSyncService jiraSyncService = mock(JiraSyncService.class);
        JiraSyncScheduler scheduler =
                new JiraSyncScheduler(jiraSyncService, properties(false, Duration.ofMinutes(5)));
        ScheduledTaskRegistrar registrar = mock(ScheduledTaskRegistrar.class);

        scheduler.configureTasks(registrar);

        verifyNoInteractions(registrar);
        verifyNoInteractions(jiraSyncService);
    }

    @Test
    void enabledSchedulerRegistersFixedDelayTaskWithConfiguredInterval() {
        JiraSyncService jiraSyncService = mock(JiraSyncService.class);
        Duration interval = Duration.ofMinutes(2);
        JiraSyncScheduler scheduler = new JiraSyncScheduler(jiraSyncService, properties(true, interval));
        ScheduledTaskRegistrar registrar = mock(ScheduledTaskRegistrar.class);

        scheduler.configureTasks(registrar);

        // fixedDelay, never fixedRate: the next run only starts after the previous one finishes.
        verify(registrar).addFixedDelayTask(any(Runnable.class), eq(interval));
        verify(registrar, never()).addFixedRateTask(any(Runnable.class), any(Duration.class));
    }

    @Test
    void scheduledRunCallsJiraSyncService() {
        JiraSyncService jiraSyncService = mock(JiraSyncService.class);
        Instant now = Instant.now();
        given(jiraSyncService.syncBoard()).willReturn(
                new JiraSyncResult(now, now, 3, 1, false, 2, 1, List.of()));
        JiraSyncScheduler scheduler =
                new JiraSyncScheduler(jiraSyncService, properties(true, Duration.ofMinutes(5)));

        scheduler.runScheduledSync();

        verify(jiraSyncService).syncBoard();
    }

    @Test
    void scheduledRunSwallowsExceptionSoFutureRunsAreNotBroken() {
        JiraSyncService jiraSyncService = mock(JiraSyncService.class);
        given(jiraSyncService.syncBoard()).willThrow(new RuntimeException("boom"));
        JiraSyncScheduler scheduler =
                new JiraSyncScheduler(jiraSyncService, properties(true, Duration.ofMinutes(5)));

        // Must not throw: a failed run must not prevent the next fixedDelay-scheduled run.
        scheduler.runScheduledSync();

        verify(jiraSyncService).syncBoard();
    }
}
