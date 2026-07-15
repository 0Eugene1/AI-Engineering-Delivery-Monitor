package ru.eltc.deliverymonitor.sync.jira;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Binds {@code jira.sync.*} — operational settings owned by the sync layer, kept separate from the
 * integration-layer {@code jira.*} client/auth config so the two layers stay decoupled.
 */
@Validated
@ConfigurationProperties(prefix = "jira.sync")
public class JiraSyncProperties {

    /** Page size used when paginating the board filter search. Operational setting, not hard-coded. */
    @Min(value = 1, message = "jira.sync.page-size must be >= 1")
    private int pageSize = 50;

    /**
     * Whether the {@code @Scheduled}-style background sync ({@link JiraSyncScheduler}, Phase 2.5)
     * is active. Defaults to {@code false} — manual sync ({@code POST /api/admin/sync/jira}) stays
     * the only way to trigger a sync unless this is explicitly enabled (docs/roadmap.md "manual
     * sync first").
     */
    private boolean enabled = false;

    /**
     * Delay between the end of one scheduled sync run and the start of the next ({@code
     * fixedDelay} semantics — never {@code fixedRate}: a slow/failed run must not cause overlapping
     * runs). Env-driven, e.g. {@code JIRA_SYNC_INTERVAL=5m}.
     */
    @NotNull
    private Duration interval = Duration.ofMinutes(5);

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
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
}
