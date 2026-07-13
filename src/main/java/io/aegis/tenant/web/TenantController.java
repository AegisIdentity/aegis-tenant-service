package io.aegis.tenant.web;

import io.aegis.tenant.domain.Tenant;
import io.aegis.tenant.service.TenantService;
import io.aegis.tenant.web.TenantDtos.CreateTenantRequest;
import io.aegis.tenant.web.TenantDtos.TenantResponse;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Tenant control-plane API. Authorization is by scope in {@code SecurityConfig}. */
@RestController
public class TenantController {

    private final TenantService tenantService;

    public TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @PostMapping("/api/v1/tenants")
    public ResponseEntity<TenantResponse> create(@Valid @RequestBody CreateTenantRequest request) {
        Tenant tenant = tenantService.create(request.name(), request.slug(), request.primaryDomain());
        return ResponseEntity.created(URI.create("/api/v1/tenants/" + tenant.getSlug()))
                .body(TenantResponse.from(tenant));
    }

    @GetMapping("/api/v1/tenants/{slug}")
    public TenantResponse getBySlug(@PathVariable String slug) {
        return TenantResponse.from(tenantService.getBySlug(slug));
    }

    /** Host-based resolution for the edge gateway. */
    @GetMapping("/api/v1/tenants:resolve")
    public TenantResponse resolve(@RequestParam String domain) {
        return TenantResponse.from(tenantService.resolveByDomain(domain));
    }
}
