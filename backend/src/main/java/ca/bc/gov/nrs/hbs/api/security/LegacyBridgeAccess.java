package ca.bc.gov.nrs.hbs.api.security;

import java.util.function.Supplier;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

/**
 * Confines service-account tokens (the legacy share bridge, role
 * {@link HbsRoles#LEGACY_BRIDGE}) to {@link #PATHS}: the filter chain sends
 * those paths to {@code hasRole('HBS_LEGACY_BRIDGE')} and every other request
 * through {@link #authenticatedPerson()}, which refuses a token carrying a
 * service role. A person's token never carries one (FAM grants it to no
 * user), so nothing changes for the SPA.
 */
public final class LegacyBridgeAccess {

  /** The endpoints the share bridge may call (LegacyBridgeController). */
  public static final String PATHS = "/api/v1/hbs/integrations/legacy/**";

  static final String AUTHORITY = "ROLE_" + HbsRoles.LEGACY_BRIDGE;

  private LegacyBridgeAccess() {
  }

  /** Authenticated, and not a service-account token. */
  public static AuthorizationManager<RequestAuthorizationContext> authenticatedPerson() {
    return (Supplier<Authentication> authentication, RequestAuthorizationContext context) ->
        new AuthorizationDecision(isPerson(authentication.get()));
  }

  static boolean isPerson(Authentication auth) {
    if (auth == null || !auth.isAuthenticated()
        || auth instanceof org.springframework.security.authentication.AnonymousAuthenticationToken) {
      return false;
    }
    return auth.getAuthorities().stream().noneMatch(a -> AUTHORITY.equals(a.getAuthority()));
  }
}
