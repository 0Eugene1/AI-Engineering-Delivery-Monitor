package ru.eltc.deliverymonitor.domain.workstream;

/**
 * Input contract for upserting a {@code workstreams} row (owned by {@code domain.workstream}).
 *
 * <p>Matching key is {@code (issueKey, workstreamTypeCode)} — not {@code repositoryId}.
 *
 * @param issueKey             business anchor (ADR-001); required
 * @param workstreamTypeCode   Workstream Type code (ADR-002); required; FK to {@code workstream_types}
 * @param repositoryId         nullable Git provenance FK → {@code repositories.id}; null for non-Git
 * @param issueId              nullable FK → {@code issues.id}; left null until IssueEntity lookup exists
 * @param derivedStatus        derived status string (see {@link WorkstreamDerivedStatuses})
 */
public record WorkstreamUpsertCommand(
        String issueKey,
        String workstreamTypeCode,
        Long repositoryId,
        Long issueId,
        String derivedStatus
) {
}
