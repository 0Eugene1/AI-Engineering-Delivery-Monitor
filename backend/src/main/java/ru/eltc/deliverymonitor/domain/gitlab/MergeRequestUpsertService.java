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
 * Default {@link MergeRequestPersistencePort}: upserts by {@code (repositoryId, gitlabIid)}.
 *
 * <p>Database errors are not caught here: they propagate to the caller.
 */
@Service
public class MergeRequestUpsertService implements MergeRequestPersistencePort {

    private final MergeRequestRepository repository;

    public MergeRequestUpsertService(MergeRequestRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public MergeRequestUpsertOutcome upsertAll(List<MergeRequestUpsertCommand> commands) {
        if (commands == null || commands.isEmpty()) {
            return MergeRequestUpsertOutcome.empty();
        }

        Map<Long, List<MergeRequestUpsertCommand>> byRepo = commands.stream()
                .collect(Collectors.groupingBy(MergeRequestUpsertCommand::repositoryId));

        Instant syncedAt = Instant.now();
        int created = 0;
        int updated = 0;
        List<MergeRequestEntity> toSave = new ArrayList<>(commands.size());

        for (Map.Entry<Long, List<MergeRequestUpsertCommand>> entry : byRepo.entrySet()) {
            Long repositoryId = entry.getKey();
            List<MergeRequestUpsertCommand> group = entry.getValue();
            List<Long> iids = group.stream().map(MergeRequestUpsertCommand::gitlabIid).toList();
            Map<Long, MergeRequestEntity> existingByIid = repository
                    .findAllByRepositoryIdAndGitlabIidIn(repositoryId, iids)
                    .stream()
                    .collect(Collectors.toMap(MergeRequestEntity::getGitlabIid, Function.identity()));

            for (MergeRequestUpsertCommand command : group) {
                MergeRequestEntity entity = existingByIid.get(command.gitlabIid());
                if (entity == null) {
                    entity = new MergeRequestEntity(command.repositoryId(), command.gitlabIid());
                    created++;
                } else {
                    updated++;
                }
                entity.applyUpsert(command, syncedAt);
                toSave.add(entity);
            }
        }

        repository.saveAll(toSave);
        return new MergeRequestUpsertOutcome(created, updated);
    }
}
