package ru.eltc.deliverymonitor.api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * Validates the {@code Authorization: Bearer <token>} header against the configured admin token
 * (Phase 2.4, ADR-012). Runs once per request ({@link OncePerRequestFilter}); {@link
 * SecurityConfig} decides which paths actually require the resulting authentication — this filter
 * itself only ever <em>adds</em> an authentication to the context, it never rejects a request.
 * Rejection (401) happens later, in Spring Security's access-decision + entry point, if the path
 * required authentication and none was set here.
 *
 * <p><b>Intentionally stateless.</b> This checks exactly one thing — does the caller possess the
 * admin token? — and nothing else. No user is looked up, no identity is extracted; on success the
 * security context holds a generic authenticated token with a single {@code ROLE_ADMIN}
 * authority (docs/security.md §2, §7, ADR-012). There is no {@code User}/{@code Role} entity, no
 * {@code UserRepository} and no principal model behind this.
 *
 * <p>The presented and expected token values are never logged, on either the success or the
 * failure path (docs/security.md §8).
 */
public class AdminTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AdminTokenProperties properties;

    public AdminTokenAuthenticationFilter(AdminTokenProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String presentedToken = header.substring(BEARER_PREFIX.length()).trim();
            if (matchesAdminToken(presentedToken)) {
                SecurityContextHolder.getContext().setAuthentication(new AdminTokenAuthentication());
            }
        }
        filterChain.doFilter(request, response);
    }

    /**
     * A blank configured token (misconfiguration — {@link AdminTokenProperties} is normally
     * fail-fast on this) must never authenticate a blank/empty presented token: that would turn
     * "admin token not set" into "no token required".
     */
    private boolean matchesAdminToken(String presentedToken) {
        String expectedToken = properties.getToken();
        if (presentedToken.isEmpty() || expectedToken == null || expectedToken.isBlank()) {
            return false;
        }
        return constantTimeEquals(presentedToken, expectedToken);
    }

    /** Avoids leaking token length/prefix information through comparison timing. */
    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Marker authentication meaning only "this request presented a valid admin token" — see the
     * class Javadoc. Not a principal model: {@link #getPrincipal()} returns a fixed label, never
     * anything derived from the token or request.
     */
    static final class AdminTokenAuthentication extends AbstractAuthenticationToken {

        AdminTokenAuthentication() {
            super(List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
            setAuthenticated(true);
        }

        @Override
        public Object getCredentials() {
            return null;
        }

        @Override
        public Object getPrincipal() {
            return "admin-token";
        }
    }
}
