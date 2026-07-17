package ru.eltc.deliverymonitor.integration.gitlab.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * GitLab's typical error body: {@code {"message": "..."}} or {@code {"error": "..."}}.
 * {@code message} may also be a nested object on some endpoints — those are ignored via
 * {@link JsonIgnoreProperties} when the simple string binding fails at the client layer.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitLabErrorResponseDto(
        String message,
        String error
) {
}
