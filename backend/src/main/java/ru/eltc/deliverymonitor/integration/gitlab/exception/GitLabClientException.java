package ru.eltc.deliverymonitor.integration.gitlab.exception;

import java.util.List;

/**
 * Single, unified error type for every failure while talking to GitLab — both HTTP-level
 * (non-2xx status) and transport-level (timeout, connection refused, DNS failure, or any
 * other network error that never produced an HTTP response). Callers only ever need to
 * catch this one type.
 */
public class GitLabClientException extends RuntimeException {

    /**
     * Sentinel {@link #statusCode} used when no HTTP response was received at all
     * (timeout, connection refused, DNS failure, ...). Real GitLab status codes are
     * always &gt;= 100, so {@code 0} is unambiguous.
     */
    public static final int NO_HTTP_STATUS = 0;

    private final int statusCode;
    private final List<String> gitlabErrorMessages;

    public GitLabClientException(String message, int statusCode, List<String> gitlabErrorMessages) {
        super(message);
        this.statusCode = statusCode;
        this.gitlabErrorMessages = gitlabErrorMessages == null ? List.of() : List.copyOf(gitlabErrorMessages);
    }

    public GitLabClientException(String message, int statusCode, List<String> gitlabErrorMessages, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.gitlabErrorMessages = gitlabErrorMessages == null ? List.of() : List.copyOf(gitlabErrorMessages);
    }

    public int getStatusCode() {
        return statusCode;
    }

    public List<String> getGitlabErrorMessages() {
        return gitlabErrorMessages;
    }
}
