package ru.eltc.deliverymonitor.api.issue;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.eltc.deliverymonitor.domain.issue.IssueEntity;
import ru.eltc.deliverymonitor.domain.issue.IssueRepository;

import java.util.List;
import java.util.Optional;

/**
 * Read-only query layer for the {@code api.issue} read API: loads {@link IssueEntity} rows from
 * {@code domain.issue} and maps them to the external {@link IssueResponse} contract.
 *
 * <p>Both methods are annotated {@code @Transactional(readOnly = true)} so the JPA session stays
 * open while {@link IssueResponse#from(IssueEntity)} reads the LAZY {@code fixVersions}/{@code
 * labels} {@code @ElementCollection}s — without it, accessing those collections outside the
 * originating transaction would throw {@code LazyInitializationException}.
 *
 * <p>Depends only on {@code domain.issue} ({@link IssueRepository}, {@link IssueEntity}) — no
 * {@code sync.jira}, no {@code integration.jira}, no live Jira calls (docs/architecture.md
 * "Read API" dependency direction: PostgreSQL {@code ->} domain.issue {@code ->} api.issue).
 */
@Service
@Transactional(readOnly = true)
public class IssueQueryService {

    private final IssueRepository issueRepository;

    public IssueQueryService(IssueRepository issueRepository) {
        this.issueRepository = issueRepository;
    }

    /** Returns all persisted issues, mapped to {@link IssueResponse}. No pagination/sorting/filtering. */
    public List<IssueResponse> findAll() {
        return issueRepository.findAll().stream()
                .map(IssueResponse::from)
                .toList();
    }

    /** Looks up a single issue by its public Jira key ({@code issue_key}), not {@code jiraId} or the database id. */
    public Optional<IssueResponse> findByKey(String issueKey) {
        return issueRepository.findByKey(issueKey).map(IssueResponse::from);
    }
}
