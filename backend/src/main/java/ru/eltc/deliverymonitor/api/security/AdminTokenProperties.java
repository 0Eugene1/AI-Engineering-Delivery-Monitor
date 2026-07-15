package ru.eltc.deliverymonitor.api.security;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Binds {@code delivery-monitor.admin.token} — the static Bearer API token that gates {@code
 * /api/admin/**} (Phase 2.4, ADR-012). Sourced from the {@code DELIVERY_MONITOR_ADMIN_TOKEN}
 * environment variable (see {@code application.yml}); never a literal value in this repository.
 *
 * <p>{@link Validated} makes this fail-fast, the same pattern used for {@code JIRA_TOKEN} in
 * {@code JiraProperties.Auth}: a missing/blank token prevents the application context from
 * starting, instead of silently leaving {@code /api/admin/**} exposed at runtime.
 *
 * <p>This token is a separate secret from {@code JIRA_TOKEN} — it protects the <em>inbound</em>
 * direction (client → this admin API), while {@code JIRA_TOKEN} protects the <em>outbound</em>
 * direction (this app → Jira). The two must never be the same value (docs/security.md §5).
 */
@Validated
@ConfigurationProperties(prefix = "delivery-monitor.admin")
public class AdminTokenProperties {

    @NotBlank(message = "delivery-monitor.admin.token must not be blank (set DELIVERY_MONITOR_ADMIN_TOKEN)")
    private String token = "";

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }
}
