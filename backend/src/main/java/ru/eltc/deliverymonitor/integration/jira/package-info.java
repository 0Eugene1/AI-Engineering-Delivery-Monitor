/**
 * Jira integration.
 *
 * <p>Phase 2.1 (docs/roadmap.md) is implemented here: an HTTP client for Jira Server 8.x
 * ({@code /rest/api/2}) built on Spring {@code WebClient}, its configuration/properties
 * ({@code config}), pluggable authentication ({@code auth} — Basic or Bearer/PAT), request/response
 * DTOs ({@code dto}) and a client-side error type ({@code exception}).
 *
 * <p><b>Deliberately out of scope on this phase</b> — see docs/roadmap.md Phase 2.2+: sync
 * orchestration / {@code POST /api/admin/sync/jira}, persistence into PostgreSQL, the read
 * REST API, and the {@code @Scheduled} poller.
 *
 * <p>See {@code docs/architecture.md} (Backend packages table), {@code docs/integrations.md}
 * and {@code docs/discovery.md} (§9.1, confirmed project key {@code MPTPSUPP}, board 718,
 * filter 30532) for scope and config.
 */
package ru.eltc.deliverymonitor.integration.jira;
