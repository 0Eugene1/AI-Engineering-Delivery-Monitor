/**
 * Workstream Type catalogue — Phase 3.3 "Config persistence" (docs/roadmap.md).
 *
 * <p>{@code domain.workstream_type} owns the configurable Workstream Type reference data
 * (ADR-002): {@link ru.eltc.deliverymonitor.domain.workstream_type.WorkstreamTypeEntity} and
 * {@link ru.eltc.deliverymonitor.domain.workstream_type.WorkstreamTypeRepository}. Types are
 * seeded via Liquibase ({@code workstream_types}); they are <b>not</b> hardcoded platform
 * knowledge in application code.
 *
 * <p>Read API ({@code GET /api/workstream-types}) and {@code domain.workstream} arrive in later
 * Phase 3 tasks — this package only prepares the persistence seam.
 *
 * <p><b>Deliberately out of scope here:</b> {@code workstreams} (Issue × Type), REST controllers,
 * scheduler, security changes.
 */
package ru.eltc.deliverymonitor.domain.workstream_type;
