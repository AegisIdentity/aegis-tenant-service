package io.aegis.tenant;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * The service must START with the exact CORS origins the local stack injects
 * ({@code http://localhost:3000}, over plaintext).
 *
 * <p>This is the test that would have caught F1. The pre-existing ITs default
 * {@code aegis.cors.allowed-origins} to {@code https://app.aegis.local} — an https origin — so they
 * never exercised the http-localhost path, and the service shipped unable to boot under
 * {@code docker compose up} while 15 green tests said all was well. Asserting on the factory in
 * isolation does not help either: the factory always behaved this way; what broke was the caller
 * plus the profile. Only a real context start with the stack's origins discriminates.
 */
@SpringBootTest(properties = "aegis.cors.allowed-origins=http://localhost:3000,http://localhost:8080")
@ActiveProfiles("dev")
@Import(TenantTestConfig.class)
class CorsStartupIT {

    @Test
    void the_context_starts_with_the_local_stack_plaintext_origins() {
        // Reaching here means the CorsConfigurationSource bean was built — i.e. the dev profile's
        // insecure-localhost escape hatch was actually applied. Before the fix, context init threw
        // "origin must use https://" and this failed.
    }
}
