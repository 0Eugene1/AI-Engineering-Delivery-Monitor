package ru.eltc.deliverymonitor.api.activity;

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
 * Activity Feed read against Liquibase schema: default ordering, since / workstreamType /
 * orphans filters (Phase 4.1).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
        ActivityEventUpsertService.class,
        ActivityQueryService.class,
        ActivityEventMapper.class,
        ObjectMapper.class
})
class ActivityQueryServiceIntegrationTest {

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
            File dir = Files.createTempDirectory("delivery-monitor-activity-query-test").toFile();
            return new File(dir, "testdb").toPath();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Autowired
    private ActivityEventUpsertService upsertService;

    @Autowired
    private ActivityQueryService activityQueryService;

    @Test
    void findFeedWithoutFiltersReturnsEventsNewestFirst() {
        seedMixedEvents();

        ActivityFeedResponse feed = activityQueryService.findFeed(null, null, null, true);

        assertThat(feed.events()).hasSize(4);
        assertThat(feed.events()).extracting(ActivityFeedResponse.ActivityEvent::occurredAt)
                .containsExactly(
                        Instant.parse("2026-07-20T12:00:00Z"),
                        Instant.parse("2026-07-18T12:00:00Z"),
                        Instant.parse("2026-07-16T12:00:00Z"),
                        Instant.parse("2026-07-14T12:00:00Z"));
        assertThat(feed.events().get(0).source()).isEqualTo(ActivityEventTypes.SOURCE_GITLAB);
        assertThat(feed.events().get(0).summary()).isEqualTo("merged MR !1");
        assertThat(feed.events().get(0).workstreamType())
                .isEqualTo(new ActivityFeedResponse.WorkstreamTypeRef("backend", "Backend"));
    }

    @Test
    void findFeedSinceExcludesOlderEvents() {
        seedMixedEvents();

        ActivityFeedResponse feed = activityQueryService.findFeed(
                Instant.parse("2026-07-17T00:00:00Z"), null, null, true);

        assertThat(feed.events()).hasSize(2);
        assertThat(feed.events()).extracting(ActivityFeedResponse.ActivityEvent::occurredAt)
                .containsExactly(
                        Instant.parse("2026-07-20T12:00:00Z"),
                        Instant.parse("2026-07-18T12:00:00Z"));
    }

    @Test
    void findFeedWorkstreamTypeFiltersByType() {
        seedMixedEvents();

        ActivityFeedResponse feed = activityQueryService.findFeed(null, null, "frontend", true);

        assertThat(feed.events()).hasSize(1);
        assertThat(feed.events().get(0).workstreamType().code()).isEqualTo("frontend");
        assertThat(feed.events().get(0).issueKey()).isEqualTo("MPTPSUPP-200");
    }

    @Test
    void findFeedOrphansTrueIncludesEventsWithoutIssueKey() {
        seedMixedEvents();

        ActivityFeedResponse feed = activityQueryService.findFeed(null, null, null, true);

        assertThat(feed.events()).extracting(ActivityFeedResponse.ActivityEvent::issueKey)
                .contains("MPTPSUPP-100", "MPTPSUPP-200", null);
    }

    @Test
    void findFeedOrphansFalseExcludesEventsWithoutIssueKey() {
        seedMixedEvents();

        ActivityFeedResponse feed = activityQueryService.findFeed(null, null, null, false);

        assertThat(feed.events()).hasSize(3);
        assertThat(feed.events()).extracting(ActivityFeedResponse.ActivityEvent::issueKey)
                .doesNotContainNull()
                .containsExactlyInAnyOrder("MPTPSUPP-100", "MPTPSUPP-100", "MPTPSUPP-200");
    }

    private void seedMixedEvents() {
        upsertService.upsertAll(List.of(
                new ActivityEventUpsertCommand(
                        Instant.parse("2026-07-14T12:00:00Z"),
                        "MPTPSUPP-100",
                        "backend",
                        null,
                        null,
                        ActivityEventTypes.BRANCH_CREATED,
                        "{\"branch\":\"feature/MPTPSUPP-100\"}",
                        ActivityEventTypes.SOURCE_GITLAB,
                        "2159:branch:feature/MPTPSUPP-100"),
                new ActivityEventUpsertCommand(
                        Instant.parse("2026-07-16T12:00:00Z"),
                        null,
                        "backend",
                        null,
                        null,
                        ActivityEventTypes.COMMIT,
                        "{\"title\":\"orphan commit\",\"shortId\":\"abc\"}",
                        ActivityEventTypes.SOURCE_GITLAB,
                        "2159:orphan-sha"),
                new ActivityEventUpsertCommand(
                        Instant.parse("2026-07-18T12:00:00Z"),
                        "MPTPSUPP-200",
                        "frontend",
                        "a.dev",
                        "Ann Dev",
                        ActivityEventTypes.COMMIT,
                        "{\"title\":\"UI fix\"}",
                        ActivityEventTypes.SOURCE_GITLAB,
                        "760:ui-sha"),
                new ActivityEventUpsertCommand(
                        Instant.parse("2026-07-20T12:00:00Z"),
                        "MPTPSUPP-100",
                        "backend",
                        "j.doe",
                        "John Doe",
                        ActivityEventTypes.MR_MERGED,
                        "{\"iid\":1,\"state\":\"merged\"}",
                        ActivityEventTypes.SOURCE_GITLAB,
                        "2159:mr:1")));
    }
}
