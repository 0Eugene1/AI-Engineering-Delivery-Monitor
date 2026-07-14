package ru.eltc.deliverymonitor.integration.jira.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

/** Jira's standard error body: {@code {"errorMessages": [...], "errors": {...}}}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record JiraErrorResponseDto(
        List<String> errorMessages,
        Map<String, String> errors
) {
}
