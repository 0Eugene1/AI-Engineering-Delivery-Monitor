package ru.eltc.deliverymonitor.domain.gitlab;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import ru.eltc.deliverymonitor.domain.repository.RepositoryEntity;
import ru.eltc.deliverymonitor.domain.repository.RepositoryJpaRepository;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end persistence test for Phase 3.4: Liquibase {@code 0005-git-entities.yaml} on H2
 * PostgreSQL-compat, FK to seeded {@code repositories}, and matching keys for branches /
 * commits / merge_requests.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({BranchUpsertService.class, CommitUpsertService.class, MergeRequestUpsertService.class})
class GitLabEntitiesPersistenceIntegrationTest {

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
            File dir = Files.createTempDirectory("delivery-monitor-git-entities-test").toFile();
            return new File(dir, "testdb").toPath();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Autowired
    private RepositoryJpaRepository repositoryJpaRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private CommitRepository commitRepository;

    @Autowired
    private MergeRequestRepository mergeRequestRepository;

    @Autowired
    private BranchUpsertService branchUpsertService;

    @Autowired
    private CommitUpsertService commitUpsertService;

    @Autowired
    private MergeRequestUpsertService mergeRequestUpsertService;

    @Test
    void branchUpsertMatchesByRepositoryIdAndNameWithoutDuplicating() {
        Long repositoryId = seededBackendRepositoryId();

        BranchUpsertOutcome created = branchUpsertService.upsertAll(List.of(
                new BranchUpsertCommand(
                        repositoryId, "feature/MPTPSUPP-1", null, "sha1",
                        Instant.parse("2026-07-10T10:00:00Z"), null, null, null)));
        assertThat(created).isEqualTo(new BranchUpsertOutcome(1, 0));

        BranchUpsertOutcome updated = branchUpsertService.upsertAll(List.of(
                new BranchUpsertCommand(
                        repositoryId, "feature/MPTPSUPP-1", null, "sha2",
                        Instant.parse("2026-07-17T10:00:00Z"), "Alice", "a@ex.com", null)));
        assertThat(updated).isEqualTo(new BranchUpsertOutcome(0, 1));
        assertThat(branchRepository.findAll()).hasSize(1);
        assertThat(branchRepository.findByRepositoryIdAndName(repositoryId, "feature/MPTPSUPP-1"))
                .isPresent().get()
                .satisfies(b -> {
                    assertThat(b.getTipCommitSha()).isEqualTo("sha2");
                    assertThat(b.getAuthorName()).isEqualTo("Alice");
                    assertThat(b.getIssueKey()).isNull();
                });
    }

    @Test
    void commitUpsertMatchesByRepositoryIdAndShaAndAllowsOrphanIssueKey() {
        Long repositoryId = seededBackendRepositoryId();

        CommitUpsertOutcome created = commitUpsertService.upsertAll(List.of(
                new CommitUpsertCommand(
                        repositoryId, "deadbeef", null, null, "dead", "title", "msg",
                        "Alice", "a@ex.com", Instant.parse("2026-07-10T12:00:00Z"), null)));
        assertThat(created).isEqualTo(new CommitUpsertOutcome(1, 0));

        CommitUpsertOutcome updated = commitUpsertService.upsertAll(List.of(
                new CommitUpsertCommand(
                        repositoryId, "deadbeef", null, null, "dead", "title2", "msg2",
                        "Bob", "b@ex.com", Instant.parse("2026-07-17T12:00:00Z"), null)));
        assertThat(updated).isEqualTo(new CommitUpsertOutcome(0, 1));
        assertThat(commitRepository.findAll()).hasSize(1);
        assertThat(commitRepository.findByRepositoryIdAndSha(repositoryId, "deadbeef"))
                .isPresent().get()
                .satisfies(c -> {
                    assertThat(c.getTitle()).isEqualTo("title2");
                    assertThat(c.getAuthorName()).isEqualTo("Bob");
                    assertThat(c.getIssueKey()).isNull();
                });
    }

    @Test
    void mergeRequestUpsertMatchesByRepositoryIdAndGitlabIid() {
        Long repositoryId = seededBackendRepositoryId();

        MergeRequestUpsertOutcome created = mergeRequestUpsertService.upsertAll(List.of(
                new MergeRequestUpsertCommand(
                        repositoryId, 88L, 10088L, null, "Open MR", "opened",
                        "feature/X", "main", "alice", "Alice",
                        Instant.parse("2026-07-01T00:00:00Z"), Instant.parse("2026-07-10T00:00:00Z"),
                        null, null)));
        assertThat(created).isEqualTo(new MergeRequestUpsertOutcome(1, 0));

        MergeRequestUpsertOutcome updated = mergeRequestUpsertService.upsertAll(List.of(
                new MergeRequestUpsertCommand(
                        repositoryId, 88L, 10088L, null, "Merged MR", "merged",
                        "feature/X", "main", "alice", "Alice",
                        Instant.parse("2026-07-01T00:00:00Z"), Instant.parse("2026-07-17T00:00:00Z"),
                        Instant.parse("2026-07-17T00:00:00Z"), null)));
        assertThat(updated).isEqualTo(new MergeRequestUpsertOutcome(0, 1));
        assertThat(mergeRequestRepository.findAll()).hasSize(1);
        assertThat(mergeRequestRepository.findByRepositoryIdAndGitlabIid(repositoryId, 88L))
                .isPresent().get()
                .satisfies(mr -> {
                    assertThat(mr.getState()).isEqualTo("merged");
                    assertThat(mr.getMergedAt()).isEqualTo(Instant.parse("2026-07-17T00:00:00Z"));
                    assertThat(mr.getIssueKey()).isNull();
                });
    }

    @Test
    void rejectsUnknownRepositoryIdForeignKey() {
        org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () ->
                branchUpsertService.upsertAll(List.of(
                        new BranchUpsertCommand(
                                999_999L, "orphan-branch", null, null, null, null, null, null))));
    }

    private Long seededBackendRepositoryId() {
        RepositoryEntity backend = repositoryJpaRepository.findByGitlabProjectId(2159L)
                .orElseThrow(() -> new IllegalStateException("seed repository 2159 missing"));
        return backend.getId();
    }
}
