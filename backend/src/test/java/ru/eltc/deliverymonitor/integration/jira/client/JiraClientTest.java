package ru.eltc.deliverymonitor.integration.jira.client;

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
import ru.eltc.deliverymonitor.integration.jira.auth.BearerTokenAuthenticationStrategy;
import ru.eltc.deliverymonitor.integration.jira.dto.JiraMyselfDto;
import ru.eltc.deliverymonitor.integration.jira.dto.JiraSearchResultDto;
import ru.eltc.deliverymonitor.integration.jira.exception.JiraClientException;

import java.net.ConnectException;
import java.net.URI;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** End-to-end client tests against a mock Jira server — no real Jira instance needed. */
class JiraClientTest {

    @StartStop
    private final MockWebServer server = new MockWebServer();

    private JiraClient newClient() {
        WebClient webClient = WebClient.builder()
                .baseUrl(server.url("/").toString())
                .filter(new BearerTokenAuthenticationStrategy("test-token").authorizationFilter())
                .build();
        return new JiraClient(webClient);
    }

    @Test
    void getMyselfParsesSuccessResponseAndSendsBearerToken() throws InterruptedException {
        server.enqueue(new MockResponse.Builder()
                .code(200)
                .addHeader("Content-Type", "application/json")
                .body("""
                        {
                          "name": "repin.ea",
                          "key": "repin.ea",
                          "emailAddress": "repin.ea@eltc.ru",
                          "displayName": "Repin Evgeny",
                          "active": true,
                          "timeZone": "Europe/Moscow"
                        }
                        """)
                .build());

        JiraMyselfDto myself = newClient().getMyself().block();

        assertThat(myself).isNotNull();
        assertThat(myself.name()).isEqualTo("repin.ea");
        assertThat(myself.displayName()).isEqualTo("Repin Evgeny");
        assertThat(myself.active()).isTrue();

        RecordedRequest recorded = server.takeRequest();
        assertThat(recorded.getTarget()).isEqualTo("/rest/api/2/myself");
        assertThat(recorded.getHeaders().get("Authorization")).isEqualTo("Bearer test-token");
    }

    @Test
    void searchParsesIssuesWithFieldsSubset() throws InterruptedException {
        server.enqueue(new MockResponse.Builder()
                .code(200)
                .addHeader("Content-Type", "application/json")
                .body("""
                        {
                          "startAt": 0,
                          "maxResults": 50,
                          "total": 1,
                          "issues": [
                            {
                              "id": "12345",
                              "key": "MPTPSUPP-1234",
                              "self": "https://jira.eltc.ru/rest/api/2/issue/12345",
                              "fields": {
                                "summary": "Fix payment screen banner",
                                "status": {
                                  "id": "10553",
                                  "name": "В работе",
                                  "statusCategory": { "id": 4, "key": "indeterminate", "colorName": "yellow", "name": "In Progress" }
                                },
                                "assignee": { "name": "repin.ea", "displayName": "Repin Evgeny", "active": true },
                                "fixVersions": [ { "id": "1", "name": "5.7.27", "released": false, "archived": false } ],
                                "labels": ["sentry"]
                              }
                            }
                          ]
                        }
                        """)
                .build());

        JiraSearchResultDto result = newClient().searchByFilter(30532L, 0, 50).block();

        assertThat(result).isNotNull();
        assertThat(result.total()).isEqualTo(1);
        assertThat(result.issues()).hasSize(1);

        var issue = result.issues().get(0);
        assertThat(issue.key()).isEqualTo("MPTPSUPP-1234");
        assertThat(issue.fields().summary()).isEqualTo("Fix payment screen banner");
        assertThat(issue.fields().status().name()).isEqualTo("В работе");
        assertThat(issue.fields().assignee().name()).isEqualTo("repin.ea");
        assertThat(issue.fields().fixVersions()).extracting("name").containsExactly("5.7.27");

        RecordedRequest recorded = server.takeRequest();
        assertThat(recorded.getTarget()).contains("/rest/api/2/search");
        assertThat(recorded.getTarget()).contains("jql=filter%3D30532");
    }

