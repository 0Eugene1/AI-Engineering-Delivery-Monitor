package ru.eltc.deliverymonitor.domain.risk;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.eltc.deliverymonitor.domain.gitlab.MergeRequestEntity;
import ru.eltc.deliverymonitor.domain.gitlab.MergeRequestRepository;
import ru.eltc.deliverymonitor.domain.issue.IssueEntity;
import ru.eltc.deliverymonitor.domain.issue.IssueRepository;
import ru.eltc.deliverymonitor.domain.timeline.ActivityEventRepository;
import ru.eltc.deliverymonitor.domain.workstream.WorkstreamDerivedStatuses;
import ru.eltc.deliverymonitor.domain.workstream.WorkstreamEntity;
import ru.eltc.deliverymonitor.domain.workstream.WorkstreamRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Evaluate-on-read risk engine (Phase 4.2). Reads existing PostgreSQL tables only —
 * no {@code risk_flags}, no live Jira/GitLab.
 *
 * <p>Workstream-scoped rules ({@link RiskCodes#STALE_ACTIVITY}, {@link RiskCodes#OPEN_MR_STALE},
 * {@link RiskCodes#NO_MR}) iterate {@code workstreams}. {@link RiskCodes#JIRA_ACTIVE_NO_GIT}
 * starts from active Jira issues (status category name {@code In Progress}) and anti-joins
 * workstreams + activity_events. Orphan Git (null {@code issue_key}) never produces risks.
 */
@Service
@Transactional(readOnly = true)
@EnableConfigurationProperties(RiskProperties.class)
public class RiskService {

    /**
     * Jira status category <em>name</em> persisted by sync for key {@code indeterminate}
     * (docs/architecture.md uses the key; DB stores the display name).
     */
    static final String ACTIVE_JIRA_STATUS_CATEGORY = "In Progress";

    private static final Set<String> STALE_STATUSES = Set.of(
            WorkstreamDerivedStatuses.IN_PROGRESS,
            WorkstreamDerivedStatuses.IN_REVIEW);

    private static final Set<String> MR_PRESENT_STATES = Set.of("opened", "merged");

    private final WorkstreamRepository workstreamRepository;
    private final ActivityEventRepository activityEventRepository;
    private final MergeRequestRepository mergeRequestRepository;
    private final IssueRepository issueRepository;
    private final RiskProperties properties;

    public RiskService(
            WorkstreamRepository workstreamRepository,
            ActivityEventRepository activityEventRepository,
            MergeRequestRepository mergeRequestRepository,
            IssueRepository issueRepository,
            RiskProperties properties) {
        this.workstreamRepository = workstreamRepository;
        this.activityEventRepository = activityEventRepository;
        this.mergeRequestRepository = mergeRequestRepository;
        this.issueRepository = issueRepository;
        this.properties = properties;
    }

    /**
     * Evaluates all Phase 4 risk rules and returns every matching risk (no filters / limit).
     *
     * @param now evaluation clock (injected by callers for testability)
     */
    public List<Risk> evaluateAll(Instant now) {
        List<Risk> risks = new ArrayList<>();
        List<WorkstreamEntity> workstreams = workstreamRepository.findAll();

        Map<String, Instant> lastActivityByWs = loadLastActivityIndex();
        Map<String, List<MergeRequestEntity>> mrsByIssueKey = loadLinkedMrsByIssueKey(workstreams);

        for (WorkstreamEntity ws : workstreams) {
            evaluateStaleActivity(ws, lastActivityByWs, now, risks);
            evaluateOpenMrStale(ws, mrsByIssueKey, now, risks);
            evaluateNoMr(ws, mrsByIssueKey, now, risks);
        }

        evaluateJiraActiveNoGit(now, risks);
        return risks;
    }

    private void evaluateStaleActivity(
            WorkstreamEntity ws,
            Map<String, Instant> lastActivityByWs,
            Instant now,
            List<Risk> out) {
        if (!STALE_STATUSES.contains(ws.getDerivedStatus())) {
            return;
        }
        Instant threshold = now.minus(properties.getStaleActivityDays(), ChronoUnit.DAYS);
        Instant last = lastActivityByWs.get(activityKey(ws.getIssueKey(), ws.getWorkstreamTypeCode()));
        if (last != null && !last.isBefore(threshold)) {
            return;
        }
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("derivedStatus", ws.getDerivedStatus());
        evidence.put("staleActivityDays", properties.getStaleActivityDays());
        if (last != null) {
            evidence.put("lastActivityAt", last.toString());
        } else {
            evidence.put("lastActivityAt", null);
        }
        String explanation = last == null
                ? "No activity events for workstream older than threshold window"
                : "No activity for more than " + properties.getStaleActivityDays() + " days";
        out.add(new Risk(
                RiskCodes.STALE_ACTIVITY,
                RiskSeverities.MEDIUM,
                ws.getIssueKey(),
                ws.getWorkstreamTypeCode(),
                explanation,
                now,
                evidence));
    }

    private void evaluateOpenMrStale(
            WorkstreamEntity ws,
            Map<String, List<MergeRequestEntity>> mrsByIssueKey,
            Instant now,
            List<Risk> out) {
        Instant threshold = now.minus(properties.getOpenMrStaleDays(), ChronoUnit.DAYS);
        for (MergeRequestEntity mr : mrsForWorkstream(ws, mrsByIssueKey)) {
            if (!isOpened(mr.getState())) {
                continue;
            }
            Instant openedAt = mr.getGitlabCreatedAt();
            if (openedAt == null || !openedAt.isBefore(threshold)) {
                continue;
            }
            long openDays = ChronoUnit.DAYS.between(openedAt, now);
            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("mergeRequestIid", mr.getGitlabIid());
            evidence.put("openedAt", openedAt.toString());
            evidence.put("openMrStaleDays", properties.getOpenMrStaleDays());
            out.add(new Risk(
                    RiskCodes.OPEN_MR_STALE,
                    RiskSeverities.MEDIUM,
                    ws.getIssueKey(),
                    ws.getWorkstreamTypeCode(),
                    "MR !" + mr.getGitlabIid() + " opened for " + openDays + " days without merge",
                    now,
                    evidence));
        }
    }

    private void evaluateNoMr(
            WorkstreamEntity ws,
            Map<String, List<MergeRequestEntity>> mrsByIssueKey,
            Instant now,
            List<Risk> out) {
        boolean hasMr = mrsForWorkstream(ws, mrsByIssueKey).stream()
                .anyMatch(mr -> isOpenedOrMerged(mr.getState()));
        if (hasMr) {
            return;
        }
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("derivedStatus", ws.getDerivedStatus());
        out.add(new Risk(
                RiskCodes.NO_MR,
                RiskSeverities.LOW,
                ws.getIssueKey(),
                ws.getWorkstreamTypeCode(),
                "Workstream exists but has no opened or merged MR",
                now,
                evidence));
    }

    private void evaluateJiraActiveNoGit(Instant now, List<Risk> out) {
        List<IssueEntity> activeIssues =
                issueRepository.findAllByStatusCategory(ACTIVE_JIRA_STATUS_CATEGORY);
        if (activeIssues.isEmpty()) {
            return;
        }
        List<String> keys = activeIssues.stream().map(IssueEntity::getKey).toList();
        Set<String> withWorkstream = new HashSet<>(workstreamRepository.findDistinctIssueKeysIn(keys));
        Set<String> withActivity = new HashSet<>(activityEventRepository.findDistinctIssueKeysIn(keys));

        for (IssueEntity issue : activeIssues) {
            String key = issue.getKey();
            if (withWorkstream.contains(key) || withActivity.contains(key)) {
                continue;
            }
            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("status", issue.getStatusName());
            evidence.put("statusCategory", issue.getStatusCategory());
            out.add(new Risk(
                    RiskCodes.JIRA_ACTIVE_NO_GIT,
                    RiskSeverities.MEDIUM,
                    key,
                    null,
                    "Active Jira issue has no Git workstream or activity",
                    now,
                    evidence));
        }
    }

    private Map<String, Instant> loadLastActivityIndex() {
        Map<String, Instant> index = new HashMap<>();
        for (Object[] row : activityEventRepository.findLastOccurredAtGroupedByIssueKeyAndWorkstreamType()) {
            String issueKey = (String) row[0];
            String typeCode = (String) row[1];
            Instant lastAt = (Instant) row[2];
            index.put(activityKey(issueKey, typeCode), lastAt);
        }
        return index;
    }

    private Map<String, List<MergeRequestEntity>> loadLinkedMrsByIssueKey(
            List<WorkstreamEntity> workstreams) {
        Set<String> issueKeys = workstreams.stream()
                .map(WorkstreamEntity::getIssueKey)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (issueKeys.isEmpty()) {
            return Map.of();
        }
        return mergeRequestRepository.findAllByIssueKeyIn(issueKeys).stream()
                .collect(Collectors.groupingBy(MergeRequestEntity::getIssueKey));
    }

    /**
     * MRs belonging to a workstream: same {@code issue_key}, and same {@code repository_id}
     * when the workstream has Git provenance. Orphan MRs never appear (query filters null keys).
     */
    private static List<MergeRequestEntity> mrsForWorkstream(
            WorkstreamEntity ws, Map<String, List<MergeRequestEntity>> mrsByIssueKey) {
        List<MergeRequestEntity> candidates = mrsByIssueKey.getOrDefault(ws.getIssueKey(), List.of());
        if (ws.getRepositoryId() == null) {
            return candidates;
        }
        return candidates.stream()
                .filter(mr -> ws.getRepositoryId().equals(mr.getRepositoryId()))
                .toList();
    }

    private static boolean isOpened(String state) {
        return state != null && "opened".equalsIgnoreCase(state);
    }

    private static boolean isOpenedOrMerged(String state) {
        return state != null && MR_PRESENT_STATES.contains(state.toLowerCase());
    }

    private static String activityKey(String issueKey, String workstreamTypeCode) {
        return issueKey + '\0' + workstreamTypeCode;
    }
}
