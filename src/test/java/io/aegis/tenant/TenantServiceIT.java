package io.aegis.tenant;

import static io.aegis.commons.testing.AegisJwtTest.jwtForTenant;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

// ddl-auto now defaults to 'validate' (L-edge-2); the container starts with an empty schema, so the
// test build owns schema creation.
@SpringBootTest
@Import(TenantTestConfig.class)
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class TenantServiceIT {

    @Autowired
    WebApplicationContext context;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void create_requires_platform_admin_scope_then_resolvable_by_domain() throws Exception {
        String body = """
                {"name":"Acme Inc","slug":"acme","primaryDomain":"acme.aegis.io"}""";

        // per-tenant admin scope cannot create top-level tenants (H4).
        mockMvc.perform(post("/api/v1/tenants").contentType(MediaType.APPLICATION_JSON).content(body)
                        .with(jwtForTenant("ctl", "svc", "tenant:admin")))
                .andExpect(status().isForbidden());

        // platform-operator scope creates.
        mockMvc.perform(post("/api/v1/tenants").contentType(MediaType.APPLICATION_JSON).content(body)
                        .with(jwtForTenant("ctl", "svc", "tenant:platform-admin")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("acme"));

        // cross-tenant resolve is a platform-operator action (H3): a caller in another tenant is 404'd.
        mockMvc.perform(get("/api/v1/tenants:resolve").param("domain", "acme.aegis.io")
                        .with(jwtForTenant("other", "gateway", "tenant:read")))
                .andExpect(status().isNotFound());

        // ...but a platform operator may resolve any tenant's domain.
        mockMvc.perform(get("/api/v1/tenants:resolve").param("domain", "acme.aegis.io")
                        .with(jwtForTenant("ctl", "gateway", "tenant:platform-admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("acme"));

        // ...and the owning tenant may resolve its own domain with plain read scope.
        mockMvc.perform(get("/api/v1/tenants:resolve").param("domain", "acme.aegis.io")
                        .with(jwtForTenant("acme", "self", "tenant:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("acme"));
    }

    @Test
    void cross_tenant_slug_read_is_hidden() throws Exception {
        // Create acme as a platform operator...
        mockMvc.perform(post("/api/v1/tenants").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Acme Inc","slug":"acmeco","primaryDomain":"acmeco.aegis.io"}""")
                        .with(jwtForTenant("ctl", "svc", "tenant:platform-admin")))
                .andExpect(status().isCreated());

        // ...another tenant admin cannot read it by slug (H3 -> 404, not 200).
        mockMvc.perform(get("/api/v1/tenants/acmeco")
                        .with(jwtForTenant("intruder", "admin", "tenant:read")))
                .andExpect(status().isNotFound());

        // The owner can read its own.
        mockMvc.perform(get("/api/v1/tenants/acmeco")
                        .with(jwtForTenant("acmeco", "admin", "tenant:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("acmeco"));
    }

    @Test
    void unauthenticated_is_rejected() throws Exception {
        mockMvc.perform(get("/api/v1/tenants/acme")).andExpect(status().isUnauthorized());
    }

    @Test
    void duplicate_slug_conflicts() throws Exception {
        String body = """
                {"name":"Dup","slug":"dupe","primaryDomain":"dupe.aegis.io"}""";
        mockMvc.perform(post("/api/v1/tenants").contentType(MediaType.APPLICATION_JSON).content(body)
                        .with(jwtForTenant("ctl", "svc", "tenant:platform-admin")))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/tenants").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Dup2","slug":"dupe","primaryDomain":"other.aegis.io"}""")
                        .with(jwtForTenant("ctl", "svc", "tenant:platform-admin")))
                .andExpect(status().isConflict());
    }
}
