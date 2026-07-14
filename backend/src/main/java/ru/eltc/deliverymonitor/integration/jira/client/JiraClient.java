package ru.eltc.deliverymonitor.integration.jira.client;

import io.netty.channel.ConnectTimeoutException;
import io.netty.handler.timeout.ReadTimeoutException;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;
import ru.eltc.deliverymonitor.integration.jira.dto.JiraErrorResponseDto;
import ru.eltc.deliverymonitor.integration.jira.dto.JiraMyselfDto;
import ru.eltc.deliverymonitor.integration.jira.dto.JiraSearchResultDto;
import ru.eltc.deliverymonitor.integration.jira.exception.JiraClientException;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

/**
 * Thin REST client for Jira Server 8.x ({@code /rest/api/2}). Phase 2.1 scope only:
 * no persistence, no sync orchestration, no scheduler — see docs/roadmap.md.
 *
 * <p>Every failure — HTTP-level (non-2xx) or transport-level (timeout, connection
 * refused, DNS failure, any other network error) — surfaces as {@link JiraClientException}.
 * Callers never need to catch anything else.
 */
@Component
public class JiraClient {

    private static final String MYSELF_PATH = "/rest/api/2/myself";
    private static final String SEARCH_PATH = "/rest/api/2/search";

    private final WebClient webClient;

    public JiraClient(WebClient jiraWebClient) {
        this.webClient = jiraWebClient;
    }

    /** Smoke check for auth wiring: {@code GET /rest/api/2/myself}. */
    public Mono<JiraMyselfDto> getMyself() {
        return withUnifiedErrorHandling(
                webClient.get()
                        .uri(MYSELF_PATH)
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, this::toClientException)
                        .bodyToMono(JiraMyselfDto.class));
    }

    /**
     * JQL search: {@code GET /rest/api/2/search}.
     *
     * @param jql        JQL string, e.g. {@code "filter=30532"} or a full query
     * @param startAt    paging offset
     * @param maxResults page size
     * @param fields     Jira fields to return (empty = Jira default set)
     */
    public Mono<JiraSearchResultDto> search(String jql, int startAt, int maxResults, List<String> fields) {
        return withUnifiedErrorHandling(
                webClient.get()
                        .uri(uriBuilder -> {
                            uriBuilder.path(SEARCH_PATH)
                                    .queryParam("jql", jql)
                                    .queryParam("startAt", startAt)
                                    .queryParam("maxResults", maxResults);
                            if (fields != null && !fields.isEmpty()) {
                                uriBuilder.queryParam("fields", String.join(",", fields));
                            }
                            return uriBuilder.build();
                        })
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, this::toClientException)
                        .bodyToMono(JiraSearchResultDto.class));
    }

    /** Convenience: search using an existing Jira filter id, e.g. board filter 30532. */
    public Mono<JiraSearchResultDto> searchByFilter(long filterId, int startAt, int maxResults) {
        return search("filter=" + filterId, startAt, maxResults, List.of());
    }

    private Mono<? extends Throwable> toClientException(ClientResponse response) {
        int statusCode = response.statusCode().value();
        return response.bodyToMono(JiraErrorResponseDto.class)
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .onErrorReturn(Optional.empty())
                .map(body -> {
                    List<String> messages = body.map(JiraErrorResponseDto::errorMessages).orElse(List.of());
                    String message = "Jira API request failed with status " + statusCode
                            + (messages.isEmpty() ? "" : ": " + String.join("; ", messages));
                    return new JiraClientException(message, statusCode, messages);
                });
    }

    /**
     * Maps every error that is not already a {@link JiraClientException} (i.e. every
     * transport-level failure — no HTTP response was ever received) into one, so callers
     * have a single exception type to handle regardless of failure kind.
     */
    private <T> Mono<T> withUnifiedErrorHandling(Mono<T> mono) {
        return mono.onErrorMap(ex -> !(ex instanceof JiraClientException), this::toTransportException);
    }

    private JiraClientException toTransportException(Throwable ex) {
        Throwable cause = ex instanceof WebClientRequestException wre && wre.getCause() != null ? wre.getCause() : ex;
        String reason = describeTransportFailure(cause);
        String message = "Jira API request failed before receiving a response: " + reason;
        return new JiraClientException(message, JiraClientException.NO_HTTP_STATUS, List.of(), ex);
    }

    private static String describeTransportFailure(Throwable cause) {
        if (cause instanceof UnknownHostException) {
            return "DNS resolution failed (" + cause.getMessage() + ")";
        }
        if (cause instanceof ConnectException) {
            return "connection refused (" + cause.getMessage() + ")";
        }
        if (cause instanceof ConnectTimeoutException
                || cause instanceof ReadTimeoutException
                || cause instanceof TimeoutException) {
            return "timed out (" + cause.getClass().getSimpleName() + ")";
        }
        return "network error (" + cause.getClass().getSimpleName()
                + (cause.getMessage() != null ? ": " + cause.getMessage() : "") + ")";
    }
}
