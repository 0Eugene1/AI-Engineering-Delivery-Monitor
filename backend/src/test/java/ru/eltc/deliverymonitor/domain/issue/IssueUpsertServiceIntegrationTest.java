package ru.eltc.deliverymonitor.domain.issue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end persistence test for Phase 2.3: runs the real Liquibase changeset ({@code
 * 0002-issues.yaml}) against a throwaway file-based H2 database in PostgreSQL-compatibility mode
 * (same trick as {@code DeliveryMonitorApplicationTests}), then exercises {@link
 * IssueUpsertService} against the real schema — catching column/constraint mismatches that a pure
 * mock-based unit test cannot.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(IssueUpsertService.class)
class IssueUpsertServiceIntegrationTest {

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
            File dir = Files.createTempDirectory("delivery-monitor-issue-test").toFile();
            return new File(dir, "testdb").toPath();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Autowired
    private IssueRepository repository;

    @Autowired
    private IssueUpsertService service;

    @Test
    void schemaMatchesEntityAndPersistsANewIssue() {
        IssueUpsertCommand command = new IssueUpsertCommand(
                "10001", "MPTPSUPP-1", "Fix the thing", "In Review", "In Progress",
                "j.doe", "John Doe", "Bug", List.of("5.7.27"), List.of("backend", "urgent"),
                Instant.parse("2026-07-01T09:00:00Z"), Instant.parse("2026-07-10T12:30:00Z"));

        IssueUpsertOutcome outcome = service.upsertPage(List.of(command));

        assertThat(outcome).isEqualTo(new IssueUpsertOutcome(1, 0));
        List<IssueEntity> all = repository.findAll();
        assertThat(all).hasSize(1);
        IssueEntity saved = all.get(0);
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getJiraId()).isEqualTo("10001");
        assertThat(saved.getKey()).isEqualTo("MPTPSUPP-1");
        assertThat(saved.getFixVersions()).containsExactly("5.7.27");
        assertThat(saved.getLabels()).containsExactlyInAnyOrder("backend", "urgent");
        assertThat(saved.getSyncedAt()).isNotNull();
    }

    @Test
    void secondUpsertWithSameJiraIdUpdatesRatherThanDuplicates() {
        IssueUpsertCommand first = new IssueUpsertCommand(
                "20001", "MPTPSUPP-2", "Original summary", "To Do", "To Do",
                null, null, "Task", List.of(), List.of(), null, null);
        service.upsertPage(List.of(first));

        IssueUpsertCommand second = new IssueUpsertCommand(
                "20001", "MPTPSUPP-2", "Updated summary", "Done", "Done",
                "j.doe", "John Doe", "Task", List.of("5.8.0"), List.of("urgent"), null, null);
        IssueUpsertOutcome outcome = service.upsertPage(List.of(second));

        assertThat(outcome).isEqualTo(new IssueUpsertOutcome(0, 1));
        List<IssueEntity> all = repository.findAll();
        assertThat(all).hasSize(1);
        assertThat(all.get(0).getSummary()).isEqualTo("Updated summary");
        assertThat(all.get(0).getFixVersions()).containsExactly("5.8.0");
    }

    @Test
    void rejectsDuplicateKeyAcrossDifferentJiraIds() {
        service.upsertPage(List.of(new IssueUpsertCommand(
                "30001", "MPTPSUPP-3", "s", null, null, null, null, null,
                List.of(), List.of(), null, null)));

        org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () ->
                service.upsertPage(List.of(new IssueUpsertCommand(
                        "30002", "MPTPSUPP-3", "s2", null, null, null, null, null,
                        List.of(), List.of(), null, null))));
    }
}
