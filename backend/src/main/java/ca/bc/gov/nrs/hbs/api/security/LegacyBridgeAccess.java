package ca.bc.gov.nrs.hbs.api.security;

import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

/**
 * Confines service-account tokens to their own endpoints. The legacy share
 * bridge ({@link HbsRoles#LEGACY_BRIDGE}) may call only {@link #PATHS}; the
 * scale-data submission accounts ({@link HbsRoles#SUBMISSION_API}) have their
 * own filter chain ({@code scaledata.ScaleDataApiSecurityConfig}). Every other
 * request goes through {@link #authenticatedPerson()}, which refuses a token
 * carrying <em>any</em> service role ({@link HbsRoles#SERVICE}). A person's
 * token never carries one (FAM grants them to no user), so nothing changes for
 * the SPA.
 */
public final class LegacyBridgeAccess {

  /** The endpoints the share bridge may call (LegacyBridgeController). */
  public static final String PATHS = "/api/v1/hbs/integrations/legacy/**";

  static final java.util.Set<String> SERVICE_AUTHORITIES = HbsRoles.SERVICE.stream()
      .map(role -> "ROLE_" + role)
      .collect(java.util.stream.Collectors.toUnmodifiableSet());

  private LegacyBridgeAccess() {
  }

  /** Authenticated, and not a service-account token (no role in {@link HbsRoles#SERVICE}). */
  public static AuthorizationManager<RequestAuthorizationContext> authenticatedPerson() {
    return (authentication, context) -> new AuthorizationDecision(isPerson(authentication.get()));
  }

  static boolean isPerson(Authentication auth) {
    if (auth == null || !auth.isAuthenticated()
        || auth instanceof org.springframework.security.authentication.AnonymousAuthenticationToken) {
      return false;
    }
    return auth.getAuthorities().stream().noneMatch(a -> SERVICE_AUTHORITIES.contains(a.getAuthority()));
  }
}
