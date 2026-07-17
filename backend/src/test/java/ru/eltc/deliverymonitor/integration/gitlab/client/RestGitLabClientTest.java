package ru.eltc.deliverymonitor.integration.gitlab.client;

import io.netty.channel.ChannelOption;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import mockwebserver3.junit5.StartStop;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import ru.eltc.deliverymonitor.integration.gitlab.config.GitLabClientConfig;
import ru.eltc.deliverymonitor.integration.gitlab.dto.GitLabBranchDto;
import ru.eltc.deliverymonitor.integration.gitlab.dto.GitLabCommitDto;
import ru.eltc.deliverymonitor.integration.gitlab.dto.GitLabMergeRequestDto;
import ru.eltc.deliverymonitor.integration.gitlab.dto.GitLabProjectDto;
import ru.eltc.deliverymonitor.integration.gitlab.exception.GitLabClientException;

import java.net.ConnectException;
import java.net.URI;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** End-to-end RestGitLabClient tests against a mock GitLab server — no real GitLab needed. */
class RestGitLabClientTest {

    @StartStop
    private final MockWebServer server = new MockWebServer();

    private RestGitLabClient newClient() {
        WebClient webClient = WebClient.builder()
                .baseUrl(server.url("/").toString())
                .filter(GitLabClientConfig.privateTokenFilter("test-gitlab-token"))
                .build();
        return new RestGitLabClient(webClient);
    }

    @Test
    void getProjectParsesSuccessAndSendsPrivateToken() throws InterruptedException {
        server.enqueue(new MockResponse.Builder()
                .code(200)
                .addHeader("Content-Type", "application/json")
                .body("""
                        {
                          "id": 2159,
                          "name": "mptp8",
                          "path": "mptp8",
                          "path_with_namespace": "mptp/mptp8",
                          "default_branch": "master",
                          "web_url": "https://git.eltc.ru/mptp/mptp8"
                        }
                        """)
                .build());

        GitLabProjectDto project = newClient().getProject("2159").block();

        assertThat(project).isNotNull();
        assertThat(project.id()).isEqualTo(2159L);
        assertThat(project.pathWithNamespace()).isEqualTo("mptp/mptp8");

        RecordedRequest recorded = server.takeRequest();
        assertThat(recorded.getTarget()).isEqualTo("/api/v4/projects/2159");
        assertThat(recorded.getHeaders().get("PRIVATE-TOKEN")).isEqualTo("test-gitlab-token");
    }

    @Test
    void getProjectEncodesPathWithNamespaceSlash() throws InterruptedException {
        server.enqueue(new MockResponse.Builder()
                .code(200)
                .addHeader("Content-Type", "application/json")
                .body("""
                        {"id":2159,"name":"mptp8","path":"mptp8","path_with_namespace":"mptp/mptp8"}
                        """)
                .build());

        newClient().getProject("mptp/mptp8").block();

        RecordedRequest recorded = server.takeRequest();
        assertThat(recorded.getTarget()).isEqualTo("/api/v4/projects/mptp%2Fmptp8");
    }

    @Test
    void listBranchesParsesPage() throws InterruptedException {
        server.enqueue(new MockResponse.Builder()
                .code(200)
                .addHeader("Content-Type", "application/json")
                .body("""
                        [
                          {
                            "name": "feature/MPTPSUPP-1234",
                            "merged": false,
                            "protected": false,
                            "default": false,
                            "web_url": "https://git.eltc.ru/mptp/mptp8/-/tree/feature/MPTPSUPP-1234",
                            "commit": {
                              "id": "abc123",
                              "short_id": "abc123",
                              "title": "MPTPSUPP-1234: fix",
                              "message": "MPTPSUPP-1234: fix\\n",
                              "author_name": "Dev",
                              "committed_date": "2026-07-10T12:00:00.000Z"
                            }
                          }
                        ]
                        """)
                .build());

        List<GitLabBranchDto> branches = newClient().listBranches("2159", 1, 20).block();

        assertThat(branches).hasSize(1);
        assertThat(branches.get(0).name()).isEqualTo("feature/MPTPSUPP-1234");
        assertThat(branches.get(0).commit().authorName()).isEqualTo("Dev");
        assertThat(branches.get(0).protectedBranch()).isFalse();

        RecordedRequest recorded = server.takeRequest();
        assertThat(recorded.getTarget()).contains("/api/v4/projects/2159/repository/branches");
        assertThat(recorded.getTarget()).contains("page=1");
        assertThat(recorded.getTarget()).contains("per_page=20");
    }

    @Test
    void listCommitsPassesSinceQueryParam() throws InterruptedException {
        server.enqueue(new MockResponse.Builder()
                .code(200)
                .addHeader("Content-Type", "application/json")
                .body("""
                        [
                          {
                            "id": "deadbeef",
                            "short_id": "deadbeef",
                            "title": "MPTPSUPP-1: work",
                            "message": "MPTPSUPP-1: work\\n",
                            "author_name": "Dev",
                            "committed_date": "2026-07-10T12:00:00.000Z"
                          }
                        ]
                        """)
                .build());

        Instant since = Instant.parse("2026-07-01T00:00:00Z");
        List<GitLabCommitDto> commits = newClient().listCommits("2159", since, 1, 50).block();

        assertThat(commits).hasSize(1);
        assertThat(commits.get(0).id()).isEqualTo("deadbeef");

        RecordedRequest recorded = server.takeRequest();
        assertThat(recorded.getTarget()).contains("/api/v4/projects/2159/repository/commits");
        assertThat(recorded.getTarget()).contains("since=2026-07-01T00:00:00Z");
    }

