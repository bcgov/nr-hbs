/**
 * Decoding of the scope FAM encodes into a CSS role name.
 *
 * A CSS role is a bare name — no attributes, no description — so when FAM grants
 * a role for a district, region or forest client it creates a role named
 * `<role>_<SCOPE_TYPE>-<value>` and assigns that. Nothing in the token says
 * "scope" separately: drop the suffix and the authorisation is gone.
 *
 * Consequently **the bare code never reaches the token for a scoped holder**. A
 * client-scoped HBS industry scaler arrives as `HBS_CLI_SCALER_FOREST_CLIENT-00012345`,
 * never `HBS_CLI_SCALER`.
 *
 * Suffixes are stripped from the right rather than split on the last underscore,
 * because two things make a blind split wrong: region values contain underscores
 * (`KOOTENAY_BOUNDARY`), and the scope type `FOREST_CLIENT` contains one itself —
 * a split reads `FOM_SUBMITTER_FOREST_CLIENT-00001018` as scope type `CLIENT` of
 * role `FOM_SUBMITTER_FOREST`. Known types are matched longest first instead, so
 * a hyphen that is not a scope separator leaves the name alone.
 *
 * Mirrors the backend `ca.bc.gov.nrs.hbs.api.security.RoleScope`, itself ported from
 * `CssRoleNaming` in nr-fam. The two must agree: the UI deciding a user may edit
 * something the API then refuses is the failure this pairing exists to prevent.
 */

/** Scoped by natural resource district; the value is an `ORG_UNIT_CODE`. */
export const SCOPE_DISTRICT = 'DISTRICT';

/** Scoped by natural resource region; the value is a `REGION_CODE`. */
export const SCOPE_REGION = 'REGION';

/** Scoped by forest client; the value is a client number. */
export const SCOPE_FOREST_CLIENT = 'FOREST_CLIENT';

/**
 * Matched longest first, so `FOREST_CLIENT` is never read as `CLIENT`. This is
 * the parsing order; FAM *writes* suffixes district, region, forest client.
 */
const SCOPE_TYPES = [SCOPE_FOREST_CLIENT, SCOPE_DISTRICT, SCOPE_REGION] as const;

export type ParsedRole = {
  /** The role code with every scope suffix removed. */
  baseRole: string;
  /** Scope type to the values granted; empty for an unscoped role. */
  scopes: Record<string, string[]>;
};

/**
 * Splits a role name into its base code and scopes.
 *
 * A name whose hyphen is not a scope separator (`SOME-ROLE`), or which has
 * nothing before the scope type (`_DISTRICT-DCC`), is its own base role with no
 * scopes.
 */
export function parseRoleName(roleName: string): ParsedRole {
  if (!roleName) return { baseRole: '', scopes: {} };

  let remaining = roleName;
  const scopes: Record<string, string[]> = {};

  for (;;) {
    const dash = remaining.lastIndexOf('-');
    if (dash <= 0) break;

    const value = remaining.slice(dash + 1);
    const head = remaining.slice(0, dash);
    const matched = SCOPE_TYPES.find((type) => head.endsWith(`_${type}`));

    if (!matched || value.length === 0) break;

    const base = head.slice(0, head.length - matched.length - 1);
    // Nothing before the scope type: the whole thing is a role name.
    if (base.length === 0) break;

    (scopes[matched] ??= []).unshift(value); // parsed right-to-left
    remaining = base;
  }

  return { baseRole: remaining, scopes };
}

/** The district codes a parsed role is scoped to. */
export function districtsOf(parsed: ParsedRole): string[] {
  return parsed.scopes[SCOPE_DISTRICT] ?? [];
}
