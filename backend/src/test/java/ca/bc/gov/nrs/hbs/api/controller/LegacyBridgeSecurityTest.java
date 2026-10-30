package ca.bc.gov.nrs.hbs.api.controller;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ca.bc.gov.nrs.hbs.api.processparams.AppSettings;
import ca.bc.gov.nrs.hbs.api.security.TokenRoles;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * The share-bridge service account (HBS_LEGACY_BRIDGE) reaches only
 * /integrations/legacy/**, and only it does — through the real filter chain.
 */
@SpringBootTest
@AutoConfigureMockMvc
class LegacyBridgeSecurityTest {

  static final String URL = "/api/v1/hbs/integrations/legacy/process-parameters";

  @Autowired
  MockMvc mvc;

  @MockitoBean
  JwtDecoder jwtDecoder;

  @MockitoBean
  AppSettings appSettings;

  private static RequestPostProcessor token(String azp, String... roles) {
    return jwt().jwt(j -> j.claim(TokenRoles.CLAIM_CLIENT_ROLES, List.of(roles))
            .claim(TokenRoles.CLAIM_AZP, azp))
        .authorities(TokenRoles::authoritiesFrom);
  }

  @Test
  void bridgeServiceAccount_getsTheLegacyFiles() throws Exception {
    when(appSettings.all()).thenReturn(Map.of("B2031.START_LOC", "5", "B2031.TIME_LIMIT", "9999999999",
        "B2041.START_LOC", "0", "B2041.TIME_LIMIT", "9999999999"));
    mvc.perform(get(URL).with(token("hbs-legacy-bridge", "HBS_LEGACY_BRIDGE")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.files[0].name").value("HBSProcessingParameter.csv"))
        .andExpect(jsonPath("$.files[0].content").value("B2031,5,9999999999\nB2041,0,9999999999\n"))
        .andExpect(jsonPath("$.files[0].encoding").value("windows-1252"))
        .andExpect(jsonPath("$.generatedAt").exists());
  }

  @Test
  void bridgeServiceAccount_cannotUseAnythingElse() throws Exception {
    mvc.perform(get("/api/v1/hbs/commands").with(token("hbs-legacy-bridge", "HBS_LEGACY_BRIDGE")))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/hbs/queries/admin.alertText/list").with(token("hbs-legacy-bridge", "HBS_LEGACY_BRIDGE")))
        .andExpect(status().isForbidden());
  }

  @Test
  void people_cannotCallTheBridgeEndpoint() throws Exception {
    mvc.perform(get(URL).with(token("hbs-test-client", "HBS_MOF_USER", "HBS_PROD_CTL")))
        .andExpect(status().isForbidden());
    mvc.perform(get(URL)).andExpect(status().isUnauthorized());
  }
}
