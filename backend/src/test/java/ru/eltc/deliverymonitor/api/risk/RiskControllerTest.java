package ru.eltc.deliverymonitor.api.risk;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.eltc.deliverymonitor.api.security.SecurityConfig;
import ru.eltc.deliverymonitor.domain.risk.RiskCodes;
import ru.eltc.deliverymonitor.domain.risk.RiskSeverities;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller-level tests for {@code GET /api/risks} through the real {@link SecurityConfig}
 * (public read endpoint). {@link RiskQueryService} is mocked.
 */
@WebMvcTest(controllers = RiskController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "delivery-monitor.admin.token=test-only-placeholder-admin-token")
class RiskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RiskQueryService riskQueryService;

    @Test
    void risksReturns200WithItems() throws Exception {
        RisksResponse response = new RisksResponse(List.of(
                new RisksResponse.RiskItem(
                        RiskCodes.OPEN_MR_STALE,
                        RiskSeverities.MEDIUM,
                        "MPTPSUPP-43006",
                        new RisksResponse.WorkstreamTypeRef("backend", "Backend"),
                        "MR !88 opened for 7 days without merge",
                        Instant.parse("2026-07-20T12:00:00Z"),
                        Map.of(
                                "mergeRequestIid", 88,
                                "openedAt", "2026-07-13T09:00:00Z"))));
        given(riskQueryService.findRisks(isNull(), isNull(), isNull(), isNull()))
                .willReturn(response);

        mockMvc.perform(get("/api/risks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.risks[0].code").value("OPEN_MR_STALE"))
                .andExpect(jsonPath("$.risks[0].severity").value("MEDIUM"))
                .andExpect(jsonPath("$.risks[0].issueKey").value("MPTPSUPP-43006"))
                .andExpect(jsonPath("$.risks[0].workstreamType.code").value("backend"))
                .andExpect(jsonPath("$.risks[0].evidence.mergeRequestIid").value(88));
    }

    @Test
    void risksReturns200WithEmptyList() throws Exception {
        given(riskQueryService.findRisks(any(), any(), any(), any()))
                .willReturn(new RisksResponse(List.of()));

        mockMvc.perform(get("/api/risks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.risks").isArray())
                .andExpect(jsonPath("$.risks").isEmpty());
    }

    @Test
    void risksPassesQueryParamsToService() throws Exception {
        given(riskQueryService.findRisks(any(), any(), any(), any()))
                .willReturn(new RisksResponse(List.of()));

        mockMvc.perform(get("/api/risks")
                        .param("severity", "MEDIUM")
                        .param("code", "NO_MR")
                        .param("issueKey", "MPTPSUPP-1")
                        .param("limit", "10"))
                .andExpect(status().isOk());

        verify(riskQueryService).findRisks(
                eq("MEDIUM"),
                eq("NO_MR"),
                eq("MPTPSUPP-1"),
                eq(10));
    }
}
