package ru.eltc.deliverymonitor.integration.jira.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

/**
 * Binds the {@code jira.*} settings (see {@code application.yml} and
 * {@code docs/discovery.md} §9.1). Only client/auth configuration — no sync,
 * persistence or scheduler settings belong here.
 *
 * <p>{@link Validated} makes this fail-fast: a missing base URL or missing/incomplete
 * auth credentials (e.g. {@code JIRA_TOKEN} not set in env) prevents the application
 * context from starting, instead of failing later on the first real Jira call.
 */
@Validated
@ConfigurationProperties(prefix = "jira")
public class JiraProperties {

    /** Jira Server base URL, e.g. {@code https://jira.eltc.ru}. No trailing slash. */
    @NotBlank(message = "jira.base-url must not be blank")
    private String baseUrl = "https://jira.eltc.ru";

    /** TCP connect timeout for the underlying HTTP client. */
    private Duration connectTimeout = Duration.ofSeconds(5);

    /** Overall response timeout for a single Jira request. */
    private Duration responseTimeout = Duration.ofSeconds(10);

    /** Jira project keys relevant to this Monitor instance (informational at this stage). */
    private List<String> projectKeys = List.of("MPTPSUPP");

    /** Board id this Monitor observes (docs/discovery.md §9.1 — "МПЛК доска", Kanban). Informational context. */
    private Long boardId = 718L;

    /** Default board filter id, used later by sync/board tasks (Phase 2.2+). */
    private Long defaultFilterId = 30532L;

    /**
     * Data source for the {@code JiraContextProvider} (Phase 2.3): {@link Mode#REST} calls the
     * real Jira Server, {@link Mode#MOCK} serves in-memory sanitized demo data for offline
     * development. Default is {@link Mode#REST}; {@code MOCK} is guarded against production use
     * (see {@code MockJiraContextProvider}).
     */
    @NotNull(message = "jira.mode must be set (rest or mock)")
    private Mode mode = Mode.REST;

    @NotNull(message = "jira.auth must be configured")
    @Valid
    private Auth auth = new Auth();

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

    public List<String> getProjectKeys() {
        return projectKeys;
    }

    public void setProjectKeys(List<String> projectKeys) {
        this.projectKeys = projectKeys;
    }

    public Long getBoardId() {
        return boardId;
    }

    public void setBoardId(Long boardId) {
        this.boardId = boardId;
    }

    public Long getDefaultFilterId() {
        return defaultFilterId;
    }

    public void setDefaultFilterId(Long defaultFilterId) {
        this.defaultFilterId = defaultFilterId;
    }

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    public Auth getAuth() {
        return auth;
    }

    public void setAuth(Auth auth) {
        this.auth = auth;
    }

    /** Which authentication scheme to use — see {@code docs/discovery.md} §1 (still TODO on the real instance). */
    public enum AuthType {
        BASIC,
        BEARER
    }

    /** Where {@code JiraContextProvider} reads from — see {@link #mode}. */
    public enum Mode {
        /** Call the real Jira Server via {@code JiraClient}. */
        REST,
        /** Serve in-memory sanitized demo data for offline development. Never for production. */
        MOCK
    }

    public static class Auth {

        @NotNull(message = "jira.auth.type must be set (basic or bearer)")
        private AuthType type = AuthType.BEARER;

        /** Only used for {@link AuthType#BASIC}. */
        private String username = "";

        /** Personal Access Token (bearer) or password (basic). Never committed — env only. */
        @NotBlank(message = "jira.auth.token must not be blank (set JIRA_TOKEN)")
        private String token = "";

        public AuthType getType() {
            return type;
        }

        public void setType(AuthType type) {
            this.type = type;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }

        /**
         * Cross-field check: {@code basic} auth is meaningless without a username
         * (the token alone would be ambiguous — password? PAT?). {@code bearer} auth
         * only needs the token, already enforced by {@link #token}'s {@code @NotBlank}.
         */
        @AssertTrue(message = "jira.auth.username must not be blank when jira.auth.type=basic")
        private boolean isUsernameValidForAuthType() {
            return type != AuthType.BASIC || (username != null && !username.isBlank());
        }
    }
}
