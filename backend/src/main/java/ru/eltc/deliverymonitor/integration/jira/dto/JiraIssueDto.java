package ru.eltc.deliverymonitor.integration.jira.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** A single Jira issue as returned by {@code /rest/api/2/search} or {@code /rest/api/2/issue/{key}}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record JiraIssueDto(
        String id,
        String key,
        String self,
        JiraIssueFieldsDto fields
) {
}
