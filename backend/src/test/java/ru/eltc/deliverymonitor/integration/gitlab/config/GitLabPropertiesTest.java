package ru.eltc.deliverymonitor.integration.gitlab.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies {@code gitlab.*} properties bind with sane defaults and fail-fast when required. */
class GitLabPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(TestConfig.class);

    @Test
    void bindsDefaultsWhenTokenProvidedForRest() {
        contextRunner
                .withPropertyValues("gitlab.token=placeholder-token")
                .run(context -> {
                    GitLabProperties properties = context.getBean(GitLabProperties.class);

                    assertThat(properties.getBaseUrl()).isEqualTo("https://git.eltc.ru");
                    assertThat(properties.getConnectTimeout()).isEqualTo(Duration.ofSeconds(5));
                    assertThat(properties.getResponseTimeout()).isEqualTo(Duration.ofSeconds(10));
                    assertThat(properties.getMode()).isEqualTo(GitLabProperties.Mode.REST);
                    assertThat(properties.getToken()).isEqualTo("placeholder-token");
                });
    }

    @Test
    void bindsOverriddenValues() {
        contextRunner
                .withPropertyValues(
                        "gitlab.base-url=https://gitlab.example.com",
                        "gitlab.connect-timeout=2s",
                        "gitlab.response-timeout=15s",
                        "gitlab.mode=mock",
                        "gitlab.token="
                )
                .run(context -> {
                    GitLabProperties properties = context.getBean(GitLabProperties.class);

                    assertThat(properties.getBaseUrl()).isEqualTo("https://gitlab.example.com");
                    assertThat(properties.getConnectTimeout()).isEqualTo(Duration.ofSeconds(2));
                    assertThat(properties.getResponseTimeout()).isEqualTo(Duration.ofSeconds(15));
                    assertThat(properties.getMode()).isEqualTo(GitLabProperties.Mode.MOCK);
                    assertThat(properties.getToken()).isEmpty();
                });
    }

    @Test
    void failsToStartWhenRestModeAndTokenBlank() {
        contextRunner.run(context -> assertThat(context).hasFailed());
    }

    @Test
    void startsInMockModeWithoutToken() {
        contextRunner
                .withPropertyValues("gitlab.mode=mock")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void failsToStartWhenBaseUrlIsBlank() {
        contextRunner
                .withPropertyValues("gitlab.base-url=", "gitlab.token=some-token")
                .run(context -> assertThat(context).hasFailed());
    }

    @EnableConfigurationProperties(GitLabProperties.class)
    static class TestConfig {
    }
}
