package io.aegis.tenant.service;

import io.aegis.tenant.domain.Tenant;
import io.aegis.tenant.domain.TenantRepository;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Tenant lifecycle and resolution. */
@Service
public class TenantService {

    private static final Pattern SLUG = Pattern.compile("^[a-z0-9][a-z0-9-]{0,62}[a-z0-9]$");

    private final TenantRepository tenants;

    public TenantService(TenantRepository tenants) {
        this.tenants = tenants;
    }

    public static class DuplicateTenantException extends RuntimeException {
        public DuplicateTenantException(String m) {
            super(m);
        }
    }

    public static class TenantNotFoundException extends RuntimeException {
        public TenantNotFoundException(String m) {
            super(m);
        }
    }

    @Transactional
    public Tenant create(String name, String slug, String primaryDomain) {
        if (slug == null || !SLUG.matcher(slug).matches()) {
            throw new IllegalArgumentException("slug must be a lowercase DNS-safe label");
        }
        if (tenants.existsBySlug(slug)) {
            throw new DuplicateTenantException("slug already in use");
        }
        if (primaryDomain != null && !primaryDomain.isBlank()
                && tenants.existsByPrimaryDomain(primaryDomain)) {
            throw new DuplicateTenantException("domain already in use");
        }
        return tenants.save(new Tenant(UUID.randomUUID(), name, slug,
                (primaryDomain == null || primaryDomain.isBlank()) ? null : primaryDomain));
    }

    @Transactional(readOnly = true)
    public Tenant getBySlug(String slug) {
        return tenants.findBySlug(slug)
                .orElseThrow(() -> new TenantNotFoundException("no tenant with that slug"));
    }

    /** Host-based resolution used by the edge gateway to map a request host to a tenant. */
    @Transactional(readOnly = true)
    public Tenant resolveByDomain(String domain) {
        return tenants.findByPrimaryDomain(domain)
                .orElseThrow(() -> new TenantNotFoundException("no tenant for that domain"));
    }
}
