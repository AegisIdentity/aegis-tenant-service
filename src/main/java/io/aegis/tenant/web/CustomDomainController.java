package io.aegis.tenant.web;

import io.aegis.tenant.service.CustomDomainService;
import io.aegis.tenant.web.CustomDomainDtos.AddDomainRequest;
import io.aegis.tenant.web.CustomDomainDtos.DomainView;
import io.aegis.tenant.web.CustomDomainDtos.ResolveResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Per-tenant custom-domain (white-label sign-in host) API. The tenant-admin surface is gated by
 * {@code SCOPE_tenant:admin} and always scopes to the caller's own tenant (from the token, never the
 * request). The internal resolve endpoint is gated by {@code SCOPE_tenant:resolve} (server-to-server;
 * only the authorization-server's service token carries it) and maps a request Host to its tenant.
 * Authorization is wired in {@code SecurityConfig}.
 */
@RestController
public class CustomDomainController {

    private final CustomDomainService service;
    private final String ingressTarget;

    public CustomDomainController(CustomDomainService service,
                                  @Value("${aegis.domains.ingress-target:ingress.aegis.example}") String ingressTarget) {
        this.service = service;
        this.ingressTarget = ingressTarget;
    }

    @GetMapping("/api/v1/domains")
    public List<DomainView> list(@AuthenticationPrincipal Jwt caller) {
        return service.list(tenantOf(caller)).stream()
                .map(cd -> DomainView.from(cd, ingressTarget))
                .toList();
    }

    @PostMapping("/api/v1/domains")
    public ResponseEntity<DomainView> add(@Valid @RequestBody AddDomainRequest request,
                                          @AuthenticationPrincipal Jwt caller) {
        DomainView view = DomainView.from(service.add(tenantOf(caller), request.domain()), ingressTarget);
        return ResponseEntity.created(URI.create("/api/v1/domains/" + view.id())).body(view);
    }

    @PostMapping("/api/v1/domains/{id}/verify")
    public DomainView verify(@PathVariable UUID id, @AuthenticationPrincipal Jwt caller) {
        return DomainView.from(service.verify(tenantOf(caller), id), ingressTarget);
    }

    @DeleteMapping("/api/v1/domains/{id}")
    public ResponseEntity<Void> remove(@PathVariable UUID id, @AuthenticationPrincipal Jwt caller) {
        service.remove(tenantOf(caller), id);
        return ResponseEntity.noContent().build();
    }

    /** Server-to-server: map a request Host to the tenant that owns it (VERIFIED only), else 404. */
    @GetMapping("/api/v1/internal/domains/resolve")
    public ResolveResponse resolve(@RequestParam String host) {
        String tenant = service.resolve(host);
        if (tenant == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "no verified tenant for that host");
        }
        return new ResolveResponse(tenant);
    }

    private static String tenantOf(Jwt caller) {
        String tenant = caller.getClaimAsString("tenant");
        if (tenant == null || tenant.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "token carries no tenant");
        }
        return tenant;
    }
}
