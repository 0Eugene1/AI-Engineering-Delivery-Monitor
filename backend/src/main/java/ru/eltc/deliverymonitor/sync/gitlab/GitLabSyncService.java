package ru.eltc.deliverymonitor.sync.gitlab;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import ru.eltc.deliverymonitor.domain.gitlab.BranchPersistencePort;
import ru.eltc.deliverymonitor.domain.gitlab.BranchUpsertCommand;
import ru.eltc.deliverymonitor.domain.gitlab.BranchUpsertOutcome;
import ru.eltc.deliverymonitor.domain.gitlab.CommitPersistencePort;
import ru.eltc.deliverymonitor.domain.gitlab.CommitUpsertCommand;
import ru.eltc.deliverymonitor.domain.gitlab.CommitUpsertOutcome;
import ru.eltc.deliverymonitor.domain.gitlab.MergeRequestPersistencePort;
import ru.eltc.deliverymonitor.domain.gitlab.MergeRequestUpsertCommand;
import ru.eltc.deliverymonitor.domain.gitlab.MergeRequestUpsertOutcome;
import ru.eltc.deliverymonitor.domain.repository.RepositoryEntity;
import ru.eltc.deliverymonitor.domain.repository.RepositoryPersistencePort;
import ru.eltc.deliverymonitor.domain.timeline.ActivityEventPersistencePort;
import ru.eltc.deliverymonitor.domain.timeline.ActivityEventTypes;
import ru.eltc.deliverymonitor.domain.timeline.ActivityEventUpsertCommand;
import ru.eltc.deliverymonitor.domain.timeline.ActivityEventUpsertOutcome;
import ru.eltc.deliverymonitor.domain.timeline.IssueKeyExtractor;
import ru.eltc.deliverymonitor.domain.workstream.WorkstreamDerivedStatuses;
import ru.eltc.deliverymonitor.domain.workstream.WorkstreamPersistencePort;
import ru.eltc.deliverymonitor.domain.workstream.WorkstreamUpsertCommand;
import ru.eltc.deliverymonitor.domain.workstream.WorkstreamUpsertOutcome;
import ru.eltc.deliverymonitor.integration.gitlab.client.GitLabClient;
import ru.eltc.deliverymonitor.integration.gitlab.config.GitLabProperties;
import ru.eltc.deliverymonitor.integration.gitlab.dto.GitLabBranchDto;
import ru.eltc.deliverymonitor.integration.gitlab.dto.GitLabCommitDto;
import ru.eltc.deliverymonitor.integration.gitlab.dto.GitLabMergeRequestDto;
import ru.eltc.deliverymonitor.integration.gitlab.dto.GitLabProjectDto;
import ru.eltc.deliverymonitor.integration.gitlab.dto.GitLabUserDto;
import ru.eltc.deliverymonitor.integration.gitlab.exception.GitLabClientException;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Application-level GitLab sync (docs/roadmap.md Phase 3.2 + 3.4.1 + 3.5 + 3.6).
 *
 * <p>Observed-repository list (Single SoT — docs/decisions.md):
 * <ul>
 *   <li>{@code gitlab.mode=rest} (production): {@link RepositoryPersistencePort#findAllOrdered()}
 *       only — {@code gitlab.sync.repositories} yaml is <b>not</b> read;</li>
 *   <li>{@code gitlab.mode=mock}: yaml {@code gitlab.sync.repositories} selects which projects to
 *       sync; each entry is resolved to a seeded {@link RepositoryEntity} by
 *       {@code gitlabProjectId} (FK {@code repository_id} still comes from PostgreSQL).</li>
 * </ul>
 *
 * <p>Flow per target: {@code RepositoryEntity} → {@link GitLabClient} → snapshots →
 * {@link IssueKeyExtractor} stamps {@code issue_key} on git upserts →
 * {@code domain.gitlab} ports → {@code activity_events} via
 * {@link ActivityEventPersistencePort} (idempotent {@code source}+{@code source_ref}) →
 * {@code workstreams} via {@link WorkstreamPersistencePort} when {@code issue_key} is present
 * (identity {@code (issue_key, workstream_type_code)}; {@code repository_id} = Git provenance).
 * Orphans (no key) are still persisted with {@code issue_key = null} and do <b>not</b> create
 * workstreams.
 *
 * <p><b>Out of scope:</b> dashboard, IssueEntity lookup, scheduler (Phase 3.9), pipelines,
 * Jenkins. Admin HTTP entry is {@code api.admin.GitLabSyncController} (Phase 3.8).
 */
@Service
@EnableConfigurationProperties(GitLabSyncProperties.class)
public class GitLabSyncService {

    private static final Logger log = LoggerFactory.getLogger(GitLabSyncService.class);

    private static final int MAX_PAGES_PER_LIST = 10_000;
    private static final String MR_STATE_ALL = "all";
    private static final String MR_STATE_MERGED = "merged";

    private final GitLabClient gitLabClient;
    private final GitLabSyncProperties syncProperties;
    private final GitLabProperties gitLabProperties;
    private final RepositoryPersistencePort repositoryPersistencePort;
    private final BranchPersistencePort branchPersistencePort;
    private final CommitPersistencePort commitPersistencePort;
    private final MergeRequestPersistencePort mergeRequestPersistencePort;
    private final ActivityEventPersistencePort activityEventPersistencePort;
    private final WorkstreamPersistencePort workstreamPersistencePort;
    private final IssueKeyExtractor issueKeyExtractor;
    private final ObjectMapper objectMapper;
    private final Duration blockTimeout;

    public GitLabSyncService(
            GitLabClient gitLabClient,
            GitLabSyncProperties syncProperties,
            GitLabProperties gitLabProperties,
            RepositoryPersistencePort repositoryPersistencePort,
            BranchPersistencePort branchPersistencePort,
            CommitPersistencePort commitPersistencePort,
            MergeRequestPersistencePort mergeRequestPersistencePort,
            ActivityEventPersistencePort activityEventPersistencePort,
            WorkstreamPersistencePort workstreamPersistencePort,
            IssueKeyExtractor issueKeyExtractor,
            ObjectMapper objectMapper) {
        this.gitLabClient = gitLabClient;
        this.syncProperties = syncProperties;
        this.gitLabProperties = gitLabProperties;
        this.repositoryPersistencePort = repositoryPersistencePort;
        this.branchPersistencePort = branchPersistencePort;
        this.commitPersistencePort = commitPersistencePort;
        this.mergeRequestPersistencePort = mergeRequestPersistencePort;
        this.activityEventPersistencePort = activityEventPersistencePort;
        this.workstreamPersistencePort = workstreamPersistencePort;
        this.issueKeyExtractor = issueKeyExtractor;
        this.objectMapper = objectMapper;
        this.blockTimeout = gitLabProperties.getConnectTimeout()
                .plus(gitLabProperties.getResponseTimeout())
                .plusSeconds(5);
    }

    /** Syncs every observed repository (source depends on {@code gitlab.mode}). */
    public GitLabSyncResult syncAll() {
        Instant startedAt = Instant.now();
        List<String> resolveErrors = new ArrayList<>();
        List<SyncTarget> targets = resolveSyncTargets(resolveErrors);
        return runSync(startedAt, targets, resolveErrors);
    }

    /**
     * Syncs a single observed repository identified by numeric GitLab id or path-with-namespace.
     * Must resolve to a known target from the active source (DB in rest, yaml→DB in mock).
     */
    public GitLabSyncResult syncProject(String projectIdOrPath) {
        Instant startedAt = Instant.now();
        if (projectIdOrPath == null || projectIdOrPath.isBlank()) {
            return emptyResult(startedAt, List.of("projectIdOrPath must not be blank"));
        }
        List<String> resolveErrors = new ArrayList<>();
        List<SyncTarget> all = resolveSyncTargets(resolveErrors);
        SyncTarget match = findTarget(all, projectIdOrPath.trim());
        if (match == null) {
            List<String> errors = new ArrayList<>(resolveErrors);
            errors.add("No observed repository for: " + projectIdOrPath);
            return emptyResult(startedAt, errors);
        }
        return runSync(startedAt, List.of(match), resolveErrors);
    }

    private GitLabSyncResult runSync(
            Instant startedAt, List<SyncTarget> targets, List<String> initialErrors) {
        List<String> errors = new ArrayList<>(initialErrors);
        int pages = 0;
        int projectsSynced = 0;
        int branchesFetched = 0;
        int commitsFetched = 0;
        int mergeRequestsFetched = 0;
        int created = 0;
        int updated = 0;
        boolean mocked = gitLabProperties.getMode() == GitLabProperties.Mode.MOCK;

        Instant commitsSince = Instant.now()
                .minus(syncProperties.getCommitHistoryDays(), ChronoUnit.DAYS);
        int pageSize = syncProperties.getPageSize();

        for (SyncTarget target : targets) {
            String projectKey = target.projectIdOrPath();
            try {
                ProjectFetch fetch = fetchProject(target, projectKey, commitsSince, pageSize);
                PersistCounts counts = persistProject(target, fetch.snapshot());
                pages += fetch.pages();
                branchesFetched += fetch.snapshot().branches().size();
                commitsFetched += fetch.snapshot().commits().size();
                mergeRequestsFetched += fetch.snapshot().mergeRequests().size();
                created += counts.created();
                updated += counts.updated();
                projectsSynced++;
            } catch (GitLabClientException e) {
                errors.add(describe(projectKey, e));
                log.warn("GitLab sync failed for project {}: {}", projectKey, e.getMessage());
            }
        }

        GitLabSyncResult result = new GitLabSyncResult(
                startedAt,
                Instant.now(),
                projectsSynced,
                branchesFetched,
                commitsFetched,
                mergeRequestsFetched,
                pages,
                mocked,
                created,
                updated,
                List.copyOf(errors));
        log.info(
                "GitLab sync done: projects={} branches={} commits={} mrs={} pages={} mocked={} "
                        + "created={} updated={} errors={}",
                result.projectsSynced(),
                result.branchesFetched(),
                result.commitsFetched(),
                result.mergeRequestsFetched(),
                result.pages(),
                result.mocked(),
                result.created(),
                result.updated(),
                result.errors().size());
        return result;
    }

    /**
     * Production ({@code rest}): PostgreSQL only. Mock: yaml list resolved to seeded DB rows by
     * {@code gitlabProjectId}.
     */
    private List<SyncTarget> resolveSyncTargets(List<String> errors) {
        if (gitLabProperties.getMode() == GitLabProperties.Mode.MOCK) {
            return resolveFromYaml(errors);
        }
        return repositoryPersistencePort.findAllOrdered().stream()
                .map(SyncTarget::fromEntity)
                .toList();
    }

    private List<SyncTarget> resolveFromYaml(List<String> errors) {
        List<SyncTarget> targets = new ArrayList<>();
        for (GitLabSyncProperties.Repository yamlRepo : syncProperties.getRepositories()) {
            if (yamlRepo.getGitlabId() == null) {
                errors.add("Mock sync requires gitlab-id on yaml repository entry"
                        + (yamlRepo.getPath() != null ? " (" + yamlRepo.getPath() + ")" : ""));
                continue;
            }
            Optional<RepositoryEntity> entity =
                    repositoryPersistencePort.findByGitlabProjectId(yamlRepo.getGitlabId());
            if (entity.isEmpty()) {
                errors.add("No repositories row for gitlab_project_id=" + yamlRepo.getGitlabId()
                        + " (yaml mock list); seed Phase 3.3 first");
                continue;
            }
            targets.add(SyncTarget.fromEntity(entity.get()));
        }
        return targets;
    }

    private static SyncTarget findTarget(List<SyncTarget> targets, String needle) {
        for (SyncTarget target : targets) {
            if (Long.toString(target.gitlabProjectId()).equals(needle)) {
                return target;
            }
            if (target.path() != null && needle.equalsIgnoreCase(target.path())) {
                return target;
            }
        }
        return null;
    }

    private ProjectFetch fetchProject(
            SyncTarget target,
            String projectKey,
            Instant commitsSince,
            int pageSize) {
        GitLabProjectDto project = requireBody(gitLabClient.getProject(projectKey).block(blockTimeout), projectKey);
        long projectId = project.id() != null ? project.id() : target.gitlabProjectId();
        String path = project.pathWithNamespace() != null ? project.pathWithNamespace() : target.path();
        String workstreamTypeCode = target.workstreamTypeCode();

        PageResult<GitLabBranchSnapshot> branches =
                paginateBranches(projectKey, projectId, workstreamTypeCode, pageSize);
        PageResult<GitLabCommitSnapshot> commits =
                paginateCommits(projectKey, projectId, workstreamTypeCode, commitsSince, pageSize);
        PageResult<GitLabMergeRequestSnapshot> mergeRequests =
                paginateMergeRequests(projectKey, projectId, workstreamTypeCode, pageSize);

        GitLabProjectSyncSnapshot snapshot = new GitLabProjectSyncSnapshot(
                projectId,
                path,
                project.name(),
                project.defaultBranch(),
                workstreamTypeCode,
                branches.items(),
                commits.items(),
                mergeRequests.items());
        return new ProjectFetch(snapshot, branches.pages() + commits.pages() + mergeRequests.pages());
    }

    private PersistCounts persistProject(SyncTarget target, GitLabProjectSyncSnapshot snapshot) {
        Long repositoryId = target.repositoryId();
        long projectId = snapshot.gitlabId();

        List<BranchUpsertCommand> branchCommands = snapshot.branches().stream()
                .map(b -> toBranchCommand(repositoryId, b))
                .toList();
        List<CommitUpsertCommand> commitCommands = snapshot.commits().stream()
                .map(c -> toCommitCommand(repositoryId, c))
                .toList();
        List<MergeRequestUpsertCommand> mrCommands = snapshot.mergeRequests().stream()
                .map(mr -> toMergeRequestCommand(repositoryId, mr))
                .toList();

        BranchUpsertOutcome branches = branchPersistencePort.upsertAll(branchCommands);
        CommitUpsertOutcome commits = commitPersistencePort.upsertAll(commitCommands);
        MergeRequestUpsertOutcome mrs = mergeRequestPersistencePort.upsertAll(mrCommands);

        List<ActivityEventUpsertCommand> events = new ArrayList<>();
        for (GitLabBranchSnapshot b : snapshot.branches()) {
            events.add(toBranchEvent(projectId, b));
        }
        for (GitLabCommitSnapshot c : snapshot.commits()) {
            events.add(toCommitEvent(projectId, c));
        }
        for (GitLabMergeRequestSnapshot mr : snapshot.mergeRequests()) {
            events.add(toMergeRequestEvent(projectId, mr));
        }
        ActivityEventUpsertOutcome activity = activityEventPersistencePort.upsertAll(events);

        List<WorkstreamUpsertCommand> workstreams = collectWorkstreams(target, snapshot);
        WorkstreamUpsertOutcome ws = workstreamPersistencePort.upsertAll(workstreams);

        return new PersistCounts(
                branches.created() + commits.created() + mrs.created() + activity.created() + ws.created(),
                branches.updated() + commits.updated() + mrs.updated() + activity.updated() + ws.updated());
    }

    /**
     * Builds workstream upserts for linked Git activity only. Orphans (no {@code issue_key}) are
     * skipped. Duplicate {@code (issue_key, type)} pairs within the project are collapsed by the
     * port; here we emit one candidate per signal with a minimal derived status.
     */
    private List<WorkstreamUpsertCommand> collectWorkstreams(
            SyncTarget target, GitLabProjectSyncSnapshot snapshot) {
        Long repositoryId = target.repositoryId();
        String typeCode = target.workstreamTypeCode();
        List<WorkstreamUpsertCommand> commands = new ArrayList<>();

        for (GitLabBranchSnapshot b : snapshot.branches()) {
            String issueKey = extractIssueKey(b.name());
            if (issueKey != null) {
                commands.add(workstreamCommand(
                        issueKey, typeCode, repositoryId, WorkstreamDerivedStatuses.IN_PROGRESS));
            }
        }
        for (GitLabCommitSnapshot c : snapshot.commits()) {
            String issueKey = extractIssueKeyFromCommit(c);
            if (issueKey != null) {
                commands.add(workstreamCommand(
                        issueKey, typeCode, repositoryId, WorkstreamDerivedStatuses.IN_PROGRESS));
            }
        }
        for (GitLabMergeRequestSnapshot mr : snapshot.mergeRequests()) {
            String issueKey = extractIssueKeyFromMergeRequest(mr);
            if (issueKey != null) {
                commands.add(workstreamCommand(
                        issueKey, typeCode, repositoryId, derivedStatusFromMergeRequest(mr.state())));
            }
        }
        return commands;
    }

    private static WorkstreamUpsertCommand workstreamCommand(
            String issueKey, String typeCode, Long repositoryId, String derivedStatus) {
        return new WorkstreamUpsertCommand(issueKey, typeCode, repositoryId, null, derivedStatus);
    }

    private static String derivedStatusFromMergeRequest(String state) {
        if (state != null && MR_STATE_MERGED.equalsIgnoreCase(state)) {
            return WorkstreamDerivedStatuses.MERGED;
        }
        return WorkstreamDerivedStatuses.IN_REVIEW;
    }

    private BranchUpsertCommand toBranchCommand(Long repositoryId, GitLabBranchSnapshot b) {
        return new BranchUpsertCommand(
                repositoryId,
                b.name(),
                extractIssueKey(b.name()),
                b.commitSha(),
                b.lastCommitAt(),
                null,
                null,
                b.webUrl());
    }

    private CommitUpsertCommand toCommitCommand(Long repositoryId, GitLabCommitSnapshot c) {
        return new CommitUpsertCommand(
                repositoryId,
                c.sha(),
                null,
                extractIssueKeyFromCommit(c),
                c.shortId(),
                c.title(),
                c.message(),
                c.authorName(),
                c.authorEmail(),
                c.committedAt(),
                c.webUrl());
    }

    private MergeRequestUpsertCommand toMergeRequestCommand(
            Long repositoryId, GitLabMergeRequestSnapshot mr) {
        return new MergeRequestUpsertCommand(
                repositoryId,
                mr.iid(),
                mr.id(),
                extractIssueKeyFromMergeRequest(mr),
                mr.title(),
                mr.state(),
                mr.sourceBranch(),
                mr.targetBranch(),
                mr.authorUsername(),
                mr.authorDisplayName(),
                mr.createdAt(),
                mr.updatedAt(),
                mr.mergedAt(),
                mr.webUrl());
    }

    private ActivityEventUpsertCommand toBranchEvent(long projectId, GitLabBranchSnapshot b) {
        String issueKey = extractIssueKey(b.name());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("branch", b.name());
        payload.put("commitSha", b.commitSha());
        payload.put("webUrl", b.webUrl());
        return new ActivityEventUpsertCommand(
                coalesce(b.lastCommitAt(), Instant.now()),
                issueKey,
                b.workstreamTypeCode(),
                null,
                null,
                ActivityEventTypes.BRANCH_CREATED,
                toJson(payload),
                ActivityEventTypes.SOURCE_GITLAB,
                projectId + ":branch:" + b.name());
    }

    private ActivityEventUpsertCommand toCommitEvent(long projectId, GitLabCommitSnapshot c) {
        String issueKey = extractIssueKeyFromCommit(c);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sha", c.sha());
        payload.put("shortId", c.shortId());
        payload.put("title", c.title());
        payload.put("webUrl", c.webUrl());
        return new ActivityEventUpsertCommand(
                coalesce(c.committedAt(), Instant.now()),
                issueKey,
                c.workstreamTypeCode(),
                null,
                c.authorName(),
                ActivityEventTypes.COMMIT,
                toJson(payload),
                ActivityEventTypes.SOURCE_GITLAB,
                projectId + ":" + c.sha());
    }

    private ActivityEventUpsertCommand toMergeRequestEvent(long projectId, GitLabMergeRequestSnapshot mr) {
        String issueKey = extractIssueKeyFromMergeRequest(mr);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("iid", mr.iid());
        payload.put("title", mr.title());
        payload.put("state", mr.state());
        payload.put("sourceBranch", mr.sourceBranch());
        payload.put("targetBranch", mr.targetBranch());
        payload.put("webUrl", mr.webUrl());
        Instant occurredAt = coalesce(mr.mergedAt(), mr.createdAt(), mr.updatedAt(), Instant.now());
        return new ActivityEventUpsertCommand(
                occurredAt,
                issueKey,
                mr.workstreamTypeCode(),
                mr.authorUsername(),
                mr.authorDisplayName(),
                mergeRequestEventType(mr.state()),
                toJson(payload),
                ActivityEventTypes.SOURCE_GITLAB,
                projectId + ":mr:" + mr.iid());
    }

    private String extractIssueKey(String text) {
        return issueKeyExtractor.extract(text).orElse(null);
    }

    private String extractIssueKeyFromCommit(GitLabCommitSnapshot c) {
        return issueKeyExtractor.extract(c.message())
                .or(() -> issueKeyExtractor.extract(c.title()))
                .orElse(null);
    }

    /**
     * MR linking: regex on {@code source_branch} first; soft-link via title if branch has no key
     * (docs/architecture.md Phase 3).
     */
    private String extractIssueKeyFromMergeRequest(GitLabMergeRequestSnapshot mr) {
        return issueKeyExtractor.extract(mr.sourceBranch())
                .or(() -> issueKeyExtractor.extract(mr.title()))
                .orElse(null);
    }

    private static String mergeRequestEventType(String state) {
        if (state != null && MR_STATE_MERGED.equalsIgnoreCase(state)) {
            return ActivityEventTypes.MR_MERGED;
        }
        return ActivityEventTypes.MR_OPENED;
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize activity_events payload: {}", e.getMessage());
            return null;
        }
    }

    private static Instant coalesce(Instant... values) {
        for (Instant value : values) {
            if (value != null) {
                return value;
            }
        }
        return Instant.now();
    }

    private PageResult<GitLabBranchSnapshot> paginateBranches(
            String projectKey, long projectId, String workstreamTypeCode, int pageSize) {
        List<GitLabBranchSnapshot> items = new ArrayList<>();
        int pages = 0;
        int page = 1;
        while (page <= MAX_PAGES_PER_LIST) {
            List<GitLabBranchDto> batch = requireList(
                    gitLabClient.listBranches(projectKey, page, pageSize).block(blockTimeout));
            pages++;
            if (batch.isEmpty()) {
                break;
            }
            for (GitLabBranchDto dto : batch) {
                items.add(toBranchSnapshot(projectId, workstreamTypeCode, dto));
            }
            if (batch.size() < pageSize) {
                break;
            }
            page++;
        }
        if (page > MAX_PAGES_PER_LIST) {
            throw new GitLabClientException(
                    "Reached the safety limit of " + MAX_PAGES_PER_LIST + " branch pages for " + projectKey,
                    GitLabClientException.NO_HTTP_STATUS,
                    List.of());
        }
        return new PageResult<>(List.copyOf(items), pages);
    }

    private PageResult<GitLabCommitSnapshot> paginateCommits(
            String projectKey,
            long projectId,
            String workstreamTypeCode,
            Instant since,
            int pageSize) {
        List<GitLabCommitSnapshot> items = new ArrayList<>();
        int pages = 0;
        int page = 1;
        while (page <= MAX_PAGES_PER_LIST) {
            List<GitLabCommitDto> batch = requireList(
                    gitLabClient.listCommits(projectKey, since, page, pageSize).block(blockTimeout));
            pages++;
            if (batch.isEmpty()) {
                break;
            }
            for (GitLabCommitDto dto : batch) {
                items.add(toCommitSnapshot(projectId, workstreamTypeCode, dto));
            }
            if (batch.size() < pageSize) {
                break;
            }
            page++;
        }
        if (page > MAX_PAGES_PER_LIST) {
            throw new GitLabClientException(
                    "Reached the safety limit of " + MAX_PAGES_PER_LIST + " commit pages for " + projectKey,
                    GitLabClientException.NO_HTTP_STATUS,
                    List.of());
        }
        return new PageResult<>(List.copyOf(items), pages);
    }

    private PageResult<GitLabMergeRequestSnapshot> paginateMergeRequests(
            String projectKey, long projectId, String workstreamTypeCode, int pageSize) {
        List<GitLabMergeRequestSnapshot> items = new ArrayList<>();
        int pages = 0;
        int page = 1;
        while (page <= MAX_PAGES_PER_LIST) {
            List<GitLabMergeRequestDto> batch = requireList(
                    gitLabClient.listMergeRequests(projectKey, MR_STATE_ALL, page, pageSize)
                            .block(blockTimeout));
            pages++;
            if (batch.isEmpty()) {
                break;
            }
            for (GitLabMergeRequestDto dto : batch) {
                items.add(toMergeRequestSnapshot(projectId, workstreamTypeCode, dto));
            }
            if (batch.size() < pageSize) {
                break;
            }
            page++;
        }
        if (page > MAX_PAGES_PER_LIST) {
            throw new GitLabClientException(
                    "Reached the safety limit of " + MAX_PAGES_PER_LIST
                            + " merge-request pages for " + projectKey,
                    GitLabClientException.NO_HTTP_STATUS,
                    List.of());
        }
        return new PageResult<>(List.copyOf(items), pages);
    }

    private GitLabBranchSnapshot toBranchSnapshot(
            long projectId, String workstreamTypeCode, GitLabBranchDto dto) {
        GitLabCommitDto tip = dto.commit();
        return new GitLabBranchSnapshot(
                projectId,
                dto.name(),
                tip != null ? tip.id() : null,
                dto.merged(),
                dto.protectedBranch(),
                dto.defaultBranch(),
                dto.webUrl(),
                tip != null ? parseInstant(tip.committedDate()) : null,
                workstreamTypeCode);
    }

    private GitLabCommitSnapshot toCommitSnapshot(
            long projectId, String workstreamTypeCode, GitLabCommitDto dto) {
        return new GitLabCommitSnapshot(
                projectId,
                dto.id(),
                dto.shortId(),
                dto.title(),
                dto.message(),
                dto.authorName(),
                dto.authorEmail(),
                parseInstant(dto.committedDate()),
                dto.webUrl(),
                workstreamTypeCode);
    }

    private GitLabMergeRequestSnapshot toMergeRequestSnapshot(
            long projectId, String workstreamTypeCode, GitLabMergeRequestDto dto) {
        GitLabUserDto author = dto.author();
        long iid = dto.iid() != null ? dto.iid() : 0L;
        return new GitLabMergeRequestSnapshot(
                projectId,
                dto.id(),
                iid,
                dto.title(),
                dto.state(),
                dto.sourceBranch(),
                dto.targetBranch(),
                author != null ? author.username() : null,
                author != null ? author.name() : null,
                parseInstant(dto.createdAt()),
                parseInstant(dto.updatedAt()),
                parseInstant(dto.mergedAt()),
                dto.webUrl(),
                workstreamTypeCode);
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException e) {
            log.warn("Failed to parse GitLab timestamp '{}': {}", value, e.getMessage());
            return null;
        }
    }

    private GitLabSyncResult emptyResult(Instant startedAt, List<String> errors) {
        return new GitLabSyncResult(
                startedAt,
                Instant.now(),
                0,
                0,
                0,
                0,
                0,
                gitLabProperties.getMode() == GitLabProperties.Mode.MOCK,
                0,
                0,
                List.copyOf(errors));
    }

    private static <T> T requireBody(T body, String projectKey) {
        if (body == null) {
            throw new GitLabClientException(
                    "GitLab returned no project body for " + projectKey,
                    GitLabClientException.NO_HTTP_STATUS,
                    List.of());
        }
        return body;
    }

    private static <T> List<T> requireList(List<T> body) {
        return body != null ? body : List.of();
    }

    private static String describe(String projectKey, GitLabClientException e) {
        StringBuilder sb = new StringBuilder("project ").append(projectKey).append(": ");
        if (e.getStatusCode() != GitLabClientException.NO_HTTP_STATUS) {
            sb.append("HTTP ").append(e.getStatusCode()).append(": ");
        }
        sb.append(Objects.toString(e.getMessage(), "unknown error"));
        if (!e.getGitlabErrorMessages().isEmpty()) {
            sb.append(" (").append(String.join("; ", e.getGitlabErrorMessages())).append(')');
        }
        return sb.toString();
    }

    /**
     * One syncable repository: internal DB id (FK for git entities) + GitLab identity + type.
     */
    private record SyncTarget(
            Long repositoryId,
            Long gitlabProjectId,
            String path,
            String name,
            String workstreamTypeCode) {

        static SyncTarget fromEntity(RepositoryEntity entity) {
            return new SyncTarget(
                    entity.getId(),
                    entity.getGitlabProjectId(),
                    entity.getPath(),
                    entity.getName(),
                    entity.getWorkstreamTypeCode());
        }

        String projectIdOrPath() {
            if (gitlabProjectId != null) {
                return Long.toString(gitlabProjectId);
            }
            return path;
        }
    }

    private record ProjectFetch(GitLabProjectSyncSnapshot snapshot, int pages) {
    }

    private record PageResult<T>(List<T> items, int pages) {
    }

    private record PersistCounts(int created, int updated) {
    }
}
