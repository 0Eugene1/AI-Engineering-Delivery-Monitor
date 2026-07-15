package ru.eltc.deliverymonitor.api.issue;

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
import java.util.Optional;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller-level tests for {@code GET /api/issues} and {@code GET /api/issues/{key}} through
 * the real {@link SecurityConfig} filter chain (imported explicitly, same reason as
 * {@code JiraSyncControllerTest} — {@code @WebMvcTest} does not auto-scan {@code @Configuration}
 * classes outside the sliced controller, and with {@code spring-boot-starter-security} on the
 * classpath the default behaviour without an explicit chain would require authentication for
 * every request). Importing {@link SecurityConfig} also pulls in its {@code AdminTokenProperties}
 * fail-fast validation, so a placeholder admin token is set here too (never presented by these
 * tests — {@code /api/issues/**} is {@code permitAll()}). {@link IssueQueryService} is mocked: no
 * real PostgreSQL.
 */
@WebMvcTest(controllers = IssueController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "delivery-monitor.admin.token=test-only-placeholder-admin-token")
class IssueControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IssueQueryService issueQueryService;

    @Test
    void listEndpointIsPubliclyAccessibleAndReturnsIssues() throws Exception {
        IssueResponse issue = sampleIssue();
        given(issueQueryService.findAll()).willReturn(List.of(issue));

        mockMvc.perform(get("/api/issues"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].issueKey").value("MPTPSUPP-1"))
                .andExpect(jsonPath("$[0].summary").value("Fix the thing"))
                .andExpect(jsonPath("$[0].fixVersions[0]").value("5.7.27"))
                .andExpect(jsonPath("$[0].labels[0]").value("backend"));
    }

    @Test
    void getByKeyReturnsIssueWhenFound() throws Exception {
        given(issueQueryService.findByKey("MPTPSUPP-1")).willReturn(Optional.of(sampleIssue()));

        mockMvc.perform(get("/api/issues/MPTPSUPP-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issueKey").value("MPTPSUPP-1"))
                .andExpect(jsonPath("$.status").value("In Review"))
                .andExpect(jsonPath("$.statusCategory").value("In Progress"))
                .andExpect(jsonPath("$.assigneeUsername").value("j.doe"));
    }

    @Test
    void getByKeyReturns404WithErrorBodyWhenNotFound() throws Exception {
        given(issueQueryService.findByKey("UNKNOWN-404")).willReturn(Optional.empty());

        mockMvc.perform(get("/api/issues/UNKNOWN-404"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.code").value("ISSUE_NOT_FOUND"));
    }

    private static IssueResponse sampleIssue() {
        return new IssueResponse(
                "MPTPSUPP-1",
                "Fix the thing",
                "In Review",
                "In Progress",
                "j.doe",
                "John Doe",
                "Bug",
                Instant.parse("2026-07-01T09:00:00Z"),
                Instant.parse("2026-07-10T12:30:00Z"),
                List.of("5.7.27"),
                List.of("backend"));
    }
}
