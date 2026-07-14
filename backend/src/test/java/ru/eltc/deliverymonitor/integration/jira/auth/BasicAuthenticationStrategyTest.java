package ru.eltc.deliverymonitor.integration.jira.auth;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class BasicAuthenticationStrategyTest {

    @Test
    void addsBasicAuthorizationHeader() {
        BasicAuthenticationStrategy strategy = new BasicAuthenticationStrategy("svc-account", "s3cr3t");
        ClientRequest original = ClientRequest
                .create(HttpMethod.GET, URI.create("http://localhost/rest/api/2/myself"))
                .build();

        ClientRequest[] processedRequest = new ClientRequest[1];
        strategy.authorizationFilter()
                .filter(original, request -> {
                    processedRequest[0] = request;
                    return Mono.just(ClientResponse.create(HttpStatus.OK).build());
                })
                .block();

        String expected = "Basic " + Base64.getEncoder()
                .encodeToString("svc-account:s3cr3t".getBytes(StandardCharsets.UTF_8));
        assertThat(processedRequest[0].headers().getFirst("Authorization")).isEqualTo(expected);
    }
}
