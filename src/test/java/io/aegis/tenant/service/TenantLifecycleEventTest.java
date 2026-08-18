package io.aegis.tenant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.aegis.commons.audit.AuditEventPublisher;
import io.aegis.commons.events.DomainEventPublisher;
import io.aegis.tenant.domain.Tenant;
import io.aegis.tenant.domain.TenantRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Creating a tenant must publish a {@code tenant.created} BUSINESS event (so the authorization-server
 * can eagerly provision the tenant's signing key) — on the tenant-lifecycle topic, keyed by slug,
 * distinct from the audit stream.
 */
class TenantLifecycleEventTest {

    private final TenantRepository tenants = mock(TenantRepository.class);
    private final AuditEventPublisher audit = mock(AuditEventPublisher.class);
    private final DomainEventPublisher domainPublisher = mock(DomainEventPublisher.class);

    @SuppressWarnings("unchecked")
    private final ObjectProvider<DomainEventPublisher> domainProvider = mock(ObjectProvider.class);

    private TenantService service() {
        when(domainProvider.getIfAvailable()).thenReturn(domainPublisher);
        when(tenants.existsBySlug(any())).thenReturn(false);
        when(tenants.save(any())).thenAnswer(i -> i.getArgument(0));
        return new TenantService(tenants, audit, domainProvider);
    }

    @Test
    void creating_a_tenant_publishes_a_tenant_created_business_event() {
        service().create("Acme Corp", "acme", null, "operator@platform");

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(domainPublisher).publish(
                eq(TenantService.TENANT_LIFECYCLE_TOPIC), eq("acme"), payload.capture());

        assertThat(payload.getValue()).isInstanceOf(TenantService.TenantLifecycleEvent.class);
        TenantService.TenantLifecycleEvent event = (TenantService.TenantLifecycleEvent) payload.getValue();
        assertThat(event.eventType()).isEqualTo("tenant.created");
        assertThat(event.slug()).isEqualTo("acme");
        assertThat(event.name()).isEqualTo("Acme Corp");
        assertThat(event.occurredAt()).isNotNull();
    }

    @Test
    void tenant_creation_still_succeeds_when_no_domain_publisher_is_configured() {
        // No Kafka -> no publisher. Creation (and the audit event) must still work.
        when(domainProvider.getIfAvailable()).thenReturn(null);
        when(tenants.existsBySlug(any())).thenReturn(false);
        when(tenants.save(any())).thenAnswer(i -> i.getArgument(0));

        Tenant created = new TenantService(tenants, audit, domainProvider)
                .create("Globex", "globex", null, "operator");

        assertThat(created.getSlug()).isEqualTo("globex");
    }
}
