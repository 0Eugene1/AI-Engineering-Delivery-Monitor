package ru.eltc.deliverymonitor.api.admin;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.eltc.deliverymonitor.api.security.SecurityConfig;
import ru.eltc.deliverymonitor.sync.jira.JiraSyncResult;
import ru.eltc.deliverymonitor.sync.jira.JiraSyncService;

import java.time.Instant;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller-level tests for {@code POST /api/admin/sync/jira} through the real {@link
 * SecurityConfig} filter chain (imported explicitly — {@code @WebMvcTest} does not auto-scan
 * {@code @Configuration} classes outside the sliced controller). {@link JiraSyncService} is
 * mocked: no real Jira, no real PostgreSQL.
 *
 * <p>Covers the three scenarios from the agreed design: successful POST with the correct Bearer
 * token, no token, and a wrong token.
 */
@WebMvcTest(controllers = JiraSyncController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "delivery-monitor.admin.token=" + JiraSyncControllerTest.ADMIN_TOKEN)
class JiraSyncControllerTest {

    static final String ADMIN_TOKEN = "test-admin-token";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JiraSyncService jiraSyncService;

    @Test
    void returnsSyncResultWhenBearerTokenIsCorrect() throws Exception {
        JiraSyncResult result = new JiraSyncResult(
                Instant.parse("2026-07-15T10:00:00Z"),
                Instant.parse("2026-07-15T10:00:03Z"),
                2, 1, false, 1, 1, List.of());
        given(jiraSyncService.syncBoard()).willReturn(result);

        mockMvc.perform(post("/api/admin/sync/jira")
                        .header("Authorization", "Bearer " + ADMIN_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fetched").value(2))
                .andExpect(jsonPath("$.created").value(1))
                .andExpect(jsonPath("$.updated").value(1))
                // JiraSyncResult#saved() is a derived method, not a record component — Jackson
                // does not serialize it, so it deliberately does not appear in the JSON body.
                .andExpect(jsonPath("$.mocked").value(false))
                .andExpect(jsonPath("$.errors").isEmpty());

        verify(jiraSyncService).syncBoard();
    }

    @Test
    void rejectsRequestWithoutAuthorizationHeader() throws Exception {
        mockMvc.perform(post("/api/admin/sync/jira"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(jiraSyncService);
    }

    @Test
    void rejectsRequestWithWrongToken() throws Exception {
        mockMvc.perform(post("/api/admin/sync/jira")
                        .header("Authorization", "Bearer wrong-token"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(jiraSyncService);
    }
}
