package ru.eltc.deliverymonitor.sync.gitlab;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Background GitLab reconcile trigger (docs/roadmap.md Phase 3.9) — calls the exact same
 * {@link GitLabSyncService#syncAll()} that {@code api.admin.GitLabSyncController} calls for the
 * manual {@code POST /api/admin/sync/gitlab} trigger. This class does not talk to {@code
 * GitLabClient}/{@code integration.gitlab} directly and does not bypass {@link GitLabSyncService}
 * — scheduling, logging and error handling are its only responsibilities.
 *
 * <p>Registers a single task via {@link SchedulingConfigurer} rather than the simpler {@code
 * @Scheduled(fixedDelayString = ...)} annotation, because the task must be registered
 * conditionally (only when {@code gitlab.sync.enabled=true}) and its delay is an env-driven
 * {@link Duration} (e.g. {@code GITLAB_SYNC_INTERVAL=10m}), not a compile-time constant.
 *
 * <p><b>{@code fixedDelay}, deliberately not {@code fixedRate}:</b> {@link
 * ScheduledTaskRegistrar#addFixedDelayTask(Runnable, Duration)} starts counting the configured
 * interval only after the previous run has <em>finished</em>, so a slow or stuck sync can never
 * cause two runs to overlap. Overlap protection is layered twice on purpose: this scheduling
 * semantic prevents the scheduler from ever starting a second run concurrently with itself, and
 * {@link GitLabSyncService}'s own in-process guard additionally protects against a scheduled run
 * overlapping a manual {@code POST /api/admin/sync/gitlab} run (or vice versa).
 *
 * <p><b>Deliberately not implemented</b> (docs/roadmap.md Phase 3.9 decision): a {@code
 * sync_state} table, a distributed lock (ShedLock/Redis), incremental sync, a retry framework,
 * webhooks — every scheduled run is a full {@link GitLabSyncService#syncAll()} run, exactly like
 * the manual trigger.
 */
@Component
@EnableConfigurationProperties(GitLabSyncProperties.class)
public class GitLabSyncScheduler implements SchedulingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(GitLabSyncScheduler.class);

    private final GitLabSyncService gitLabSyncService;
    private final GitLabSyncProperties properties;

    public GitLabSyncScheduler(GitLabSyncService gitLabSyncService, GitLabSyncProperties properties) {
        this.gitLabSyncService = gitLabSyncService;
        this.properties = properties;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        if (!properties.isEnabled()) {
            log.info("GitLab scheduled sync is disabled (gitlab.sync.enabled=false); "
                    + "no scheduled task registered, manual sync only");
            return;
        }
        Duration interval = properties.getInterval();
        log.info("GitLab scheduled sync enabled: fixedDelay={} (next run starts only after the "
                + "previous one finishes)", interval);
        taskRegistrar.addFixedDelayTask(this::runScheduledSync, interval);
    }

    /**
     * Runs one scheduled sync and logs its outcome. Any exception — including one propagating out
     * of {@link GitLabSyncService#syncAll()} itself, e.g. a database failure that method does not
     * catch — is caught and logged here, never rethrown: a single failed run must not stop future
     * {@code fixedDelay} executions.
     */
    void runScheduledSync() {
        try {
            GitLabSyncResult result = gitLabSyncService.syncAll();
            log.info("Scheduled GitLab sync finished: projects={} fetched={} pages={} mocked={} "
                    + "created={} updated={} errors={}",
                    result.projectsSynced(), result.fetched(), result.pages(), result.mocked(),
                    result.created(), result.updated(), result.errors().size());
        } catch (Exception e) {
            log.error("Scheduled GitLab sync failed unexpectedly; will retry on the next scheduled "
                    + "run in {}", properties.getInterval(), e);
        }
    }
}
