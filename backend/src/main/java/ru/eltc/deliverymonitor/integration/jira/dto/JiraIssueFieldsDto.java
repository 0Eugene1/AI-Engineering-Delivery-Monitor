package ru.eltc.deliverymonitor.integration.jira.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Subset of {@code fields} we care about, per docs/roadmap.md (task 2.4):
 * summary, status, assignee, fixVersion. Extra Jira fields are ignored.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record JiraIssueFieldsDto(
        String summary,
        JiraStatusDto status,
        JiraUserDto assignee,
        JiraUserDto reporter,
        JiraIssueTypeDto issuetype,
        List<JiraFixVersionDto> fixVersions,
        List<String> labels,
        String created,
        String updated
) {
}
