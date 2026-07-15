package ru.eltc.deliverymonitor.api.issue;

/**
 * Standard error body shape from docs/api.md ("Conventions" — {@code { "error": "...", "code":
 * "..." } }). Used by {@link IssueController} for the {@code 404} response when an issue key is
 * not found.
 *
 * @param error human-readable message
 * @param code  machine-readable error code
 */
public record ErrorResponse(String error, String code) {
}
