package ru.eltc.deliverymonitor.domain.gitlab;

import java.util.List;

/**
 * {@code domain.gitlab}'s persistence contract for {@code commits}.
 *
 * <p>Upsert matches existing rows by {@code (repositoryId, sha)}.
 */
public interface CommitPersistencePort {

    CommitUpsertOutcome upsertAll(List<CommitUpsertCommand> commands);
}
