package ru.eltc.deliverymonitor.domain.risk;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Binds {@code risk.*} thresholds and list limits for {@code GET /api/risks}
 * (docs/api.md Phase 4.2).
 */
@Validated
@ConfigurationProperties(prefix = "risk")
public class RiskProperties {

    /** Days without activity before {@link RiskCodes#STALE_ACTIVITY} fires. */
    @Min(1)
    private int staleActivityDays = 3;

    /** Days an open MR may age before {@link RiskCodes#OPEN_MR_STALE} fires. */
    @Min(1)
    private int openMrStaleDays = 5;

    /** Default {@code limit} when the query param is omitted. */
    @Min(1)
    private int defaultLimit = 100;

    /** Hard ceiling for {@code limit}. */
    @Min(1)
    private int maxLimit = 200;

    public int getStaleActivityDays() {
        return staleActivityDays;
    }

    public void setStaleActivityDays(int staleActivityDays) {
        this.staleActivityDays = staleActivityDays;
    }

    public int getOpenMrStaleDays() {
        return openMrStaleDays;
    }

    public void setOpenMrStaleDays(int openMrStaleDays) {
        this.openMrStaleDays = openMrStaleDays;
    }

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
