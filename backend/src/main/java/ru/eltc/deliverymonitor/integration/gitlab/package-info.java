/**
 * GitLab integration.
 *
 * <p>Phase 3.1 (docs/roadmap.md) is implemented here: an HTTP client for GitLab API v4
 * ({@code /api/v4}) built on Spring {@code WebClient}, its configuration/properties
 * ({@code config}), wire DTOs ({@code dto}) and a client-side error type ({@code exception}).
 *
 * <p>Two swappable implementations of {@code GitLabClient} — {@code RestGitLabClient}
 * (real GitLab via {@code PRIVATE-TOKEN}) and {@code MockGitLabClient} (sanitized offline
 * demo data) — are selected purely by config ({@code gitlab.mode=rest|mock}), so development
 * can proceed without a real {@code GITLAB_TOKEN} and the switch to real GitLab needs no code
 * change. Mock is guarded against production use.
 *
 * <p><b>Deliberately out of scope on this phase</b> — see docs/roadmap.md Phase 3.2+: sync
 * orchestration / {@code POST /api/admin/sync/gitlab}, persistence into PostgreSQL, Timeline
 * read API, the reconcile scheduler, webhooks, pipelines, and Jenkins.
 *
 * <p>See {@code docs/architecture.md} (Phase 3 / Backend packages), {@code docs/integrations.md}
 * and {@code docs/discovery.md} (§9.2 — {@code https://git.eltc.ru}, projects 760 / 2159 / 3494)
 * for scope and config.
 */
package ru.eltc.deliverymonitor.integration.gitlab;
