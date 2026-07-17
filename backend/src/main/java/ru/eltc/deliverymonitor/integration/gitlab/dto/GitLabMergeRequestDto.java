package ru.eltc.deliverymonitor.integration.gitlab.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** A GitLab merge request as returned by {@code GET /api/v4/projects/:id/merge_requests}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitLabMergeRequestDto(
        Long id,
        Long iid,
        @JsonProperty("project_id") Long projectId,
        String title,
        String description,
        String state,
        @JsonProperty("merged_at") String mergedAt,
        @JsonProperty("created_at") String createdAt,
        @JsonProperty("updated_at") String updatedAt,
        @JsonProperty("source_branch") String sourceBranch,
        @JsonProperty("target_branch") String targetBranch,
        @JsonProperty("web_url") String webUrl,
        GitLabUserDto author
) {
}
