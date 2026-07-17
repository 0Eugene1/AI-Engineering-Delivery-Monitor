package ru.eltc.deliverymonitor.domain.gitlab;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Default {@link CommitPersistencePort}: upserts by {@code (repositoryId, sha)}.
 *
 * <p>Database errors are not caught here: they propagate to the caller.
 */
@Service
public class CommitUpsertService implements CommitPersistencePort {

    private final CommitRepository repository;

    public CommitUpsertService(CommitRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public CommitUpsertOutcome upsertAll(List<CommitUpsertCommand> commands) {
        if (commands == null || commands.isEmpty()) {
            return CommitUpsertOutcome.empty();
        }

        Map<Long, List<CommitUpsertCommand>> byRepo = commands.stream()
                .collect(Collectors.groupingBy(CommitUpsertCommand::repositoryId));

        Instant syncedAt = Instant.now();
        int created = 0;
        int updated = 0;
        List<CommitEntity> toSave = new ArrayList<>(commands.size());

        for (Map.Entry<Long, List<CommitUpsertCommand>> entry : byRepo.entrySet()) {
            Long repositoryId = entry.getKey();
            List<CommitUpsertCommand> group = entry.getValue();
            List<String> shas = group.stream().map(CommitUpsertCommand::sha).toList();
            Map<String, CommitEntity> existingBySha = repository
                    .findAllByRepositoryIdAndShaIn(repositoryId, shas)
                    .stream()
                    .collect(Collectors.toMap(CommitEntity::getSha, Function.identity()));

            for (CommitUpsertCommand command : group) {
                CommitEntity entity = existingBySha.get(command.sha());
                if (entity == null) {
                    entity = new CommitEntity(command.repositoryId(), command.sha());
                    created++;
                } else {
                    updated++;
                }
                entity.applyUpsert(command, syncedAt);
                toSave.add(entity);
            }
        }

        repository.saveAll(toSave);
        return new CommitUpsertOutcome(created, updated);
    }
}