    @Test
    void listMergeRequestsParsesAuthorAndStateFilter() throws InterruptedException {
        server.enqueue(new MockResponse.Builder()
                .code(200)
                .addHeader("Content-Type", "application/json")
                .body("""
                        [
                          {
                            "id": 10001,
                            "iid": 88,
                            "project_id": 2159,
                            "title": "MPTPSUPP-1234: MR",
                            "state": "opened",
                            "source_branch": "feature/MPTPSUPP-1234",
                            "target_branch": "master",
                            "web_url": "https://git.eltc.ru/mptp/mptp8/-/merge_requests/88",
                            "author": { "id": 1, "username": "dev", "name": "Dev" }
                          }
                        ]
                        """)
                .build());

        List<GitLabMergeRequestDto> mrs = newClient()
                .listMergeRequests("2159", "opened", 1, 20)
                .block();

        assertThat(mrs).hasSize(1);
        assertThat(mrs.get(0).iid()).isEqualTo(88L);
        assertThat(mrs.get(0).author().username()).isEqualTo("dev");

        RecordedRequest recorded = server.takeRequest();
        assertThat(recorded.getTarget()).contains("/api/v4/projects/2159/merge_requests");
        assertThat(recorded.getTarget()).contains("state=opened");
    }

    @Test
    void getMergeRequestParsesDetail() throws InterruptedException {
        server.enqueue(new MockResponse.Builder()
                .code(200)
                .addHeader("Content-Type", "application/json")
                .body("""
                        {
                          "id": 10001,
                          "iid": 88,
                          "project_id": 2159,
                          "title": "MPTPSUPP-1234: MR",
                          "state": "merged",
                          "merged_at": "2026-07-09T11:00:00.000Z",
                          "source_branch": "feature/MPTPSUPP-1234",
                          "target_branch": "master"
                        }
                        """)
                .build());

        GitLabMergeRequestDto mr = newClient().getMergeRequest("2159", 88L).block();

        assertThat(mr).isNotNull();
        assertThat(mr.state()).isEqualTo("merged");
        assertThat(mr.mergedAt()).isEqualTo("2026-07-09T11:00:00.000Z");

        RecordedRequest recorded = server.takeRequest();
        assertThat(recorded.getTarget()).isEqualTo("/api/v4/projects/2159/merge_requests/88");
    }

    @Test
    void wrapsErrorResponseInGitLabClientException() {
        server.enqueue(new MockResponse.Builder()
                .code(404)
                .addHeader("Content-Type", "application/json")
                .body("""
                        {"message":"404 Project Not Found"}
                        """)
                .build());

        Mono<GitLabProjectDto> result = newClient().getProject("99999");

        assertThatThrownBy(result::block)
                .isInstanceOf(GitLabClientException.class)
                .satisfies(ex -> {
                    GitLabClientException gitlabEx = (GitLabClientException) ex;
                    assertThat(gitlabEx.getStatusCode()).isEqualTo(404);
                    assertThat(gitlabEx.getGitlabErrorMessages()).containsExactly("404 Project Not Found");
                });
    }

    @Test
    void wrapsUnauthorizedWithoutBody() {
        server.enqueue(new MockResponse.Builder().code(401).build());

        assertThatThrownBy(() -> newClient().getProject("2159").block())
                .isInstanceOf(GitLabClientException.class)
                .satisfies(ex -> assertThat(((GitLabClientException) ex).getStatusCode()).isEqualTo(401));
    }

    private RestGitLabClient clientFailingWith(Throwable transportCause) {
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> Mono.error(new WebClientRequestException(
                        transportCause, HttpMethod.GET, URI.create("http://gitlab.invalid/api/v4/projects/1"),
                        new HttpHeaders())))
                .build();
        return new RestGitLabClient(webClient);
    }

    @Test
    void wrapsConnectionRefusedAsGitLabClientException() {
        RestGitLabClient client = clientFailingWith(new ConnectException("Connection refused"));

        assertThatThrownBy(() -> client.getProject("2159").block())
                .isInstanceOf(GitLabClientException.class)
                .satisfies(ex -> {
                    GitLabClientException gitlabEx = (GitLabClientException) ex;
                    assertThat(gitlabEx.getStatusCode()).isEqualTo(GitLabClientException.NO_HTTP_STATUS);
                    assertThat(gitlabEx.getMessage()).containsIgnoringCase("connection refused");
                });
    }

    @Test
    void wrapsDnsFailureAsGitLabClientException() {
        RestGitLabClient client = clientFailingWith(new UnknownHostException("gitlab.invalid"));

        assertThatThrownBy(() -> client.listBranches("2159", 1, 20).block())
                .isInstanceOf(GitLabClientException.class)
                .satisfies(ex -> {
                    GitLabClientException gitlabEx = (GitLabClientException) ex;
                    assertThat(gitlabEx.getStatusCode()).isEqualTo(GitLabClientException.NO_HTTP_STATUS);
                    assertThat(gitlabEx.getMessage()).containsIgnoringCase("DNS");
                });
    }

    @Test
    void wrapsResponseTimeoutAsGitLabClientException() {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofMillis(200))
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 1000);
        WebClient webClient = WebClient.builder()
                .baseUrl(server.url("/").toString())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
        RestGitLabClient client = new RestGitLabClient(webClient);

        assertThatThrownBy(() -> client.getProject("2159").block())
                .isInstanceOf(GitLabClientException.class)
                .satisfies(ex -> assertThat(((GitLabClientException) ex).getStatusCode())
                        .isEqualTo(GitLabClientException.NO_HTTP_STATUS));
    }
}
