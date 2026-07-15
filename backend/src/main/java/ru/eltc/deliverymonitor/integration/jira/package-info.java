/**
 * Jira integration.
 *
 * <p>Phase 2.1 (docs/roadmap.md) is implemented here: an HTTP client for Jira Server 8.x
 * ({@code /rest/api/2}) built on Spring {@code WebClient}, its configuration/properties
 * ({@code config}), pluggable authentication ({@code auth} — Basic or Bearer/PAT), request/response
 * DTOs ({@code dto}) and a client-side error type ({@code exception}).
 *
 * <p>Phase 2.3 adds {@code provider}: a {@code JiraContextProvider} seam that exposes the board
 * context (issues of board 718 / filter 30532) as a domain-meaningful operation. Two swappable
 * implementations — {@code RestJiraContextProvider} (real Jira via {@code JiraClient}) and
 * {@code MockJiraContextProvider} (sanitized offline demo data) — are selected purely by config
 * ({@code jira.mode=rest|mock}), so development can proceed without a real service account and
 * the switch to real Jira needs no code change. Mock is guarded against production use.
 *
 * <p><b>Deliberately out of scope on this phase</b> — see docs/roadmap.md Phase 2.2+: sync
 * orchestration / {@code POST /api/admin/sync/jira}, persistence into PostgreSQL, the read
 * REST API, the {@code @Scheduled} poller, and live board configuration / sprint metadata
 * (Jira Agile API).
 *
 * <p>See {@code docs/architecture.md} (Backend packages table), {@code docs/integrations.md}
 * and {@code docs/discovery.md} (§9.1, confirmed project key {@code MPTPSUPP}, board 718,
 * filter 30532) for scope and config.
 */
package ru.eltc.deliverymonitor.integration.jira;
