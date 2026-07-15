package ru.eltc.deliverymonitor.sync.jira;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Background Jira sync trigger (docs/roadmap.md Phase 2.5 "Scheduler") — calls the exact same
 * {@link JiraSyncService#syncBoard()} that {@code api.admin.JiraSyncController} calls for the
 * manual {@code POST /api/admin/sync/jira} trigger. This class does not talk to {@code
 * JiraClient}/{@code integration.jira} directly and does not bypass {@link JiraSyncService} —
 * scheduling, logging and error handling are its only responsibilities.
 *
 * <p>Registers a single task via {@link SchedulingConfigurer} rather than the simpler {@code
 * @Scheduled(fixedDelayString = ...)} annotation, because the task must be registered
 * conditionally (only when {@code jira.sync.enabled=true}) and its delay is an env-driven {@link
 * Duration} (e.g. {@code JIRA_SYNC_INTERVAL=5m}), not a compile-time constant.
 *
 * <p><b>{@code fixedDelay}, deliberately not {@code fixedRate}:</b> {@link
 * ScheduledTaskRegistrar#addFixedDelayTask(Runnable, Duration)} starts counting the configured
 * interval only after the previous run has <em>finished</em>, so a slow or stuck sync can never
 * cause two runs to overlap. Overlap protection is layered twice on purpose: this scheduling
 * semantic prevents the scheduler from ever starting a second run concurrently with itself, and
 * {@link JiraSyncService}'s own in-process guard additionally protects against a scheduled run
 * overlapping a manual {@code POST /api/admin/sync/jira} run (or vice versa).
 *
 * <p><b>Deliberately not implemented</b> (docs/roadmap.md Phase 2.5 decision): a {@code
 * sync_state} table, a distributed lock (ShedLock/Redis), incremental sync, a retry framework —
 * every scheduled run is a full {@link JiraSyncService#syncBoard()} run, exactly like the manual
 * trigger.
 */
@Component
@EnableConfigurationProperties(JiraSyncProperties.class)
public class JiraSyncScheduler implements SchedulingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(JiraSyncScheduler.class);

    private final JiraSyncService jiraSyncService;
    private final JiraSyncProperties properties;

    public JiraSyncScheduler(JiraSyncService jiraSyncService, JiraSyncProperties properties) {
        this.jiraSyncService = jiraSyncService;
        this.properties = properties;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        if (!properties.isEnabled()) {
            log.info("Jira scheduled sync is disabled (jira.sync.enabled=false); "
                    + "no scheduled task registered, manual sync only");
            return;
        }
        Duration interval = properties.getInterval();
        log.info("Jira scheduled sync enabled: fixedDelay={} (next run starts only after the "
                + "previous one finishes)", interval);
        taskRegistrar.addFixedDelayTask(this::runScheduledSync, interval);
    }

    /**
     * Runs one scheduled sync and logs its outcome. Any exception — including one propagating out
     * of {@link JiraSyncService#syncBoard()} itself, e.g. a database failure that method does not
     * catch — is caught and logged here, never rethrown: a single failed run must not stop future
     * {@code fixedDelay} executions.
     */
    void runScheduledSync() {
        try {
            JiraSyncResult result = jiraSyncService.syncBoard();
            log.info("Scheduled Jira sync finished: fetched={} pages={} mocked={} created={} "
                    + "updated={} errors={}",
                    result.fetched(), result.pages(), result.mocked(),
                    result.created(), result.updated(), result.errors().size());
        } catch (Exception e) {
            log.error("Scheduled Jira sync failed unexpectedly; will retry on the next scheduled "
                    + "run in {}", properties.getInterval(), e);
        }
    }
}
