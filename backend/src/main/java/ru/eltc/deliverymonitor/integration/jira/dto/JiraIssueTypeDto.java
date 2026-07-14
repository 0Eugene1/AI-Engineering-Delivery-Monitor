package ru.eltc.deliverymonitor.integration.jira.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record JiraIssueTypeDto(
        String id,
        String name,
        Boolean subtask
) {
}
