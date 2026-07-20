package ru.eltc.deliverymonitor.api.risk;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * External contract for {@code GET /api/risks} (docs/api.md Phase 4.2).
 *
 * <p>Always returned with HTTP {@code 200}, including when {@code risks} is empty.
 *
 * @param risks evaluated risks (order not significant; callers may filter via query params)
 */
public record RisksResponse(List<RiskItem> risks) {

    /**
     * One evaluated risk.
     *
     * @param code           rule code ({@code STALE_ACTIVITY}, …)
     * @param severity       {@code LOW} | {@code MEDIUM} | {@code HIGH}
     * @param issueKey       Jira issue key
     * @param workstreamType type code + display name, or {@code null}
     * @param explanation    short human-readable reason
     * @param detectedAt     evaluation timestamp (UTC)
     * @param evidence       structured supporting facts
     */
    public record RiskItem(
            String code,
            String severity,
            String issueKey,
            WorkstreamTypeRef workstreamType,
            String explanation,
            Instant detectedAt,
            Map<String, Object> evidence
    ) {
    }

    /**
     * Workstream Type pill — {@code code} + {@code displayName} from the dictionary.
     */
    public record WorkstreamTypeRef(String code, String displayName) {
    }
}
