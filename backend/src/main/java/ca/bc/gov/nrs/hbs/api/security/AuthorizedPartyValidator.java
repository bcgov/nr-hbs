package ca.bc.gov.nrs.hbs.api.security;

import java.util.Collection;
import java.util.List;
import java.util.Set;
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
 *
 * <p>Service accounts: a client-credentials token is issued to the service
 * account's own CSS client, so its {@code azp} is not HBS's. The configured
 * service-account client ids ({@code hbs.keycloak.legacy-bridge-client-id})
 * are accepted <em>only</em> when every HBS role on the token is one of the
 * roles those accounts may carry (by default any {@link HbsRoles#SERVICE}
 * role; the main chain allows only {@link HbsRoles#LEGACY_BRIDGE}); the filter
 * chain then confines such tokens to their endpoints ({@link LegacyBridgeAccess}).
 * Scale-data submission accounts are not accepted here at all: their tokens
 * are only decoded by the scale-data chain.
 */
public class AuthorizedPartyValidator implements OAuth2TokenValidator<Jwt> {

  private static final Logger LOG = LoggerFactory.getLogger(AuthorizedPartyValidator.class);

  private final String expectedClientId;
  private final Set<String> serviceAccountClientIds;
  private final Set<String> serviceAccountRoles;

  public AuthorizedPartyValidator(String expectedClientId) {
    this(expectedClientId, List.of());
  }

  public AuthorizedPartyValidator(String expectedClientId, Collection<String> serviceAccountClientIds) {
    this(expectedClientId, serviceAccountClientIds, HbsRoles.SERVICE);
  }

  /**
   * @param serviceAccountRoles the only roles a token from one of the
   *     {@code serviceAccountClientIds} may carry (a subset of {@link HbsRoles#SERVICE})
   */
  public AuthorizedPartyValidator(String expectedClientId, Collection<String> serviceAccountClientIds,
      Set<String> serviceAccountRoles) {
    if (expectedClientId == null || expectedClientId.isBlank()) {
      throw new IllegalStateException(
          "hbs.keycloak.client-id (KEYCLOAK_CLIENT_ID) must be set — a blank value "
              + "would accept every token the realm signs, for any client");
    }
    this.expectedClientId = expectedClientId;
    this.serviceAccountClientIds = serviceAccountClientIds.stream()
        .filter(id -> id != null && !id.isBlank())
        .map(String::trim)
        .collect(java.util.stream.Collectors.toUnmodifiableSet());
    if (!HbsRoles.SERVICE.containsAll(serviceAccountRoles)) {
      throw new IllegalArgumentException("Service accounts may only carry service roles: " + serviceAccountRoles);
    }
    this.serviceAccountRoles = Set.copyOf(serviceAccountRoles);
  }

  @Override
  public OAuth2TokenValidatorResult validate(Jwt token) {
    String azp = token.getClaimAsString(TokenRoles.CLAIM_AZP);
    if (expectedClientId.equals(azp)) {
      return OAuth2TokenValidatorResult.success();
    }
    if (azp != null && serviceAccountClientIds.contains(azp)) {
      Set<String> roles = TokenRoles.hbsRolesFrom(token);
      if (!roles.isEmpty() && serviceAccountRoles.containsAll(roles)) {
        return OAuth2TokenValidatorResult.success();
      }
      LOG.warn("Rejected a service-account token for client '{}' carrying non-service roles {}.", azp, roles);
      return OAuth2TokenValidatorResult.failure(
          new OAuth2Error("invalid_token", "This service account may only carry service roles.", null));
    }
    LOG.warn("Rejected a token issued to client '{}'; this API accepts only '{}'.", azp, expectedClientId);
    return OAuth2TokenValidatorResult.failure(
        new OAuth2Error("invalid_token", "This token was not issued to this application.", null));
  }
}
