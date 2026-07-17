package ru.eltc.deliverymonitor.api.workstream;

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
 * Verifies {@link WorkstreamTypeQueryService} maps Liquibase-seeded active types in sort order.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(WorkstreamTypeQueryService.class)
class WorkstreamTypeQueryServiceIntegrationTest {

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
            File dir = Files.createTempDirectory("delivery-monitor-wst-query-test").toFile();
            return new File(dir, "testdb").toPath();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Autowired
    private WorkstreamTypeQueryService queryService;

    @Test
    void findActiveReturnsSeededTypesInSortOrder() {
        List<WorkstreamTypeResponse> active = queryService.findActive();

        assertThat(active).extracting(WorkstreamTypeResponse::code)
                .containsExactly("backend", "frontend", "oracle", "qa");
        assertThat(active).extracting(WorkstreamTypeResponse::displayName)
                .containsExactly("Backend", "Frontend", "Oracle", "QA");
        assertThat(active).extracting(WorkstreamTypeResponse::sortOrder)
                .containsExactly(1, 2, 3, 4);
    }
}
