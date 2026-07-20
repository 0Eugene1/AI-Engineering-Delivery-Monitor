package ru.eltc.deliverymonitor.api.activity;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Binds {@code activity.feed.*} — default/max page size for {@code GET /api/activity}
 * (docs/api.md Phase 4.1).
 */
@Validated
@ConfigurationProperties(prefix = "activity.feed")
public class ActivityFeedProperties {

    /** Default {@code limit} when the query param is omitted. */
    @Min(1)
    private int defaultLimit = 50;

    /** Hard ceiling for {@code limit} (requested values are clamped to this). */
    @Min(1)
    private int maxLimit = 200;

    public int getDefaultLimit() {
        return defaultLimit;
    }

    public void setDefaultLimit(int defaultLimit) {
        this.defaultLimit = defaultLimit;
    }

    public int getMaxLimit() {
        return maxLimit;
    }

    public void setMaxLimit(int maxLimit) {
        this.maxLimit = maxLimit;
    }
}
