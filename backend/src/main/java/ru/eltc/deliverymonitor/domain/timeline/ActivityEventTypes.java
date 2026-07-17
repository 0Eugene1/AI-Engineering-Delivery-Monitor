package ru.eltc.deliverymonitor.domain.timeline;

/**
 * Stable {@code activity_events.type} values written in Phase 3.5 (GitLab). Additional types
 * (Jira / Jenkins) arrive in later phases — keep this class as string constants rather than a
 * closed enum so new writers do not force a schema migration.
 */
public final class ActivityEventTypes {

    public static final String BRANCH_CREATED = "BRANCH_CREATED";
    public static final String COMMIT = "COMMIT";
    public static final String MR_OPENED = "MR_OPENED";
    public static final String MR_APPROVED = "MR_APPROVED";
    public static final String MR_MERGED = "MR_MERGED";

    /** {@code activity_events.source} for GitLab-originated events. */
    public static final String SOURCE_GITLAB = "GITLAB";

    private ActivityEventTypes() {
    }
}
