package ru.eltc.deliverymonitor.domain.risk;

/**
 * Stable risk rule codes for Phase 4.2 (docs/architecture.md § Phase 4 Risks).
 */
public final class RiskCodes {

    public static final String STALE_ACTIVITY = "STALE_ACTIVITY";
    public static final String OPEN_MR_STALE = "OPEN_MR_STALE";
    public static final String NO_MR = "NO_MR";
    public static final String JIRA_ACTIVE_NO_GIT = "JIRA_ACTIVE_NO_GIT";

    private RiskCodes() {
    }
}
