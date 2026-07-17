package ru.eltc.deliverymonitor.domain.timeline;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Default {@link ActivityEventPersistencePort}: upserts by {@code (source, sourceRef)}.
 *
 * <p>Database errors are not caught here: they propagate to the caller.
 */
@Service
public class ActivityEventUpsertService implements ActivityEventPersistencePort {

    private final ActivityEventRepository repository;

    public ActivityEventUpsertService(ActivityEventRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public ActivityEventUpsertOutcome upsertAll(List<ActivityEventUpsertCommand> commands) {
        if (commands == null || commands.isEmpty()) {
            return ActivityEventUpsertOutcome.empty();
        }

        Map<String, List<ActivityEventUpsertCommand>> bySource = commands.stream()
                .collect(Collectors.groupingBy(ActivityEventUpsertCommand::source));

        int created = 0;
        int updated = 0;
        List<ActivityEventEntity> toSave = new ArrayList<>(commands.size());

        for (Map.Entry<String, List<ActivityEventUpsertCommand>> entry : bySource.entrySet()) {
            String source = entry.getKey();
            List<ActivityEventUpsertCommand> group = entry.getValue();
            List<String> refs = group.stream().map(ActivityEventUpsertCommand::sourceRef).toList();
            Map<String, ActivityEventEntity> existingByRef = repository
                    .findAllBySourceAndSourceRefIn(source, refs)
                    .stream()
                    .collect(Collectors.toMap(ActivityEventEntity::getSourceRef, Function.identity()));

            for (ActivityEventUpsertCommand command : group) {
                ActivityEventEntity entity = existingByRef.get(command.sourceRef());
                if (entity == null) {
                    entity = new ActivityEventEntity(command.source(), command.sourceRef());
                    created++;
                } else {
                    updated++;
                }
                Objects.requireNonNull(command.occurredAt(), "occurredAt must not be null");
                Objects.requireNonNull(command.type(), "type must not be null");
                entity.applyUpsert(command);
                toSave.add(entity);
            }
        }

        repository.saveAll(toSave);
        return new ActivityEventUpsertOutcome(created, updated);
    }
}
