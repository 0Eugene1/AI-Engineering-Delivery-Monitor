package ru.eltc.deliverymonitor.integration.jira.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import ru.eltc.deliverymonitor.integration.jira.config.JiraProperties;
import ru.eltc.deliverymonitor.integration.jira.dto.JiraIssueDto;
import ru.eltc.deliverymonitor.integration.jira.dto.JiraSearchResultDto;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Offline {@link JiraContextProvider}: serves <b>sanitized demo data</b> from a bundled fixture
 * so the application (and the layers built on top of it) can be developed without access to a
 * real Jira service account. Active only when {@code jira.mode=mock}.
 *
 * <p><b>Never for production.</b> The data is fake/sanitized (see the fixture header and
 * docs/discovery.md). To prevent accidentally enabling it in a real deployment, construction
 * <b>fails fast</b> if a production Spring profile is active — so {@code jira.mode=mock} cannot
 * silently ship to prod even if the property leaks into that environment.
 */
@Component
@ConditionalOnProperty(name = "jira.mode", havingValue = "mock")
public class MockJiraContextProvider implements JiraContextProvider {

    private static final Logger log = LoggerFactory.getLogger(MockJiraContextProvider.class);

    /** Profiles that must never run with mock Jira data. */
    private static final Set<String> FORBIDDEN_PROFILES = Set.of("prod", "production");

    static final String FIXTURE_PATH = "jira/mock/board-718-filter-30532.json";

    private final JiraProperties properties;
    private final JiraSearchResultDto fixture;

    public MockJiraContextProvider(JiraProperties properties, ObjectMapper objectMapper, Environment environment) {
        assertNotProduction(environment);
        this.properties = properties;
        this.fixture = loadFixture(objectMapper);
        log.warn("Jira is running in MOCK mode: serving SANITIZED DEMO data from classpath:{} - "
                + "not real Jira content. Never enable jira.mode=mock in production.", FIXTURE_PATH);
    }

    private static void assertNotProduction(Environment environment) {
        List<String> active = Arrays.asList(environment.getActiveProfiles());
        boolean prod = active.stream().anyMatch(p -> FORBIDDEN_PROFILES.contains(p.toLowerCase()));
        if (prod) {
            throw new IllegalStateException(
                    "jira.mode=mock is not allowed with a production profile (active profiles: " + active
                            + "). Mock serves demo data only; use jira.mode=rest in production.");
        }
    }

    private static JiraSearchResultDto loadFixture(ObjectMapper objectMapper) {
        ClassPathResource resource = new ClassPathResource(FIXTURE_PATH);
        try (InputStream in = resource.getInputStream()) {
            String json = StreamUtils.copyToString(in, StandardCharsets.UTF_8);
            return objectMapper.readValue(json, JiraSearchResultDto.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load mock Jira fixture: " + FIXTURE_PATH, e);
        }
    }

    @Override
    public JiraBoardContext getBoardContext(int startAt, int maxResults) {
        List<JiraIssueDto> all = fixture.issues() != null ? fixture.issues() : List.of();
        int total = fixture.total() != null ? fixture.total() : all.size();

        int from = Math.min(Math.max(startAt, 0), all.size());
        int to = Math.min(from + Math.max(maxResults, 0), all.size());
        List<JiraIssueDto> page = List.copyOf(all.subList(from, to));

        return new JiraBoardContext(
                properties.getBoardId() != null ? properties.getBoardId() : 0L,
                properties.getDefaultFilterId(),
                startAt,
                maxResults,
                total,
                page,
                Instant.now(),
                true);
    }
}
