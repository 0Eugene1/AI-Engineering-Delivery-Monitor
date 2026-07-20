package ru.eltc.deliverymonitor.domain.risk;

import java.time.Instant;
import java.util.Map;

/**
 * Logical risk produced by evaluate-on-read rules. Not a JPA entity — never persisted
 * (no {@code risk_flags} in Phase 4).
 *
 * @param code               rule code ({@link RiskCodes})
 * @param severity           {@link RiskSeverities}
 * @param issueKey           Jira issue key (always present for Phase 4 rules)
 * @param workstreamTypeCode Workstream Type code, or {@code null} (e.g. {@code JIRA_ACTIVE_NO_GIT})
 * @param explanation        short human-readable reason
 * @param detectedAt         evaluation timestamp (read time)
 * @param evidence           structured facts supporting the risk (JSON-serializable map)
 */
public record Risk(
        String code,
        String severity,
        String issueKey,
        String workstreamTypeCode,
        String explanation,
        Instant detectedAt,
        Map<String, Object> evidence
) {
}
