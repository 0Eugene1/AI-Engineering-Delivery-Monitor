package ru.eltc.deliverymonitor.api.issue;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * HTTP entry point for the read-only issue API (docs/api.md "Issues" — {@code GET /api/issues},
 * {@code GET /api/issues/{key}}). A pure HTTP adapter: it delegates to {@link IssueQueryService}
 * and translates its result into a response — no business logic, no persistence, no Jira calls
 * live here.
 *
 * <p>{@code GET /api/sprints/current} is deliberately not implemented in this package: there is
 * no {@code sprints} persistence yet (docs/database.md "Planned / future"), and no mock/stub/live
 * -Jira substitute is provided instead (see docs/discovery.md TODO and docs/roadmap.md).
 *
 * <p>Access control is unchanged from before this phase: these endpoints fall under {@code
 * anyRequest().permitAll()} in {@link ru.eltc.deliverymonitor.api.security.SecurityConfig} — open
 * within the VPN perimeter, same as every other non-admin endpoint (docs/api.md "Conventions").
 */
@RestController
@RequestMapping("/api/issues")
public class IssueController {

    private final IssueQueryService issueQueryService;

    public IssueController(IssueQueryService issueQueryService) {
        this.issueQueryService = issueQueryService;
    }

    /** Returns every issue currently persisted in PostgreSQL (no live Jira call, no pagination). */
    @GetMapping
    public List<IssueResponse> getAll() {
        return issueQueryService.findAll();
    }

    /** Returns a single issue by its public Jira key, or {@code 404} if no such issue is persisted. */
    @GetMapping("/{key}")
    public ResponseEntity<Object> getByKey(@PathVariable String key) {
        return issueQueryService.findByKey(key)
                .<ResponseEntity<Object>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ErrorResponse("Issue not found: " + key, "ISSUE_NOT_FOUND")));
    }
}
