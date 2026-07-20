package ru.eltc.deliverymonitor.sync.gitlab;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Binds {@code gitlab.sync.*} — operational settings owned by the sync layer, kept separate from
 * the integration-layer {@code gitlab.*} client/auth config so the two layers stay decoupled.
 *
 * <p>{@code page-size} and {@code commit-history-days} are always used. {@code repositories} is
 * <b>only</b> for {@code gitlab.mode=mock} / local / tests (Phase 3.4.1 Single SoT): production
 * ({@code rest}) reads observed projects exclusively from PostgreSQL via
 * {@code RepositoryPersistencePort} and ignores this list.
 */
@Validated
@ConfigurationProperties(prefix = "gitlab.sync")
public class GitLabSyncProperties {

    /** Page size ({@code per_page}) when paginating GitLab list endpoints. */
    @Min(value = 1, message = "gitlab.sync.page-size must be >= 1")
    private int pageSize = 50;

    /**
     * How many days of commit history to fetch. Mapped to GitLab API {@code since}
     * ({@code Instant.now().minus(commitHistoryDays, DAYS)}). Full historical import is out of
     * Phase 3 (docs/architecture.md Commit History Policy).
     */
    @Min(value = 1, message = "gitlab.sync.commit-history-days must be >= 1")
    private int commitHistoryDays = 30;

    /**
     * Whether the background reconcile ({@link GitLabSyncScheduler}, Phase 3.9) is active.
     * Defaults to {@code false} — manual sync ({@code POST /api/admin/sync/gitlab}) stays the only
     * way to trigger a sync unless this is explicitly enabled (docs/roadmap.md "manual sync
     * first").
     */
    private boolean enabled = false;

    /**
     * Delay between the end of one scheduled sync run and the start of the next ({@code
     * fixedDelay} semantics — never {@code fixedRate}: a slow/failed run must not cause overlapping
     * runs). Env-driven, e.g. {@code GITLAB_SYNC_INTERVAL=10m}.
     */
    @NotNull
    private Duration interval = Duration.ofMinutes(10);

    /**
     * Mock / local / test filter of GitLab projects to sync when {@code gitlab.mode=mock}.
     * Ignored in {@code rest} (production) mode. Each entry must have {@code gitlab-id} that
     * exists in seeded {@code repositories}. Empty list → mock {@code syncAll()} fetches nothing.
     */
    @Valid
    @NestedConfigurationProperty
    private List<Repository> repositories = new ArrayList<>();

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public int getCommitHistoryDays() {
        return commitHistoryDays;
    }

    public void setCommitHistoryDays(int commitHistoryDays) {
        this.commitHistoryDays = commitHistoryDays;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Duration getInterval() {
        return interval;
    }

    public void setInterval(Duration interval) {
        this.interval = interval;
    }

    public List<Repository> getRepositories() {
        return repositories;
    }

    public void setRepositories(List<Repository> repositories) {
        this.repositories = repositories != null ? repositories : new ArrayList<>();
    }

    /**
     * One configured GitLab project to sync. Identified by numeric {@code gitlabId} and/or
     * {@code path}; {@code workstreamTypeCode} is carried into snapshots for later workstream
     * upsert (Phase 3.6) — not inferred from platform names in code (ADR-002).
     */
    public static class Repository {

        /** GitLab project id, e.g. {@code 2159}. Preferred client lookup key when set. */
        private Long gitlabId;

        /** Path with namespace, e.g. {@code mptp/mptp8}. Used when {@code gitlabId} is absent. */
        private String path;

        /** Seed Workstream Type code from config, e.g. {@code backend}. */
        @NotBlank(message = "gitlab.sync.repositories[].workstream-type-code must not be blank")
        private String workstreamTypeCode;

        public Long getGitlabId() {
            return gitlabId;
        }

        public void setGitlabId(Long gitlabId) {
            this.gitlabId = gitlabId;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getWorkstreamTypeCode() {
            return workstreamTypeCode;
        }

        public void setWorkstreamTypeCode(String workstreamTypeCode) {
            this.workstreamTypeCode = workstreamTypeCode;
        }

        /** Client {@code projectIdOrPath} argument: prefer numeric id, else path. */
        public String projectIdOrPath() {
            if (gitlabId != null) {
                return Long.toString(gitlabId);
            }
            return path;
        }

        @AssertTrue(message = "gitlab.sync.repositories[] must set gitlab-id and/or path")
        private boolean isIdentityPresent() {
            return gitlabId != null || (path != null && !path.isBlank());
        }
    }
}
