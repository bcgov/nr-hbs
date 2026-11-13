package ca.bc.gov.nrs.hbs.api.scaledata;

import ca.bc.gov.nrs.hbs.api.security.TokenRoles;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Authorities of a submission-account token: {@code ROLE_<HBS role>} (as for
 * every HBS token, {@link TokenRoles}) plus {@code SCOPE_<scope>} for each OAuth
 * scope the token was granted.
 *
 * <p>Scopes come from the standard {@code scope} claim (space-separated; where
 * Keycloak puts a client's assigned client scopes) or {@code scp} (a list).
 * When {@code hbs.scale-data-api.accept-role-scopes=true}, the same scopes may
 * instead be granted as client roles ({@link #ROLE_SCOPES}), for an issuer whose
 * integrations can only be given roles.
 */
public final class ScaleDataAuthorities {

  static final String SCOPE_CLAIM = "scope";
  static final String SCP_CLAIM = "scp";

  /** Client roles that stand for a scope when role scopes are accepted. */
  static final Map<String, String> ROLE_SCOPES = Map.of(
      "HBS_SCALE_DATA_SUBMIT", ScaleDataApi.SCOPE_SUBMIT,
      "HBS_SCALE_DATA_READ", ScaleDataApi.SCOPE_READ,
      "HBS_STATEMENTS_READ", ScaleDataApi.SCOPE_STATEMENTS);

  private ScaleDataAuthorities() {
  }

  public static Collection<GrantedAuthority> from(Jwt jwt, boolean acceptRoleScopes) {
    Set<GrantedAuthority> out = new LinkedHashSet<>(TokenRoles.authoritiesFrom(jwt));
    for (String scope : scopes(jwt, acceptRoleScopes)) {
      out.add(new SimpleGrantedAuthority("SCOPE_" + scope));
    }
    return List.copyOf(out);
  }

  /** The scopes granted to the token. */
  public static Set<String> scopes(Jwt jwt, boolean acceptRoleScopes) {
    Set<String> scopes = new LinkedHashSet<>();
    Object scope = jwt.getClaim(SCOPE_CLAIM);
    if (scope instanceof String s) {
      for (String part : s.trim().split("\\s+")) {
        if (!part.isEmpty()) {
          scopes.add(part);
        }
      }
    } else if (scope instanceof Collection<?> c) {
      c.stream().filter(String.class::isInstance).map(String.class::cast).forEach(scopes::add);
    }
    Object scp = jwt.getClaim(SCP_CLAIM);
    if (scp instanceof Collection<?> c) {
      c.stream().filter(String.class::isInstance).map(String.class::cast).forEach(scopes::add);
    } else if (scp instanceof String s && !s.isBlank()) {
      for (String part : s.trim().split("\\s+")) {
        scopes.add(part);
      }
    }
    if (acceptRoleScopes) {
      for (String role : TokenRoles.rolesFrom(jwt)) {
        String mapped = ROLE_SCOPES.get(role);
        if (mapped != null) {
          scopes.add(mapped);
        }
      }
    }
    return scopes;
  }
}
