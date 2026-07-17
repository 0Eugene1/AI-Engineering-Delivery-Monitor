package ru.eltc.deliverymonitor.domain.repository;

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
 * End-to-end persistence test for Phase 3.3: real Liquibase changesets
 * ({@code 0003-workstream-types.yaml}, {@code 0004-repositories.yaml}) on H2 PostgreSQL-compat,
 * seed verification, and upsert matching by {@code gitlab_project_id}.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(RepositoryUpsertService.class)
class RepositoryUpsertServiceIntegrationTest {

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
            File dir = Files.createTempDirectory("delivery-monitor-repo-test").toFile();
            return new File(dir, "testdb").toPath();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Autowired
    private RepositoryJpaRepository jpaRepository;

    @Autowired
    private RepositoryUpsertService service;

    @Test
    void seedLoadsThreeDiscoveryRepositoriesMatchedByGitlabProjectId() {
        List<RepositoryEntity> all = service.findAllOrdered();

        assertThat(all).hasSize(3);
        assertThat(all).extracting(RepositoryEntity::getGitlabProjectId)
                .containsExactlyInAnyOrder(760L, 2159L, 3494L);

        assertThat(service.findByGitlabProjectId(2159L)).isPresent().get()
                .satisfies(repo -> {
                    assertThat(repo.getPath()).isEqualTo("mptp/mptp8");
                    assertThat(repo.getName()).isEqualTo("mptp8");
                    assertThat(repo.getWorkstreamTypeCode()).isEqualTo("backend");
                });

        assertThat(service.findByGitlabProjectId(760L)).isPresent().get()
                .extracting(RepositoryEntity::getWorkstreamTypeCode)
                .isEqualTo("frontend");

        assertThat(service.findByGitlabProjectId(3494L)).isPresent().get()
                .extracting(RepositoryEntity::getWorkstreamTypeCode)
                .isEqualTo("oracle");
    }

    @Test
    void upsertByGitlabProjectIdUpdatesPathWithoutDuplicating() {
        RepositoryUpsertOutcome outcome = service.upsertAll(List.of(
                new RepositoryUpsertCommand(2159L, "mptp/mptp8-renamed", "mptp8-renamed", "backend")));

        assertThat(outcome).isEqualTo(new RepositoryUpsertOutcome(0, 1));
        assertThat(jpaRepository.findAll()).hasSize(3);
        assertThat(service.findByGitlabProjectId(2159L)).isPresent().get()
                .satisfies(repo -> {
                    assertThat(repo.getPath()).isEqualTo("mptp/mptp8-renamed");
                    assertThat(repo.getName()).isEqualTo("mptp8-renamed");
                });
    }

    @Test
    void upsertCreatesNewRepositoryWhenGitlabProjectIdIsUnknown() {
        RepositoryUpsertOutcome outcome = service.upsertAll(List.of(
                new RepositoryUpsertCommand(9999L, "demo/new-repo", "new-repo", "qa")));

        assertThat(outcome).isEqualTo(new RepositoryUpsertOutcome(1, 0));
        assertThat(jpaRepository.findAll()).hasSize(4);
        assertThat(service.findByGitlabProjectId(9999L)).isPresent().get()
                .extracting(RepositoryEntity::getWorkstreamTypeCode)
                .isEqualTo("qa");
    }

    @Test
    void rejectsUnknownWorkstreamTypeCode() {
        org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () ->
                service.upsertAll(List.of(
                        new RepositoryUpsertCommand(8888L, "x/y", "y", "does-not-exist"))));
    }
}
