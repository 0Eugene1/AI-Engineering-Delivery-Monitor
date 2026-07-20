package ru.eltc.deliverymonitor.domain.risk;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import ru.eltc.deliverymonitor.domain.gitlab.MergeRequestUpsertCommand;
import ru.eltc.deliverymonitor.domain.gitlab.MergeRequestUpsertService;
import ru.eltc.deliverymonitor.domain.issue.IssueUpsertCommand;
import ru.eltc.deliverymonitor.domain.issue.IssueUpsertService;
import ru.eltc.deliverymonitor.domain.repository.RepositoryJpaRepository;
import ru.eltc.deliverymonitor.domain.timeline.ActivityEventTypes;
import ru.eltc.deliverymonitor.domain.timeline.ActivityEventUpsertCommand;
import ru.eltc.deliverymonitor.domain.timeline.ActivityEventUpsertService;
import ru.eltc.deliverymonitor.domain.workstream.WorkstreamDerivedStatuses;
import ru.eltc.deliverymonitor.domain.workstream.WorkstreamUpsertCommand;
import ru.eltc.deliverymonitor.domain.workstream.WorkstreamUpsertService;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Evaluate-on-read risk rules against Liquibase schema (Phase 4.2).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
        RiskService.class,
        WorkstreamUpsertService.class,
        ActivityEventUpsertService.class,
        MergeRequestUpsertService.class,
        IssueUpsertService.class
})
class RiskServiceIntegrationTest {

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        Path dbFile = tempDbFile();
        registry.add("spring.datasource.url",
                () -> "jdbc:h2:file:" + dbFile + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    private static Path tempDbFile() {
        try {
            File dir = Files.createTempDirectory("delivery-monitor-risk-test").toFile();
            return new File(dir, "testdb").toPath();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Autowired
    private RiskService riskService;

    @Autowired
    private WorkstreamUpsertService workstreamUpsertService;

    @Autowired
    private ActivityEventUpsertService activityEventUpsertService;

    @Autowired
    private MergeRequestUpsertService mergeRequestUpsertService;

    @Autowired
    private IssueUpsertService issueUpsertService;

    @Autowired
    private RepositoryJpaRepository repositoryJpaRepository;

    @Test
    void staleActivityCreatesRisk() {
        Instant now = Instant.parse("2026-07-20T12:00:00Z");
        Long repoId = backendRepoId();
        workstreamUpsertService.upsertAll(List.of(
                new WorkstreamUpsertCommand(
                        "MPTPSUPP-STALE", "backend", repoId, null,
                        WorkstreamDerivedStatuses.IN_PROGRESS)));
        activityEventUpsertService.upsertAll(List.of(
                commitEvent(
                        "MPTPSUPP-STALE",
                        "backend",
                        now.minus(5, ChronoUnit.DAYS),
                        "stale-sha")));

        List<Risk> risks = riskService.evaluateAll(now);

        assertThat(risks)
                .filteredOn(r -> RiskCodes.STALE_ACTIVITY.equals(r.code()))
                .extracting(Risk::issueKey)
                .containsExactly("MPTPSUPP-STALE");
    }

    @Test
    void freshActivityDoesNotCreateStaleRisk() {
        Instant now = Instant.parse("2026-07-20T12:00:00Z");
        Long repoId = backendRepoId();
        workstreamUpsertService.upsertAll(List.of(
                new WorkstreamUpsertCommand(
                        "MPTPSUPP-FRESH", "backend", repoId, null,
                        WorkstreamDerivedStatuses.IN_PROGRESS)));
        activityEventUpsertService.upsertAll(List.of(
                commitEvent(
                        "MPTPSUPP-FRESH",
                        "backend",
                        now.minus(1, ChronoUnit.DAYS),
                        "fresh-sha")));
        // Open MR so NO_MR does not fire — focus assertion on STALE_ACTIVITY absence.
        mergeRequestUpsertService.upsertAll(List.of(
                openMr(repoId, 101L, "MPTPSUPP-FRESH", now.minus(1, ChronoUnit.DAYS))));

        List<Risk> risks = riskService.evaluateAll(now);

        assertThat(risks)
                .filteredOn(r -> RiskCodes.STALE_ACTIVITY.equals(r.code()))
                .isEmpty();
        assertThat(risks)
                .filteredOn(r -> "MPTPSUPP-FRESH".equals(r.issueKey()))
                .extracting(Risk::code)
                .doesNotContain(RiskCodes.STALE_ACTIVITY);
    }

    @Test
    void oldOpenMrCreatesRisk() {
        Instant now = Instant.parse("2026-07-20T12:00:00Z");
        Long repoId = backendRepoId();
        workstreamUpsertService.upsertAll(List.of(
                new WorkstreamUpsertCommand(
                        "MPTPSUPP-MRSTALE", "backend", repoId, null,
                        WorkstreamDerivedStatuses.IN_REVIEW)));
        activityEventUpsertService.upsertAll(List.of(
                commitEvent(
                        "MPTPSUPP-MRSTALE",
                        "backend",
                        now.minus(1, ChronoUnit.DAYS),
                        "mr-stale-sha")));
        mergeRequestUpsertService.upsertAll(List.of(
                openMr(repoId, 88L, "MPTPSUPP-MRSTALE", now.minus(7, ChronoUnit.DAYS))));

        List<Risk> risks = riskService.evaluateAll(now);

        assertThat(risks)
                .filteredOn(r -> RiskCodes.OPEN_MR_STALE.equals(r.code()))
                .singleElement()
                .satisfies(r -> {
                    assertThat(r.issueKey()).isEqualTo("MPTPSUPP-MRSTALE");
                    assertThat(r.severity()).isEqualTo(RiskSeverities.MEDIUM);
                    assertThat(r.evidence()).containsEntry("mergeRequestIid", 88L);
                });
    }

    @Test
    void workstreamWithoutMrCreatesRisk() {
        Instant now = Instant.parse("2026-07-20T12:00:00Z");
        Long repoId = backendRepoId();
        workstreamUpsertService.upsertAll(List.of(
                new WorkstreamUpsertCommand(
                        "MPTPSUPP-NOMR", "backend", repoId, null,
                        WorkstreamDerivedStatuses.IN_PROGRESS)));
        activityEventUpsertService.upsertAll(List.of(
                commitEvent(
                        "MPTPSUPP-NOMR",
                        "backend",
                        now.minus(1, ChronoUnit.DAYS),
                        "nomr-sha")));

        List<Risk> risks = riskService.evaluateAll(now);

        assertThat(risks)
                .filteredOn(r -> RiskCodes.NO_MR.equals(r.code()))
                .extracting(Risk::issueKey)
                .containsExactly("MPTPSUPP-NOMR");
    }

    @Test
    void activeJiraWithoutGitCreatesRisk() {
        Instant now = Instant.parse("2026-07-20T12:00:00Z");
        issueUpsertService.upsertPage(List.of(
                new IssueUpsertCommand(
                        "90001",
                        "MPTPSUPP-NOGIT",
                        "Active without git",
                        "In Progress",
                        RiskService.ACTIVE_JIRA_STATUS_CATEGORY,
                        null,
                        null,
                        "Task",
                        List.of(),
                        List.of(),
                        now.minus(10, ChronoUnit.DAYS),
                        now.minus(1, ChronoUnit.DAYS)),
                new IssueUpsertCommand(
                        "90002",
                        "MPTPSUPP-DONE",
                        "Done issue",
                        "Done",
                        "Done",
                        null,
                        null,
                        "Task",
                        List.of(),
                        List.of(),
                        now.minus(10, ChronoUnit.DAYS),
                        now.minus(1, ChronoUnit.DAYS))));

        List<Risk> risks = riskService.evaluateAll(now);

        assertThat(risks)
                .filteredOn(r -> RiskCodes.JIRA_ACTIVE_NO_GIT.equals(r.code()))
                .extracting(Risk::issueKey)
                .containsExactly("MPTPSUPP-NOGIT");
    }

    private Long backendRepoId() {
        return repositoryJpaRepository.findByGitlabProjectId(2159L)
                .orElseThrow()
                .getId();
    }

    private static ActivityEventUpsertCommand commitEvent(
            String issueKey, String typeCode, Instant occurredAt, String sha) {
        return new ActivityEventUpsertCommand(
                occurredAt,
                issueKey,
                typeCode,
                "dev",
                "Dev",
                ActivityEventTypes.COMMIT,
                "{\"title\":\"wip\",\"shortId\":\"" + sha + "\"}",
                ActivityEventTypes.SOURCE_GITLAB,
                "2159:" + sha);
    }

    private static MergeRequestUpsertCommand openMr(
            Long repoId, Long iid, String issueKey, Instant createdAt) {
        return new MergeRequestUpsertCommand(
                repoId,
                iid,
                iid,
                issueKey,
                "MR for " + issueKey,
                "opened",
                "feature/" + issueKey,
                "main",
                "dev",
                "Dev",
                createdAt,
                createdAt,
                null,
                "https://git.eltc.ru/mr/" + iid);
    }
}
