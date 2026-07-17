/**
 * Admin-only HTTP controllers (Phase 2.4 + 3.8, docs/roadmap.md), protected by the admin
 * Bearer-token filter in {@link ru.eltc.deliverymonitor.api.security} (ADR-012).
 *
 * <p>{@link ru.eltc.deliverymonitor.api.admin.JiraSyncController} — thin HTTP adapter over
 * {@link ru.eltc.deliverymonitor.sync.jira.JiraSyncService} ({@code POST /api/admin/sync/jira}).
 *
 * <p>{@link ru.eltc.deliverymonitor.api.admin.GitLabSyncController} — thin HTTP adapter over
 * {@link ru.eltc.deliverymonitor.sync.gitlab.GitLabSyncService} ({@code POST /api/admin/sync/gitlab}).
 *
 * <p>Neither controller contains business logic — sync/persistence stay in {@code sync.*} /
 * {@code domain.*}. No additional security beyond the existing {@code /api/admin/**} gate.
 */
package ru.eltc.deliverymonitor.api.admin;
