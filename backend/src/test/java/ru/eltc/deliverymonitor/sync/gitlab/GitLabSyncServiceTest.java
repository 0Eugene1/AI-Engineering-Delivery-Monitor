package ru.eltc.deliverymonitor.sync.gitlab;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
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
import ru.eltc.deliverymonitor.domain.repository.RepositoryUpsertCommand;
import ru.eltc.deliverymonitor.domain.repository.RepositoryUpsertOutcome;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link GitLabSyncService} — fake {@link GitLabClient}, fake
 * {@link RepositoryPersistencePort}, recording git-entity + activity_events + workstream ports.
 * No HTTP / DB / Spring context.
 */
class GitLabSyncServiceTest {

    private static final IssueKeyExtractor ISSUE_KEY_EXTRACTOR = new IssueKeyExtractor();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void restModeUsesDbRepositoriesAndIgnoresYamlList() {
        FakeGitLabClient client = FakeGitLabClient.singleProject(
                project(2159L, "mptp8", "mptp/mptp8"),
                List.of(branch("master", commit("aaa", "2026-07-10T10:00:00Z")),
                        branch("feature/MPTPSUPP-1", commit("bbb", "2026-07-11T10:00:00Z"))),
                List.of(commit("bbb", "2026-07-11T10:00:00Z"),
                        commit("ccc", "2026-07-09T10:00:00Z")),
                List.of(mergeRequest(88L, "opened", "feature/MPTPSUPP-1")));

        RecordingPorts ports = RecordingPorts.createAll();
        GitLabSyncProperties props = properties(50, 30, yamlRepo(760L, "mptp/mptp-react-native", "frontend"));
        FakeRepositoryPort repos = FakeRepositoryPort.of(entity(10L, 2159L, "mptp/mptp8", "mptp8", "backend"));

        GitLabSyncResult result = newService(client, props, GitLabProperties.Mode.REST, repos, ports).syncAll();

        assertThat(result.errors()).isEmpty();
        assertThat(result.projectsSynced()).isEqualTo(1);
        assertThat(result.branchesFetched()).isEqualTo(2);
        assertThat(result.commitsFetched()).isEqualTo(2);
        assertThat(result.mergeRequestsFetched()).isEqualTo(1);
        assertThat(result.fetched()).isEqualTo(5);
        // 5 git entities + 5 activity_events + 2 workstream commands (branch + MR; commits have no key)
        assertThat(result.created()).isEqualTo(12);
        assertThat(result.updated()).isZero();
        assertThat(result.saved()).isEqualTo(12);
        assertThat(result.mocked()).isFalse();
        assertThat(result.pages()).isEqualTo(3);

        assertThat(ports.branchBatches).hasSize(1);
        assertThat(ports.branchBatches.get(0)).extracting(BranchUpsertCommand::repositoryId)
                .containsOnly(10L);
        assertThat(ports.branchBatches.get(0)).extracting(BranchUpsertCommand::name)
                .containsExactly("master", "feature/MPTPSUPP-1");
        assertThat(ports.commitBatches.get(0)).extracting(CommitUpsertCommand::sha)
                .containsExactly("bbb", "ccc");
        assertThat(ports.mrBatches.get(0)).extracting(MergeRequestUpsertCommand::gitlabIid)
                .containsExactly(88L);
        assertThat(ports.branchBatches.get(0).get(0).issueKey()).isNull();
        assertThat(ports.branchBatches.get(0).get(1).issueKey()).isEqualTo("MPTPSUPP-1");
        assertThat(ports.eventBatches.get(0)).hasSize(5);
        assertThat(ports.eventBatches.get(0)).extracting(ActivityEventUpsertCommand::sourceRef)
                .contains(
                        "2159:branch:master",
                        "2159:branch:feature/MPTPSUPP-1",
                        "2159:bbb",
                        "2159:ccc",
                        "2159:mr:88");
        assertThat(ports.workstreamBatches).hasSize(1);
        assertThat(ports.workstreamBatches.get(0)).hasSize(2);
        assertThat(ports.workstreamBatches.get(0)).extracting(WorkstreamUpsertCommand::issueKey)
                .containsOnly("MPTPSUPP-1");
        assertThat(ports.workstreamBatches.get(0)).extracting(WorkstreamUpsertCommand::workstreamTypeCode)
                .containsOnly("backend");
        assertThat(ports.workstreamBatches.get(0)).extracting(WorkstreamUpsertCommand::repositoryId)
                .containsOnly(10L);
        assertThat(ports.workstreamBatches.get(0)).extracting(WorkstreamUpsertCommand::derivedStatus)
                .containsExactly(
                        WorkstreamDerivedStatuses.IN_PROGRESS,
                        WorkstreamDerivedStatuses.IN_REVIEW);
    }

