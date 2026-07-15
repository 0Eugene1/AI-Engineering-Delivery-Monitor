package ru.eltc.deliverymonitor.api.issue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import ru.eltc.deliverymonitor.domain.issue.IssueUpsertCommand;
import ru.eltc.deliverymonitor.domain.issue.IssueUpsertService;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end test for {@link IssueQueryService} against the real Liquibase schema ({@code
 * 0002-issues.yaml}), on a throwaway file-based H2 database in PostgreSQL-compatibility mode
 * (same trick as {@code IssueUpsertServiceIntegrationTest}).
 *
 * <p>Exercises real Hibernate LAZY loading for {@code fixVersions}/{@code labels} — the
 * {@code @ElementCollection} value tables are only fetched on access, so this test would fail
 * with {@code LazyInitializationException} if {@link IssueQueryService} were not
 * {@code @Transactional(readOnly = true)}.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({IssueUpsertService.class, IssueQueryService.class})
class IssueQueryServiceIntegrationTest {

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
            File dir = Files.createTempDirectory("delivery-monitor-issue-query-test").toFile();
            return new File(dir, "testdb").toPath();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Autowired
    private IssueUpsertService upsertService;

    @Autowired
    private IssueQueryService queryService;

    @Test
    void findAllMapsPersistedEntitiesToResponsesIncludingLazyCollections() {
        upsertService.upsertPage(List.of(
                new IssueUpsertCommand("10001", "MPTPSUPP-1", "Fix the thing", "In Review", "In Progress",
                        "j.doe", "John Doe", "Bug", List.of("5.7.27"), List.of("backend", "urgent"),
                        Instant.parse("2026-07-01T09:00:00Z"), Instant.parse("2026-07-10T12:30:00Z")),
                new IssueUpsertCommand("10002", "MPTPSUPP-2", "Unassigned task", "To Do", "To Do",
                        null, null, "Task", List.of(), List.of(), null, null)));

        List<IssueResponse> all = queryService.findAll();

        assertThat(all).hasSize(2);
        IssueResponse first = all.stream().filter(r -> r.issueKey().equals("MPTPSUPP-1")).findFirst().orElseThrow();
        assertThat(first.summary()).isEqualTo("Fix the thing");
        assertThat(first.status()).isEqualTo("In Review");
        assertThat(first.statusCategory()).isEqualTo("In Progress");
        assertThat(first.assigneeUsername()).isEqualTo("j.doe");
        assertThat(first.assigneeDisplayName()).isEqualTo("John Doe");
        assertThat(first.issueType()).isEqualTo("Bug");
        assertThat(first.jiraCreated()).isEqualTo(Instant.parse("2026-07-01T09:00:00Z"));
        assertThat(first.jiraUpdated()).isEqualTo(Instant.parse("2026-07-10T12:30:00Z"));
        assertThat(first.fixVersions()).containsExactly("5.7.27");
        assertThat(first.labels()).containsExactlyInAnyOrder("backend", "urgent");

        IssueResponse second = all.stream().filter(r -> r.issueKey().equals("MPTPSUPP-2")).findFirst().orElseThrow();
        assertThat(second.assigneeUsername()).isNull();
        assertThat(second.fixVersions()).isEmpty();
        assertThat(second.labels()).isEmpty();
    }

    @Test
    void findByKeyReturnsIssueWhenPresent() {
        upsertService.upsertPage(List.of(new IssueUpsertCommand(
                "20001", "MPTPSUPP-3", "Some summary", "Done", "Done",
                "j.doe", "John Doe", "Story", List.of("5.8.0"), List.of("qa"),
                null, null)));

        Optional<IssueResponse> found = queryService.findByKey("MPTPSUPP-3");

        assertThat(found).isPresent();
        assertThat(found.get().issueKey()).isEqualTo("MPTPSUPP-3");
        assertThat(found.get().fixVersions()).containsExactly("5.8.0");
    }

    @Test
    void findByKeyReturnsEmptyWhenIssueKeyIsUnknown() {
        Optional<IssueResponse> found = queryService.findByKey("UNKNOWN-404");

        assertThat(found).isEmpty();
    }
}
