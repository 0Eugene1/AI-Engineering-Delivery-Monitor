package ru.eltc.deliverymonitor.integration.jira.config;

import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.junit5.StartStop;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.reactive.function.client.WebClient;
import ru.eltc.deliverymonitor.integration.jira.auth.BasicAuthenticationStrategy;
import ru.eltc.deliverymonitor.integration.jira.auth.BearerTokenAuthenticationStrategy;
import ru.eltc.deliverymonitor.integration.jira.auth.JiraAuthenticationStrategy;
import ru.eltc.deliverymonitor.integration.jira.client.JiraClient;
import ru.eltc.deliverymonitor.integration.jira.dto.JiraSearchResultDto;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies the WebClient/auth-strategy wiring picks the right implementation per {@code jira.auth.type}. */
class JiraClientConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(JiraClientConfig.class);

    @Test
    void defaultsToBearerTokenStrategyAndExposesWebClient() {
        // jira.auth.token is required (fail-fast validation); only it needs to be set here,
        // everything else (base URL, auth type = bearer, timeouts, ...) uses its default.
        contextRunner
                .withPropertyValues("jira.auth.token=test-token")
                .run(context -> {
                    assertThat(context).hasSingleBean(JiraAuthenticationStrategy.class);
                    assertThat(context).hasSingleBean(WebClient.class);
                    assertThat(context.getBean(JiraAuthenticationStrategy.class)).isInstanceOf(BearerTokenAuthenticationStrategy.class);
                });
    }

    @Test
    void usesBasicAuthenticationStrategyWhenConfigured() {
        contextRunner
                .withPropertyValues("jira.auth.type=basic", "jira.auth.username=svc", "jira.auth.token=secret")
                .run(context -> assertThat(context.getBean(JiraAuthenticationStrategy.class))
                        .isInstanceOf(BasicAuthenticationStrategy.class));
    }

    @Test
    void failsToStartWhenAuthTokenIsMissing() {
        // No jira.* properties at all: the default jira.auth.token is "" (no secrets
        // committed), which now fails @NotBlank fail-fast validation on JiraProperties.
        contextRunner.run(context -> assertThat(context).hasFailed());
    }

    @StartStop
    private final MockWebServer server = new MockWebServer();

    @Test
    void webClientDecodesResponsesLargerThanSpringDefaultInMemoryLimit() {
        // Spring's WebClient default in-memory buffer limit is 256 KB. This body is bigger
        // than that but well under JiraClientConfig.MAX_IN_MEMORY_SIZE_BYTES, so it only
        // succeeds because jiraWebClient() actually applies the raised ExchangeStrategies.
        String oversizedSummary = "x".repeat(400_000);
        server.enqueue(new MockResponse.Builder()
                .code(200)
                .addHeader("Content-Type", "application/json")
                .body("""
                        {"startAt":0,"maxResults":50,"total":1,"issues":[
                          {"id":"1","key":"MPTPSUPP-1","self":"s","fields":{"summary":"%s"}}
                        ]}
                        """.formatted(oversizedSummary))
                .build());

        JiraProperties properties = new JiraProperties();
        properties.setBaseUrl(server.url("/").toString());
        properties.getAuth().setToken("test-token");
        JiraClientConfig config = new JiraClientConfig();
        JiraAuthenticationStrategy authStrategy = config.jiraAuthenticationStrategy(properties);
        WebClient webClient = config.jiraWebClient(properties, authStrategy);
        JiraClient client = new JiraClient(webClient);

        JiraSearchResultDto result = client.search("filter=30532", 0, 50, List.of()).block();

        assertThat(result).isNotNull();
        assertThat(result.issues().get(0).fields().summary()).hasSize(400_000);
    }
}
