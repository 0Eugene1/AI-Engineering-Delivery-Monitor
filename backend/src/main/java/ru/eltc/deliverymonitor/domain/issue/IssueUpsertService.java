package ru.eltc.deliverymonitor.domain.issue;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Default {@link IssuePersistencePort}: upserts one page of issues per call, matching existing
 * rows by {@link IssueUpsertCommand#jiraId()} (never {@code key} — see {@link IssueEntity}).
 *
 * <p>Database errors are not caught here: they propagate to the caller ({@code sync.jira}), which
 * does not mask or continue past them (unlike {@code JiraClientException} from the Jira side).
 */
@Service
public class IssueUpsertService implements IssuePersistencePort {

    private final IssueRepository repository;

    public IssueUpsertService(IssueRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public IssueUpsertOutcome upsertPage(List<IssueUpsertCommand> commands) {
        if (commands == null || commands.isEmpty()) {
            return IssueUpsertOutcome.empty();
        }

        List<String> jiraIds = commands.stream().map(IssueUpsertCommand::jiraId).toList();
        Map<String, IssueEntity> existingByJiraId = repository.findAllByJiraIdIn(jiraIds).stream()
                .collect(Collectors.toMap(IssueEntity::getJiraId, Function.identity()));

        Instant syncedAt = Instant.now();
        int created = 0;
        int updated = 0;
        List<IssueEntity> toSave = new ArrayList<>(commands.size());

        for (IssueUpsertCommand command : commands) {
            IssueEntity entity = existingByJiraId.get(command.jiraId());
            if (entity == null) {
                entity = new IssueEntity(command.jiraId());
                created++;
            } else {
                updated++;
            }
            entity.applyUpsert(command, syncedAt);
            toSave.add(entity);
        }

        repository.saveAll(toSave);
        return new IssueUpsertOutcome(created, updated);
    }
}
