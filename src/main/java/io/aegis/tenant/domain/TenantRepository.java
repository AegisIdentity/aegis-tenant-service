package io.aegis.tenant.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    Optional<Tenant> findBySlug(String slug);

    Optional<Tenant> findByPrimaryDomain(String primaryDomain);

    boolean existsBySlug(String slug);

    boolean existsByPrimaryDomain(String primaryDomain);
}
