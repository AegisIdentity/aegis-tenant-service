package io.aegis.tenant.config;

import io.aegis.commons.security.CorsConfigFactory;
import io.aegis.commons.security.SecurityHardening;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;

/** Resource-server security for the tenant API. Tenant reads need {@code tenant:read} (ownership is
 * enforced in the controller, H3) or the platform-operator scope for cross-tenant reads; creating a
 * top-level tenant needs the dedicated platform-operator scope {@code tenant:platform-admin} (H4),
 * NOT the per-tenant {@code tenant:admin} used for self-service custom-domain management; the internal
 * Host&rarr;tenant resolve endpoint needs the service scope {@code tenant:resolve}. Default-deny,
 * shared hardening baseline. */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
        SecurityHardening.applyHardeningHeaders(http);
        SecurityHardening.statelessBearerApi(http);
        http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        // H4: creating a top-level tenant is a platform-operator action, not a
                        // per-tenant admin one.
                        .requestMatchers(HttpMethod.POST, "/api/v1/tenants")
                        .hasAuthority("SCOPE_tenant:platform-admin")
                        // Reads: per-tenant read scope (controller enforces ownership, H3) or a
                        // platform operator (may read any tenant).
                        .requestMatchers(HttpMethod.GET, "/api/v1/tenants/**", "/api/v1/tenants:resolve")
                        .hasAnyAuthority("SCOPE_tenant:read", "SCOPE_tenant:platform-admin")
                        // Server-to-server Host->tenant resolution for the edge/authorization-server.
                        // Only the AS's own service token carries tenant:resolve.
                        .requestMatchers("/api/v1/internal/domains/**").hasAuthority("SCOPE_tenant:resolve")
                        // Tenant-admin custom-domain (white-label sign-in host) management.
                        .requestMatchers("/api/v1/domains", "/api/v1/domains/**").hasAuthority("SCOPE_tenant:admin")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
        return http.build();
    }

    /**
     * CORS origins. {@code CorsConfigFactory} requires https:// origins, with a dev-only escape
     * hatch for plaintext localhost — and that escape hatch must actually be passed, or the service
     * fails to START under the local stack, which serves the console over http://localhost.
     * Gating it on the explicit {@code dev} profile keeps the production rule fail-closed: a
     * stage/prod deploy that is handed an http:// origin still refuses to start rather than
     * silently allowing a plaintext origin to read authenticated responses.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${aegis.cors.allowed-origins}") List<String> allowedOrigins,
            org.springframework.core.env.Environment environment) {
        boolean devProfile = java.util.Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile -> profile.equalsIgnoreCase("dev"));
        return CorsConfigFactory.fromAllowedOrigins(allowedOrigins, devProfile);
    }
}
