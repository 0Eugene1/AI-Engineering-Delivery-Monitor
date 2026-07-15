/**
 * Admin-only HTTP controllers (Phase 2.4, docs/roadmap.md), protected by the admin Bearer-token
 * filter in {@link ru.eltc.deliverymonitor.api.security} (ADR-012).
 *
 * <p>{@link ru.eltc.deliverymonitor.api.admin.JiraSyncController} is a thin HTTP adapter over
 * {@link ru.eltc.deliverymonitor.sync.jira.JiraSyncService}: it does not contain business logic —
 * pagination, normalization and persistence all stay in {@code sync.jira}/{@code domain.issue}.
 */
package ru.eltc.deliverymonitor.api.admin;
