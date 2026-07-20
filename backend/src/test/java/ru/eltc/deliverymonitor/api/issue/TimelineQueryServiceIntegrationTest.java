package ru.eltc.deliverymonitor.api.issue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import ru.eltc.deliverymonitor.api.ActivityEventMapper;
import ru.eltc.deliverymonitor.domain.timeline.ActivityEventTypes;
import ru.eltc.deliverymonitor.domain.timeline.ActivityEventUpsertCommand;
import ru.eltc.deliverymonitor.domain.timeline.ActivityEventUpsertService;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end Timeline read against Liquibase schema: events sorted {@code occurred_at DESC},
 * empty key → {@code events: []}, display names from seeded {@code workstream_types}.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ActivityEventUpsertService.class, TimelineQueryService.class, ActivityEventMapper.class,
        ObjectMapper.class})
class TimelineQueryServiceIntegrationTest {

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
            File dir = Files.createTempDirectory("delivery-monitor-timeline-query-test").toFile();
            return new File(dir, "testdb").toPath();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Autowired
    private ActivityEventUpsertService upsertService;

    @Autowired
    private TimelineQueryService timelineQueryService;

    @Test
    void findTimelineOrdersEventsByOccurredAtDescAndEnrichesWorkstreamType() {
        upsertService.upsertAll(List.of(
                new ActivityEventUpsertCommand(
                        Instant.parse("2026-07-09T09:00:00Z"),
                        "MPTPSUPP-100",
                        "backend",
                        null,
                        null,
                        ActivityEventTypes.BRANCH_CREATED,
                        "{\"branch\":\"feature/MPTPSUPP-100\"}",
                        ActivityEventTypes.SOURCE_GITLAB,
                        "2159:branch:feature/MPTPSUPP-100"),
                new ActivityEventUpsertCommand(
                        Instant.parse("2026-07-10T12:00:00Z"),
                        "MPTPSUPP-100",
                        "backend",
                        null,
                        "Demo Dev",
                        ActivityEventTypes.COMMIT,
                        "{\"sha\":\"abc\",\"shortId\":\"abc\",\"title\":\"MPTPSUPP-100 fix\"}",
                        ActivityEventTypes.SOURCE_GITLAB,
                        "2159:abc"),
                new ActivityEventUpsertCommand(
                        Instant.parse("2026-07-11T15:00:00Z"),
                        "MPTPSUPP-100",
                        "backend",
                        "j.doe",
                        "John Doe",
                        ActivityEventTypes.MR_MERGED,
                        "{\"iid\":42,\"title\":\"Merge fix\",\"state\":\"merged\"}",
                        ActivityEventTypes.SOURCE_GITLAB,
                        "2159:mr:42")));

        TimelineResponse timeline = timelineQueryService.findTimeline("MPTPSUPP-100");

        assertThat(timeline.issueKey()).isEqualTo("MPTPSUPP-100");
        assertThat(timeline.events()).hasSize(3);
        assertThat(timeline.events()).extracting(TimelineResponse.TimelineEvent::type)
                .containsExactly(
                        ActivityEventTypes.MR_MERGED,
                        ActivityEventTypes.COMMIT,
                        ActivityEventTypes.BRANCH_CREATED);
        assertThat(timeline.events().get(0).occurredAt())
                .isEqualTo(Instant.parse("2026-07-11T15:00:00Z"));
        assertThat(timeline.events().get(0).workstreamType())
                .isEqualTo(new TimelineResponse.WorkstreamTypeRef("backend", "Backend"));
        assertThat(timeline.events().get(0).actor())
                .isEqualTo(new TimelineResponse.ActorRef("j.doe", "John Doe"));
        assertThat(timeline.events().get(0).summary()).isEqualTo("merged MR !42");
        assertThat(timeline.events().get(1).summary()).isEqualTo("MPTPSUPP-100 fix");
        assertThat(timeline.events().get(2).summary()).isEqualTo("created branch feature/MPTPSUPP-100");
        assertThat(timeline.events().get(2).payload().get("branch").asText())
                .isEqualTo("feature/MPTPSUPP-100");
    }

    @Test
    void findTimelineReturnsEmptyEventsForUnknownKeyWithoutRequiringIssueEntity() {
        TimelineResponse timeline = timelineQueryService.findTimeline("UNKNOWN-NO-EVENTS");

        assertThat(timeline.issueKey()).isEqualTo("UNKNOWN-NO-EVENTS");
        assertThat(timeline.events()).isEmpty();
    }

    @Test
    void findTimelineDoesNotIncludeEventsForOtherIssueKeys() {
        upsertService.upsertAll(List.of(
                new ActivityEventUpsertCommand(
                        Instant.parse("2026-07-10T10:00:00Z"),
                        "MPTPSUPP-A",
                        "frontend",
                        null,
                        null,
                        ActivityEventTypes.COMMIT,
                        "{\"title\":\"A\"}",
                        ActivityEventTypes.SOURCE_GITLAB,
                        "760:aaa"),
                new ActivityEventUpsertCommand(
                        Instant.parse("2026-07-10T11:00:00Z"),
                        "MPTPSUPP-B",
                        "frontend",
                        null,
                        null,
                        ActivityEventTypes.COMMIT,
                        "{\"title\":\"B\"}",
                        ActivityEventTypes.SOURCE_GITLAB,
                        "760:bbb")));

        TimelineResponse timeline = timelineQueryService.findTimeline("MPTPSUPP-A");

        assertThat(timeline.events()).hasSize(1);
        assertThat(timeline.events().get(0).summary()).isEqualTo("A");
        assertThat(timeline.events().get(0).workstreamType().displayName()).isEqualTo("Frontend");
    }
}
