package ru.eltc.deliverymonitor.integration.gitlab.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** A GitLab project as returned by {@code GET /api/v4/projects/:id}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitLabProjectDto(
        Long id,
        String name,
        String path,
        @JsonProperty("path_with_namespace") String pathWithNamespace,
        @JsonProperty("default_branch") String defaultBranch,
        @JsonProperty("web_url") String webUrl,
        String description
) {
}
