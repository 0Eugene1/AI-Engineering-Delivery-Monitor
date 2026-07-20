package ru.eltc.deliverymonitor.sync.gitlab;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Binding / validation checks for {@link GitLabSyncProperties} ({@code gitlab.sync.*}).
 */
class GitLabSyncPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Test
    void bindsDefaultsAndRepositorySeed() {
        contextRunner
                .withPropertyValues(
                        "gitlab.sync.repositories[0].gitlab-id=2159",
                        "gitlab.sync.repositories[0].path=mptp/mptp8",
                        "gitlab.sync.repositories[0].workstream-type-code=backend")
                .run(context -> {
                    GitLabSyncProperties properties = context.getBean(GitLabSyncProperties.class);
                    assertThat(properties.getPageSize()).isEqualTo(50);
                    assertThat(properties.getCommitHistoryDays()).isEqualTo(30);
                    assertThat(properties.isEnabled()).isFalse();
                    assertThat(properties.getInterval()).isEqualTo(Duration.ofMinutes(10));
                    assertThat(properties.getRepositories()).hasSize(1);
                    GitLabSyncProperties.Repository repo = properties.getRepositories().get(0);
                    assertThat(repo.getGitlabId()).isEqualTo(2159L);
                    assertThat(repo.getPath()).isEqualTo("mptp/mptp8");
                    assertThat(repo.getWorkstreamTypeCode()).isEqualTo("backend");
                    assertThat(repo.projectIdOrPath()).isEqualTo("2159");
                });
    }

    @Test
    void failsWhenWorkstreamTypeCodeMissing() {
        contextRunner
                .withPropertyValues(
                        "gitlab.sync.repositories[0].gitlab-id=2159",
                        "gitlab.sync.repositories[0].path=mptp/mptp8")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void failsWhenNeitherGitlabIdNorPathSet() {
        contextRunner
                .withPropertyValues(
                        "gitlab.sync.repositories[0].workstream-type-code=backend")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void failsWhenPageSizeBelowOne() {
        contextRunner
                .withPropertyValues("gitlab.sync.page-size=0")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void failsWhenCommitHistoryDaysBelowOne() {
        contextRunner
                .withPropertyValues("gitlab.sync.commit-history-days=0")
                .run(context -> assertThat(context).hasFailed());
    }

    @EnableConfigurationProperties(GitLabSyncProperties.class)
    static class TestConfig {
    }
}
