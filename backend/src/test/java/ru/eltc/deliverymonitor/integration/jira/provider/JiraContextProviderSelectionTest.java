package ru.eltc.deliverymonitor.integration.jira.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import ru.eltc.deliverymonitor.integration.jira.client.JiraClient;
import ru.eltc.deliverymonitor.integration.jira.config.JiraClientConfig;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies {@code jira.mode} selects exactly one {@link JiraContextProvider} implementation. */
class JiraContextProviderSelectionTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(
                    JiraClientConfig.class,
                    JiraClient.class,
                    RestJiraContextProvider.class,
                    MockJiraContextProvider.class)
            .withBean(ObjectMapper.class, ObjectMapper::new);

    @Test
    void defaultsToRestProvider() {
        contextRunner
                .withPropertyValues("jira.auth.token=test-token")
                .run(context -> {
                    assertThat(context).hasSingleBean(JiraContextProvider.class);
                    assertThat(context.getBean(JiraContextProvider.class))
                            .isInstanceOf(RestJiraContextProvider.class);
                    assertThat(context).doesNotHaveBean(MockJiraContextProvider.class);
                });
    }

    @Test
    void selectsMockProviderWhenModeIsMock() {
        contextRunner
                .withPropertyValues("jira.mode=mock", "jira.auth.token=mock-offline-placeholder-token")
                .run(context -> {
                    assertThat(context).hasSingleBean(JiraContextProvider.class);
                    assertThat(context.getBean(JiraContextProvider.class))
                            .isInstanceOf(MockJiraContextProvider.class);
                    assertThat(context).doesNotHaveBean(RestJiraContextProvider.class);
                });
    }
}
