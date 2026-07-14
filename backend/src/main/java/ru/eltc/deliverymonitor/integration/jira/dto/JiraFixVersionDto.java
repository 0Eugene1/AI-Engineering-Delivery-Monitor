package ru.eltc.deliverymonitor.integration.jira.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record JiraFixVersionDto(
        String id,
        String name,
        Boolean released,
        Boolean archived,
        String releaseDate
) {
}
