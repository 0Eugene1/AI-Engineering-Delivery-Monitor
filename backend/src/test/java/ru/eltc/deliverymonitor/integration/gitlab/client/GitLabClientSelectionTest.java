package ru.eltc.deliverymonitor.integration.gitlab.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.reactive.function.client.WebClient;
import ru.eltc.deliverymonitor.integration.gitlab.config.GitLabClientConfig;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies {@code gitlab.mode} selects exactly one {@link GitLabClient} implementation. */
class GitLabClientSelectionTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(
                    GitLabClientConfig.class,
                    RestGitLabClient.class,
                    MockGitLabClient.class)
            .withBean(ObjectMapper.class, ObjectMapper::new);

    @Test
    void defaultsToRestClientAndExposesWebClient() {
        contextRunner
                .withPropertyValues("gitlab.token=test-token")
                .run(context -> {
                    assertThat(context).hasSingleBean(GitLabClient.class);
                    assertThat(context.getBean(GitLabClient.class)).isInstanceOf(RestGitLabClient.class);
                    assertThat(context).hasSingleBean(WebClient.class);
                    assertThat(context).doesNotHaveBean(MockGitLabClient.class);
                });
    }

    @Test
    void selectsMockClientWhenModeIsMock() {
        contextRunner
                .withPropertyValues("gitlab.mode=mock")
                .run(context -> {
                    assertThat(context).hasSingleBean(GitLabClient.class);
                    assertThat(context.getBean(GitLabClient.class)).isInstanceOf(MockGitLabClient.class);
                    assertThat(context).doesNotHaveBean(RestGitLabClient.class);
                    assertThat(context).doesNotHaveBean(WebClient.class);
                });
    }

    @Test
    void failsToStartWhenRestModeAndTokenMissing() {
        contextRunner.run(context -> assertThat(context).hasFailed());
    }
}
