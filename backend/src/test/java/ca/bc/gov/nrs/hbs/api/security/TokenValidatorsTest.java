package ca.bc.gov.nrs.hbs.api.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenValidatorsTest {

  private static Jwt token(String azp, List<String> roles) {
    var b = Jwt.withTokenValue("t").header("alg", "none")
        .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60))
        .claim("identity_provider", "azureidir");
    if (azp != null) b.claim(TokenRoles.CLAIM_AZP, azp);
    if (roles != null) b.claim(TokenRoles.CLAIM_CLIENT_ROLES, roles);
    return b.build();
  }

  @Test
  void authorizedParty_isRequiredConfiguration() {
    assertThatThrownBy(() -> new AuthorizedPartyValidator(" "))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> new AuthorizedPartyValidator(null))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void authorizedParty_acceptsOnlyHbsClient() {
    var v = new AuthorizedPartyValidator("hbs-client");
    assertThat(v.validate(token("hbs-client", null)).hasErrors()).isFalse();
    assertThat(v.validate(token("fta-client", null)).hasErrors()).isTrue();
    assertThat(v.validate(token(null, null)).hasErrors()).isTrue();
  }

  @Test
  void roleValidator_requiresAnHbsRole() {
    var v = new HbsRoleValidator();
    assertThat(v.validate(token("c", List.of("HBS_CLI_USER_FOREST_CLIENT-00012345"))).hasErrors()).isFalse();
    assertThat(v.validate(token("c", List.of("FTA_ADMIN"))).hasErrors()).isTrue();
    assertThat(v.validate(token("c", List.of("FAM:EXPIRES:HBS_MOF_USER"))).hasErrors()).isTrue();
    assertThat(v.validate(token("c", null)).hasErrors()).isTrue();
  }

  @Test
  void authorities_areTheBaseRoles() {
    var jwt = token("c", List.of("HBS_MOF_USER", "HBS_CLI_SCALER_FOREST_CLIENT-00012345", "OTHER"));
    assertThat(TokenRoles.authoritiesFrom(jwt)).extracting(Object::toString)
        .containsExactlyInAnyOrder("ROLE_HBS_MOF_USER", "ROLE_HBS_CLI_SCALER");
  }
}
