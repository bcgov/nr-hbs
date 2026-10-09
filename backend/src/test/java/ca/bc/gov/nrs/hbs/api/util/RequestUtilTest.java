package ca.bc.gov.nrs.hbs.api.util;

import ca.bc.gov.nrs.hbs.api.security.HbsRoles;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The Keycloak (standard realm) claims → HBS identity, roles and client scope. */
class RequestUtilTest {

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
    RequestContextHolder.resetRequestAttributes();
  }

  private static void signIn(Map<String, Object> claims) {
    var b = Jwt.withTokenValue("t").header("alg", "none")
        .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60));
    claims.forEach(b::claim);
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(b.build()));
  }

  @Test
  void idirUser_auditIdAndMinistryRoles() {
    signIn(Map.of(
        "identity_provider", "azureidir",
        "idir_username", "jsmith",
        "client_roles", List.of("HBS_MOF_USER", "HBS_BILL_ADMIN", "FAM:EXPIRES:HBS_BILL_ADMIN")));
    assertThat(RequestUtil.getCurrentAuditUserId()).isEqualTo("IDIR\\JSMITH");
    assertThat(RequestUtil.getCurrentRoles()).containsExactly(HbsRoles.MOF_USER, HbsRoles.BILL_ADMIN);
    assertThat(RequestUtil.isMinistryUser()).isTrue();
    assertThat(RequestUtil.getLegacyUserType()).isEqualTo("MOF");
    assertThat(RequestUtil.getCurrentClientNumber()).isEmpty();
  }

  @Test
  void providerFallsBackToPreferredUsername() {
    signIn(Map.of(
        "preferred_username", "abc123@bceidbusiness",
        "bceid_username", "acme_clerk",
        "client_roles", List.of("HBS_CLI_USER_FOREST_CLIENT-00012345")));
    assertThat(RequestUtil.getCurrentAuditUserId()).isEqualTo("BCEID\\ACME_CLERK");
  }

  @Test
  void bceidUser_rolesFollowTheActiveClient() {
    signIn(Map.of(
        "identity_provider", "bceidbusiness",
        "bceid_username", "acme_clerk",
        "client_roles", List.of(
            "HBS_CLI_USER_FOREST_CLIENT-00012345",
            "HBS_CLI_USER_FOREST_CLIENT-00067890",
            "HBS_CLI_SCALER_FOREST_CLIENT-00067890")));

    // No header: first client.
    assertThat(RequestUtil.getCurrentClientNumber()).isEqualTo("00012345");
    assertThat(RequestUtil.getCurrentRoles()).containsExactly(HbsRoles.CLI_USER);
    assertThat(RequestUtil.getLegacyUserType()).isEqualTo("CLI");

    // Header naming one of the user's own clients switches scope.
    var req = new MockHttpServletRequest();
    req.addHeader("X-HBS-Active-Org-Client-Number", "00067890");
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));
    assertThat(RequestUtil.getCurrentClientNumber()).isEqualTo("00067890");
    assertThat(RequestUtil.getCurrentRoles()).containsExactlyInAnyOrder(HbsRoles.CLI_USER, HbsRoles.CLI_SCALER);

    // A forged header is ignored.
    req = new MockHttpServletRequest();
    req.addHeader("X-HBS-Active-Org-Client-Number", "99999999");
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));
    assertThat(RequestUtil.getCurrentClientNumber()).isEqualTo("00012345");
  }

  @Test
  void auditIdIsTruncatedToLegacyColumn() {
    signIn(Map.of("identity_provider", "azureidir", "idir_username", "a".repeat(40),
        "client_roles", List.of("HBS_MOF_USER")));
    assertThat(RequestUtil.getCurrentAuditUserId()).hasSize(30).startsWith("IDIR\\A");
  }
}
