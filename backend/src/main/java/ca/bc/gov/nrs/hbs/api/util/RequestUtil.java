package ca.bc.gov.nrs.hbs.api.util;

import ca.bc.gov.nrs.hbs.api.security.HbsRoles;
import ca.bc.gov.nrs.hbs.api.security.RoleScope;
import ca.bc.gov.nrs.hbs.api.security.TokenRoles;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * The caller's identity, roles and client scope, read off the BC Gov SSO
 * (Keycloak, standard realm) access token — the nr-fta / nr-rept claim set,
 * plus the Business BCeID claims HBS needs:
 *
 * <table>
 *   <tr><th>Claim</th><th>Meaning</th></tr>
 *   <tr><td>{@code identity_provider}</td><td>{@code azureidir} (IDIR - MFA) or {@code bceidbusiness}</td></tr>
 *   <tr><td>{@code preferred_username}</td><td>{@code <guid>@<provider>} — fallback for the provider</td></tr>
 *   <tr><td>{@code idir_username} / {@code idir_user_guid}</td><td>IDIR identity</td></tr>
 *   <tr><td>{@code bceid_username} / {@code bceid_user_guid}</td><td>Business BCeID identity</td></tr>
 *   <tr><td>{@code client_roles}</td><td>FAM roles, scope in the name ({@code _FOREST_CLIENT-<n>})</td></tr>
 * </table>
 */
public final class RequestUtil {

  private RequestUtil() {
  }

  // Legacy audit columns (ENTRY_USERID / UPDATE_USERID) are VARCHAR2(30).
  private static final int LEGACY_AUDIT_USERID_MAX = 30;

  private static final String CLAIM_IDENTITY_PROVIDER = "identity_provider";
  private static final String CLAIM_PREFERRED_USERNAME = "preferred_username";
  private static final String CLAIM_IDIR_USERNAME = "idir_username";
  private static final String CLAIM_IDIR_USER_GUID = "idir_user_guid";
  private static final String CLAIM_BCEID_USERNAME = "bceid_username";
  private static final String CLAIM_BCEID_USER_GUID = "bceid_user_guid";

  /** Normalised identity provider. */
  public enum Provider { IDIR, BCEID, UNKNOWN }

