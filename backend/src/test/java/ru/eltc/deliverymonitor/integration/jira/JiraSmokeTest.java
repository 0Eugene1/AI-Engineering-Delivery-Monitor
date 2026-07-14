package ru.eltc.deliverymonitor.integration.jira;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.reactive.function.client.WebClient;
import ru.eltc.deliverymonitor.integration.jira.auth.JiraAuthenticationStrategy;
import ru.eltc.deliverymonitor.integration.jira.client.JiraClient;
import ru.eltc.deliverymonitor.integration.jira.config.JiraClientConfig;
import ru.eltc.deliverymonitor.integration.jira.config.JiraProperties;
import ru.eltc.deliverymonitor.integration.jira.dto.JiraMyselfDto;
import ru.eltc.deliverymonitor.integration.jira.dto.JiraSearchResultDto;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <b>Temporary gate-check before Phase 2.2</b> (docs/roadmap.md) — not part of the regular
 * {@code mvnw verify} run. Exercises the Phase 2.1 {@link JiraClient} against the <b>real</b>
 * Jira Server 8.20.30 (docs/discovery.md §9.1), using exactly the same wiring as production
 * ({@link JiraClientConfig}) — only the config source (env vars, read directly here instead
 * of a running Spring context) differs.
 *
 * <p>Scope is intentionally narrow, matching the gate-check request: only
 * {@code GET /rest/api/2/myself} and {@code GET /rest/api/2/search} (board filter 30532).
 * No sync orchestration, no persistence, no scheduler, no new domain entities.
 *
 * <p><b>Disabled by default</b> — skipped unless {@code JIRA_TOKEN} is set, so it never runs
 * in a normal build/CI and never fails the build for missing credentials. To run it manually
 * against the real instance:
 *
 * <pre>{@code
 * $env:JIRA_TOKEN = "<real PAT or password>"
 * $env:JIRA_AUTH_TYPE = "bearer"          # or "basic" - see docs/discovery.md §1
 * $env:JIRA_USERNAME = "<only for basic>"
 * $env:JIRA_BASE_URL = "https://jira.eltc.ru"   # default, override only if different
 * cd backend
 * .\mvnw.cmd test -Dtest=JiraSmokeTest
 * }</pre>
 *
 * <p>After running, record the result (which auth type actually works, any surprises) in
 * docs/discovery.md §1 and docs/session_log.md — see the gate-check task in session_log.md.
 * Delete this class once the gate-check is done and the result is captured in docs/, unless
 * the team decides to keep it as a standing manual diagnostic.
 */
class JiraSmokeTest {

    private static final Duration BLOCK_TIMEOUT = Duration.ofSeconds(30);

    /** Mirrors {@code application.yml} defaults for the fields this smoke test doesn't force. */
    private JiraClient realJiraClient() {
        JiraProperties properties = new JiraProperties();
        properties.setBaseUrl(env("JIRA_BASE_URL", properties.getBaseUrl()));

        JiraProperties.Auth auth = properties.getAuth();
        String authType = env("JIRA_AUTH_TYPE", "bearer");
        auth.setType("basic".equalsIgnoreCase(authType) ? JiraProperties.AuthType.BASIC : JiraProperties.AuthType.BEARER);
        auth.setUsername(env("JIRA_USERNAME", ""));
        auth.setToken(env("JIRA_TOKEN", ""));

        JiraClientConfig config = new JiraClientConfig();
        JiraAuthenticationStrategy authStrategy = config.jiraAuthenticationStrategy(properties);
        WebClient webClient = config.jiraWebClient(properties, authStrategy);
        return new JiraClient(webClient);
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return (value == null || value.isBlank()) ? fallback : value;
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "JIRA_TOKEN", matches = ".+")
    void myselfAuthenticatesAgainstRealJira() {
        JiraMyselfDto myself = realJiraClient().getMyself().block(BLOCK_TIMEOUT);

        assertThat(myself).isNotNull();
        assertThat(myself.name()).isNotBlank();
        System.out.println("[JiraSmokeTest] /myself OK -> name=" + myself.name()
                + ", displayName=" + myself.displayName() + ", active=" + myself.active());
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "JIRA_TOKEN", matches = ".+")
    void searchByDefaultFilterReturnsIssues() {
        long filterId = Long.parseLong(env("JIRA_DEFAULT_FILTER_ID", "30532"));

        JiraSearchResultDto result = realJiraClient().searchByFilter(filterId, 0, 10).block(BLOCK_TIMEOUT);

        assertThat(result).isNotNull();
        assertThat(result.issues()).isNotNull();
        System.out.println("[JiraSmokeTest] /search?jql=filter=" + filterId + " OK -> total="
                + result.total() + ", returned=" + result.issues().size());
        result.issues().stream().limit(5).forEach(issue ->
                System.out.println("[JiraSmokeTest]   " + issue.key() + " - " + issue.fields().summary()));
    }
}
