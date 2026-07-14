package ru.eltc.deliverymonitor.integration.jira.auth;

import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFilterFunctions;

/**
 * HTTP Basic authentication ({@code jira.auth.type=basic}) — username + password/PAT.
 */
public class BasicAuthenticationStrategy implements JiraAuthenticationStrategy {

    private final String username;
    private final String password;

    public BasicAuthenticationStrategy(String username, String password) {
        this.username = username;
        this.password = password;
    }

    @Override
    public ExchangeFilterFunction authorizationFilter() {
        return ExchangeFilterFunctions.basicAuthentication(username, password);
    }
}
