package io.aegis.tenant.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A white-label sign-in host claimed by a tenant (e.g. {@code login.acme.com}). Ownership is proven by
 * publishing a DNS TXT record ({@code _aegis-challenge.<domain>} = {@link #verificationToken}); once
 * {@link Status#VERIFIED} the edge/authorization-server may resolve the request Host to this tenant.
 * {@code domain} is globally unique so one host maps to exactly one tenant.
 */
@Entity
@Table(name = "custom_domain", indexes = @Index(name = "ix_custom_domain_tenant", columnList = "tenant_id"))
public class CustomDomain {

    public enum Status { PENDING, VERIFIED }

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(nullable = false, unique = true, length = 253)
    private String domain;

    @Column(name = "verification_token", nullable = false, length = 128)
    private String verificationToken;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.PENDING;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "verified_at")
    private Instant verifiedAt;

    protected CustomDomain() {
    }

    public CustomDomain(UUID id, String tenantId, String domain, String verificationToken) {
        this.id = id;
        this.tenantId = tenantId;
        this.domain = domain;
        this.verificationToken = verificationToken;
    }

    /** Mark this domain VERIFIED and stamp the moment. Idempotent. */
    public void markVerified() {
        this.status = Status.VERIFIED;
        if (this.verifiedAt == null) {
            this.verifiedAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getDomain() {
        return domain;
    }

    public String getVerificationToken() {
        return verificationToken;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }
}
