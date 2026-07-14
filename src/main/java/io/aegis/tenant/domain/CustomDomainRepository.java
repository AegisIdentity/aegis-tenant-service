package io.aegis.tenant.domain;

import io.aegis.tenant.domain.CustomDomain.Status;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomDomainRepository extends JpaRepository<CustomDomain, UUID> {

    List<CustomDomain> findByTenantId(String tenantId);

    Optional<CustomDomain> findByTenantIdAndId(String tenantId, UUID id);

    Optional<CustomDomain> findByDomainAndStatus(String domain, Status status);

    boolean existsByDomain(String domain);
}
