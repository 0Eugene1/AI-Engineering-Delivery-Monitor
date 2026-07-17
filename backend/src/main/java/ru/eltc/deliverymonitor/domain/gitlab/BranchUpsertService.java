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
 * Default {@link BranchPersistencePort}: upserts by {@code (repositoryId, name)}.
 *
 * <p>Database errors are not caught here: they propagate to the caller.
 */
@Service
public class BranchUpsertService implements BranchPersistencePort {

    private final BranchRepository repository;

    public BranchUpsertService(BranchRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public BranchUpsertOutcome upsertAll(List<BranchUpsertCommand> commands) {
        if (commands == null || commands.isEmpty()) {
            return BranchUpsertOutcome.empty();
        }

        Map<Long, List<BranchUpsertCommand>> byRepo = commands.stream()
                .collect(Collectors.groupingBy(BranchUpsertCommand::repositoryId));

        Instant syncedAt = Instant.now();
        int created = 0;
        int updated = 0;
        List<BranchEntity> toSave = new ArrayList<>(commands.size());

        for (Map.Entry<Long, List<BranchUpsertCommand>> entry : byRepo.entrySet()) {
            Long repositoryId = entry.getKey();
            List<BranchUpsertCommand> group = entry.getValue();
            List<String> names = group.stream().map(BranchUpsertCommand::name).toList();
            Map<String, BranchEntity> existingByName = repository
                    .findAllByRepositoryIdAndNameIn(repositoryId, names)
                    .stream()
                    .collect(Collectors.toMap(BranchEntity::getName, Function.identity()));

            for (BranchUpsertCommand command : group) {
                BranchEntity entity = existingByName.get(command.name());
                if (entity == null) {
                    entity = new BranchEntity(command.repositoryId(), command.name());
                    created++;
                } else {
                    updated++;
                }
                entity.applyUpsert(command, syncedAt);
                toSave.add(entity);
            }
        }

        repository.saveAll(toSave);
        return new BranchUpsertOutcome(created, updated);
    }
}
