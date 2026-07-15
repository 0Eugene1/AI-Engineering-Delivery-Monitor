package ru.eltc.deliverymonitor.sync.jira;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

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

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }
}
