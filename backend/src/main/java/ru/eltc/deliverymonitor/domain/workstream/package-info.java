/**
 * Workstream = Issue × Workstream Type — Phase 3.6 (docs/roadmap.md, docs/database.md, ADR-002).
 *
 * <p>{@code domain.workstream} owns persistence for {@code workstreams}: entity, repository,
 * upsert port + service. Identity is UNIQUE {@code (issue_key, workstream_type_code)}.
 * {@code repository_id} is nullable Git provenance only — not part of uniqueness.
 *
 * <p>Git-driven creation: {@code sync.gitlab} upserts a workstream when Git activity carries an
 * {@code issue_key}; orphans (no key) do not create workstreams. Empty shell {@code qa} rows are
 * not auto-created.
 *
 * <p><b>Deliberately out of scope here:</b> timeline / workstream read API, dashboard, IssueEntity
 * lookup for {@code issue_id}, Release Health, notifications, AI.
 */
package ru.eltc.deliverymonitor.domain.workstream;
