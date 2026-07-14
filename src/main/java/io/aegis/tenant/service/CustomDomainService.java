package io.aegis.tenant.service;

import io.aegis.tenant.domain.CustomDomain;
import io.aegis.tenant.domain.CustomDomain.Status;
import io.aegis.tenant.domain.CustomDomainRepository;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Per-tenant custom-domain (white-label sign-in host) registry: claim a host, prove ownership by DNS
 * TXT challenge, then resolve a request Host back to the owning tenant. Every mutating/read operation
 * is scoped to the caller's tenant (taken from the token), so an admin only ever sees their own hosts.
 */
@Service
public class CustomDomainService {

    /** Hostname label rules: letters/digits/hyphens, 1..63 per label, at least two labels. */
    private static final Pattern HOSTNAME = Pattern.compile(
            "^(?=.{1,253}$)(?!-)[a-z0-9-]{1,63}(?<!-)(\\.(?!-)[a-z0-9-]{1,63}(?<!-))+$");

    private static final SecureRandom RANDOM = new SecureRandom();

    private final CustomDomainRepository domains;
    private final DnsVerifier dnsVerifier;
    private final boolean autoVerify;

    public CustomDomainService(CustomDomainRepository domains, DnsVerifier dnsVerifier,
                               @Value("${aegis.domains.auto-verify:false}") boolean autoVerify) {
        this.domains = domains;
        this.dnsVerifier = dnsVerifier;
        this.autoVerify = autoVerify;
    }

    public static class DuplicateDomainException extends RuntimeException {
        public DuplicateDomainException(String m) {
            super(m);
        }
    }

    public static class InvalidDomainException extends RuntimeException {
        public InvalidDomainException(String m) {
            super(m);
        }
    }

    public static class DomainNotFoundException extends RuntimeException {
        public DomainNotFoundException(String m) {
            super(m);
        }
    }

    public static class VerificationFailedException extends RuntimeException {
        public VerificationFailedException(String m) {
            super(m);
        }
    }

    @Transactional(readOnly = true)
    public List<CustomDomain> list(String tenant) {
        return domains.findByTenantId(tenant);
    }

    @Transactional
    public CustomDomain add(String tenant, String domain) {
        String normalized = normalize(domain);
        if (!HOSTNAME.matcher(normalized).matches()) {
            throw new InvalidDomainException("domain must be a valid hostname, e.g. login.acme.com");
        }
        if (domains.existsByDomain(normalized)) {
            throw new DuplicateDomainException("domain already registered");
        }
        String token = "aegis-verify=" + randomToken();
        return domains.save(new CustomDomain(UUID.randomUUID(), tenant, normalized, token));
    }

    /**
     * Verify ownership. Unless {@code aegis.domains.auto-verify=true}, requires a DNS TXT record at
     * {@code _aegis-challenge.<domain>} equal to the stored verification token; a missing record → 400
     * via {@link VerificationFailedException}. Idempotent (re-verifying a VERIFIED domain is a no-op).
     */
    @Transactional
    public CustomDomain verify(String tenant, UUID id) {
        CustomDomain cd = domains.findByTenantIdAndId(tenant, id)
                .orElseThrow(() -> new DomainNotFoundException("no such domain for this tenant"));
        if (cd.getStatus() == Status.VERIFIED) {
            return cd;
        }
        if (!autoVerify && !dnsVerifier.txtRecordPresent(cd.getDomain(), cd.getVerificationToken())) {
            throw new VerificationFailedException(
                    "DNS TXT record not found for " + DnsVerifier.challengeHost(cd.getDomain()));
        }
        cd.markVerified();
        return domains.save(cd);
    }

    @Transactional
    public void remove(String tenant, UUID id) {
        CustomDomain cd = domains.findByTenantIdAndId(tenant, id)
                .orElseThrow(() -> new DomainNotFoundException("no such domain for this tenant"));
        domains.delete(cd);
    }

    /** The tenant that owns a VERIFIED host, or {@code null} if none. Used by the edge to map Host→tenant. */
    @Transactional(readOnly = true)
    public String resolve(String host) {
        if (host == null || host.isBlank()) {
            return null;
        }
        return domains.findByDomainAndStatus(normalize(host), Status.VERIFIED)
                .map(CustomDomain::getTenantId)
                .orElse(null);
    }

    private static String normalize(String domain) {
        if (domain == null) {
            return "";
        }
        return domain.trim().toLowerCase();
    }

    private static String randomToken() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
