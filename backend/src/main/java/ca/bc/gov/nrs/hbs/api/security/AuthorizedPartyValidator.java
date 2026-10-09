package ca.bc.gov.nrs.hbs.api.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Refuses a token minted by the realm for somebody else's client (nr-fta's
 * {@code validateAuthorizedParty}). The BC Gov SSO standard realm is shared by
 * many applications whose tokens are all signed by the same issuer and JWKS, so
 * signature + issuer alone only prove the realm minted the token, not that it
 * was meant for HBS. The expected client id is required configuration.
 */
public class AuthorizedPartyValidator implements OAuth2TokenValidator<Jwt> {

  private static final Logger LOG = LoggerFactory.getLogger(AuthorizedPartyValidator.class);

  private final String expectedClientId;

  public AuthorizedPartyValidator(String expectedClientId) {
    if (expectedClientId == null || expectedClientId.isBlank()) {
      throw new IllegalStateException(
          "hbs.keycloak.client-id (KEYCLOAK_CLIENT_ID) must be set — a blank value "
              + "would accept every token the realm signs, for any client");
    }
    this.expectedClientId = expectedClientId;
  }

  @Override
  public OAuth2TokenValidatorResult validate(Jwt token) {
    String azp = token.getClaimAsString(TokenRoles.CLAIM_AZP);
    if (expectedClientId.equals(azp)) {
      return OAuth2TokenValidatorResult.success();
    }
    LOG.warn("Rejected a token issued to client '{}'; this API accepts only '{}'.", azp, expectedClientId);
    return OAuth2TokenValidatorResult.failure(
        new OAuth2Error("invalid_token", "This token was not issued to this application.", null));
  }
}
