package ru.eltc.deliverymonitor.integration.jira.auth;

import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import reactor.core.publisher.Mono;

/**
 * Personal Access Token authentication ({@code jira.auth.type=bearer}) — sends
 * {@code Authorization: Bearer <token>}. Recommended for Jira Server 8.14+.
 */
public class BearerTokenAuthenticationStrategy implements JiraAuthenticationStrategy {

    private final String token;

    public BearerTokenAuthenticationStrategy(String token) {
        this.token = token;
    }

    @Override
    public ExchangeFilterFunction authorizationFilter() {
        return ExchangeFilterFunction.ofRequestProcessor(request ->
                Mono.just(ClientRequest.from(request)
                        .headers(headers -> headers.setBearerAuth(token))
                        .build()));
    }
}
