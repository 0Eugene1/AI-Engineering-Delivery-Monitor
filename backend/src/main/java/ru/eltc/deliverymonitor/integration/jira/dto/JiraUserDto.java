package ru.eltc.deliverymonitor.integration.jira.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Jira Server user reference (assignee/reporter). REST API v2 shape — {@code name}
 * is the username, not present on Jira Cloud (out of scope here).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record JiraUserDto(
        String name,
        String key,
        String displayName,
        String emailAddress,
        Boolean active
) {
}
