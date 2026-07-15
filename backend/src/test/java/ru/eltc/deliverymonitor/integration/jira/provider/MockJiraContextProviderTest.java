package ru.eltc.deliverymonitor.integration.jira.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;
import ru.eltc.deliverymonitor.integration.jira.config.JiraProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Verifies the offline provider serves the sanitized demo fixture and refuses to run in production. */
class MockJiraContextProviderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockJiraContextProvider newProvider(String... activeProfiles) {
        JiraProperties properties = new JiraProperties();
        properties.setMode(JiraProperties.Mode.MOCK);
        StandardEnvironment environment = new StandardEnvironment();
        if (activeProfiles.length > 0) {
            environment.setActiveProfiles(activeProfiles);
        }
        return new MockJiraContextProvider(properties, objectMapper, environment);
    }

    @Test
    void servesSanitizedDemoData() {
        JiraBoardContext context = newProvider().getBoardContext(0, 50);

        assertThat(context.mocked()).isTrue();
        assertThat(context.boardId()).isEqualTo(718L);
        assertThat(context.filterId()).isEqualTo(30532L);
        assertThat(context.total()).isEqualTo(5);
        assertThat(context.issues()).hasSize(5);
        assertThat(context.issues().get(0).key()).isEqualTo("MPTPSUPP-90001");
        assertThat(context.issues().get(0).fields().summary()).startsWith("[DEMO]");
    }

    @Test
    void appliesPaging() {
        MockJiraContextProvider provider = newProvider();

        JiraBoardContext firstPage = provider.getBoardContext(0, 2);
        assertThat(firstPage.issues()).hasSize(2);
        assertThat(firstPage.total()).isEqualTo(5);
        assertThat(firstPage.startAt()).isZero();
        assertThat(firstPage.maxResults()).isEqualTo(2);

        JiraBoardContext lastPage = provider.getBoardContext(4, 2);
        assertThat(lastPage.issues()).hasSize(1);
        assertThat(lastPage.total()).isEqualTo(5);
    }

    @Test
    void refusesToStartWithProductionProfile() {
        assertThatThrownBy(() -> newProvider("prod"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jira.mode=mock is not allowed");
    }
}
