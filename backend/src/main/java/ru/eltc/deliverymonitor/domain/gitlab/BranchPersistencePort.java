package ru.eltc.deliverymonitor.domain.gitlab;

import java.util.List;

/**
 * {@code domain.gitlab}'s persistence contract for {@code branches}. Callers (future
 * {@code sync.gitlab}) depend on this interface; this package does not import upper layers.
 *
 * <p>Upsert matches existing rows by {@code (repositoryId, name)}.
 */
public interface BranchPersistencePort {

    BranchUpsertOutcome upsertAll(List<BranchUpsertCommand> commands);
}
