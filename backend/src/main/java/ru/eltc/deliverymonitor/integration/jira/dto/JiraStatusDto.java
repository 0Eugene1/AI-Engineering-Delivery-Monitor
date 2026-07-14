package ru.eltc.deliverymonitor.integration.jira.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record JiraStatusDto(
        String id,
        String name,
        String description,
        JiraStatusCategoryDto statusCategory
) {
}
