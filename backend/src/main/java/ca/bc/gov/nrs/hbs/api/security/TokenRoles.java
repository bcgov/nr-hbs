package ca.bc.gov.nrs.hbs.api.security;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * The caller's roles, read off a BC Gov SSO (Keycloak) access token, and the
 * authorities they grant. Ported from nr-fta's {@code TokenRoles}.
 *
 * <p>FAM puts a grant's scope in the role name and nowhere else: a BCeID user
 * granted Industry Scaler for client 00012345 carries
 * {@code HBS_CLI_SCALER_FOREST_CLIENT-00012345} — never the bare
 * {@code HBS_CLI_SCALER}. So each recognised role contributes its <em>base</em>
 * role as the authority ({@code ROLE_HBS_CLI_SCALER}, matched by the
 * {@code hasRole('HBS_CLI_SCALER')} expressions in {@link HbsAuthorities}). Which
 * client the grant is for is read separately via {@link #rolesFrom} +
 * {@link RoleScope} (see {@code RequestUtil.getCurrentRoles()}).
 *
 * <p>HBS roles stack (legacy WebADE behaviour): every recognised role becomes an
 * authority.
 */
public final class TokenRoles {

  /** Roles CSS/FAM attaches to a token for the client it was issued to. */
  public static final String CLAIM_CLIENT_ROLES = "client_roles";

  /** Where stock Keycloak puts the same information. */
  static final String CLAIM_RESOURCE_ACCESS = "resource_access";

  /** The client the token was issued to — checked against hbs.keycloak.client-id. */
  public static final String CLAIM_AZP = "azp";

  private static final String AUTHORITY_PREFIX = "ROLE_";

  private TokenRoles() {}

  /**
   * The role names on the token, FAM bookkeeping roles
   * ({@code FAM:EXPIRES:…}) excluded. {@code client_roles} under CSS, falling
   * back to {@code resource_access.<azp>.roles}.
   */
  public static List<String> rolesFrom(Jwt jwt) {
    List<String> roles = jwt.getClaimAsStringList(CLAIM_CLIENT_ROLES);
    if (roles == null || roles.isEmpty()) {
      roles = rolesFromResourceAccess(jwt);
    }
    return roles.stream().filter(role -> !RoleScope.isSidecar(role)).toList();
  }

  /** The distinct canonical HBS roles the token holds (any scope). */
  public static Set<String> hbsRolesFrom(Jwt jwt) {
    Set<String> out = new LinkedHashSet<>();
    for (String role : rolesFrom(jwt)) {
      String canonical = HbsRoles.canonicalRoleFor(role);
      if (canonical != null) {
        out.add(canonical);
      }
    }
    return out;
  }

  /** {@code ROLE_<HBS base role>} for every recognised role on the token. */
  public static Collection<GrantedAuthority> authoritiesFrom(Jwt jwt) {
    return hbsRolesFrom(jwt).stream()
        .map(role -> (GrantedAuthority) new SimpleGrantedAuthority(AUTHORITY_PREFIX + role))
        .toList();
  }

  private static List<String> rolesFromResourceAccess(Jwt jwt) {
    Object resourceAccess = jwt.getClaim(CLAIM_RESOURCE_ACCESS);
    String clientId = jwt.getClaimAsString(CLAIM_AZP);
    if (!(resourceAccess instanceof Map<?, ?> byClient) || clientId == null) {
      return List.of();
    }
    if (byClient.get(clientId) instanceof Map<?, ?> entry
        && entry.get("roles") instanceof List<?> roles) {
      return roles.stream().filter(String.class::isInstance).map(String.class::cast).toList();
    }
    return List.of();
  }
}
