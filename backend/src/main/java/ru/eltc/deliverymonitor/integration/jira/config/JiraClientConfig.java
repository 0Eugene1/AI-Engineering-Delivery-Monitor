package ru.eltc.deliverymonitor.integration.jira.config;

import io.netty.channel.ChannelOption;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import ru.eltc.deliverymonitor.integration.jira.auth.BasicAuthenticationStrategy;
import ru.eltc.deliverymonitor.integration.jira.auth.BearerTokenAuthenticationStrategy;
import ru.eltc.deliverymonitor.integration.jira.auth.JiraAuthenticationStrategy;

/**
 * Wires the Jira {@link WebClient}: base URL + timeouts from {@link JiraProperties},
 * auth header from the configured {@link JiraAuthenticationStrategy}. No sync/scheduler
 * beans here — Phase 2.1 scope only.
 */
@Configuration
@EnableConfigurationProperties(JiraProperties.class)
public class JiraClientConfig {

    /**
     * Spring WebClient's default in-memory buffer limit per response is 256 KB, which is
     * comfortable for {@code /myself} but tight for {@code /search} on board filter 30532
     * (docs/discovery.md §9.1 — a few hundred issues) once fields like {@code changelog}
     * are added (docs/architecture.md: {@code integration.jira} is planned to poll
     * changelog/links too). 10 MB gives generous headroom for that growth while still being
     * a safe, fixed bound: this is a single internal Jira Server instance with a known,
     * bounded issue set — not a public/multi-tenant API — so buffering a whole response in
     * memory stays acceptable rather than requiring streaming.
     */
    static final int MAX_IN_MEMORY_SIZE_BYTES = 10 * 1024 * 1024;

    @Bean
    public JiraAuthenticationStrategy jiraAuthenticationStrategy(JiraProperties properties) {
        JiraProperties.Auth auth = properties.getAuth();
        return switch (auth.getType()) {
            case BASIC -> new BasicAuthenticationStrategy(auth.getUsername(), auth.getToken());
            case BEARER -> new BearerTokenAuthenticationStrategy(auth.getToken());
        };
    }

    @Bean
    public WebClient jiraWebClient(JiraProperties properties, JiraAuthenticationStrategy jiraAuthenticationStrategy) {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(properties.getResponseTimeout())
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) properties.getConnectTimeout().toMillis());

        ExchangeStrategies exchangeStrategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(MAX_IN_MEMORY_SIZE_BYTES))
                .build();

        return WebClient.builder()
                .baseUrl(properties.getBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .exchangeStrategies(exchangeStrategies)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .filter(jiraAuthenticationStrategy.authorizationFilter())
                .build();
    }
}
