package ru.eltc.deliverymonitor.integration.jira.exception;

import java.util.List;

/**
 * Single, unified error type for every failure while talking to Jira — both HTTP-level
 * (non-2xx status) and transport-level (timeout, connection refused, DNS failure, or any
 * other network error that never produced an HTTP response). Callers only ever need to
 * catch this one type.
 */
public class JiraClientException extends RuntimeException {

    /**
     * Sentinel {@link #statusCode} used when no HTTP response was received at all
     * (timeout, connection refused, DNS failure, ...). Real Jira status codes are
     * always &gt;= 100, so {@code 0} is unambiguous.
     */
    public static final int NO_HTTP_STATUS = 0;

    private final int statusCode;
    private final List<String> jiraErrorMessages;

    public JiraClientException(String message, int statusCode, List<String> jiraErrorMessages) {
        super(message);
        this.statusCode = statusCode;
        this.jiraErrorMessages = jiraErrorMessages == null ? List.of() : List.copyOf(jiraErrorMessages);
    }

    public JiraClientException(String message, int statusCode, List<String> jiraErrorMessages, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.jiraErrorMessages = jiraErrorMessages == null ? List.of() : List.copyOf(jiraErrorMessages);
    }

    public int getStatusCode() {
        return statusCode;
    }

    public List<String> getJiraErrorMessages() {
        return jiraErrorMessages;
    }
}
