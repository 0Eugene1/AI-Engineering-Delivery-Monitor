package ru.eltc.deliverymonitor.api.workstream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.eltc.deliverymonitor.api.security.SecurityConfig;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller-level tests for {@code GET /api/workstreams/progress}.
 */
@WebMvcTest(controllers = WorkstreamProgressController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "delivery-monitor.admin.token=test-only-placeholder-admin-token")
class WorkstreamProgressControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WorkstreamProgressQueryService workstreamProgressQueryService;

    @Test
    void progressEndpointIsPublicAndReturnsItems() throws Exception {
        given(workstreamProgressQueryService.progress()).willReturn(new WorkstreamProgressResponse(List.of(
                new WorkstreamProgressResponse.Item(
                        new WorkstreamProgressResponse.WorkstreamTypeRef("backend", "Backend"),
                        10,
                        8,
                        80),
                new WorkstreamProgressResponse.Item(
                        new WorkstreamProgressResponse.WorkstreamTypeRef("frontend", "Frontend"),
                        5,
                        3,
                        60))));

        mockMvc.perform(get("/api/workstreams/progress"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].workstreamType.code").value("backend"))
                .andExpect(jsonPath("$.items[0].percent").value(80))
                .andExpect(jsonPath("$.items[1].workstreamType.displayName").value("Frontend"))
                .andExpect(jsonPath("$.items[1].merged").value(3));
    }
}
