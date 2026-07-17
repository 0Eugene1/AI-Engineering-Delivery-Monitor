package ru.eltc.deliverymonitor.domain.timeline;

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
 * End-to-end persistence test for Phase 3.5: Liquibase {@code 0006-activity-events.yaml} on H2
 * PostgreSQL-compat, UNIQUE {@code (source, source_ref)}, orphan {@code issue_key = null}.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ActivityEventUpsertService.class)
class ActivityEventPersistenceIntegrationTest {

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
            File dir = Files.createTempDirectory("delivery-monitor-activity-events-test").toFile();
            return new File(dir, "testdb").toPath();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Autowired
    private ActivityEventRepository activityEventRepository;

    @Autowired
    private ActivityEventUpsertService activityEventUpsertService;

    @Test
    void upsertMatchesBySourceAndSourceRefWithoutDuplicating() {
        ActivityEventUpsertOutcome created = activityEventUpsertService.upsertAll(List.of(
                event("2159:bbbb", "MPTPSUPP-90001", ActivityEventTypes.COMMIT)));
        assertThat(created).isEqualTo(new ActivityEventUpsertOutcome(1, 0));

        ActivityEventUpsertOutcome updated = activityEventUpsertService.upsertAll(List.of(
                new ActivityEventUpsertCommand(
                        Instant.parse("2026-07-11T12:00:00Z"),
                        "MPTPSUPP-90001",
                        "backend",
                        null,
                        "Demo Dev",
                        ActivityEventTypes.COMMIT,
                        "{\"sha\":\"bbbb\"}",
                        ActivityEventTypes.SOURCE_GITLAB,
                        "2159:bbbb")));
        assertThat(updated).isEqualTo(new ActivityEventUpsertOutcome(0, 1));
        assertThat(activityEventRepository.count()).isEqualTo(1);
        ActivityEventEntity row = activityEventRepository.findAll().get(0);
        assertThat(row.getOccurredAt()).isEqualTo(Instant.parse("2026-07-11T12:00:00Z"));
        assertThat(row.getPayload()).contains("bbbb");
    }

    @Test
    void orphanEventWithNullIssueKeyIsStored() {
        activityEventUpsertService.upsertAll(List.of(
                event("2159:branch:feature/orphan-no-jira-key", null, ActivityEventTypes.BRANCH_CREATED)));

        ActivityEventEntity row = activityEventRepository.findAll().get(0);
        assertThat(row.getIssueKey()).isNull();
        assertThat(row.getSourceRef()).isEqualTo("2159:branch:feature/orphan-no-jira-key");
        assertThat(row.getType()).isEqualTo(ActivityEventTypes.BRANCH_CREATED);
    }

    @Test
    void mergeRequestStateChangeUpdatesSameSourceRef() {
        activityEventUpsertService.upsertAll(List.of(
                event("2159:mr:88", "MPTPSUPP-90001", ActivityEventTypes.MR_OPENED)));
        activityEventUpsertService.upsertAll(List.of(
                event("2159:mr:88", "MPTPSUPP-90001", ActivityEventTypes.MR_MERGED)));

        assertThat(activityEventRepository.count()).isEqualTo(1);
        assertThat(activityEventRepository.findAll().get(0).getType())
                .isEqualTo(ActivityEventTypes.MR_MERGED);
    }

    private static ActivityEventUpsertCommand event(String sourceRef, String issueKey, String type) {
        return new ActivityEventUpsertCommand(
                Instant.parse("2026-07-10T12:00:00Z"),
                issueKey,
                "backend",
                "demo.dev",
                "Demo Dev",
                type,
                "{}",
                ActivityEventTypes.SOURCE_GITLAB,
                sourceRef);
    }
}
