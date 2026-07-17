package ru.eltc.deliverymonitor.domain.gitlab;

import java.util.List;

/**
 * {@code domain.gitlab}'s persistence contract for {@code merge_requests}.
 *
 * <p>Upsert matches existing rows by {@code (repositoryId, gitlabIid)}.
 */
public interface MergeRequestPersistencePort {

    MergeRequestUpsertOutcome upsertAll(List<MergeRequestUpsertCommand> commands);
}
