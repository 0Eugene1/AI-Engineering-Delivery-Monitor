package ru.eltc.deliverymonitor.domain.timeline;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure issue-key extraction from free text (branch name, commit message, MR title, …).
 *
 * <p>Lives in {@code domain.timeline} (docs/decisions.md Design notes, 2026-07-17) — not in
 * {@code sync.*}, not in {@code domain.issue}, and not a Jira {@code IssueEntity} resolver.
 * No database access. Callers pass the text; this component only runs the regex.
 *
 * <p>Regex: {@code (?&lt;key&gt;[A-Z]+-\d+)} anywhere in the input (first match wins). Empty /
 * blank / no match → {@link Optional#empty()} (orphan policy: caller still persists the object /
 * event with {@code issue_key = null}).
 */
@Component
public class IssueKeyExtractor {

    private static final Pattern ISSUE_KEY = Pattern.compile("(?<key>[A-Z]+-\\d+)");

    /**
     * Extracts the first Jira-style issue key from {@code text}.
     *
     * @param text branch name, commit message, MR title, or any other free text; may be
     *             {@code null}
     * @return the matched key (e.g. {@code MPTPSUPP-1234}), or empty if none
     */
    public Optional<String> extract(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher = ISSUE_KEY.matcher(text);
        if (matcher.find()) {
            return Optional.of(matcher.group("key"));
        }
        return Optional.empty();
    }
}
