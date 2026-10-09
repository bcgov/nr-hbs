package ca.bc.gov.nrs.hbs.api.controller;

import ca.bc.gov.nrs.hbs.api.security.TokenRoles;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end check of the API side of login: the real security filter chain,
 * Keycloak client_roles → authority mapping, @PreAuthorize gates and capability
 * filtering, with a mocked JwtDecoder (no Keycloak, no Oracle — H2 test DB).
 */
@SpringBootTest
@AutoConfigureMockMvc
class LoginSecurityTest {

  @Autowired
  MockMvc mvc;

  @MockitoBean
  JwtDecoder jwtDecoder;

  private static RequestPostProcessor token(String... roles) {
    return jwt().jwt(j -> j.claim(TokenRoles.CLAIM_CLIENT_ROLES, List.of(roles))
            .claim(TokenRoles.CLAIM_AZP, "hbs-test-client")
            .claim("identity_provider", "azureidir")
            .claim("idir_username", "JSMITH"))
        .authorities(TokenRoles::authoritiesFrom);
  }

  @Test
  void noToken_is401() throws Exception {
    mvc.perform(get("/api/v1/hbs/commands")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/hbs/reports")).andExpect(status().isUnauthorized());
  }

  @Test
  void healthIsPublic() throws Exception {
    mvc.perform(get("/actuator/health")).andExpect(status().is(org.hamcrest.Matchers.oneOf(200, 503)));
  }

  @Test
  void ministryUser_isAuthenticated_andSeesMinistryReports() throws Exception {
    mvc.perform(get("/api/v1/hbs/reports").with(token("HBS_MOF_USER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].id", hasItem("HBS2R912")));
  }

  @Test
  void industryUser_onlySeesIndustryReports() throws Exception {
    mvc.perform(get("/api/v1/hbs/reports").with(token("HBS_CLI_USER_FOREST_CLIENT-00012345")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].id", hasItem("HBS2R051")))
        .andExpect(jsonPath("$[*].id", not(hasItem("HBS2R912"))));
  }

  @Test
  void industryUser_cannotRunMinistryReport() throws Exception {
    mvc.perform(post("/api/v1/hbs/reports/HBS2R912").with(token("HBS_CLI_USER_FOREST_CLIENT-00012345"))
            .contentType("application/json").content("{\"parameters\":{}}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void commandsAreFilteredByCapability() throws Exception {
    // A base ministry user holds no write capabilities…
    mvc.perform(get("/api/v1/hbs/commands").with(token("HBS_MOF_USER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", not(hasItem("profiles.markHolders.update"))));
    // …stacking the billing administrator role grants them.
    mvc.perform(get("/api/v1/hbs/commands").with(token("HBS_MOF_USER", "HBS_BILL_ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasItem("profiles.markHolders.update")));
  }

  @Test
  void queryWithoutCapability_is403() throws Exception {
    // Production-control settings are HBS_PROD_CTL only.
    mvc.perform(post("/api/v1/hbs/commands/admin.processParameter.save")
            .with(token("HBS_MOF_USER")).contentType("application/json").content("{}"))
        .andExpect(status().isForbidden());
  }
}
