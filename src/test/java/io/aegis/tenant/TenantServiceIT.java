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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@Import(TenantTestConfig.class)
class TenantServiceIT {

    @Autowired
    WebApplicationContext context;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void create_requires_admin_scope_then_resolvable_by_domain() throws Exception {
        String body = """
                {"name":"Acme Inc","slug":"acme","primaryDomain":"acme.aegis.io"}""";

        // read scope cannot create.
        mockMvc.perform(post("/api/v1/tenants").contentType(MediaType.APPLICATION_JSON).content(body)
                        .with(jwtForTenant("ctl", "svc", "tenant:read")))
                .andExpect(status().isForbidden());

        // admin scope creates.
        mockMvc.perform(post("/api/v1/tenants").contentType(MediaType.APPLICATION_JSON).content(body)
                        .with(jwtForTenant("ctl", "svc", "tenant:admin")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("acme"));

        // resolvable by domain with read scope.
        mockMvc.perform(get("/api/v1/tenants:resolve").param("domain", "acme.aegis.io")
                        .with(jwtForTenant("ctl", "gateway", "tenant:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("acme"));
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
                        .with(jwtForTenant("ctl", "svc", "tenant:admin")))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/tenants").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Dup2","slug":"dupe","primaryDomain":"other.aegis.io"}""")
                        .with(jwtForTenant("ctl", "svc", "tenant:admin")))
                .andExpect(status().isConflict());
    }
}
