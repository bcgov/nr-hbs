package ca.bc.gov.nrs.hbs.api.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Rejects tokens that carry no HBS role in {@code client_roles} (or
 * {@code resource_access.<azp>.roles}). Role names may be FAM-scoped, e.g.
 * {@code HBS_CLI_USER_FOREST_CLIENT-00012345}; see {@link HbsRoles}.
 */
public class HbsRoleValidator implements OAuth2TokenValidator<Jwt> {

  private static final OAuth2Error MISSING_ROLE = new OAuth2Error(
      "insufficient_scope", "Token does not carry an HBS role", null);

  @Override
  public OAuth2TokenValidatorResult validate(Jwt jwt) {
    return TokenRoles.hbsRolesFrom(jwt).isEmpty()
        ? OAuth2TokenValidatorResult.failure(MISSING_ROLE)
        : OAuth2TokenValidatorResult.success();
  }
}
