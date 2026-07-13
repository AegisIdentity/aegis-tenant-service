package io.aegis.tenant.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A tenant (customer organization). {@code slug} is the stable identifier used in the per-tenant
 * OIDC issuer path ({@code /t/{slug}}); {@code primaryDomain} supports host-based tenant resolution
 * at the edge. Both are globally unique. (Multiple domains per tenant is a documented enhancement.)
 */
@Entity
@Table(name = "tenant")
public class Tenant {

    public enum Status { ACTIVE, SUSPENDED }

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, unique = true, length = 64)
    private String slug;

    @Column(name = "primary_domain", unique = true, length = 253)
    private String primaryDomain;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Tenant() {
    }

    public Tenant(UUID id, String name, String slug, String primaryDomain) {
        this.id = id;
        this.name = name;
        this.slug = slug;
        this.primaryDomain = primaryDomain;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getSlug() {
        return slug;
    }

    public String getPrimaryDomain() {
        return primaryDomain;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
