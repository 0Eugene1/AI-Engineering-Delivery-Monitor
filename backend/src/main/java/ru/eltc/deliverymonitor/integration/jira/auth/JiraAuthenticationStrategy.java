package ru.eltc.deliverymonitor.integration.jira.auth;

import org.springframework.web.reactive.function.client.ExchangeFilterFunction;

/**
 * Adds the Jira credentials to outgoing requests. Selected at startup based on
 * {@code jira.auth.type} — see {@code JiraProperties.AuthType}.
 */
public interface JiraAuthenticationStrategy {

    /**
     * A WebClient exchange filter that stamps the {@code Authorization} header
     * (or equivalent) on every request before it is sent.
     */
    ExchangeFilterFunction authorizationFilter();
}
