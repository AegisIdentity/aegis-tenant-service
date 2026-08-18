package io.aegis.tenant.service;

import io.aegis.commons.audit.AuditEvent;
import io.aegis.commons.audit.AuditEventPublisher;
import io.aegis.commons.audit.AuditOutcome;
import io.aegis.tenant.domain.Tenant;
import io.aegis.tenant.domain.TenantRepository;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Tenant lifecycle and resolution. */
@Service
public class TenantService {

    private static final Logger log = LoggerFactory.getLogger(TenantService.class);
    private static final Pattern SLUG = Pattern.compile("^[a-z0-9][a-z0-9-]{0,62}[a-z0-9]$");

    /** Business topic for tenant lifecycle events (distinct from the audit topic). */
    static final String TENANT_LIFECYCLE_TOPIC = "aegis.tenant.lifecycle";

    private final TenantRepository tenants;
    private final AuditEventPublisher audit;
    private final org.springframework.beans.factory.ObjectProvider<
            io.aegis.commons.events.DomainEventPublisher> domainEvents;

    public TenantService(TenantRepository tenants, AuditEventPublisher audit,
                         org.springframework.beans.factory.ObjectProvider<
                                 io.aegis.commons.events.DomainEventPublisher> domainEvents) {
        this.tenants = tenants;
        this.audit = audit;
        this.domainEvents = domainEvents;
    }

    /**
     * The tenant-lifecycle integration event. Its own record (not shared) so the producer owns the
     * schema and consumers read tolerantly — the loose coupling event-driven services want.
     */
    public record TenantLifecycleEvent(String eventType, String tenantId, String slug, String name,
                                       java.time.Instant occurredAt) {
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
    public Tenant create(String name, String slug, String primaryDomain, String actor) {
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
        Tenant created = tenants.save(new Tenant(UUID.randomUUID(), name, slug,
                (primaryDomain == null || primaryDomain.isBlank()) ? null : primaryDomain));
        // A new top-level tenant is a control-plane event — stream it to the platform audit trail,
        // attributed to the operator who created it (from their token subject).
        publish("tenant.created", slug, actor, "tenant name=" + name);
        // ...and publish a BUSINESS event so downstream services can react (the authorization-server
        // eagerly provisions this tenant's signing key). Separate topic, separate concern from audit.
        io.aegis.commons.events.DomainEventPublisher publisher = domainEvents.getIfAvailable();
        if (publisher != null) {
            publisher.publish(TENANT_LIFECYCLE_TOPIC, slug,
                    new TenantLifecycleEvent("tenant.created", slug, slug, name, java.time.Instant.now()));
        }
        return created;
    }

    /** Best-effort audit stream; never fails the caller's operation (audit degrades, never breaks). */
    private void publish(String action, String tenantSlug, String actor, String detail) {
        try {
            audit.publish(AuditEvent.of("tenant", action, AuditOutcome.SUCCESS)
                    .tenant(tenantSlug)
                    .actor(actor == null || actor.isBlank() ? "system" : actor)
                    .target(tenantSlug)
                    .attribute("detail", detail)
                    .build());
        } catch (RuntimeException ex) {
            log.warn("tenant audit publish failed (action={}, slug={}): {}", action, tenantSlug, ex.toString());
        }
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