    @Test
    void mockModeUsesYamlFilterResolvedAgainstDb() {
        FakeGitLabClient client = FakeGitLabClient.singleProject(
                project(2159L, "mptp8", "mptp/mptp8"),
                List.of(branch("master", commit("aaa", "2026-07-10T10:00:00Z"))),
                List.of(),
                List.of());

        RecordingPorts ports = RecordingPorts.createAll();
        FakeRepositoryPort repos = FakeRepositoryPort.of(
                entity(1L, 760L, "mptp/mptp-react-native", "mptp-react-native", "frontend"),
                entity(2L, 2159L, "mptp/mptp8", "mptp8", "backend"));
        GitLabSyncProperties props = properties(50, 30, yamlRepo(2159L, "mptp/mptp8", "backend"));

        GitLabSyncResult result = newService(client, props, GitLabProperties.Mode.MOCK, repos, ports).syncAll();

        assertThat(result.mocked()).isTrue();
        assertThat(result.projectsSynced()).isEqualTo(1);
        assertThat(result.branchesFetched()).isEqualTo(1);
        assertThat(ports.branchBatches.get(0).get(0).repositoryId()).isEqualTo(2L);
        assertThat(client.projectCalls()).isEqualTo(1);
    }

    @Test
    void restModeDoesNotCallGitlabWhenDbRepositoriesEmpty() {
        FakeGitLabClient client = FakeGitLabClient.singleProject(
                project(2159L, "mptp8", "mptp/mptp8"), List.of(), List.of(), List.of());
        RecordingPorts ports = RecordingPorts.createAll();
        GitLabSyncProperties props = properties(50, 30, yamlRepo(2159L, "mptp/mptp8", "backend"));

        GitLabSyncResult result = newService(
                client, props, GitLabProperties.Mode.REST, FakeRepositoryPort.of(), ports).syncAll();

        assertThat(result.projectsSynced()).isZero();
        assertThat(result.fetched()).isZero();
        assertThat(result.errors()).isEmpty();
        assertThat(client.projectCalls()).isZero();
        assertThat(ports.branchBatches).isEmpty();
        assertThat(ports.eventBatches).isEmpty();
        assertThat(ports.workstreamBatches).isEmpty();
    }

