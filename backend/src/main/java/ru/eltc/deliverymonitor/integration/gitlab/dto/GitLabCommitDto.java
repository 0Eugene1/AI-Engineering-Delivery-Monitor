package ru.eltc.deliverymonitor.integration.gitlab.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A GitLab commit as returned by {@code GET /api/v4/projects/:id/repository/commits}
 * (and nested under branch payloads).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitLabCommitDto(
        String id,
        @JsonProperty("short_id") String shortId,
        String title,
        String message,
        @JsonProperty("author_name") String authorName,
        @JsonProperty("author_email") String authorEmail,
        @JsonProperty("authored_date") String authoredDate,
        @JsonProperty("committer_name") String committerName,
        @JsonProperty("committer_email") String committerEmail,
        @JsonProperty("committed_date") String committedDate,
        @JsonProperty("web_url") String webUrl
) {
}
