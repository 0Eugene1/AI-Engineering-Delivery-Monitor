package ru.eltc.deliverymonitor.integration.gitlab.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** A GitLab branch as returned by {@code GET /api/v4/projects/:id/repository/branches}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitLabBranchDto(
        String name,
        GitLabCommitDto commit,
        Boolean merged,
        @JsonProperty("protected") Boolean protectedBranch,
        @JsonProperty("default") Boolean defaultBranch,
        @JsonProperty("web_url") String webUrl
) {
}
