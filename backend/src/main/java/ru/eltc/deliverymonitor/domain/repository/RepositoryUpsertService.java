package ru.eltc.deliverymonitor.domain.repository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Default {@link RepositoryPersistencePort}: upserts by {@code gitlabProjectId} (never
 * {@code path}/{@code name} — see {@link RepositoryEntity}).
 *
 * <p>Database errors are not caught here: they propagate to the caller.
 */
@Service
public class RepositoryUpsertService implements RepositoryPersistencePort {

    private final RepositoryJpaRepository repository;

    public RepositoryUpsertService(RepositoryJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public RepositoryUpsertOutcome upsertAll(List<RepositoryUpsertCommand> commands) {
        if (commands == null || commands.isEmpty()) {
            return RepositoryUpsertOutcome.empty();
        }

        List<Long> gitlabIds = commands.stream().map(RepositoryUpsertCommand::gitlabProjectId).toList();
        Map<Long, RepositoryEntity> existingByGitlabId = repository.findAllByGitlabProjectIdIn(gitlabIds)
                .stream()
                .collect(Collectors.toMap(RepositoryEntity::getGitlabProjectId, Function.identity()));

        int created = 0;
        int updated = 0;
        List<RepositoryEntity> toSave = new ArrayList<>(commands.size());

        for (RepositoryUpsertCommand command : commands) {
            RepositoryEntity entity = existingByGitlabId.get(command.gitlabProjectId());
            if (entity == null) {
                entity = new RepositoryEntity(command.gitlabProjectId());
                created++;
            } else {
                updated++;
            }
            entity.applyUpsert(command);
            toSave.add(entity);
        }

        repository.saveAll(toSave);
        return new RepositoryUpsertOutcome(created, updated);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RepositoryEntity> findByGitlabProjectId(long gitlabProjectId) {
        return repository.findByGitlabProjectId(gitlabProjectId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RepositoryEntity> findAllOrdered() {
        return repository.findAllByOrderByIdAsc();
    }
}
