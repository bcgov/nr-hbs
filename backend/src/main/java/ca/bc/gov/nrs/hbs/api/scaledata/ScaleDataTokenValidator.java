package ca.bc.gov.nrs.hbs.api.scaledata;

import ca.bc.gov.nrs.hbs.api.security.HbsRoles;
import ca.bc.gov.nrs.hbs.api.security.TokenRoles;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Accepts only submission-account tokens on the scale data API (on top of the
 * signature, issuer and expiry checks):
 *
 * <ul>
 *   <li>{@code azp} is a mapped account ({@link ApiAccountRegistry}). The
 *       shared SSO realm signs tokens for every application, so this is what
 *       makes a token "for HBS's scale data API"; it also shuts an account out
 *       the moment its mapping line is removed;</li>
 *   <li>the only HBS role on it is {@code HBS_SUBMISSION_API}: a person's token
 *       (staff or BCeID) or the share bridge's is refused here, even when it
 *       is otherwise valid;</li>
 *   <li>no IDIR / BCeID identity claims (a client-credentials token never has
 *       them).</li>
 * </ul>
 *
 * The scopes are checked per endpoint by the filter chain, not here, so a
 * token without the right scope gets 403 {@code insufficient_scope} rather
 * than 401.
 */
@Slf4j
public class ScaleDataTokenValidator implements OAuth2TokenValidator<Jwt> {

  private static final Set<String> PERSON_CLAIMS = Set.of("idir_user_guid", "idir_username", "bceid_user_guid",
      "bceid_username", "bceid_business_guid");

  private final ApiAccountRegistry accounts;

  public ScaleDataTokenValidator(ApiAccountRegistry accounts) {
    this.accounts = accounts;
  }

  @Override
  public OAuth2TokenValidatorResult validate(Jwt token) {
    String azp = token.getClaimAsString(TokenRoles.CLAIM_AZP);
    if (accounts.find(azp).isEmpty()) {
      log.warn("Scale data API: refused a token for client '{}', which is not a mapped submission account", azp);
      return failure("This client is not set up for the HBS scale data API.");
    }
    Set<String> roles = TokenRoles.hbsRolesFrom(token);
    if (!roles.equals(Set.of(HbsRoles.SUBMISSION_API))) {
      log.warn("Scale data API: refused a token for '{}' carrying roles {}", azp, roles);
      return failure("The token must carry the " + HbsRoles.SUBMISSION_API + " role and no other HBS role.");
    }
    if (PERSON_CLAIMS.stream().anyMatch(c -> token.getClaims().containsKey(c))) {
      log.warn("Scale data API: refused a person's token for client '{}'", azp);
      return failure("The scale data API accepts client-credentials (service account) tokens only.");
    }
    return OAuth2TokenValidatorResult.success();
  }

  private static OAuth2TokenValidatorResult failure(String description) {
    return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", description, null));
  }
}
