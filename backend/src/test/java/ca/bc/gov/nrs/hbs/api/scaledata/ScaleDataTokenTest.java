package ca.bc.gov.nrs.hbs.api.scaledata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.hbs.api.security.TokenRoles;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/** Token validation, authorities, statuses and cursors of the scale data API. */
class ScaleDataTokenTest {

  static Jwt token(String azp, List<String> roles, String scope) {
    var b = Jwt.withTokenValue("t").header("alg", "none")
        .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60))
        .claim(TokenRoles.CLAIM_AZP, azp)
        .claim(TokenRoles.CLAIM_CLIENT_ROLES, roles);
    if (scope != null) {
      b.claim("scope", scope);
    }
    return b.build();
  }

  ScaleDataTokenValidator validator() {
    ApiAccountRegistry registry = mock(ApiAccountRegistry.class);
    when(registry.find("hbs-scale-acme")).thenReturn(Optional.of(new ApiAccount("hbs-scale-acme", List.of("00012345"))));
    when(registry.find(org.mockito.ArgumentMatchers.argThat(id -> !"hbs-scale-acme".equals(id))))
        .thenReturn(Optional.empty());
    return new ScaleDataTokenValidator(registry);
  }

  @Test
  void acceptsOnlyMappedSubmissionAccounts() {
    var v = validator();
    assertThat(v.validate(token("hbs-scale-acme", List.of("HBS_SUBMISSION_API"), "hbs.scale-data.submit"))
        .hasErrors()).isFalse();
    // not mapped
    assertThat(v.validate(token("hbs-scale-other", List.of("HBS_SUBMISSION_API"), null)).hasErrors()).isTrue();
    // staff app's client, a person's roles
    assertThat(v.validate(token("hbs-test-client", List.of("HBS_MOF_USER"), null)).hasErrors()).isTrue();
    // the role plus anything else
    assertThat(v.validate(token("hbs-scale-acme", List.of("HBS_SUBMISSION_API", "HBS_CLI_USER"), null))
        .hasErrors()).isTrue();
    assertThat(v.validate(token("hbs-scale-acme", List.of("HBS_LEGACY_BRIDGE"), null)).hasErrors()).isTrue();
    assertThat(v.validate(token("hbs-scale-acme", List.of(), null)).hasErrors()).isTrue();
  }

  @Test
  void refusesAPersonsTokenEvenWithTheRole() {
    Jwt person = Jwt.withTokenValue("t").header("alg", "none")
        .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60))
        .claim(TokenRoles.CLAIM_AZP, "hbs-scale-acme")
        .claim(TokenRoles.CLAIM_CLIENT_ROLES, List.of("HBS_SUBMISSION_API"))
        .claim("bceid_user_guid", "abc")
        .build();
    assertThat(validator().validate(person).hasErrors()).isTrue();
  }

  @Test
  void scopesComeFromTheScopeClaimAndOptionallyFromRoles() {
    Jwt jwt = token("hbs-scale-acme", List.of("HBS_SUBMISSION_API", "HBS_SCALE_DATA_READ"),
        "profile hbs.scale-data.submit");
    assertThat(names(ScaleDataAuthorities.from(jwt, false)))
        .contains("ROLE_HBS_SUBMISSION_API", "SCOPE_hbs.scale-data.submit", "SCOPE_profile")
        .doesNotContain("SCOPE_hbs.scale-data.read");
    assertThat(names(ScaleDataAuthorities.from(jwt, true))).contains("SCOPE_hbs.scale-data.read");

    Jwt scp = Jwt.withTokenValue("t").header("alg", "none").claim("scp", List.of("hbs.statements.read"))
        .claim(TokenRoles.CLAIM_AZP, "x").build();
    assertThat(ScaleDataAuthorities.scopes(scp, false)).containsExactly("hbs.statements.read");
  }

  private static List<String> names(java.util.Collection<GrantedAuthority> authorities) {
    return authorities.stream().map(GrantedAuthority::getAuthority).toList();
  }

  @Test
  void statusesMapFromTheDatabaseCodes() {
    assertThat(ScaleDataStatus.transmission("RCD", "CSA")).isEqualTo("QUEUED");
    assertThat(ScaleDataStatus.transmission("RCD", "CUS")).isEqualTo("REJECTED");
    assertThat(ScaleDataStatus.transmission("UNP", "CSA")).isEqualTo("PROCESSING");
    assertThat(ScaleDataStatus.transmission("UNP", "CUS")).isEqualTo("REJECTED");
    assertThat(ScaleDataStatus.transmission("HBE", "PRC")).isEqualTo("PROCESSING");
    assertThat(ScaleDataStatus.transmission("HBE", "CSA")).isEqualTo("PROCESSED");
    assertThat(ScaleDataStatus.version("ACC")).isEqualTo("ACCEPTED");
    assertThat(ScaleDataStatus.version("RDY")).isEqualTo("READY");
    assertThat(ScaleDataStatus.version("DSF")).isEqualTo("SIGNATURE_FAILED");
    assertThat(ScaleDataStatus.version("LCK")).isEqualTo("SUMMARIZED");
    assertThat(ScaleDataStatus.version("ZZZ")).isEqualTo("OTHER");
  }

  @Test
  void cursorsRoundTripAndRejectTampering() {
    LocalDateTime at = LocalDateTime.of(2026, 10, 29, 6, 0, 19);
    assertThat(Cursors.decode(Cursors.encode(at, 812345))).isEqualTo(new Cursors.Position(at, 812345));
    assertThat(Cursors.decodeId(Cursors.encodeId(42))).isEqualTo(42);
    assertThatThrownBy(() -> Cursors.decode("not-a-cursor")).isInstanceOf(ScaleDataApiException.class);
    assertThatThrownBy(() -> Cursors.decodeId(Cursors.encode(at, 1))).isInstanceOf(ScaleDataApiException.class);
  }

  @Test
  void theVersionABatchDocumentCreatedIsTheOneItSent() {
    List<Object[]> versions = List.of(new Object[] {"D1", 3, "RDY", null}, new Object[] {"D1", 2, "SPR", null});
    assertThat(ScaleDataReads.pickVersion(versions, 2)[1]).isEqualTo(2);
    assertThat(ScaleDataReads.pickVersion(versions, null)[1]).isEqualTo(2);
    assertThat(ScaleDataReads.pickVersion(List.of(), 1)).isNull();
    assertThat(ScaleDataReads.parseVersion(" 7 ")).isEqualTo(7);
    assertThat(ScaleDataReads.parseVersion("x")).isNull();
  }

  @Test
  void requiredScopeFollowsTheEndpoint() {
    assertThat(scopeFor("POST", "/submissions")).isEqualTo("hbs.scale-data.submit");
    assertThat(scopeFor("POST", "/submissions:validate")).isEqualTo("hbs.scale-data.submit");
    assertThat(scopeFor("GET", "/submissions/812345/documents")).isEqualTo("hbs.scale-data.read");
    assertThat(scopeFor("GET", "/statements/90211.pdf")).isEqualTo("hbs.statements.read");
    assertThat(scopeFor("DELETE", "/submissions/1")).isNull();
  }

  private static String scopeFor(String method, String rest) {
    var request = new org.springframework.mock.web.MockHttpServletRequest(method, ScaleDataApi.BASE + rest);
    return ScaleDataErrors.requiredScope(request);
  }
}
