# aegis-tenant-service — working notes

**Maturity: core.** Tenant/org control plane. Package `io.aegis.tenant`. Port 9101. Postgres.
Resource server.

## Where things are
- `domain/Tenant` — `slug` (per-tenant OIDC issuer path) + `primaryDomain` (host resolution), both
  globally unique. `service/TenantService.resolveByDomain` backs the gateway`\s tenant resolution.
- `config/SecurityConfig` — `tenant:read` for reads, `tenant:admin` for writes; default-deny.

## Non-negotiables
- Slugs are validated DNS-safe labels; they end up in issuer URLs and Redis keys.
- Multiple domains per tenant is a documented enhancement — keep the resolution API stable for the
  gateway when adding it.

## Build / test
`mvn verify` (Docker required).
