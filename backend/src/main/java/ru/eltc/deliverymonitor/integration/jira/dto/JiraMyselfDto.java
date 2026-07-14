package ru.eltc.deliverymonitor.integration.jira.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Response of {@code GET /rest/api/2/myself} — used as the auth smoke test. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record JiraMyselfDto(
        String name,
        String key,
        String emailAddress,
        String displayName,
        Boolean active,
        String timeZone
) {
}
