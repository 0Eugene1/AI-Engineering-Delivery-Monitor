package ru.eltc.deliverymonitor.integration.gitlab.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** A GitLab user snippet as nested under MR author / commit author payloads. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitLabUserDto(
        Long id,
        String username,
        String name,
        String state,
        @JsonProperty("web_url") String webUrl
) {
}
