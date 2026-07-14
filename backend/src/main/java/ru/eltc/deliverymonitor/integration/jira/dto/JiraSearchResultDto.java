package ru.eltc.deliverymonitor.integration.jira.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** Response of {@code GET /rest/api/2/search?jql=...}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record JiraSearchResultDto(
        Integer startAt,
        Integer maxResults,
        Integer total,
        List<JiraIssueDto> issues
) {
}
