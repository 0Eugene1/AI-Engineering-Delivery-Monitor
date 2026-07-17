package ru.eltc.deliverymonitor.api.admin;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.eltc.deliverymonitor.sync.gitlab.GitLabSyncResult;
import ru.eltc.deliverymonitor.sync.gitlab.GitLabSyncService;

/**
 * HTTP entry point for the manual GitLab sync trigger (docs/roadmap.md Phase 3.8;
 * docs/api.md). Pure HTTP adapter: accepts the request, calls {@link
 * GitLabSyncService#syncAll()} and returns its {@link GitLabSyncResult} — no fetch,
 * persistence, linking or workstream logic lives here; that already belongs to
 * {@code sync.gitlab} / {@code domain.*} (Phases 3.2–3.6).
 *
 * <p>There is deliberately no separate response DTO: {@link GitLabSyncResult} is already the
 * application layer's contract for a sync run outcome, so this controller reuses it as-is
 * (mirror of {@link JiraSyncController} + {@code JiraSyncResult}).
 *
 * <p>Access control is not this class's concern: {@code /api/admin/**} is gated by the existing
 * {@link ru.eltc.deliverymonitor.api.security.SecurityConfig} (ADR-012) — no new security logic.
 */
@RestController
@RequestMapping("/api/admin/sync")
public class GitLabSyncController {

    private final GitLabSyncService gitLabSyncService;

    public GitLabSyncController(GitLabSyncService gitLabSyncService) {
        this.gitLabSyncService = gitLabSyncService;
    }

    /** Triggers one full sync run of observed GitLab repositories into PostgreSQL. */
    @PostMapping("/gitlab")
    public ResponseEntity<GitLabSyncResult> syncGitLab() {
        GitLabSyncResult result = gitLabSyncService.syncAll();
        return ResponseEntity.ok(result);
    }
}
