package ru.eltc.deliverymonitor.domain.repository;

/**
 * Input contract for upserting an observed GitLab repository into {@code repositories}.
 * Owned by {@code domain.repository}; callers (future {@code sync.gitlab}) map their own data
 * into this command — this package never imports sync/integration types.
 *
 * @param gitlabProjectId    immutable GitLab project id (matching key)
 * @param path               {@code path_with_namespace} (mutable)
 * @param name               display name (mutable)
 * @param workstreamTypeCode Workstream Type code (ADR-002); must exist in {@code workstream_types}
 */
public record RepositoryUpsertCommand(
        Long gitlabProjectId,
        String path,
        String name,
        String workstreamTypeCode
) {
}
