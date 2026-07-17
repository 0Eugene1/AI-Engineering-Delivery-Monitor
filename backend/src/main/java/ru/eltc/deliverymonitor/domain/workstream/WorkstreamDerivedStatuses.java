package ru.eltc.deliverymonitor.domain.workstream;

/**
 * Minimal derived-status vocabulary for {@code workstreams.derived_status}
 * (docs/database.md § Derived workstream status). String constants (not a closed enum) so later
 * writers can add values without a Java enum migration.
 */
public final class WorkstreamDerivedStatuses {

    public static final String NOT_STARTED = "not_started";
    public static final String IN_PROGRESS = "in_progress";
    public static final String IN_REVIEW = "in_review";
    public static final String MERGED = "merged";

    private WorkstreamDerivedStatuses() {
    }

    /**
     * Rank for monotonic merge on upsert — higher wins (never downgrade on re-sync of a weaker
     * signal). Unknown statuses rank as {@code 0}.
     */
    public static int rank(String status) {
        if (status == null) {
            return 0;
        }
        return switch (status) {
            case MERGED -> 3;
            case IN_REVIEW -> 2;
            case IN_PROGRESS -> 1;
            case NOT_STARTED -> 0;
            default -> 0;
        };
    }

    /** Returns the status with the higher {@link #rank(String)}; null yields to the other side. */
    public static String max(String a, String b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return rank(a) >= rank(b) ? a : b;
    }
}
