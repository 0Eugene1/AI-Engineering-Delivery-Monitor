package ru.eltc.deliverymonitor.api.admin;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.eltc.deliverymonitor.api.security.SecurityConfig;
import ru.eltc.deliverymonitor.sync.gitlab.GitLabSyncResult;
import ru.eltc.deliverymonitor.sync.gitlab.GitLabSyncService;

import java.time.Instant;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller-level tests for {@code POST /api/admin/sync/gitlab} through the real {@link
 * SecurityConfig} filter chain (imported explicitly — {@code @WebMvcTest} does not auto-scan
 * {@code @Configuration} classes outside the sliced controller). {@link GitLabSyncService} is
 * mocked: no real GitLab, no real PostgreSQL.
 *
 * <p>Covers: successful POST with the correct Bearer token (service interaction), 401 without
 * token, 401 with a wrong token.
 */
@WebMvcTest(controllers = GitLabSyncController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "delivery-monitor.admin.token=" + GitLabSyncControllerTest.ADMIN_TOKEN)
class GitLabSyncControllerTest {

    static final String ADMIN_TOKEN = "test-admin-token";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GitLabSyncService gitLabSyncService;

    @Test
    void returnsSyncResultWhenBearerTokenIsCorrect() throws Exception {
        GitLabSyncResult result = new GitLabSyncResult(
                Instant.parse("2026-07-17T10:00:00Z"),
                Instant.parse("2026-07-17T10:00:05Z"),
                3, 10, 20, 5, 4, false, 30, 5, List.of());
        given(gitLabSyncService.syncAll()).willReturn(result);

        mockMvc.perform(post("/api/admin/sync/gitlab")
                        .header("Authorization", "Bearer " + ADMIN_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectsSynced").value(3))
                .andExpect(jsonPath("$.branchesFetched").value(10))
                .andExpect(jsonPath("$.commitsFetched").value(20))
                .andExpect(jsonPath("$.mergeRequestsFetched").value(5))
                .andExpect(jsonPath("$.created").value(30))
                .andExpect(jsonPath("$.updated").value(5))
                // GitLabSyncResult#saved() / #fetched() are derived methods, not record
                // components — Jackson does not serialize them.
                .andExpect(jsonPath("$.mocked").value(false))
                .andExpect(jsonPath("$.errors").isEmpty());

        verify(gitLabSyncService).syncAll();
    }

    @Test
    void rejectsRequestWithoutAuthorizationHeader() throws Exception {
        mockMvc.perform(post("/api/admin/sync/gitlab"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(gitLabSyncService);
    }

    @Test
    void rejectsRequestWithWrongToken() throws Exception {
        mockMvc.perform(post("/api/admin/sync/gitlab")
                        .header("Authorization", "Bearer wrong-token"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(gitLabSyncService);
    }
}
