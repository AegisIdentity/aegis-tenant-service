package io.aegis.tenant.web;

import io.aegis.tenant.domain.Tenant;
import io.aegis.tenant.service.TenantService;
import io.aegis.tenant.web.TenantDtos.CreateTenantRequest;
import io.aegis.tenant.web.TenantDtos.TenantResponse;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Tenant control-plane API. Authorization is by scope in {@code SecurityConfig}; ownership is enforced
 * here. Creating a tenant is a platform-operator action ({@code SCOPE_tenant:platform-admin}). Reads
 * are scoped to the caller's own tenant (from the JWT {@code tenant} claim, never the request) so a
 * tenant admin cannot enumerate other tenants (H3); a platform operator may read any tenant.
 */
@RestController
public class TenantController {

    /** Platform-operator scope: cross-tenant reads and tenant creation (H4). Distinct from the
     * per-tenant {@code tenant:admin} used for self-service custom-domain management. */
    private static final String PLATFORM_ADMIN = "SCOPE_tenant:platform-admin";

    private final TenantService tenantService;

    public TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @PostMapping("/api/v1/tenants")
    public ResponseEntity<TenantResponse> create(@Valid @RequestBody CreateTenantRequest request,
                                                 @AuthenticationPrincipal Jwt caller) {
        // The subject of a platform-operator token — a client id for a service token, a user id for a
        // console admin — so the audit trail records who created the tenant.
        String actor = caller == null ? null : caller.getSubject();
        Tenant tenant = tenantService.create(request.name(), request.slug(), request.primaryDomain(), actor);
        return ResponseEntity.created(URI.create("/api/v1/tenants/" + tenant.getSlug()))
                .body(TenantResponse.from(tenant));
    }

    @GetMapping("/api/v1/tenants/{slug}")
    public TenantResponse getBySlug(@PathVariable String slug, @AuthenticationPrincipal Jwt caller,
                                    Authentication authentication) {
        // H3: a non-operator may only read its own tenant. 404 (not 403) for anything else so the
        // endpoint is not a cross-tenant existence oracle.
        if (!isPlatformOperator(authentication) && !slug.equals(tenantOf(caller))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "no tenant with that slug");
        }
        return TenantResponse.from(tenantService.getBySlug(slug));
    }

    /** Host-based resolution for the edge gateway. Cross-tenant, so restricted to platform operators;
     * a per-tenant caller may only resolve a domain that belongs to its own tenant (H3). */
    @GetMapping("/api/v1/tenants:resolve")
    public TenantResponse resolve(@RequestParam String domain, @AuthenticationPrincipal Jwt caller,
                                  Authentication authentication) {
        Tenant tenant = tenantService.resolveByDomain(domain);
        if (!isPlatformOperator(authentication) && !tenant.getSlug().equals(tenantOf(caller))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "no tenant for that domain");
        }
        return TenantResponse.from(tenant);
    }

    private static boolean isPlatformOperator(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> PLATFORM_ADMIN.equals(a.getAuthority()));
    }

    private static String tenantOf(Jwt caller) {
        String tenant = caller == null ? null : caller.getClaimAsString("tenant");
        if (tenant == null || tenant.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "token carries no tenant");
        }
        return tenant;
    }
}