  public static Jwt getCurrentJwt() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    return (Jwt) auth.getPrincipal();
  }

  /** {@code azureidir}/{@code idir} → IDIR, {@code bceidbusiness} → BCEID. */
  public static Provider getProvider(Jwt jwt) {
    String raw = jwt.getClaimAsString(CLAIM_IDENTITY_PROVIDER);
    if (raw == null || raw.isBlank()) {
      String preferred = jwt.getClaimAsString(CLAIM_PREFERRED_USERNAME);
      int at = preferred == null ? -1 : preferred.indexOf('@');
      raw = at < 0 ? "" : preferred.substring(at + 1);
    }
    return switch (raw.toLowerCase(Locale.ROOT)) {
      case "idir", "azureidir" -> Provider.IDIR;
      case "bceidbusiness", "bceidboth", "bceidbasic" -> Provider.BCEID;
      default -> Provider.UNKNOWN;
    };
  }

  /** The IDIR / BCeID username (falls back to the user GUID), upper-cased. */
  public static String getCurrentUserName() {
    try {
      Jwt jwt = getCurrentJwt();
      String[] claims = getProvider(jwt) == Provider.BCEID
          ? new String[] {CLAIM_BCEID_USERNAME, CLAIM_BCEID_USER_GUID}
          : new String[] {CLAIM_IDIR_USERNAME, CLAIM_IDIR_USER_GUID};
      for (String claim : claims) {
        String v = jwt.getClaimAsString(claim);
        if (v != null && !v.isBlank()) return v.trim().toUpperCase(Locale.ROOT);
      }
      return "";
    } catch (RuntimeException ex) {
      return "";
    }
  }

  /**
   * The legacy audit user id — {@code IDIR\NAME} or {@code BCEID\NAME}, the form
   * the legacy app (WebADE) stamped into ENTRY_USERID / UPDATE_USERID and kept in
   * HBS_USER.USER_ID. Truncated to the VARCHAR2(30) audit columns.
   */
  public static String getCurrentAuditUserId() {
    try {
      String username = getCurrentUserName();
      if (username.isBlank()) return "";
      String prefix = switch (getProvider(getCurrentJwt())) {
        case IDIR -> "IDIR\\";
        case BCEID -> "BCEID\\";
        case UNKNOWN -> "";
      };
      return truncate(prefix + username);
    } catch (RuntimeException ex) {
      return "";
    }
  }

  private static String truncate(String value) {
    return value.length() <= LEGACY_AUDIT_USERID_MAX ? value : value.substring(0, LEGACY_AUDIT_USERID_MAX);
  }

  // ── Role + client-scope helpers ───────────────────────────────────
  //
  // HBS roles stack (legacy WebADE behaviour). FAM serves them as client roles
  // named HBS_<ROLE>, client-tied (BCeID) ones scoped per forest client with
  // the scope in the name: HBS_CLI_SCALER_FOREST_CLIENT-00012345.

  /**
   * Header sent by the SPA when a BCeID user party to several clients has
   * picked an active client. Validated against the user's own role scopes
   * before use — a forged value is ignored.
   */
  private static final String ACTIVE_ORG_HEADER = "X-HBS-Active-Org-Client-Number";

  private static List<String> currentRoleNames() {
    try {
      return TokenRoles.rolesFrom(getCurrentJwt());
    } catch (RuntimeException ex) {
      return List.of();
    }
  }

  /**
   * The caller's canonical roles. Unscoped roles always apply; client-scoped
   * roles apply only for the active client.
   */
  public static Set<String> getCurrentRoles() {
    Set<String> result = new LinkedHashSet<>();
    String activeClient = getCurrentClientNumber();
    for (String name : currentRoleNames()) {
      String canonical = HbsRoles.canonicalRoleFor(name);
      if (canonical == null) continue;
      List<String> clients = RoleScope.parse(name).forestClients();
      if (clients.isEmpty() || clients.contains(activeClient)) {
        result.add(canonical);
      }
    }
    return result;
  }

  public static boolean hasRole(String canonicalRole) {
    return getCurrentRoles().contains(canonicalRole);
  }

  public static boolean hasAnyRole(String... canonicalRoles) {
    Set<String> roles = getCurrentRoles();
    for (String r : canonicalRoles) {
      if (roles.contains(r)) return true;
    }
    return false;
  }

  /** True when the caller holds any ministry (IDIR) role. */
  public static boolean isMinistryUser() {
    return HbsRoles.anyMinistry(getCurrentRoles());
  }

  /** Legacy user-type code for proc parameters: MOF / CLI / SPC / PUB. */
  public static String getLegacyUserType() {
    return HbsRoles.legacyUserType(getCurrentRoles());
  }

  /**
   * The 8-digit forest-client number scoping this request ("" when the caller
   * holds no client-scoped role). The active-org header when it is one of the
   * caller's own client scopes, else the first one.
   */
  public static String getCurrentClientNumber() {
    Set<String> own = new LinkedHashSet<>();
    for (String name : currentRoleNames()) {
      if (HbsRoles.canonicalRoleFor(name) == null) continue;
      RoleScope.parse(name).forestClients().stream()
          .filter(c -> c.matches("\\d{8}"))
          .forEach(own::add);
    }
    if (own.isEmpty()) return "";
    String requested = readActiveOrgHeader();
    if (requested != null && own.contains(requested)) {
      return requested;
    }
    return own.iterator().next();
  }

  private static String readActiveOrgHeader() {
    try {
      var attrs = RequestContextHolder.getRequestAttributes();
      if (!(attrs instanceof ServletRequestAttributes sra)) return null;
      String raw = sra.getRequest().getHeader(ACTIVE_ORG_HEADER);
      if (raw == null) return null;
      String trimmed = raw.trim();
      return trimmed.matches("\\d{8}") ? trimmed : null;
    } catch (RuntimeException ex) {
      return null;
    }
  }
}
