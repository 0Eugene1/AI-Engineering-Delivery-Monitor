package ru.eltc.deliverymonitor.api.issue;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.eltc.deliverymonitor.api.security.SecurityConfig;

import java.time.Instant;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller-level tests for {@code GET /api/issues/{key}/timeline} through the real
 * {@link SecurityConfig} (public read endpoint). {@link TimelineQueryService} is mocked.
 */
@WebMvcTest(controllers = TimelineController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "delivery-monitor.admin.token=test-only-placeholder-admin-token")
class TimelineControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TimelineQueryService timelineQueryService;

    @Test
    void timelineReturns200WithEventsOrderedAsProvided() throws Exception {
        ObjectNode payload = JsonNodeFactory.instance.objectNode();
        payload.put("branch", "feature/MPTPSUPP-1");
        TimelineResponse response = new TimelineResponse(
                "MPTPSUPP-1",
                List.of(new TimelineResponse.TimelineEvent(
                        "42",
                        Instant.parse("2026-07-10T12:00:00Z"),
                        "BRANCH_CREATED",
                        new TimelineResponse.WorkstreamTypeRef("backend", "Backend"),
                        new TimelineResponse.ActorRef("j.doe", "John Doe"),
                        "created branch feature/MPTPSUPP-1",
                        payload)));
        given(timelineQueryService.findTimeline("MPTPSUPP-1")).willReturn(response);

        mockMvc.perform(get("/api/issues/MPTPSUPP-1/timeline"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issueKey").value("MPTPSUPP-1"))
                .andExpect(jsonPath("$.events[0].type").value("BRANCH_CREATED"))
                .andExpect(jsonPath("$.events[0].workstreamType.code").value("backend"))
                .andExpect(jsonPath("$.events[0].workstreamType.displayName").value("Backend"))
                .andExpect(jsonPath("$.events[0].summary").value("created branch feature/MPTPSUPP-1"))
                .andExpect(jsonPath("$.events[0].payload.branch").value("feature/MPTPSUPP-1"));
    }

    @Test
    void timelineReturns200WithEmptyEventsWhenNothingLinked() throws Exception {
        given(timelineQueryService.findTimeline("UNKNOWN"))
                .willReturn(new TimelineResponse("UNKNOWN", List.of()));

        mockMvc.perform(get("/api/issues/UNKNOWN/timeline"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issueKey").value("UNKNOWN"))
                .andExpect(jsonPath("$.events").isArray())
                .andExpect(jsonPath("$.events").isEmpty());
    }
}
