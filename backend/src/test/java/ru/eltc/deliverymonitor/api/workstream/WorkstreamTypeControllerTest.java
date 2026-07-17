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
 * Controller-level tests for {@code GET /api/workstream-types} through the real
 * {@link SecurityConfig}. {@link WorkstreamTypeQueryService} is mocked.
 */
@WebMvcTest(controllers = WorkstreamTypeController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "delivery-monitor.admin.token=test-only-placeholder-admin-token")
class WorkstreamTypeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WorkstreamTypeQueryService workstreamTypeQueryService;

    @Test
    void listEndpointIsPublicAndReturnsActiveTypes() throws Exception {
        given(workstreamTypeQueryService.findActive()).willReturn(List.of(
                new WorkstreamTypeResponse("backend", "Backend", 1),
                new WorkstreamTypeResponse("frontend", "Frontend", 2)));

        mockMvc.perform(get("/api/workstream-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("backend"))
                .andExpect(jsonPath("$[0].displayName").value("Backend"))
                .andExpect(jsonPath("$[0].sortOrder").value(1))
                .andExpect(jsonPath("$[1].code").value("frontend"));
    }
}
