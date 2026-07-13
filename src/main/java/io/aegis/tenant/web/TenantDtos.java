package io.aegis.tenant.web;

import io.aegis.tenant.domain.Tenant;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public final class TenantDtos {

    private TenantDtos() {
    }

    public record CreateTenantRequest(
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Size(max = 64) String slug,
            @Size(max = 253) String primaryDomain) {
    }

    public record TenantResponse(UUID id, String name, String slug, String primaryDomain, String status) {
        public static TenantResponse from(Tenant t) {
            return new TenantResponse(t.getId(), t.getName(), t.getSlug(), t.getPrimaryDomain(),
                    t.getStatus().name());
        }
    }
}
