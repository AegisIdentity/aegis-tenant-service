package io.aegis.tenant;

import static io.aegis.commons.testing.AegisJwtTest.jwtForTenant;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

/**
 * Custom-domain (white-label sign-in host) API. {@code aegis.domains.auto-verify=true} lets verify()
 * skip the real DNS TXT lookup so the flow is exercisable without live DNS.
 */
@SpringBootTest
@Import(TenantTestConfig.class)
// ddl-auto defaults to 'validate' (L-edge-2); create the schema for the empty test container here.
@TestPropertySource(properties = {
        "aegis.domains.auto-verify=true",
        "spring.jpa.hibernate.ddl-auto=create-drop"})
class CustomDomainIT {

    @Autowired
    WebApplicationContext context;

    final ObjectMapper mapper = new ObjectMapper();

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void unauthenticated_is_rejected() throws Exception {
        mockMvc.perform(get("/api/v1/domains")).andExpect(status().isUnauthorized());
    }

    @Test
    void wrong_scope_is_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/domains").with(jwtForTenant("acme", "admin", "tenant:read")))
                .andExpect(status().isForbidden());
    }

    @Test
    void add_returns_pending_with_dns_records_and_token() throws Exception {
        mockMvc.perform(post("/api/v1/domains").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"domain":"login.acme.com"}""")
                        .with(jwtForTenant("acme", "admin", "tenant:admin")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.domain").value("login.acme.com"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.verificationToken").value(org.hamcrest.Matchers.startsWith("aegis-verify=")))
                .andExpect(jsonPath("$.dnsRecords.cname.host").value("login.acme.com"))
                .andExpect(jsonPath("$.dnsRecords.cname.target").value("ingress.aegis.example"))
                .andExpect(jsonPath("$.dnsRecords.txt.host").value("_aegis-challenge.login.acme.com"))
                .andExpect(jsonPath("$.dnsRecords.txt.value").value(org.hamcrest.Matchers.startsWith("aegis-verify=")));
    }

    @Test
    void list_reflects_added_domain() throws Exception {
        mockMvc.perform(post("/api/v1/domains").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"domain":"login.listco.com"}""")
                        .with(jwtForTenant("listco", "admin", "tenant:admin")))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/domains").with(jwtForTenant("listco", "admin", "tenant:admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].domain").value("login.listco.com"))
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    @Test
    void verify_with_auto_verify_flips_to_verified_and_resolves() throws Exception {
        String created = mockMvc.perform(post("/api/v1/domains").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"domain":"login.verifyco.com"}""")
                        .with(jwtForTenant("verifyco", "admin", "tenant:admin")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = mapper.readTree(created).get("id").asText();

        // verify -> VERIFIED (auto-verify skips the DNS check).
        mockMvc.perform(post("/api/v1/domains/" + id + "/verify")
                        .with(jwtForTenant("verifyco", "admin", "tenant:admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VERIFIED"));

        // internal resolve returns the owning tenant for a verified host.
        mockMvc.perform(get("/api/v1/internal/domains/resolve").param("host", "login.verifyco.com")
                        .with(jwtForTenant("ctl", "authorization-server", "tenant:resolve")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenant").value("verifyco"));
    }

    @Test
    void internal_resolve_unknown_host_is_404() throws Exception {
        mockMvc.perform(get("/api/v1/internal/domains/resolve").param("host", "nobody.example.com")
                        .with(jwtForTenant("ctl", "authorization-server", "tenant:resolve")))
                .andExpect(status().isNotFound());
    }

    @Test
    void internal_resolve_ignores_pending_domain() throws Exception {
        // Added but never verified -> still PENDING -> not resolvable.
        mockMvc.perform(post("/api/v1/domains").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"domain":"login.pendingco.com"}""")
                        .with(jwtForTenant("pendingco", "admin", "tenant:admin")))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/internal/domains/resolve").param("host", "login.pendingco.com")
                        .with(jwtForTenant("ctl", "authorization-server", "tenant:resolve")))
                .andExpect(status().isNotFound());
    }

    @Test
    void internal_resolve_requires_resolve_scope() throws Exception {
        mockMvc.perform(get("/api/v1/internal/domains/resolve").param("host", "login.acme.com")
                        .with(jwtForTenant("acme", "admin", "tenant:admin")))
                .andExpect(status().isForbidden());
    }

    @Test
    void duplicate_domain_conflicts() throws Exception {
        mockMvc.perform(post("/api/v1/domains").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"domain":"login.dupeco.com"}""")
                        .with(jwtForTenant("dupeco", "admin", "tenant:admin")))
                .andExpect(status().isCreated());

        // Same host, any tenant -> global uniqueness -> 409.
        mockMvc.perform(post("/api/v1/domains").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"domain":"login.dupeco.com"}""")
                        .with(jwtForTenant("otherco", "admin", "tenant:admin")))
                .andExpect(status().isConflict());
    }

    @Test
    void invalid_domain_is_400() throws Exception {
        mockMvc.perform(post("/api/v1/domains").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"domain":"not a hostname"}""")
                        .with(jwtForTenant("acme", "admin", "tenant:admin")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void tenant_isolation_hides_other_tenants_domains() throws Exception {
        String created = mockMvc.perform(post("/api/v1/domains").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"domain":"login.isoco.com"}""")
                        .with(jwtForTenant("isoco", "admin", "tenant:admin")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = mapper.readTree(created);
        String id = node.get("id").asText();

        // A different tenant does not see isoco's domain in its list.
        mockMvc.perform(get("/api/v1/domains").with(jwtForTenant("intruder", "admin", "tenant:admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // ...and cannot verify or delete it (scoped by tenant -> 404).
        mockMvc.perform(post("/api/v1/domains/" + id + "/verify")
                        .with(jwtForTenant("intruder", "admin", "tenant:admin")))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/domains/" + id)
                        .with(jwtForTenant("intruder", "admin", "tenant:admin")))
                .andExpect(status().isNotFound());

        // The owner can delete it.
        mockMvc.perform(delete("/api/v1/domains/" + id)
                        .with(jwtForTenant("isoco", "admin", "tenant:admin")))
                .andExpect(status().isNoContent());
    }
}