    @Test
    void wrapsErrorResponseInJiraClientException() {
        server.enqueue(new MockResponse.Builder()
                .code(400)
                .addHeader("Content-Type", "application/json")
                .body("""
                        {
                          "errorMessages": ["The value 'notAJql' does not exist for the field 'jql'."],
                          "errors": {}
                        }
                        """)
                .build());

        Mono<JiraSearchResultDto> result = newClient().search("notAJql", 0, 50, List.of());

        assertThatThrownBy(result::block)
                .isInstanceOf(JiraClientException.class)
                .satisfies(ex -> {
                    JiraClientException jiraEx = (JiraClientException) ex;
                    assertThat(jiraEx.getStatusCode()).isEqualTo(400);
                    assertThat(jiraEx.getJiraErrorMessages())
                            .containsExactly("The value 'notAJql' does not exist for the field 'jql'.");
                });
    }

    @Test
    void wrapsUnauthorizedResponseWithoutBody() {
        server.enqueue(new MockResponse.Builder()
                .code(401)
                .build());

        Mono<JiraMyselfDto> result = newClient().getMyself();

        assertThatThrownBy(result::block)
                .isInstanceOf(JiraClientException.class)
                .satisfies(ex -> assertThat(((JiraClientException) ex).getStatusCode()).isEqualTo(401));
    }

    // --- Transport-level errors (no HTTP response ever received) also unify into
    // JiraClientException — see JiraClient#withUnifiedErrorHandling. These use a stub
    // ExchangeFunction so the failure is deterministic and independent of the sandbox's
    // real network/DNS availability, except for the response-timeout case which uses a
    // real (local, loopback) MockWebServer.

    /** Builds a JiraClient whose every request immediately fails with the given transport cause. */
    private JiraClient clientFailingWith(Throwable transportCause) {
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> Mono.error(new WebClientRequestException(
                        transportCause, HttpMethod.GET, URI.create("http://jira.invalid/rest/api/2/myself"),
                        new HttpHeaders())))
                .build();
        return new JiraClient(webClient);
    }

    @Test
    void wrapsConnectionRefusedAsJiraClientException() {
        JiraClient client = clientFailingWith(new ConnectException("Connection refused"));

        assertThatThrownBy(() -> client.getMyself().block())
                .isInstanceOf(JiraClientException.class)
                .satisfies(ex -> {
                    JiraClientException jiraEx = (JiraClientException) ex;
                    assertThat(jiraEx.getStatusCode()).isEqualTo(JiraClientException.NO_HTTP_STATUS);
                    assertThat(jiraEx.getMessage()).containsIgnoringCase("connection refused");
                    assertThat(jiraEx.getCause()).isInstanceOf(WebClientRequestException.class);
                });
    }

    @Test
    void wrapsDnsFailureAsJiraClientException() {
        JiraClient client = clientFailingWith(new UnknownHostException("jira.invalid"));

        assertThatThrownBy(() -> client.searchByFilter(30532L, 0, 50).block())
                .isInstanceOf(JiraClientException.class)
                .satisfies(ex -> {
                    JiraClientException jiraEx = (JiraClientException) ex;
                    assertThat(jiraEx.getStatusCode()).isEqualTo(JiraClientException.NO_HTTP_STATUS);
                    assertThat(jiraEx.getMessage()).containsIgnoringCase("DNS");
                });
    }

    @Test
    void wrapsGenericNetworkErrorAsJiraClientException() {
        JiraClient client = clientFailingWith(new java.io.IOException("reset by peer"));

        assertThatThrownBy(() -> client.getMyself().block())
                .isInstanceOf(JiraClientException.class)
                .satisfies(ex -> assertThat(((JiraClientException) ex).getStatusCode())
                        .isEqualTo(JiraClientException.NO_HTTP_STATUS));
    }

    @Test
    void doesNotDoubleWrapAlreadyUnifiedExceptions() {
        server.enqueue(new MockResponse.Builder().code(500).build());

        assertThatThrownBy(() -> newClient().getMyself().block())
                .isInstanceOf(JiraClientException.class)
                .satisfies(ex -> assertThat(((JiraClientException) ex).getStatusCode()).isEqualTo(500));
    }

    @Test
    void wrapsResponseTimeoutAsJiraClientException() {
        // No response is ever enqueued: the server accepts the connection but never replies,
        // so the very short response timeout below fires deterministically (local loopback,
        // no real network/DNS involved).
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofMillis(200))
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 1000);
        WebClient webClient = WebClient.builder()
                .baseUrl(server.url("/").toString())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
        JiraClient client = new JiraClient(webClient);

        assertThatThrownBy(() -> client.getMyself().block())
                .isInstanceOf(JiraClientException.class)
                .satisfies(ex -> assertThat(((JiraClientException) ex).getStatusCode())
                        .isEqualTo(JiraClientException.NO_HTTP_STATUS));
    }
}
