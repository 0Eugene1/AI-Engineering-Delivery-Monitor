package ru.eltc.deliverymonitor.api.admin;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.eltc.deliverymonitor.sync.jira.JiraSyncResult;
import ru.eltc.deliverymonitor.sync.jira.JiraSyncService;

/**
 * HTTP entry point for the manual Jira sync trigger (docs/roadmap.md "manual sync first";
 * docs/api.md "Admin sync"). This is a pure HTTP adapter: it accepts the request, calls {@link
 * JiraSyncService#syncBoard()} and returns its {@link JiraSyncResult} — no pagination,
 * normalization, persistence or error-handling policy lives here; all of that already belongs to
 * {@code sync.jira} / {@code domain.issue} (Phase 2.2/2.3).
 *
 * <p>There is deliberately no separate response DTO: {@link JiraSyncResult} is already the
 * application layer's contract for a sync run outcome, so this controller reuses it as-is instead
 * of introducing a parallel shape.
 *
 * <p>Access control is not this class's concern: {@code /api/admin/**} is gated by {@link
 * ru.eltc.deliverymonitor.api.security.SecurityConfig} (ADR-012) — by the time a request reaches
 * this controller, Spring Security has already authenticated it.
 */
@RestController
@RequestMapping("/api/admin/sync")
public class JiraSyncController {

    private final JiraSyncService jiraSyncService;

    public JiraSyncController(JiraSyncService jiraSyncService) {
        this.jiraSyncService = jiraSyncService;
    }

    /** Triggers one full sync run of the observed Jira board into PostgreSQL. */
    @PostMapping("/jira")
    public ResponseEntity<JiraSyncResult> syncJira() {
        JiraSyncResult result = jiraSyncService.syncBoard();
        return ResponseEntity.ok(result);
    }
}
