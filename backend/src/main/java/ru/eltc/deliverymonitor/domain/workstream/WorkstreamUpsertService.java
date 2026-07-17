package ru.eltc.deliverymonitor.domain.workstream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Default {@link WorkstreamPersistencePort}: upserts by {@code (issueKey, workstreamTypeCode)}.
 *
 * <p>Database errors are not caught here: they propagate to the caller.
 */
@Service
public class WorkstreamUpsertService implements WorkstreamPersistencePort {

    private final WorkstreamRepository repository;

    public WorkstreamUpsertService(WorkstreamRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public WorkstreamUpsertOutcome upsertAll(List<WorkstreamUpsertCommand> commands) {
        if (commands == null || commands.isEmpty()) {
            return WorkstreamUpsertOutcome.empty();
        }

        // Collapse duplicate identity keys within the batch (keep highest derived status).
        Map<String, WorkstreamUpsertCommand> collapsed = commands.stream()
                .collect(Collectors.toMap(
                        WorkstreamUpsertService::identityKey,
                        Function.identity(),
                        WorkstreamUpsertService::mergeCommands));

        List<WorkstreamUpsertCommand> unique = List.copyOf(collapsed.values());
        Set<String> issueKeys = new HashSet<>();
        Set<String> typeCodes = new HashSet<>();
        for (WorkstreamUpsertCommand command : unique) {
            Objects.requireNonNull(command.issueKey(), "issueKey must not be null");
            Objects.requireNonNull(command.workstreamTypeCode(), "workstreamTypeCode must not be null");
            Objects.requireNonNull(command.derivedStatus(), "derivedStatus must not be null");
            issueKeys.add(command.issueKey());
            typeCodes.add(command.workstreamTypeCode());
        }

        Map<String, WorkstreamEntity> existingByIdentity = repository
                .findAllByIssueKeyInAndWorkstreamTypeCodeIn(issueKeys, typeCodes)
                .stream()
                .collect(Collectors.toMap(
                        e -> identityKey(e.getIssueKey(), e.getWorkstreamTypeCode()),
                        Function.identity()));

        int created = 0;
        int updated = 0;
        List<WorkstreamEntity> toSave = new ArrayList<>(unique.size());

        for (WorkstreamUpsertCommand command : unique) {
            String key = identityKey(command);
            WorkstreamEntity entity = existingByIdentity.get(key);
            if (entity == null) {
                entity = new WorkstreamEntity(command.issueKey(), command.workstreamTypeCode());
                created++;
            } else {
                updated++;
            }
            entity.applyUpsert(command);
            toSave.add(entity);
        }

        repository.saveAll(toSave);
        return new WorkstreamUpsertOutcome(created, updated);
    }

    private static String identityKey(WorkstreamUpsertCommand command) {
        return identityKey(command.issueKey(), command.workstreamTypeCode());
    }

    private static String identityKey(String issueKey, String workstreamTypeCode) {
        return issueKey + '\0' + workstreamTypeCode;
    }

    private static WorkstreamUpsertCommand mergeCommands(
            WorkstreamUpsertCommand a, WorkstreamUpsertCommand b) {
        return new WorkstreamUpsertCommand(
                a.issueKey(),
                a.workstreamTypeCode(),
                b.repositoryId() != null ? b.repositoryId() : a.repositoryId(),
                b.issueId() != null ? b.issueId() : a.issueId(),
                WorkstreamDerivedStatuses.max(a.derivedStatus(), b.derivedStatus()));
    }
}
