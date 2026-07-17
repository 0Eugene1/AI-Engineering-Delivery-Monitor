package ru.eltc.deliverymonitor.integration.gitlab.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;
import ru.eltc.deliverymonitor.integration.gitlab.dto.GitLabBranchDto;
import ru.eltc.deliverymonitor.integration.gitlab.dto.GitLabCommitDto;
import ru.eltc.deliverymonitor.integration.gitlab.dto.GitLabMergeRequestDto;
import ru.eltc.deliverymonitor.integration.gitlab.dto.GitLabProjectDto;
import ru.eltc.deliverymonitor.integration.gitlab.exception.GitLabClientException;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Verifies the offline client serves sanitized demo fixtures and refuses production profiles. */
class MockGitLabClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockGitLabClient newClient(String... activeProfiles) {
        StandardEnvironment environment = new StandardEnvironment();
        if (activeProfiles.length > 0) {
            environment.setActiveProfiles(activeProfiles);
        }
        return new MockGitLabClient(objectMapper, environment);
    }

    @Test
    void getProjectServesDemoProjectByIdAndPath() {
        MockGitLabClient client = newClient();

        GitLabProjectDto byId = client.getProject("2159").block();
        GitLabProjectDto byPath = client.getProject("mptp/mptp8").block();

        assertThat(byId).isNotNull();
        assertThat(byId.id()).isEqualTo(2159L);
        assertThat(byId.name()).isEqualTo("mptp8");
        assertThat(byId.description()).contains("[DEMO]");
        assertThat(byPath.pathWithNamespace()).isEqualTo("mptp/mptp8");
    }

    @Test
    void listBranchesServesFixtureAndPages() {
        MockGitLabClient client = newClient();

        List<GitLabBranchDto> all = client.listBranches("2159", 1, 50).block();
        assertThat(all).hasSize(3);
        assertThat(all.get(1).name()).isEqualTo("feature/MPTPSUPP-90001_demo-banner");

        List<GitLabBranchDto> page = client.listBranches("2159", 2, 1).block();
        assertThat(page).hasSize(1);
        assertThat(page.get(0).name()).isEqualTo("feature/MPTPSUPP-90001_demo-banner");
    }

    @Test
    void listCommitsFiltersBySince() {
        MockGitLabClient client = newClient();

        List<GitLabCommitDto> recent = client
                .listCommits("2159", Instant.parse("2026-07-01T00:00:00Z"), 1, 50)
                .block();

        assertThat(recent).hasSize(2);
        assertThat(recent).extracting(GitLabCommitDto::shortId)
                .containsExactly("bbbbbbbb", "dddddddd");
    }

    @Test
    void listMergeRequestsFiltersByState() {
        MockGitLabClient client = newClient();

        List<GitLabMergeRequestDto> opened = client.listMergeRequests("2159", "opened", 1, 50).block();
        assertThat(opened).hasSize(1);
        assertThat(opened.get(0).iid()).isEqualTo(88L);

        GitLabMergeRequestDto detail = client.getMergeRequest("2159", 87L).block();
        assertThat(detail).isNotNull();
        assertThat(detail.state()).isEqualTo("merged");
    }

    @Test
    void unknownProjectReturns404() {
        assertThatThrownBy(() -> newClient().getProject("999").block())
                .isInstanceOf(GitLabClientException.class)
                .satisfies(ex -> assertThat(((GitLabClientException) ex).getStatusCode()).isEqualTo(404));
    }

    @Test
    void refusesToStartWithProductionProfile() {
        assertThatThrownBy(() -> newClient("prod"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("gitlab.mode=mock is not allowed");
    }
}
