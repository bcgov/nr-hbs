package ca.bc.gov.nrs.hbs.api.security;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Decoding of the scope FAM encodes into a CSS role name.
 *
 * <p>A CSS role is a bare name — no attributes, no description — so when FAM
 * grants a role for a district, region or forest client it creates a role named
 * {@code <role>_<SCOPE_TYPE>-<value>} and assigns that. Nothing in the token
 * says "scope" separately: drop the suffix and the authorisation is gone.
 *
 * <p>Consequently <b>the bare code never reaches the token for a scoped
 * holder</b>. A client-scoped HBS industry scaler arrives as
 * {@code HBS_CLI_SCALER_FOREST_CLIENT-00012345}, never {@code HBS_CLI_SCALER}, so an equality check
 * against the plain role name matches nothing and the user appears to hold no
 * role at all.
 *
 * <p>Parsing strips suffixes from the right rather than splitting on the last
 * underscore, because two things make a blind split wrong: region values contain
 * underscores ({@code KOOTENAY_BOUNDARY}), and the scope type
 * {@code FOREST_CLIENT} contains one itself — a split would read
 * {@code FOM_SUBMITTER_FOREST_CLIENT-00001018} as scope type {@code CLIENT} of
 * role {@code FOM_SUBMITTER_FOREST}. Known scope types are matched longest
 * first instead, so a hyphen that is not a scope separator leaves the name
 * alone.
 *
 * <p>Ported from {@code CssRoleNaming} in {@code nr-fam}, which is the
 * reference implementation.
 */
public final class RoleScope {

  /** Scoped by natural resource district; the value is an {@code ORG_UNIT_CODE}. */
  public static final String SCOPE_DISTRICT = "DISTRICT";

  /** Scoped by natural resource region; the value is a {@code REGION_CODE}. */
  public static final String SCOPE_REGION = "REGION";

  /** Scoped by forest client; the value is a client number. */
  public static final String SCOPE_FOREST_CLIENT = "FOREST_CLIENT";

  /**
   * Matched longest first, so {@code FOREST_CLIENT} is never read as
   * {@code CLIENT}. This is the parsing order, not the writing order — FAM
   * writes suffixes district, region, forest client.
   */
  private static final List<String> SCOPE_TYPES =
      List.of(SCOPE_FOREST_CLIENT, SCOPE_DISTRICT, SCOPE_REGION);

  /**
   * FAM's own bookkeeping roles, which ride the token like any other role.
   * Per-grant expiry is recorded as {@code FAM:EXPIRES:<date>:<role>} because a
   * role is a name and nothing else. Never a privilege.
   */
  private static final String SIDECAR_PREFIX = "FAM:";

  private RoleScope() {}

  /**
   * A role name decomposed into its base code and whatever it is scoped to.
   *
   * @param baseRole the role code with every scope suffix removed
   * @param scopes   scope type to the values granted, in the order parsed;
   *                 empty for an unscoped role
   */
  public record Parsed(String baseRole, Map<String, List<String>> scopes) {

    /** The district codes this role is scoped to, or empty if unscoped. */
    /** The forest-client numbers this role is scoped to (empty when unscoped). */
    public List<String> forestClients() {
      return scopes.getOrDefault(SCOPE_FOREST_CLIENT, List.of());
    }

    public List<String> districts() {
      return scopes.getOrDefault(SCOPE_DISTRICT, List.of());
    }
  }

  /**
   * Splits a role name into its base code and scopes.
   *
   * <p>A name whose hyphen is not a scope separator ({@code SOME-ROLE}), or
   * which has nothing before the scope type ({@code _DISTRICT-DCC}), is its own
   * base role with no scopes.
   */
  public static Parsed parse(String roleName) {
    if (roleName == null || roleName.isBlank()) {
      return new Parsed("", Map.of());
    }
    String remaining = roleName;
    Map<String, List<String>> scopes = new LinkedHashMap<>();

    while (true) {
      int dash = remaining.lastIndexOf('-');
      if (dash <= 0) {
        break;
      }
      String value = remaining.substring(dash + 1);
      String head = remaining.substring(0, dash);

      String matched = null;
      for (String type : SCOPE_TYPES) {
        if (head.endsWith('_' + type)) {
          matched = type;
          break;
        }
      }
      if (matched == null || value.isEmpty()) {
        break;
      }
      String base = head.substring(0, head.length() - matched.length() - 1);
      if (base.isEmpty()) {
        // Nothing before the scope type: the whole thing is a role name.
        break;
      }
      scopes.computeIfAbsent(matched, k -> new ArrayList<>()).add(value);
      remaining = base;
    }

    // Parsed right-to-left, so reverse each list back into grant order.
    scopes.values().forEach(Collections::reverse);
    return new Parsed(remaining, Collections.unmodifiableMap(scopes));
  }

  /** Whether a role name is one of FAM's bookkeeping sidecars. */
  public static boolean isSidecar(String roleName) {
    return roleName != null && roleName.startsWith(SIDECAR_PREFIX);
  }

  /**
   * Every district code the user holds for the given base role.
   *
   * <p>One role per scope value, so a user holding a role for three districts
   * carries three role names. An unscoped grant of the same role contributes no
   * districts — the caller distinguishes "scoped to nothing" (no access) from
   * "unscoped" (all districts) via {@link #hasUnscoped}.
   */
  public static List<String> districtsFor(Iterable<String> roleNames, String baseRole) {
    List<String> districts = new ArrayList<>();
    for (String name : roleNames) {
      if (isSidecar(name)) {
        continue;
      }
      Parsed parsed = parse(name);
      if (parsed.baseRole().equals(baseRole)) {
        districts.addAll(parsed.districts());
      }
    }
    return districts;
  }

  /** Whether the user holds the base role with no scope at all. */
  public static boolean hasUnscoped(Iterable<String> roleNames, String baseRole) {
    for (String name : roleNames) {
      if (isSidecar(name)) {
        continue;
      }
      Parsed parsed = parse(name);
      if (parsed.baseRole().equals(baseRole) && parsed.scopes().isEmpty()) {
        return true;
      }
    }
    return false;
  }
}
