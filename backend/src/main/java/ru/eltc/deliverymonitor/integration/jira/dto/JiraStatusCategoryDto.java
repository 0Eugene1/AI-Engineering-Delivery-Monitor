package ru.eltc.deliverymonitor.integration.jira.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record JiraStatusCategoryDto(
        Long id,
        String key,
        String colorName,
        String name
) {
}
