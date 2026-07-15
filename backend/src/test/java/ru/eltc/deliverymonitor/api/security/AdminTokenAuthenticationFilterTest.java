package ru.eltc.deliverymonitor.api.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link AdminTokenAuthenticationFilter} in isolation — no Spring context, no
 * {@code SecurityFilterChain}. Verifies the token comparison logic itself: correct token
 * authenticates, missing/blank/wrong token does not, and the filter chain always continues (the
 * 401 decision belongs to {@link SecurityConfig}, not this filter).
 */
class AdminTokenAuthenticationFilterTest {

    private static final String VALID_TOKEN = "s3cr3t-admin-token";

    @org.junit.jupiter.api.AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatesWhenBearerTokenMatches() throws Exception {
        AdminTokenAuthenticationFilter filter = filterWithToken(VALID_TOKEN);
        MockHttpServletRequest request = requestWithAuthorization("Bearer " + VALID_TOKEN);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(authentication.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_ADMIN");
        // No identity is extracted from the token (docs/security.md §2, §7, ADR-012).
        assertThat(authentication.getPrincipal()).isEqualTo("admin-token");
        assertThat(authentication.getCredentials()).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    void doesNotAuthenticateWhenHeaderIsMissing() throws Exception {
        AdminTokenAuthenticationFilter filter = filterWithToken(VALID_TOKEN);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    void doesNotAuthenticateWhenTokenIsWrong() throws Exception {
        AdminTokenAuthenticationFilter filter = filterWithToken(VALID_TOKEN);
        MockHttpServletRequest request = requestWithAuthorization("Bearer wrong-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    void doesNotAuthenticateWhenHeaderIsNotBearerScheme() throws Exception {
        AdminTokenAuthenticationFilter filter = filterWithToken(VALID_TOKEN);
        MockHttpServletRequest request = requestWithAuthorization("Basic " + VALID_TOKEN);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    void doesNotAuthenticateWhenBearerTokenIsBlank() throws Exception {
        AdminTokenAuthenticationFilter filter = filterWithToken(VALID_TOKEN);
        MockHttpServletRequest request = requestWithAuthorization("Bearer ");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    void neverAuthenticatesWhenConfiguredTokenIsBlank() throws Exception {
        // A blank configured token is a misconfiguration (normally caught fail-fast by
        // AdminTokenProperties' @NotBlank) — even a blank presented token must not match it.
        AdminTokenAuthenticationFilter filter = filterWithToken("");
        MockHttpServletRequest request = requestWithAuthorization("Bearer ");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }

    private static AdminTokenAuthenticationFilter filterWithToken(String token) {
        AdminTokenProperties properties = new AdminTokenProperties();
        properties.setToken(token);
        return new AdminTokenAuthenticationFilter(properties);
    }

    private static MockHttpServletRequest requestWithAuthorization(String headerValue) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, headerValue);
        return request;
    }
}