    @Test
    void mockModeReportsErrorWhenYamlGitlabIdMissingFromDb() {
        FakeGitLabClient client = FakeGitLabClient.singleProject(
                project(2159L, "mptp8", "mptp/mptp8"), List.of(), List.of(), List.of());
        RecordingPorts ports = RecordingPorts.createAll();
        GitLabSyncProperties props = properties(50, 30, yamlRepo(9999L, "missing/repo", "backend"));

        GitLabSyncResult result = newService(
                client, props, GitLabProperties.Mode.MOCK, FakeRepositoryPort.of(), ports).syncAll();

        assertThat(result.projectsSynced()).isZero();
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0)).contains("gitlab_project_id=9999");
        assertThat(client.projectCalls()).isZero();
    }

    @Test
    void paginatesListEndpointsUntilShortPage() {
        List<GitLabBranchDto> branches = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            branches.add(branch("b" + i, commit("sha" + i, "2026-07-10T10:00:00Z")));
        }
        FakeGitLabClient client = FakeGitLabClient.singleProject(
                project(2159L, "mptp8", "mptp/mptp8"), branches, List.of(), List.of());
        RecordingPorts ports = RecordingPorts.createAll();

        GitLabSyncResult result = newService(
                client,
                properties(2, 30),
                GitLabProperties.Mode.REST,
                FakeRepositoryPort.of(entity(10L, 2159L, "mptp/mptp8", "mptp8", "backend")),
                ports).syncAll();

        assertThat(result.branchesFetched()).isEqualTo(5);
        assertThat(result.pages()).isEqualTo(5);
        assertThat(ports.branchBatches.get(0)).hasSize(5);
        assertThat(ports.eventBatches.get(0)).hasSize(5);
    }

    @Test
    void appliesCommitHistoryDaysAsSinceLowerBound() {
        AtomicReference<Instant> capturedSince = new AtomicReference<>();
        FakeGitLabClient client = new FakeGitLabClient(
                project(2159L, "mptp8", "mptp/mptp8"),
                List.of(),
                List.of(commit("recent", "2026-07-10T10:00:00Z")),
                List.of()) {
            @Override
            public Mono<List<GitLabCommitDto>> listCommits(
                    String projectIdOrPath, Instant since, int page, int perPage) {
                capturedSince.set(since);
                return super.listCommits(projectIdOrPath, since, page, perPage);
            }
        };

        Instant before = Instant.now().minus(Duration.ofDays(14));
        newService(
                client,
                properties(50, 14),
                GitLabProperties.Mode.REST,
                FakeRepositoryPort.of(entity(10L, 2159L, "mptp/mptp8", "mptp8", "backend")),
                RecordingPorts.createAll()).syncAll();
        Instant after = Instant.now().minus(Duration.ofDays(14));

        assertThat(capturedSince.get()).isNotNull();
        assertThat(capturedSince.get()).isBetween(before.minusSeconds(2), after.plusSeconds(2));
    }

    @Test
    void normalizesFieldsIntoUpsertCommandsAndExtractsIssueKeys() {
        GitLabUserDto author = new GitLabUserDto(1L, "demo.dev", "Demo Dev", "active", "https://git/u");
        FakeGitLabClient client = FakeGitLabClient.singleProject(
                project(2159L, "mptp8", "mptp/mptp8"),
                List.of(new GitLabBranchDto(
                        "feature/MPTPSUPP-42",
                        commit("abcdef", "2026-07-10T12:30:00.000Z"),
                        false,
                        true,
                        false,
                        "https://git/branch")),
                List.of(new GitLabCommitDto(
                        "abcdef",
                        "abcdef0",
                        "MPTPSUPP-42: fix",
                        "MPTPSUPP-42: fix\n",
                        "Demo Dev",
                        "dev@example.com",
                        "2026-07-10T12:30:00.000Z",
                        "Demo Dev",
                        "dev@example.com",
                        "2026-07-10T12:30:00.000Z",
                        "https://git/commit")),
                List.of(new GitLabMergeRequestDto(
                        10001L,
                        42L,
                        2159L,
                        "MPTPSUPP-42: Fix",
                        "desc",
                        "opened",
                        null,
                        "2026-07-10T13:00:00.000Z",
                        "2026-07-10T14:00:00.000Z",
                        "feature/MPTPSUPP-42",
                        "master",
                        "https://git/mr/42",
                        author)));

        RecordingPorts ports = RecordingPorts.createAll();
        newService(
                client,
                properties(50, 30),
                GitLabProperties.Mode.REST,
                FakeRepositoryPort.of(entity(10L, 2159L, "mptp/mptp8", "mptp8", "backend")),
                ports).syncAll();

        BranchUpsertCommand branchCmd = ports.branchBatches.get(0).get(0);
        assertThat(branchCmd.repositoryId()).isEqualTo(10L);
        assertThat(branchCmd.name()).isEqualTo("feature/MPTPSUPP-42");
        assertThat(branchCmd.tipCommitSha()).isEqualTo("abcdef");
        assertThat(branchCmd.lastCommitAt()).isEqualTo(Instant.parse("2026-07-10T12:30:00.000Z"));
        assertThat(branchCmd.issueKey()).isEqualTo("MPTPSUPP-42");

        CommitUpsertCommand commitCmd = ports.commitBatches.get(0).get(0);
        assertThat(commitCmd.sha()).isEqualTo("abcdef");
        assertThat(commitCmd.shortId()).isEqualTo("abcdef0");
        assertThat(commitCmd.title()).isEqualTo("MPTPSUPP-42: fix");
        assertThat(commitCmd.authorName()).isEqualTo("Demo Dev");
        assertThat(commitCmd.authorEmail()).isEqualTo("dev@example.com");
        assertThat(commitCmd.issueKey()).isEqualTo("MPTPSUPP-42");

        MergeRequestUpsertCommand mr = ports.mrBatches.get(0).get(0);
        assertThat(mr.gitlabIid()).isEqualTo(42L);
        assertThat(mr.gitlabId()).isEqualTo(10001L);
        assertThat(mr.title()).isEqualTo("MPTPSUPP-42: Fix");
        assertThat(mr.state()).isEqualTo("opened");
        assertThat(mr.sourceBranch()).isEqualTo("feature/MPTPSUPP-42");
        assertThat(mr.authorUsername()).isEqualTo("demo.dev");
        assertThat(mr.issueKey()).isEqualTo("MPTPSUPP-42");

        assertThat(ports.eventBatches.get(0)).extracting(ActivityEventUpsertCommand::type)
                .containsExactly(
                        ActivityEventTypes.BRANCH_CREATED,
                        ActivityEventTypes.COMMIT,
                        ActivityEventTypes.MR_OPENED);
        assertThat(ports.eventBatches.get(0)).extracting(ActivityEventUpsertCommand::issueKey)
                .containsOnly("MPTPSUPP-42");
        assertThat(ports.eventBatches.get(0)).extracting(ActivityEventUpsertCommand::source)
                .containsOnly(ActivityEventTypes.SOURCE_GITLAB);
        assertThat(ports.workstreamBatches.get(0)).hasSize(3);
        assertThat(ports.workstreamBatches.get(0)).extracting(WorkstreamUpsertCommand::issueKey)
                .containsOnly("MPTPSUPP-42");
        assertThat(ports.workstreamBatches.get(0)).extracting(WorkstreamUpsertCommand::derivedStatus)
                .containsExactly(
                        WorkstreamDerivedStatuses.IN_PROGRESS,
                        WorkstreamDerivedStatuses.IN_PROGRESS,
                        WorkstreamDerivedStatuses.IN_REVIEW);
    }

    @Test
    void orphanObjectsKeepNullIssueKeyAndStillWriteActivityEvents() {
        FakeGitLabClient client = FakeGitLabClient.singleProject(
                project(2159L, "mptp8", "mptp/mptp8"),
                List.of(branch("feature/orphan-no-jira-key", commit("ccc", "2026-07-11T09:00:00Z"))),
                List.of(new GitLabCommitDto(
                        "ccc", "ccc", "[DEMO] chore", "[DEMO] chore without issue key\n",
                        "Demo", "d@e", "2026-07-11T09:00:00Z", "Demo", "d@e",
                        "2026-07-11T09:00:00Z", null)),
                List.of(mergeRequest(9L, "opened", "feature/orphan-no-jira-key")));

        RecordingPorts ports = RecordingPorts.createAll();
        newService(
                client,
                properties(50, 30),
                GitLabProperties.Mode.REST,
                FakeRepositoryPort.of(entity(10L, 2159L, "mptp/mptp8", "mptp8", "backend")),
                ports).syncAll();

        assertThat(ports.branchBatches.get(0).get(0).issueKey()).isNull();
        assertThat(ports.commitBatches.get(0).get(0).issueKey()).isNull();
        assertThat(ports.mrBatches.get(0).get(0).issueKey()).isNull();
        assertThat(ports.eventBatches.get(0)).hasSize(3);
        assertThat(ports.eventBatches.get(0)).extracting(ActivityEventUpsertCommand::issueKey)
                .containsOnlyNulls();
        assertThat(ports.workstreamBatches).isEmpty();
    }

    @Test
    void mergeRequestSoftLinksIssueKeyFromTitleWhenSourceBranchHasNone() {
        FakeGitLabClient client = FakeGitLabClient.singleProject(
                project(2159L, "mptp8", "mptp/mptp8"),
                List.of(),
                List.of(),
                List.of(new GitLabMergeRequestDto(
                        10010L, 10L, 2159L, "MPTPSUPP-77: soft link", null, "merged",
                        "2026-07-09T11:00:00Z", "2026-07-08T09:00:00Z", "2026-07-09T11:00:00Z",
                        "feature/no-key-in-branch", "master", "https://git/mr/10", null)));

        RecordingPorts ports = RecordingPorts.createAll();
        newService(
                client,
                properties(50, 30),
                GitLabProperties.Mode.REST,
                FakeRepositoryPort.of(entity(10L, 2159L, "mptp/mptp8", "mptp8", "backend")),
                ports).syncAll();

        assertThat(ports.mrBatches.get(0).get(0).issueKey()).isEqualTo("MPTPSUPP-77");
        ActivityEventUpsertCommand event = ports.eventBatches.get(0).get(0);
        assertThat(event.type()).isEqualTo(ActivityEventTypes.MR_MERGED);
        assertThat(event.issueKey()).isEqualTo("MPTPSUPP-77");
        assertThat(event.sourceRef()).isEqualTo("2159:mr:10");
        assertThat(ports.workstreamBatches).hasSize(1);
        WorkstreamUpsertCommand ws = ports.workstreamBatches.get(0).get(0);
        assertThat(ws.issueKey()).isEqualTo("MPTPSUPP-77");
        assertThat(ws.workstreamTypeCode()).isEqualTo("backend");
        assertThat(ws.repositoryId()).isEqualTo(10L);
        assertThat(ws.derivedStatus()).isEqualTo(WorkstreamDerivedStatuses.MERGED);
        assertThat(ws.issueId()).isNull();
    }

    @Test
    void unparsableTimestampBecomesNullWithoutFailingSync() {
        FakeGitLabClient client = FakeGitLabClient.singleProject(
                project(2159L, "mptp8", "mptp/mptp8"),
                List.of(),
                List.of(new GitLabCommitDto(
                        "bad", "bad", "t", "m", "a", "a@e", null, "a", "a@e", "not-a-date", null)),
                List.of());
        RecordingPorts ports = RecordingPorts.createAll();

        GitLabSyncResult result = newService(
                client,
                properties(50, 30),
                GitLabProperties.Mode.REST,
                FakeRepositoryPort.of(entity(10L, 2159L, "mptp/mptp8", "mptp8", "backend")),
                ports).syncAll();

        assertThat(result.errors()).isEmpty();
        assertThat(ports.commitBatches.get(0).get(0).committedAt()).isNull();
    }

    @Test
    void recordsClientExceptionForOneProjectAndContinuesWithOthers() {
        FakeGitLabClient client = new FakeGitLabClient(
                project(2159L, "mptp8", "mptp/mptp8"),
                List.of(branch("master", commit("aaa", "2026-07-10T10:00:00Z"))),
                List.of(),
                List.of()) {
            @Override
            public Mono<GitLabProjectDto> getProject(String projectIdOrPath) {
                if ("760".equals(projectIdOrPath)) {
                    return Mono.error(new GitLabClientException("boom", 401, List.of("Unauthorized")));
                }
                return super.getProject(projectIdOrPath);
            }
        };

        RecordingPorts ports = RecordingPorts.createAll();
        FakeRepositoryPort repos = FakeRepositoryPort.of(
                entity(1L, 760L, "mptp/mptp-react-native", "mptp-react-native", "frontend"),
                entity(2L, 2159L, "mptp/mptp8", "mptp8", "backend"));

        GitLabSyncResult result = newService(
                client, properties(50, 30), GitLabProperties.Mode.REST, repos, ports).syncAll();

        assertThat(result.projectsSynced()).isEqualTo(1);
        assertThat(result.branchesFetched()).isEqualTo(1);
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0)).contains("project 760", "HTTP 401", "boom", "Unauthorized");
        assertThat(ports.branchBatches).hasSize(1);
        assertThat(ports.branchBatches.get(0).get(0).repositoryId()).isEqualTo(2L);
    }

    @Test
    void syncProjectMatchesDbRepositoryByIdOrPath() {
        FakeGitLabClient client = FakeGitLabClient.singleProject(
                project(2159L, "mptp8", "mptp/mptp8"),
                List.of(branch("master", commit("aaa", "2026-07-10T10:00:00Z"))),
                List.of(),
                List.of());
        FakeRepositoryPort repos = FakeRepositoryPort.of(
                entity(1L, 760L, "mptp/mptp-react-native", "mptp-react-native", "frontend"),
                entity(2L, 2159L, "mptp/mptp8", "mptp8", "backend"));

        GitLabSyncService service = newService(
                client, properties(50, 30), GitLabProperties.Mode.REST, repos, RecordingPorts.createAll());

        assertThat(service.syncProject("2159").projectsSynced()).isEqualTo(1);
        assertThat(service.syncProject("mptp/mptp8").projectsSynced()).isEqualTo(1);
        GitLabSyncResult unknown = service.syncProject("9999");
        assertThat(unknown.projectsSynced()).isZero();
        assertThat(unknown.errors().get(0)).contains("No observed repository");
    }

    @Test
    void syncProjectRejectsBlankIdentifier() {
        FakeGitLabClient client = FakeGitLabClient.singleProject(
                project(2159L, "mptp8", "mptp/mptp8"), List.of(), List.of(), List.of());
        GitLabSyncResult result = newService(
                client,
                properties(50, 30),
                GitLabProperties.Mode.REST,
                FakeRepositoryPort.of(entity(10L, 2159L, "mptp/mptp8", "mptp8", "backend")),
                RecordingPorts.createAll()).syncProject("  ");

        assertThat(result.errors()).containsExactly("projectIdOrPath must not be blank");
        assertThat(client.projectCalls()).isZero();
    }

    @Test
    void listsMergeRequestsWithStateAll() {
        AtomicReference<String> capturedState = new AtomicReference<>();
        FakeGitLabClient client = new FakeGitLabClient(
                project(2159L, "mptp8", "mptp/mptp8"), List.of(), List.of(), List.of()) {
            @Override
            public Mono<List<GitLabMergeRequestDto>> listMergeRequests(
                    String projectIdOrPath, String state, int page, int perPage) {
                capturedState.set(state);
                return super.listMergeRequests(projectIdOrPath, state, page, perPage);
            }
        };

        newService(
                client,
                properties(50, 30),
                GitLabProperties.Mode.REST,
                FakeRepositoryPort.of(entity(10L, 2159L, "mptp/mptp8", "mptp8", "backend")),
                RecordingPorts.createAll()).syncAll();

        assertThat(capturedState.get()).isEqualTo("all");
    }

    @Test
    void aggregatesCreatedAndUpdatedFromPersistencePorts() {
        FakeGitLabClient client = FakeGitLabClient.singleProject(
                project(2159L, "mptp8", "mptp/mptp8"),
                List.of(branch("master", commit("aaa", "2026-07-10T10:00:00Z"))),
                List.of(commit("aaa", "2026-07-10T10:00:00Z")),
                List.of(mergeRequest(1L, "opened", "master")));

        RecordingPorts ports = new RecordingPorts(
                cmds -> new BranchUpsertOutcome(0, cmds.size()),
                cmds -> new CommitUpsertOutcome(cmds.size(), 0),
                cmds -> new MergeRequestUpsertOutcome(0, cmds.size()),
                cmds -> new ActivityEventUpsertOutcome(0, cmds.size()),
                cmds -> new WorkstreamUpsertOutcome(0, cmds.size()));

        GitLabSyncResult result = newService(
                client,
                properties(50, 30),
                GitLabProperties.Mode.REST,
                FakeRepositoryPort.of(entity(10L, 2159L, "mptp/mptp8", "mptp8", "backend")),
                ports).syncAll();

        // MR source_branch=master → no issue_key → no workstream; 1+0+0 created, 1+1+3+0 updated
        assertThat(result.created()).isEqualTo(1);
        assertThat(result.updated()).isEqualTo(5);
        assertThat(result.saved()).isEqualTo(6);
        assertThat(ports.workstreamBatches).isEmpty();
    }

    // --- helpers ---

    private static GitLabSyncService newService(
            GitLabClient client,
            GitLabSyncProperties props,
            GitLabProperties.Mode mode,
            RepositoryPersistencePort repos,
            RecordingPorts ports) {
        return new GitLabSyncService(
                client,
                props,
                gitLabProperties(mode),
                repos,
                ports.branches,
                ports.commits,
                ports.mrs,
                ports.events,
                ports.workstreams,
                ISSUE_KEY_EXTRACTOR,
                OBJECT_MAPPER);
    }

    private static GitLabProperties gitLabProperties(GitLabProperties.Mode mode) {
        GitLabProperties properties = new GitLabProperties();
        properties.setMode(mode);
        properties.setToken(mode == GitLabProperties.Mode.REST ? "test-token" : "");
        properties.setConnectTimeout(Duration.ofSeconds(1));
        properties.setResponseTimeout(Duration.ofSeconds(1));
        return properties;
    }

    private static GitLabSyncProperties properties(
            int pageSize, int commitHistoryDays, GitLabSyncProperties.Repository... repos) {
        GitLabSyncProperties properties = new GitLabSyncProperties();
        properties.setPageSize(pageSize);
        properties.setCommitHistoryDays(commitHistoryDays);
        properties.setRepositories(List.of(repos));
        return properties;
    }

    private static GitLabSyncProperties.Repository yamlRepo(Long id, String path, String type) {
        GitLabSyncProperties.Repository repository = new GitLabSyncProperties.Repository();
        repository.setGitlabId(id);
        repository.setPath(path);
        repository.setWorkstreamTypeCode(type);
        return repository;
    }

    private static RepositoryEntity entity(
            long id, long gitlabProjectId, String path, String name, String type) {
        RepositoryEntity entity = new RepositoryEntity(gitlabProjectId);
        entity.applyUpsert(new RepositoryUpsertCommand(gitlabProjectId, path, name, type));
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    private static GitLabProjectDto project(Long id, String name, String pathWithNamespace) {
        return new GitLabProjectDto(
                id, name, name, pathWithNamespace, "master", "https://git/" + pathWithNamespace, null);
    }

    private static GitLabBranchDto branch(String name, GitLabCommitDto tip) {
        return new GitLabBranchDto(name, tip, false, false, "master".equals(name), "https://git/tree/" + name);
    }

    private static GitLabCommitDto commit(String sha, String committedDate) {
        return new GitLabCommitDto(
                sha, sha.substring(0, Math.min(8, sha.length())), "title " + sha, "message " + sha,
                "Author", "a@example.com", committedDate, "Author", "a@example.com", committedDate,
                "https://git/commit/" + sha);
    }

    private static GitLabMergeRequestDto mergeRequest(long iid, String state, String sourceBranch) {
        return new GitLabMergeRequestDto(
                10_000L + iid, iid, 2159L, "MR !" + iid, null, state, null,
                "2026-07-10T13:00:00Z", "2026-07-10T14:00:00Z",
                sourceBranch, "master", "https://git/mr/" + iid, null);
    }

    private static final class RecordingPorts {
        final List<List<BranchUpsertCommand>> branchBatches = new ArrayList<>();
        final List<List<CommitUpsertCommand>> commitBatches = new ArrayList<>();
        final List<List<MergeRequestUpsertCommand>> mrBatches = new ArrayList<>();
        final List<List<ActivityEventUpsertCommand>> eventBatches = new ArrayList<>();
        final List<List<WorkstreamUpsertCommand>> workstreamBatches = new ArrayList<>();
        final BranchPersistencePort branches;
        final CommitPersistencePort commits;
        final MergeRequestPersistencePort mrs;
        final ActivityEventPersistencePort events;
        final WorkstreamPersistencePort workstreams;

        static RecordingPorts createAll() {
            return new RecordingPorts(
                    cmds -> new BranchUpsertOutcome(cmds.size(), 0),
                    cmds -> new CommitUpsertOutcome(cmds.size(), 0),
                    cmds -> new MergeRequestUpsertOutcome(cmds.size(), 0),
                    cmds -> new ActivityEventUpsertOutcome(cmds.size(), 0),
                    cmds -> new WorkstreamUpsertOutcome(cmds.size(), 0));
        }

        RecordingPorts(
                Function<List<BranchUpsertCommand>, BranchUpsertOutcome> branchFn,
                Function<List<CommitUpsertCommand>, CommitUpsertOutcome> commitFn,
                Function<List<MergeRequestUpsertCommand>, MergeRequestUpsertOutcome> mrFn,
                Function<List<ActivityEventUpsertCommand>, ActivityEventUpsertOutcome> eventFn,
                Function<List<WorkstreamUpsertCommand>, WorkstreamUpsertOutcome> workstreamFn) {
            this.branches = commands -> {
                List<BranchUpsertCommand> batch = commands != null ? commands : List.of();
                if (!batch.isEmpty()) {
                    branchBatches.add(List.copyOf(batch));
                }
                return branchFn.apply(batch);
            };
            this.commits = commands -> {
                List<CommitUpsertCommand> batch = commands != null ? commands : List.of();
                if (!batch.isEmpty()) {
                    commitBatches.add(List.copyOf(batch));
                }
                return commitFn.apply(batch);
            };
            this.mrs = commands -> {
                List<MergeRequestUpsertCommand> batch = commands != null ? commands : List.of();
                if (!batch.isEmpty()) {
                    mrBatches.add(List.copyOf(batch));
                }
                return mrFn.apply(batch);
            };
            this.events = commands -> {
                List<ActivityEventUpsertCommand> batch = commands != null ? commands : List.of();
                if (!batch.isEmpty()) {
                    eventBatches.add(List.copyOf(batch));
                }
                return eventFn.apply(batch);
            };
            this.workstreams = commands -> {
                List<WorkstreamUpsertCommand> batch = commands != null ? commands : List.of();
                if (!batch.isEmpty()) {
                    workstreamBatches.add(List.copyOf(batch));
                }
                return workstreamFn.apply(batch);
            };
        }
    }

    private static final class FakeRepositoryPort implements RepositoryPersistencePort {
        private final Map<Long, RepositoryEntity> byGitlabId = new LinkedHashMap<>();

        static FakeRepositoryPort of(RepositoryEntity... entities) {
            FakeRepositoryPort port = new FakeRepositoryPort();
            for (RepositoryEntity entity : entities) {
                port.byGitlabId.put(entity.getGitlabProjectId(), entity);
            }
            return port;
        }

        @Override
        public RepositoryUpsertOutcome upsertAll(List<RepositoryUpsertCommand> commands) {
            throw new UnsupportedOperationException("not used by GitLabSyncService");
        }

        @Override
        public Optional<RepositoryEntity> findByGitlabProjectId(long gitlabProjectId) {
            return Optional.ofNullable(byGitlabId.get(gitlabProjectId));
        }

        @Override
        public List<RepositoryEntity> findAllOrdered() {
            return List.copyOf(byGitlabId.values());
        }
    }

    private static class FakeGitLabClient implements GitLabClient {

        private final GitLabProjectDto project;
        private final List<GitLabBranchDto> branches;
        private final List<GitLabCommitDto> commits;
        private final List<GitLabMergeRequestDto> mergeRequests;
        private int projectCalls;

        FakeGitLabClient(
                GitLabProjectDto project,
                List<GitLabBranchDto> branches,
                List<GitLabCommitDto> commits,
                List<GitLabMergeRequestDto> mergeRequests) {
            this.project = project;
            this.branches = branches;
            this.commits = commits;
            this.mergeRequests = mergeRequests;
        }

        static FakeGitLabClient singleProject(
                GitLabProjectDto project,
                List<GitLabBranchDto> branches,
                List<GitLabCommitDto> commits,
                List<GitLabMergeRequestDto> mergeRequests) {
            return new FakeGitLabClient(project, branches, commits, mergeRequests);
        }

        int projectCalls() {
            return projectCalls;
        }

        @Override
        public Mono<GitLabProjectDto> getProject(String projectIdOrPath) {
            projectCalls++;
            if (!matches(projectIdOrPath)) {
                return Mono.error(new GitLabClientException(
                        "not found: " + projectIdOrPath, 404, List.of("404 Project Not Found")));
            }
            return Mono.just(project);
        }

        @Override
        public Mono<List<GitLabBranchDto>> listBranches(String projectIdOrPath, int page, int perPage) {
            if (!matches(projectIdOrPath)) {
                return Mono.error(new GitLabClientException("not found", 404, List.of()));
            }
            return Mono.just(pageOf(branches, page, perPage));
        }

        @Override
        public Mono<List<GitLabCommitDto>> listCommits(
                String projectIdOrPath, Instant since, int page, int perPage) {
            if (!matches(projectIdOrPath)) {
                return Mono.error(new GitLabClientException("not found", 404, List.of()));
            }
            return Mono.just(pageOf(commits, page, perPage));
        }

        @Override
        public Mono<List<GitLabMergeRequestDto>> listMergeRequests(
                String projectIdOrPath, String state, int page, int perPage) {
            if (!matches(projectIdOrPath)) {
                return Mono.error(new GitLabClientException("not found", 404, List.of()));
            }
            return Mono.just(pageOf(mergeRequests, page, perPage));
        }

        @Override
        public Mono<GitLabMergeRequestDto> getMergeRequest(String projectIdOrPath, long mergeRequestIid) {
            return Mono.error(new UnsupportedOperationException("not used by GitLabSyncService"));
        }

        private boolean matches(String projectIdOrPath) {
            if (projectIdOrPath == null) {
                return false;
            }
            return Long.toString(project.id()).equals(projectIdOrPath)
                    || project.pathWithNamespace().equalsIgnoreCase(projectIdOrPath);
        }

        private static <T> List<T> pageOf(List<T> all, int page, int perPage) {
            int from = Math.min(Math.max(page - 1, 0) * perPage, all.size());
            int to = Math.min(from + perPage, all.size());
            return List.copyOf(all.subList(from, to));
        }
    }
}
