package ru.eltc.deliverymonitor.integration.jira.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies {@code jira.*} properties bind with sane defaults and can be overridden (e.g. via env). */
class JiraPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(TestConfig.class);

    @Test
    void bindsDefaultsWhenNothingConfigured() {
        // jira.auth.token has no default value (no secrets committed) and is now required by
        // fail-fast validation, so it's the only property supplied here — everything else
        // below is asserted against its real default.
        contextRunner
                .withPropertyValues("jira.auth.token=placeholder-token")
                .run(context -> {
                    JiraProperties properties = context.getBean(JiraProperties.class);

                    assertThat(properties.getBaseUrl()).isEqualTo("https://jira.eltc.ru");
                    assertThat(properties.getConnectTimeout()).isEqualTo(Duration.ofSeconds(5));
                    assertThat(properties.getResponseTimeout()).isEqualTo(Duration.ofSeconds(10));
                    assertThat(properties.getProjectKeys()).containsExactly("MPTPSUPP");
                    assertThat(properties.getDefaultFilterId()).isEqualTo(30532L);
                    assertThat(properties.getAuth().getType()).isEqualTo(JiraProperties.AuthType.BEARER);
                    assertThat(properties.getAuth().getUsername()).isEmpty();
                    assertThat(properties.getAuth().getToken()).isEqualTo("placeholder-token");
                });
    }

    @Test
    void bindsOverriddenValues() {
        contextRunner
                .withPropertyValues(
                        "jira.base-url=https://jira.example.com",
                        "jira.connect-timeout=2s",
                        "jira.response-timeout=15s",
                        "jira.project-keys=ABC,DEF",
                        "jira.default-filter-id=999",
                        "jira.auth.type=basic",
                        "jira.auth.username=svc-account",
                        "jira.auth.token=super-secret"
                )
                .run(context -> {
                    JiraProperties properties = context.getBean(JiraProperties.class);

                    assertThat(properties.getBaseUrl()).isEqualTo("https://jira.example.com");
                    assertThat(properties.getConnectTimeout()).isEqualTo(Duration.ofSeconds(2));
                    assertThat(properties.getResponseTimeout()).isEqualTo(Duration.ofSeconds(15));
                    assertThat(properties.getProjectKeys()).containsExactly("ABC", "DEF");
                    assertThat(properties.getDefaultFilterId()).isEqualTo(999L);
                    assertThat(properties.getAuth().getType()).isEqualTo(JiraProperties.AuthType.BASIC);
                    assertThat(properties.getAuth().getUsername()).isEqualTo("svc-account");
                    assertThat(properties.getAuth().getToken()).isEqualTo("super-secret");
                });
    }

    // --- Fail-fast validation: an invalid jira.* config must not produce a usable
    // application context. See JiraProperties (@Validated + Bean Validation annotations).

    @Test
    void failsToStartWhenAuthTokenIsBlank() {
        // Default jira.auth.token is "" (no secrets committed) — must fail @NotBlank.
        contextRunner.run(context -> assertThat(context).hasFailed());
    }

    @Test
    void failsToStartWhenBaseUrlIsBlank() {
        contextRunner
                .withPropertyValues("jira.base-url=", "jira.auth.token=some-token")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void failsToStartWhenBasicAuthIsMissingUsername() {
        contextRunner
                .withPropertyValues("jira.auth.type=basic", "jira.auth.token=some-token")
                // jira.auth.username intentionally left unset (blank default)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void startsWhenBasicAuthHasUsernameAndToken() {
        contextRunner
                .withPropertyValues("jira.auth.type=basic", "jira.auth.username=svc-account", "jira.auth.token=some-token")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @EnableConfigurationProperties(JiraProperties.class)
    static class TestConfig {
    }
}
