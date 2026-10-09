package ca.bc.gov.nrs.hbs.api.security;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Canonical HBS role names — one per legacy WebADE role, carried over 1:1 so
 * the legacy action matrix ({@code trunk/scripts/5.3.0/08/dml/hbs_action_lnk.sql})
 * stays the source of truth for who may do what. See
 * {@code docs/roles-and-security.md} for the full legacy → new mapping.
 *
 * <p>FAM client role names are the legacy role name with an {@code HBS_}
 * prefix when it doesn't already have one (legacy {@code MOF_USER} →
 * {@code HBS_MOF_USER}; legacy {@code HBS_BILL_ADMIN} stays
 * {@code HBS_BILL_ADMIN}). They reach the API in the BC Gov SSO (Keycloak)
 * access token's {@code client_roles}. Client-tied (BCeID) roles are granted
 * per forest client and FAM encodes the scope in the name, e.g.
 * {@code HBS_CLI_SCALER_FOREST_CLIENT-00012345} (see {@link RoleScope}).
 *
 * <p><b>Roles stack.</b> Unlike nr-fsp-new (single effective role), the
 * legacy HBS model grants a base role ({@code MOF_USER} / {@code CLI_USER} /
 * {@code SPC_USER}) plus any number of functional roles, and a user's
 * capabilities are the union. That behaviour is preserved here; the capability
 * matrix lives in {@link HbsAuthorities}.
 */
public final class HbsRoles {

  // ── Ministry (IDIR) ───────────────────────────────────────────────
  /** Base ministry role — legacy MOF_USER (ministry menus/tabs). */
  public static final String MOF_USER = "HBS_MOF_USER";
  /** Ministry user administrator — legacy MOF_ADMIN (User Services). */
  public static final String MOF_ADMIN = "HBS_MOF_ADMIN";
  /** Ministry user manager — legacy MOF_MGR (approves account requests). */
  public static final String MOF_MGR = "HBS_MOF_MGR";
  public static final String RATE_ADMIN = "HBS_RATE_ADMIN";
  public static final String HELP_DESK = "HBS_HELP_DESK";
  public static final String SUMM_DATA_CTL = "HBS_SUMM_DATA_CTL";
  public static final String SUMM_DATA_CORR = "HBS_SUMM_DATA_CORR";
  public static final String DTL_DATA_ENT = "HBS_DTL_DATA_ENT";
  public static final String PROD_CTL = "HBS_PROD_CTL";
  public static final String BILL_ADMIN = "HBS_BILL_ADMIN";
  public static final String SCALE_ADMIN = "HBS_SCALE_ADMIN";
  public static final String SUPER_MGR = "HBS_SUPER_MGR";
  public static final String INV_CORR_APP = "HBS_INV_CORR_APP";
  /** Ministry check scaler — legacy MOF_SCALER. */
  public static final String MOF_SCALER = "HBS_MOF_SCALER";
  public static final String SMP_ADMIN = "HBS_SMP_ADMIN";
  public static final String WRA_ADMIN = "HBS_WRA_ADMIN";
  /** May use the app while it is administratively disabled. */
  public static final String DISABLED_BYPASS = "HBS_DISABLED_BYPASS";

  // ── Forest industry clients (BCeID, client-scoped) ────────────────
  public static final String CLI_USER = "HBS_CLI_USER";
  public static final String CLI_ADMIN = "HBS_CLI_ADMIN";
  public static final String CLI_SITE_ADMIN = "HBS_CLI_SITE_ADMIN";
  public static final String CLI_SCALER = "HBS_CLI_SCALER";
  public static final String CLI_SMP_ADMIN = "HBS_CLI_SMP_ADMIN";
  public static final String CLI_DOC_RCVR = "HBS_CLI_DOC_RCVR";
  public static final String CLI_CRUISE_ADMIN = "HBS_CLI_CRUISE_ADMIN";

  // ── Other industry: agents / software vendors (BCeID, client-scoped) ─
  public static final String SPC_USER = "HBS_SPC_USER";
  public static final String SPC_ADMIN = "HBS_SPC_ADMIN";
  public static final String SPC_SFTWR_VNDR = "HBS_SPC_SFTWR_VNDR";
  public static final String SPC_SUBM_AGNT = "HBS_SPC_SUBM_AGNT";

