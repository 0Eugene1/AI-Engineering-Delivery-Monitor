package ru.eltc.deliverymonitor.api.risk;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP entry point for Risks (Phase 4.2, docs/api.md): {@code GET /api/risks}.
 *
 * <p>Thin HTTP adapter over {@link RiskQueryService}. Evaluate-on-read from PostgreSQL only —
 * no {@code risk_flags}, no live Jira/GitLab. Always returns {@code 200} with
 * {@code risks: []} when nothing matches.
 */
@RestController
@RequestMapping("/api/risks")
public class RiskController {

    private final RiskQueryService riskQueryService;

    public RiskController(RiskQueryService riskQueryService) {
        this.riskQueryService = riskQueryService;
    }

    @GetMapping
    public RisksResponse getRisks(
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String issueKey,
            @RequestParam(required = false) Integer limit) {
        return riskQueryService.findRisks(severity, code, issueKey, limit);
    }
}
