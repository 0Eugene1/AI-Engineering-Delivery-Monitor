package ru.eltc.deliverymonitor.integration.gitlab.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Binds the {@code gitlab.*} settings (see {@code application.yml} and
 * {@code docs/discovery.md} §9.2). Only client/auth configuration — no sync,
 * persistence or scheduler settings belong here (those land in {@code sync.gitlab}
 * at Phase 3.2+).
 *
 * <p>{@link Validated} makes this fail-fast: a missing base URL or a missing
 * {@code GITLAB_TOKEN} when {@code gitlab.mode=rest} prevents the application
 * context from starting, instead of failing later on the first real GitLab call.
 * In {@link Mode#MOCK} the token may be blank — mock serves offline fixtures.
 */
@Validated
@ConfigurationProperties(prefix = "gitlab")
public class GitLabProperties {

    /** GitLab base URL, e.g. {@code https://git.eltc.ru}. No trailing slash. */
    @NotBlank(message = "gitlab.base-url must not be blank")
    private String baseUrl = "https://git.eltc.ru";

    /** TCP connect timeout for the underlying HTTP client. */
    private Duration connectTimeout = Duration.ofSeconds(5);

    /** Overall response timeout for a single GitLab request. */
    private Duration responseTimeout = Duration.ofSeconds(10);

    /**
     * Data source for {@code GitLabClient}: {@link Mode#REST} calls the real GitLab API,
     * {@link Mode#MOCK} serves in-memory sanitized demo data for offline development.
     * Default is {@link Mode#REST}; {@code MOCK} is guarded against production use
     * (see {@code MockGitLabClient}).
     */
    @NotNull(message = "gitlab.mode must be set (rest or mock)")
    private Mode mode = Mode.REST;

    /**
     * Personal / Project / Group Access Token sent as {@code PRIVATE-TOKEN}.
     * Never committed — env {@code GITLAB_TOKEN} only. Required when {@link Mode#REST}.
     */
    private String token = "";

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getResponseTimeout() {
        return responseTimeout;
    }

    public void setResponseTimeout(Duration responseTimeout) {
        this.responseTimeout = responseTimeout;
    }

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    /** Where {@code GitLabClient} reads from — see {@link #mode}. */
    public enum Mode {
        /** Call the real GitLab API via {@code RestGitLabClient}. */
        REST,
        /** Serve in-memory sanitized demo data for offline development. Never for production. */
        MOCK
    }

    /**
     * Cross-field check: real GitLab calls need a token; mock mode intentionally allows a blank
     * token so local/CI runs can proceed without {@code GITLAB_TOKEN} (docs/architecture.md
     * Phase 3 — GitLab Mock Mode).
     */
    @AssertTrue(message = "gitlab.token must not be blank when gitlab.mode=rest (set GITLAB_TOKEN)")
    private boolean isTokenValidForMode() {
        return mode == Mode.MOCK || (token != null && !token.isBlank());
    }
}
