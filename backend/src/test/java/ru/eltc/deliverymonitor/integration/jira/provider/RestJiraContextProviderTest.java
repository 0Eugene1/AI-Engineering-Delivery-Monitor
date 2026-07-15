package ru.eltc.deliverymonitor.integration.jira.provider;

import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import mockwebserver3.junit5.StartStop;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import ru.eltc.deliverymonitor.integration.jira.auth.BearerTokenAuthenticationStrategy;
import ru.eltc.deliverymonitor.integration.jira.client.JiraClient;
import ru.eltc.deliverymonitor.integration.jira.config.JiraProperties;
import ru.eltc.deliverymonitor.integration.jira.exception.JiraClientException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Verifies the default provider fetches board context from Jira via the board filter — mock HTTP server, no real Jira. */
class RestJiraContextProviderTest {

    @StartStop
    private final MockWebServer server = new MockWebServer();

    private RestJiraContextProvider newProvider() {
        WebClient webClient = WebClient.builder()
                .baseUrl(server.url("/").toString())
                .filter(new BearerTokenAuthenticationStrategy("test-token").authorizationFilter())
                .build();
        JiraClient client = new JiraClient(webClient);
        JiraProperties properties = new JiraProperties();
        properties.getAuth().setToken("test-token");
        return new RestJiraContextProvider(client, properties);
    }

    @Test
    void fetchesBoardContextViaDefaultFilter() throws InterruptedException {
        server.enqueue(new MockResponse.Builder()
                .code(200)
                .addHeader("Content-Type", "application/json")
                .body("""
                        {
                          "startAt": 0,
                          "maxResults": 50,
                          "total": 2,
                          "issues": [
                            {
                              "id": "1", "key": "MPTPSUPP-1001", "self": "s",
                              "fields": { "summary": "First", "status": { "id": "10553", "name": "В работе" } }
                            },
                            {
                              "id": "2", "key": "MPTPSUPP-1002", "self": "s",
                              "fields": { "summary": "Second", "status": { "id": "14560", "name": "Ревью" } }
                            }
                          ]
                        }
                        """)
                .build());

        JiraBoardContext context = newProvider().getBoardContext(0, 50);

        assertThat(context).isNotNull();
        assertThat(context.mocked()).isFalse();
        assertThat(context.boardId()).isEqualTo(718L);
        assertThat(context.filterId()).isEqualTo(30532L);
        assertThat(context.total()).isEqualTo(2);
        assertThat(context.issues()).extracting("key")
                .containsExactly("MPTPSUPP-1001", "MPTPSUPP-1002");
        assertThat(context.fetchedAt()).isNotNull();

        RecordedRequest recorded = server.takeRequest();
        assertThat(recorded.getTarget()).contains("/rest/api/2/search");
        assertThat(recorded.getTarget()).contains("jql=filter%3D30532");
    }

    @Test
    void propagatesJiraErrorsAsJiraClientException() {
        server.enqueue(new MockResponse.Builder().code(401).build());

        RestJiraContextProvider provider = newProvider();

        assertThatThrownBy(() -> provider.getBoardContext(0, 50))
                .isInstanceOf(JiraClientException.class)
                .satisfies(ex -> assertThat(((JiraClientException) ex).getStatusCode()).isEqualTo(401));
    }
}
