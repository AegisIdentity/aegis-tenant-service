# aegis-tenant-service

Control plane for **organizations/tenants**: CRUD, DNS-safe slugs, domain-based resolution (used by
the edge gateway). Port `9101`. Store: PostgreSQL. Reads need scope `tenant:read`, writes `tenant:admin`.

## Build
```bash
mvn verify   # integration tests vs real Postgres
```
