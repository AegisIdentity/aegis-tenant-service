--
-- Baseline schema for tenant-service.
--
-- GENERATED from the JPA entities by Hibernate's schema exporter, not hand-written. The service
-- runs with ddl-auto: validate, so any drift between this file and the entities fails startup —
-- generating it is what guarantees the two agree.
--
-- Regenerate after an entity change (then add a NEW V<n>__ migration; never edit an applied one):
--   mvn -o verify -Dit.test=<AnIT> -DfailIfNoSpecifiedTests=false \
--     -Dspring.jpa.properties.jakarta.persistence.schema-generation.scripts.action=create \
--     -Dspring.jpa.properties.jakarta.persistence.schema-generation.scripts.create-target=target/generated-schema.sql
--
-- Existing (pre-Flyway) databases are handled by flyway.baseline-on-migrate=true: they are marked
-- at the baseline version and this migration is skipped, since their tables already exist.
--
create table custom_domain (created_at timestamp(6) with time zone not null, verified_at timestamp(6) with time zone, id uuid not null, status varchar(16) not null check ((status in ('PENDING','VERIFIED'))), tenant_id varchar(64) not null, verification_token varchar(128) not null, domain varchar(253) not null unique, primary key (id));

create table tenant (created_at timestamp(6) with time zone not null, id uuid not null, status varchar(16) not null check ((status in ('ACTIVE','SUSPENDED'))), slug varchar(64) not null unique, name varchar(200) not null, primary_domain varchar(253) unique, primary key (id));

create index ix_custom_domain_tenant on custom_domain (tenant_id);

