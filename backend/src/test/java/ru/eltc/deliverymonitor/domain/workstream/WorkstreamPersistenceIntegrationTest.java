package ru.eltc.deliverymonitor.domain.workstream;

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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end persistence test for Phase 3.6: Liquibase {@code 0007-workstreams.yaml} on H2
 * PostgreSQL-compat, UNIQUE {@code (issue_key, workstream_type_code)}, nullable {@code repository_id}.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(WorkstreamUpsertService.class)
class WorkstreamPersistenceIntegrationTest {

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
            File dir = Files.createTempDirectory("delivery-monitor-workstreams-test").toFile();
            return new File(dir, "testdb").toPath();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Autowired
    private WorkstreamRepository workstreamRepository;

    @Autowired
    private WorkstreamUpsertService workstreamUpsertService;

    @Test
    void upsertMatchesByIssueKeyAndTypeWithoutDuplicating() {
        // Seeded repositories: gitlab 2159 → id typically 2 (760=1, 2159=2, 3494=3)
        Long repoId = 2L;

        WorkstreamUpsertOutcome created = workstreamUpsertService.upsertAll(List.of(
                new WorkstreamUpsertCommand(
                        "MPTPSUPP-90001",
                        "backend",
                        repoId,
                        null,
                        WorkstreamDerivedStatuses.IN_PROGRESS)));
        assertThat(created).isEqualTo(new WorkstreamUpsertOutcome(1, 0));

        WorkstreamUpsertOutcome updated = workstreamUpsertService.upsertAll(List.of(
                new WorkstreamUpsertCommand(
                        "MPTPSUPP-90001",
                        "backend",
                        repoId,
                        null,
                        WorkstreamDerivedStatuses.IN_REVIEW)));
        assertThat(updated).isEqualTo(new WorkstreamUpsertOutcome(0, 1));
        assertThat(workstreamRepository.count()).isEqualTo(1);

        WorkstreamEntity row = workstreamRepository.findAll().get(0);
        assertThat(row.getIssueKey()).isEqualTo("MPTPSUPP-90001");
        assertThat(row.getWorkstreamTypeCode()).isEqualTo("backend");
        assertThat(row.getRepositoryId()).isEqualTo(repoId);
        assertThat(row.getIssueId()).isNull();
        assertThat(row.getDerivedStatus()).isEqualTo(WorkstreamDerivedStatuses.IN_REVIEW);
    }

    @Test
    void differentTypesForSameIssueAreSeparateWorkstreams() {
        workstreamUpsertService.upsertAll(List.of(
                new WorkstreamUpsertCommand(
                        "MPTPSUPP-90002", "backend", 2L, null, WorkstreamDerivedStatuses.IN_PROGRESS),
                new WorkstreamUpsertCommand(
                        "MPTPSUPP-90002", "frontend", 1L, null, WorkstreamDerivedStatuses.IN_PROGRESS)));

        assertThat(workstreamRepository.count()).isEqualTo(2);
        assertThat(workstreamRepository.findByIssueKeyAndWorkstreamTypeCode("MPTPSUPP-90002", "backend"))
                .isPresent();
        assertThat(workstreamRepository.findByIssueKeyAndWorkstreamTypeCode("MPTPSUPP-90002", "frontend"))
                .isPresent();
    }

    @Test
    void nullRepositoryIdIsAllowedForNonGitType() {
        workstreamUpsertService.upsertAll(List.of(
                new WorkstreamUpsertCommand(
                        "MPTPSUPP-90003",
                        "qa",
                        null,
                        null,
                        WorkstreamDerivedStatuses.IN_PROGRESS)));

        WorkstreamEntity row = workstreamRepository.findAll().get(0);
        assertThat(row.getRepositoryId()).isNull();
        assertThat(row.getWorkstreamTypeCode()).isEqualTo("qa");
    }

    @Test
    void mergedStatusDoesNotDowngradeOnSubsequentInProgressUpsert() {
        workstreamUpsertService.upsertAll(List.of(
                new WorkstreamUpsertCommand(
                        "MPTPSUPP-90004", "backend", 2L, null, WorkstreamDerivedStatuses.MERGED)));
        workstreamUpsertService.upsertAll(List.of(
                new WorkstreamUpsertCommand(
                        "MPTPSUPP-90004", "backend", 2L, null, WorkstreamDerivedStatuses.IN_PROGRESS)));

        assertThat(workstreamRepository.findAll().get(0).getDerivedStatus())
                .isEqualTo(WorkstreamDerivedStatuses.MERGED);
    }
}
