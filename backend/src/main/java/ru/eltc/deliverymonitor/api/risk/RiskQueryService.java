package ru.eltc.deliverymonitor.api.risk;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.eltc.deliverymonitor.domain.risk.Risk;
import ru.eltc.deliverymonitor.domain.risk.RiskProperties;
import ru.eltc.deliverymonitor.domain.risk.RiskService;
import ru.eltc.deliverymonitor.domain.workstream_type.WorkstreamTypeEntity;
import ru.eltc.deliverymonitor.domain.workstream_type.WorkstreamTypeRepository;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Read-only query layer for Risks (Phase 4.2): delegates evaluation to {@link RiskService},
 * applies optional filters / limit, and enriches workstream type display names.
 *
 * <p>Dependency direction: {@code domain.risk (+ workstream_type) -> api.risk}.
 */
@Service
@Transactional(readOnly = true)
public class RiskQueryService {

    private final RiskService riskService;
    private final WorkstreamTypeRepository workstreamTypeRepository;
    private final RiskProperties properties;

    public RiskQueryService(
            RiskService riskService,
            WorkstreamTypeRepository workstreamTypeRepository,
            RiskProperties properties) {
        this.riskService = riskService;
        this.workstreamTypeRepository = workstreamTypeRepository;
        this.properties = properties;
    }

    /**
     * Evaluates risks at {@link Instant#now()} and returns a filtered list.
     *
     * @param severity optional severity filter (case-insensitive)
     * @param code     optional rule code filter (case-insensitive)
     * @param issueKey optional issue key filter (exact)
     * @param limit    optional max rows; {@code null} → default; clamped to max
     */
    public RisksResponse findRisks(String severity, String code, String issueKey, Integer limit) {
        Instant now = Instant.now();
        List<Risk> evaluated = riskService.evaluateAll(now);
        Map<String, String> displayNames = workstreamTypeRepository.findAll().stream()
                .collect(Collectors.toMap(
                        WorkstreamTypeEntity::getCode,
                        WorkstreamTypeEntity::getDisplayName,
                        (a, b) -> a));

        String severityFilter = blankToNull(severity);
        String codeFilter = blankToNull(code);
        String issueKeyFilter = blankToNull(issueKey);
        int max = resolveLimit(limit);

        Stream<Risk> stream = evaluated.stream();
        if (severityFilter != null) {
            String sev = severityFilter.toUpperCase(Locale.ROOT);
            stream = stream.filter(r -> sev.equalsIgnoreCase(r.severity()));
        }
        if (codeFilter != null) {
            String c = codeFilter.toUpperCase(Locale.ROOT);
            stream = stream.filter(r -> c.equalsIgnoreCase(r.code()));
        }
        if (issueKeyFilter != null) {
            stream = stream.filter(r -> issueKeyFilter.equals(r.issueKey()));
        }

        List<RisksResponse.RiskItem> items = stream
                .limit(max)
                .map(r -> toItem(r, displayNames))
                .toList();
        return new RisksResponse(items);
    }

    private static RisksResponse.RiskItem toItem(Risk risk, Map<String, String> displayNames) {
        RisksResponse.WorkstreamTypeRef typeRef = null;
        if (risk.workstreamTypeCode() != null) {
            typeRef = new RisksResponse.WorkstreamTypeRef(
                    risk.workstreamTypeCode(),
                    displayNames.get(risk.workstreamTypeCode()));
        }
        return new RisksResponse.RiskItem(
                risk.code(),
                risk.severity(),
                risk.issueKey(),
                typeRef,
                risk.explanation(),
                risk.detectedAt(),
                risk.evidence());
    }

    private int resolveLimit(Integer limit) {
        int requested = limit == null ? properties.getDefaultLimit() : limit;
        if (requested < 1) {
            requested = properties.getDefaultLimit();
        }
        return Math.min(requested, properties.getMaxLimit());
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }
}
