package ru.eltc.deliverymonitor;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the Spring context boots on a real HTTP port and
 * {@code /actuator/health} responds with UP. A real Postgres is not required
 * for this Skeleton smoke test: Liquibase/JPA point at a throwaway file-based
 * H2 database in PostgreSQL-compatibility mode, so this runs without Docker
 * while still exercising the Liquibase wiring end-to-end.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DeliveryMonitorApplicationTests {

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        Path dbFile = tempDbFile();
        registry.add("spring.datasource.url",
                () -> "jdbc:h2:file:" + dbFile + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        // JiraProperties.Auth#token is fail-fast validated (@NotBlank) and empty by default
        // (no secrets committed). This placeholder is not a real credential — it only lets
        // the full application context start for this smoke test; no Jira call is made here.
        registry.add("jira.auth.token", () -> "test-only-placeholder-token");
        // Same fail-fast pattern for the admin Bearer token (Phase 2.4, ADR-012) — placeholder
        // only, not a real secret; adminSyncJiraRequiresAuthentication below never presents it,
        // so it never reaches JiraSyncService (no real Jira/PostgreSQL call from this test).
        registry.add("delivery-monitor.admin.token", () -> "test-only-placeholder-admin-token");
    }

    private static Path tempDbFile() {
        try {
            File dir = Files.createTempDirectory("delivery-monitor-test").toFile();
            return new File(dir, "testdb").toPath();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void contextLoads() {
        // If the context fails to start (bad config, broken Liquibase changelog, etc.), this fails.
    }

    @Test
    void actuatorHealthReturnsUp() {
        TestRestTemplate restTemplate = new TestRestTemplate();
        ResponseEntity<String> response =
                restTemplate.getForEntity("http://localhost:" + port + "/actuator/health", String.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    void adminSyncJiraRequiresAuthentication() {
        // No Authorization header is sent, so Spring Security rejects the request with 401
        // before it ever reaches JiraSyncController/JiraSyncService — no real Jira or PostgreSQL
        // call happens here (Phase 2.4, ADR-012).
        TestRestTemplate restTemplate = new TestRestTemplate();
        ResponseEntity<String> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/admin/sync/jira", null, String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void actuatorInfoRequiresAuthenticationButHealthDoesNot() {
        // ADR-012 endpoint policy: only /actuator/health (already covered by
        // actuatorHealthReturnsUp above) is public; every other actuator endpoint — e.g. /info,
        // which can leak build/configuration details — requires the admin token.
        TestRestTemplate restTemplate = new TestRestTemplate();
        ResponseEntity<String> response =
                restTemplate.getForEntity("http://localhost:" + port + "/actuator/info", String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void issuesEndpointIsPubliclyAccessibleAndReadsFromPostgres() {
        // Read API (docs/api.md "Conventions"): GET /api/** stays open within the VPN perimeter,
        // unlike /api/admin/**. Security baseline is unchanged by this endpoint. Against the
        // empty H2 schema this simply returns an empty list — no real Jira/PostgreSQL data.
        TestRestTemplate restTemplate = new TestRestTemplate();
        ResponseEntity<String> response =
                restTemplate.getForEntity("http://localhost:" + port + "/api/issues", String.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isEqualTo("[]");
    }
}
