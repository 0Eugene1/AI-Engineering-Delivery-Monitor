package ru.eltc.deliverymonitor.api.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies {@code delivery-monitor.admin.*} binds and is fail-fast, mirroring {@code
 * JiraPropertiesTest}'s pattern for {@code jira.auth.token}.
 */
class AdminTokenPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(TestConfig.class);

    @Test
    void bindsConfiguredToken() {
        contextRunner
                .withPropertyValues("delivery-monitor.admin.token=some-admin-token")
                .run(context -> {
                    AdminTokenProperties properties = context.getBean(AdminTokenProperties.class);
                    assertThat(properties.getToken()).isEqualTo("some-admin-token");
                });
    }

    @Test
    void failsToStartWhenTokenIsBlank() {
        // Default delivery-monitor.admin.token is "" (no secrets committed) — must fail @NotBlank,
        // the same fail-fast policy as JIRA_TOKEN. Force an empty property so an ambient
        // DELIVERY_MONITOR_ADMIN_TOKEN on the developer machine cannot mask the blank case.
        contextRunner
                .withPropertyValues("delivery-monitor.admin.token=")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void failsToStartWhenTokenIsExplicitlyBlank() {
        contextRunner
                .withPropertyValues("delivery-monitor.admin.token=")
                .run(context -> assertThat(context).hasFailed());
    }

    @EnableConfigurationProperties(AdminTokenProperties.class)
    static class TestConfig {
    }
}
