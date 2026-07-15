package ru.eltc.deliverymonitor.api.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Minimal Spring Security enforcement layer (Phase 2.4, ADR-012). Its scope is deliberately
 * narrow: gate {@code /api/admin/**} and every other actuator endpoint (e.g. {@code
 * /actuator/info} can leak configuration) behind {@link AdminTokenAuthenticationFilter}, keep
 * {@code /actuator/health} public, and leave every other endpoint exactly as it was before
 * Security was introduced (currently open within the VPN perimeter — docs/security.md §6). This
 * is <b>not</b> a full user authentication system.
 *
 * <p>No JWT, no OAuth2 Resource Server, no OIDC, no LDAP — these remain the target model for
 * corporate SSO (docs/security.md §2) and are deferred to a future ADR.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(AdminTokenProperties.class)
public class SecurityConfig {

    @Bean
    public AdminTokenAuthenticationFilter adminTokenAuthenticationFilter(AdminTokenProperties properties) {
        return new AdminTokenAuthenticationFilter(properties);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http, AdminTokenAuthenticationFilter adminTokenAuthenticationFilter) throws Exception {
        http
                // Machine-to-machine Bearer token, not a browser session — no CSRF token to protect,
                // and no session to fix/replay.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health").permitAll()
                        // Any other actuator endpoint (e.g. /actuator/info) can leak configuration
                        // details, so ADR-012's endpoint policy protects it too — only health/liveness
                        // is public.
                        .requestMatchers("/actuator/**").authenticated()
                        .requestMatchers("/api/admin/**").authenticated()
                        .anyRequest().permitAll())
                // A missing/invalid admin token must end in 401, not a redirect to a login page.
                .exceptionHandling(handling ->
                        handling.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(adminTokenAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
