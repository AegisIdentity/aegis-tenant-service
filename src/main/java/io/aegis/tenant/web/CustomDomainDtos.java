package io.aegis.tenant.web;

import io.aegis.tenant.domain.CustomDomain;
import io.aegis.tenant.service.DnsVerifier;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public final class CustomDomainDtos {

    private CustomDomainDtos() {
    }

    public record AddDomainRequest(@NotBlank @Size(max = 253) String domain) {
    }

    /** CNAME record pointing the white-label host at the Aegis ingress. */
    public record CnameRecord(String host, String target) {
    }

    /** TXT challenge record proving ownership of the host. */
    public record TxtRecord(String host, String value) {
    }

    /** The DNS records the tenant must publish: the CNAME to route, and the TXT to verify. */
    public record DnsRecords(CnameRecord cname, TxtRecord txt) {
    }

    public record DomainView(UUID id, String domain, String status, String verificationToken,
                             DnsRecords dnsRecords) {

        public static DomainView from(CustomDomain cd, String ingressTarget) {
            DnsRecords records = new DnsRecords(
                    new CnameRecord(cd.getDomain(), ingressTarget),
                    new TxtRecord(DnsVerifier.challengeHost(cd.getDomain()), cd.getVerificationToken()));
            return new DomainView(cd.getId(), cd.getDomain(), cd.getStatus().name(),
                    cd.getVerificationToken(), records);
        }
    }

    /** Internal Host→tenant resolution result. */
    public record ResolveResponse(String tenant) {
    }
}
