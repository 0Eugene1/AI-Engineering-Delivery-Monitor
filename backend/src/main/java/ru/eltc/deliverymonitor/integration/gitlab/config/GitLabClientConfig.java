package ru.eltc.deliverymonitor.integration.gitlab.config;

import io.netty.channel.ChannelOption;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

/**
 * Wires the GitLab {@link WebClient}: base URL + timeouts from {@link GitLabProperties},
 * auth via {@code PRIVATE-TOKEN} header. Active only when {@code gitlab.mode=rest}
 * (the default). No sync/scheduler beans here — Phase 3.1 scope only.
 */
@Configuration
@EnableConfigurationProperties(GitLabProperties.class)
public class GitLabClientConfig {

    /**
     * Raised above Spring's 256 KB default so a busy project's branches/commits/MRs page
     * still fits in memory. Same rationale as {@code JiraClientConfig}: a single internal
     * GitLab instance with a known, bounded project set.
     */
    static final int MAX_IN_MEMORY_SIZE_BYTES = 10 * 1024 * 1024;

    @Bean
    @ConditionalOnProperty(name = "gitlab.mode", havingValue = "rest", matchIfMissing = true)
    public WebClient gitlabWebClient(GitLabProperties properties) {
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
                .filter(privateTokenFilter(properties.getToken()))
                .build();
    }

    /**
     * GitLab accepts Project / Group / Personal Access Tokens via the {@code PRIVATE-TOKEN}
     * header (docs/architecture.md Phase 3, docs/integrations.md).
     */
    public static ExchangeFilterFunction privateTokenFilter(String token) {
        return ExchangeFilterFunction.ofRequestProcessor(request ->
                Mono.just(ClientRequest.from(request)
                        .headers(headers -> headers.set("PRIVATE-TOKEN", token))
                        .build()));
    }
}
