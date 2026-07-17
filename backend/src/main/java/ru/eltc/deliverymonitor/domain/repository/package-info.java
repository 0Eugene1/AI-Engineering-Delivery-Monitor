/**
 * Observed GitLab repositories — Phase 3.3 "Config persistence" (docs/roadmap.md).
 *
 * <p>{@code domain.repository} owns the {@code repositories} table persistence:
 * {@link ru.eltc.deliverymonitor.domain.repository.RepositoryEntity},
 * {@link ru.eltc.deliverymonitor.domain.repository.RepositoryJpaRepository}, and the
 * upsert/lookup port {@link ru.eltc.deliverymonitor.domain.repository.RepositoryPersistencePort}
 * (+ command/outcome + default service). Matching an existing row uses the immutable numeric
 * {@code gitlabProjectId}, never {@code path}/{@code name} (docs/database.md, decisions.md
 * Design notes 2026-07-17) — symmetry with {@code issues} matching by {@code jiraId}.
 *
 * <p>Seed rows come from Liquibase (docs/discovery.md §9.2). Production sync
 * ({@code gitlab.mode=rest}) uses this port as the <b>sole</b> source of observed projects
 * (Phase 3.4.1). Yaml {@code gitlab.sync.repositories} is mock / local / tests only.
 * This package does not import {@code sync.gitlab}.
 *
 * <p><b>Deliberately out of scope here:</b> {@code activity_events}, {@code workstreams},
 * pipelines, {@code sync_state}, REST API, scheduler, security changes. Git entities live in
 * {@code domain.gitlab}.
 */
package ru.eltc.deliverymonitor.domain.repository;
