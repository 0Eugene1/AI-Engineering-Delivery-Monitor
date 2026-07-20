package ru.eltc.deliverymonitor.api.activity;

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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller-level tests for {@code GET /api/activity} through the real {@link SecurityConfig}
 * (public read endpoint). {@link ActivityQueryService} is mocked.
 */
@WebMvcTest(controllers = ActivityController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "delivery-monitor.admin.token=test-only-placeholder-admin-token")
class ActivityControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ActivityQueryService activityQueryService;

    @Test
    void activityReturns200WithEvents() throws Exception {
        ObjectNode payload = JsonNodeFactory.instance.objectNode();
        payload.put("iid", 88);
        ActivityFeedResponse response = new ActivityFeedResponse(List.of(
                new ActivityFeedResponse.ActivityEvent(
                        "42",
                        Instant.parse("2026-07-20T10:12:00Z"),
                        "MR_MERGED",
                        "GITLAB",
                        "MPTPSUPP-43006",
                        new ActivityFeedResponse.WorkstreamTypeRef("backend", "Backend"),
                        new ActivityFeedResponse.ActorRef("j.doe", "John Doe"),
                        "merged MR !88",
                        payload)));
        given(activityQueryService.findFeed(isNull(), isNull(), isNull(), eq(true)))
                .willReturn(response);

        mockMvc.perform(get("/api/activity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events[0].id").value("42"))
                .andExpect(jsonPath("$.events[0].type").value("MR_MERGED"))
                .andExpect(jsonPath("$.events[0].source").value("GITLAB"))
                .andExpect(jsonPath("$.events[0].issueKey").value("MPTPSUPP-43006"))
                .andExpect(jsonPath("$.events[0].workstreamType.code").value("backend"))
                .andExpect(jsonPath("$.events[0].summary").value("merged MR !88"));
    }

    @Test
    void activityReturns200WithEmptyEvents() throws Exception {
        given(activityQueryService.findFeed(any(), any(), any(), anyBoolean()))
                .willReturn(new ActivityFeedResponse(List.of()));

        mockMvc.perform(get("/api/activity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events").isArray())
                .andExpect(jsonPath("$.events").isEmpty());
    }

    @Test
    void activityPassesQueryParamsToService() throws Exception {
        given(activityQueryService.findFeed(any(), any(), any(), anyBoolean()))
                .willReturn(new ActivityFeedResponse(List.of()));

        mockMvc.perform(get("/api/activity")
                        .param("since", "2026-07-15T00:00:00Z")
                        .param("limit", "10")
                        .param("workstreamType", "frontend")
                        .param("orphans", "false"))
                .andExpect(status().isOk());

        verify(activityQueryService).findFeed(
                Instant.parse("2026-07-15T00:00:00Z"),
                10,
                "frontend",
                false);
    }
}
