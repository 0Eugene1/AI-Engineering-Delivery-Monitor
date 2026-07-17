package ru.eltc.deliverymonitor.integration.gitlab.client;

import io.netty.channel.ConnectTimeoutException;
import io.netty.handler.timeout.ReadTimeoutException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;
import ru.eltc.deliverymonitor.integration.gitlab.dto.GitLabBranchDto;
import ru.eltc.deliverymonitor.integration.gitlab.dto.GitLabCommitDto;
import ru.eltc.deliverymonitor.integration.gitlab.dto.GitLabErrorResponseDto;
import ru.eltc.deliverymonitor.integration.gitlab.dto.GitLabMergeRequestDto;
import ru.eltc.deliverymonitor.integration.gitlab.dto.GitLabProjectDto;
import ru.eltc.deliverymonitor.integration.gitlab.exception.GitLabClientException;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

/**
 * Default {@link GitLabClient}: talks to a real GitLab instance over API v4 via
 * {@link WebClient}. Active when {@code gitlab.mode=rest} (the default).
 *
 * <p>Every failure — HTTP-level (non-2xx) or transport-level — surfaces as
 * {@link GitLabClientException}. Callers never need to catch anything else.
 */
@Component
@ConditionalOnProperty(name = "gitlab.mode", havingValue = "rest", matchIfMissing = true)
public class RestGitLabClient implements GitLabClient {

    private static final ParameterizedTypeReference<List<GitLabBranchDto>> BRANCH_LIST =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<GitLabCommitDto>> COMMIT_LIST =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<GitLabMergeRequestDto>> MERGE_REQUEST_LIST =
            new ParameterizedTypeReference<>() {};

    private final WebClient webClient;

    public RestGitLabClient(@Qualifier("gitlabWebClient") WebClient gitlabWebClient) {
        this.webClient = gitlabWebClient;
    }

    @Override
    public Mono<GitLabProjectDto> getProject(String projectIdOrPath) {
        requireProjectId(projectIdOrPath);
        return withUnifiedErrorHandling(
                webClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .pathSegment("api", "v4", "projects", projectIdOrPath)
                                .build())
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, this::toClientException)
                        .bodyToMono(GitLabProjectDto.class));
    }

    @Override
    public Mono<List<GitLabBranchDto>> listBranches(String projectIdOrPath, int page, int perPage) {
        requireProjectId(projectIdOrPath);
        return withUnifiedErrorHandling(
                webClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .pathSegment("api", "v4", "projects", projectIdOrPath, "repository", "branches")
                                .queryParam("page", page)
                                .queryParam("per_page", perPage)
                                .build())
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, this::toClientException)
                        .bodyToMono(BRANCH_LIST));
    }

    @Override
    public Mono<List<GitLabCommitDto>> listCommits(
            String projectIdOrPath, Instant since, int page, int perPage) {
        requireProjectId(projectIdOrPath);
        return withUnifiedErrorHandling(
                webClient.get()
                        .uri(uriBuilder -> {
                            uriBuilder.pathSegment(
                                            "api", "v4", "projects", projectIdOrPath, "repository", "commits")
                                    .queryParam("page", page)
                                    .queryParam("per_page", perPage);
                            if (since != null) {
                                uriBuilder.queryParam("since", since.toString());
                            }
                            return uriBuilder.build();
                        })
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, this::toClientException)
                        .bodyToMono(COMMIT_LIST));
    }

    @Override
    public Mono<List<GitLabMergeRequestDto>> listMergeRequests(
            String projectIdOrPath, String state, int page, int perPage) {
        requireProjectId(projectIdOrPath);
        return withUnifiedErrorHandling(
                webClient.get()
                        .uri(uriBuilder -> {
                            uriBuilder.pathSegment("api", "v4", "projects", projectIdOrPath, "merge_requests")
                                    .queryParam("page", page)
                                    .queryParam("per_page", perPage);
                            if (state != null && !state.isBlank()) {
                                uriBuilder.queryParam("state", state);
                            }
                            return uriBuilder.build();
                        })
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, this::toClientException)
                        .bodyToMono(MERGE_REQUEST_LIST));
    }

    @Override
    public Mono<GitLabMergeRequestDto> getMergeRequest(String projectIdOrPath, long mergeRequestIid) {
        requireProjectId(projectIdOrPath);
        return withUnifiedErrorHandling(
                webClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .pathSegment(
                                        "api", "v4", "projects", projectIdOrPath,
                                        "merge_requests", Long.toString(mergeRequestIid))
                                .build())
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, this::toClientException)
                        .bodyToMono(GitLabMergeRequestDto.class));
    }

    /**
     * Validates the project id/path. Encoding of {@code group/project} slashes as {@code %2F}
     * is done by {@code UriBuilder#pathSegment} (one segment → GitLab path form).
     */
    static void requireProjectId(String projectIdOrPath) {
        if (projectIdOrPath == null || projectIdOrPath.isBlank()) {
            throw new IllegalArgumentException("projectIdOrPath must not be blank");
        }
    }

    private Mono<? extends Throwable> toClientException(ClientResponse response) {
        int statusCode = response.statusCode().value();
        return response.bodyToMono(GitLabErrorResponseDto.class)
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .onErrorReturn(Optional.empty())
                .map(body -> {
                    List<String> messages = body.map(RestGitLabClient::extractMessages).orElse(List.of());
                    String message = "GitLab API request failed with status " + statusCode
                            + (messages.isEmpty() ? "" : ": " + String.join("; ", messages));
                    return new GitLabClientException(message, statusCode, messages);
                });
    }

    private static List<String> extractMessages(GitLabErrorResponseDto body) {
        if (body.message() != null && !body.message().isBlank()) {
            return List.of(body.message());
        }
        if (body.error() != null && !body.error().isBlank()) {
            return List.of(body.error());
        }
        return List.of();
    }

    private <T> Mono<T> withUnifiedErrorHandling(Mono<T> mono) {
        return mono.onErrorMap(ex -> !(ex instanceof GitLabClientException), this::toTransportException);
    }

    private GitLabClientException toTransportException(Throwable ex) {
        Throwable cause = ex instanceof WebClientRequestException wre && wre.getCause() != null
                ? wre.getCause() : ex;
        String reason = describeTransportFailure(cause);
        String message = "GitLab API request failed before receiving a response: " + reason;
        return new GitLabClientException(message, GitLabClientException.NO_HTTP_STATUS, List.of(), ex);
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
