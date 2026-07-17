package ru.eltc.deliverymonitor.domain.workstream_type;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies Phase 3.3 Liquibase seed for {@code workstream_types} against a throwaway H2
 * database in PostgreSQL-compatibility mode (same pattern as issue persistence tests).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class WorkstreamTypeRepositoryIntegrationTest {

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
            File dir = Files.createTempDirectory("delivery-monitor-wst-test").toFile();
            return new File(dir, "testdb").toPath();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Autowired
    private WorkstreamTypeRepository repository;

    @Test
    void seedLoadsFourTeamWorkstreamTypesInSortOrder() {
        List<WorkstreamTypeEntity> active = repository.findAllByActiveTrueOrderBySortOrderAsc();

        assertThat(active).extracting(WorkstreamTypeEntity::getCode)
                .containsExactly("backend", "frontend", "oracle", "qa");
        assertThat(active).extracting(WorkstreamTypeEntity::getDisplayName)
                .containsExactly("Backend", "Frontend", "Oracle", "QA");
        assertThat(active).allMatch(WorkstreamTypeEntity::isActive);
    }

    @Test
    void findByCodeReturnsSeededType() {
        assertThat(repository.findByCode("backend")).isPresent().get()
                .extracting(WorkstreamTypeEntity::getSortOrder)
                .isEqualTo(1);
    }
}