  /**
   * Every canonical role.
   */
  public static final List<String> ALL = List.of(
      CLI_CRUISE_ADMIN, CLI_SITE_ADMIN, CLI_SMP_ADMIN, CLI_DOC_RCVR, CLI_SCALER,
      CLI_ADMIN, CLI_USER,
      SPC_SFTWR_VNDR, SPC_SUBM_AGNT, SPC_ADMIN, SPC_USER,
      DISABLED_BYPASS, SUMM_DATA_CORR, SUMM_DATA_CTL, INV_CORR_APP, DTL_DATA_ENT,
      SCALE_ADMIN, RATE_ADMIN, BILL_ADMIN, SUPER_MGR, SMP_ADMIN, WRA_ADMIN,
      HELP_DESK, PROD_CTL, MOF_SCALER, MOF_ADMIN, MOF_USER, MOF_MGR
  );

  /** Ministry (IDIR) roles — never client-scoped. */
  public static final Set<String> MINISTRY = Set.of(
      MOF_USER, MOF_ADMIN, MOF_MGR, RATE_ADMIN, HELP_DESK, SUMM_DATA_CTL,
      SUMM_DATA_CORR, DTL_DATA_ENT, PROD_CTL, BILL_ADMIN, SCALE_ADMIN, SUPER_MGR,
      INV_CORR_APP, MOF_SCALER, SMP_ADMIN, WRA_ADMIN, DISABLED_BYPASS
  );

  /** Forest-industry client roles (legacy user type CLI). */
  public static final Set<String> CLIENT = Set.of(
      CLI_USER, CLI_ADMIN, CLI_SITE_ADMIN, CLI_SCALER, CLI_SMP_ADMIN,
      CLI_DOC_RCVR, CLI_CRUISE_ADMIN
  );

  /** Other-industry roles (legacy user type SPC — agents, software vendors). */
  public static final Set<String> SPECIAL = Set.of(
      SPC_USER, SPC_ADMIN, SPC_SFTWR_VNDR, SPC_SUBM_AGNT
  );

  private HbsRoles() {}

  /** True for the ministry (IDIR, non-client-scoped) roles. */
  public static boolean isMinistry(String canonicalRole) {
    return MINISTRY.contains(canonicalRole);
  }

  /** True for any role that is scoped to a forest-client number. */
  public static boolean isClientScoped(String canonicalRole) {
    return CLIENT.contains(canonicalRole) || SPECIAL.contains(canonicalRole);
  }

  /** True when any of the given canonical roles is a ministry role. */
  public static boolean anyMinistry(Collection<String> canonicalRoles) {
    return canonicalRoles != null && canonicalRoles.stream().anyMatch(HbsRoles::isMinistry);
  }

  /**
   * Returns the canonical HBS role a FAM/Keycloak client role grants, or
   * {@code null} if it does not map to any known role.
   */
  public static String canonicalRoleFor(String roleName) {
    if (roleName == null || RoleScope.isSidecar(roleName)) return null;
    // FAM encodes a grant's scope in the role name
    // (HBS_CLI_SCALER_FOREST_CLIENT-00012345); strip it and match the base
    // role exactly.
    String base = RoleScope.parse(roleName).baseRole();
    return ALL.contains(base) ? base : null;
  }

  /**
   * The legacy user type the stored procedures expect in their
   * {@code *_UserTypeCd} parameters ({@code MOF} / {@code CLI} / {@code SPC}),
   * derived from the caller's roles. Ministry wins when mixed.
   */
  public static String legacyUserType(Collection<String> canonicalRoles) {
    if (canonicalRoles == null || canonicalRoles.isEmpty()) return "PUB";
    if (anyMinistry(canonicalRoles)) return "MOF";
    if (canonicalRoles.stream().anyMatch(CLIENT::contains)) return "CLI";
    if (canonicalRoles.stream().anyMatch(SPECIAL::contains)) return "SPC";
    return "PUB";
  }
}
