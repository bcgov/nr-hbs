import { parseRoleName, SCOPE_FOREST_CLIENT } from './roleScope';
import {
  AVAILABLE_ROLES,
  validIdpProviders,
  type FamLoginUser,
  type IdpProviderType,
  type ROLE_TYPE,
  type USER_PRIVILEGE_TYPE,
} from './types';

/**
 * The claims HBS reads off a BC Gov SSO (Keycloak, standard realm) access
 * token. Same approach as nr-fta / nr-rept, plus the Business BCeID claims
 * (HBS serves industry clients as well as ministry staff).
 *
 * Claim names follow the SSO identity-mappers reference:
 * https://bcgov.github.io/sso-docs/advanced/identity-mappers
 */
export type KeycloakProfile = {
  /** `<guid>@azureidir` / `<guid>@bceidbusiness`. Stable per user per provider. */
  preferred_username?: string;
  /** `azureidir` (IDIR - MFA) or `bceidbusiness`. */
  identity_provider?: string;
  idir_username?: string;
  idir_user_guid?: string;
  bceid_username?: string;
  bceid_user_guid?: string;
  bceid_business_guid?: string;
  bceid_business_name?: string;
  display_name?: string;
  given_name?: string;
  family_name?: string;
  email?: string;
  name?: string;
  /** Roles CSS/FAM attaches for the client the token was issued to. */
  client_roles?: string[];
  resource_access?: Record<string, { roles?: string[] }>;
  azp?: string;
  [claim: string]: unknown;
};

/** FAM's bookkeeping roles (`FAM:EXPIRES:2026-09-30:HBS_CLI_USER…`) — never privileges. */
const FAM_SIDECAR_PREFIX = 'FAM:';

/**
 * Normalises the realm's provider alias: `azureidir`/`idir` → IDIR,
 * `bceidbusiness` → BCEIDBUSINESS. Falls back to the `preferred_username`
 * suffix, which is always present.
 */
export const parseIdpProvider = (profile: KeycloakProfile): IdpProviderType | undefined => {
  const raw = (profile.identity_provider ?? profile.preferred_username?.split('@')[1] ?? '').toLowerCase();
  const normalized = raw === 'azureidir' || raw === 'idir' ? 'IDIR' : raw === 'bceidbusiness' ? 'BCEIDBUSINESS' : '';
  return validIdpProviders.includes(normalized as IdpProviderType)
    ? (normalized as IdpProviderType)
    : undefined;
};

/**
 * The caller's roles for the client the token was issued to: `client_roles`
 * under CSS, falling back to stock Keycloak's `resource_access.<azp>.roles`.
 */
export const extractRoles = (profile: KeycloakProfile | undefined): string[] => {
  if (!profile) return [];
  const clientRoles = profile.client_roles;
  if (Array.isArray(clientRoles) && clientRoles.length > 0) return clientRoles;
  const clientId = profile.azp;
  if (!clientId) return [];
  const roles = profile.resource_access?.[clientId]?.roles;
  return Array.isArray(roles) ? roles : [];
};

/**
 * Parses role names into role → forest-client numbers (null = not
 * client-scoped). Recognises both spellings FAM can produce:
 *
 * - **Unscoped** — `HBS_BILL_ADMIN` → `null`.
 * - **Forest-client scoped** — `HBS_CLI_SCALER_FOREST_CLIENT-00012345` →
 *   `['00012345']`. Several clients merge into one list.
 *
 * An unscoped grant wins over scoped ones (it is strictly broader).
 */
export function parsePrivileges(input: string[]): USER_PRIVILEGE_TYPE {
  const result: USER_PRIVILEGE_TYPE = {};
  for (const item of input) {
    if (item.startsWith(FAM_SIDECAR_PREFIX)) continue;
    const { baseRole, scopes } = parseRoleName(item);
    if (!AVAILABLE_ROLES.includes(baseRole as ROLE_TYPE)) continue;
    const role = baseRole as ROLE_TYPE;
    const clients = scopes[SCOPE_FOREST_CLIENT] ?? [];
    if (clients.length === 0) {
      result[role] = null;
      continue;
    }
    if (result[role] === null) continue;
    const merged = [...(result[role] ?? [])];
    clients.forEach((c) => {
      if (!merged.includes(c)) merged.push(c);
    });
    result[role] = merged;
  }
  return result;
}

/** Client-tied roles (industry + other industry). */
const CLIENT_ROLES: ROLE_TYPE[] = [
  'HBS_CLI_USER', 'HBS_CLI_ADMIN', 'HBS_CLI_SITE_ADMIN', 'HBS_CLI_SCALER',
  'HBS_CLI_SMP_ADMIN', 'HBS_CLI_DOC_RCVR', 'HBS_CLI_CRUISE_ADMIN',
  'HBS_SPC_USER', 'HBS_SPC_ADMIN', 'HBS_SPC_SFTWR_VNDR', 'HBS_SPC_SUBM_AGNT',
];

/**
 * The user's roles for the active client. HBS roles STACK (legacy WebADE
 * behaviour), so this returns every held role: unscoped roles always, and
 * client-scoped roles granted for {@code activeClient}. Mirrors backend
 * RequestUtil.getCurrentRoles().
 */
export function rolesForClient(
  privileges: USER_PRIVILEGE_TYPE,
  activeClient: string | null | undefined,
): ROLE_TYPE[] {
  const out: ROLE_TYPE[] = [];
  for (const role of AVAILABLE_ROLES) {
    if (!(role in privileges)) continue;
    const clients = privileges[role];
    if (!CLIENT_ROLES.includes(role) || !Array.isArray(clients) || !activeClient) {
      out.push(role);
    } else if (clients.includes(activeClient)) {
      out.push(role);
    }
  }
  return out;
}

/**
 * Parses an oidc-client-ts profile into the FamLoginUser shape. Names come
 * from the real `given_name` / `family_name` claims; the user name is the IDIR
 * or BCeID username depending on the provider.
 */
export const parseToken = (profile: KeycloakProfile | undefined): FamLoginUser | undefined => {
  if (!profile) return undefined;
  const idpProvider = parseIdpProvider(profile);
  const userName =
    (idpProvider === 'BCEIDBUSINESS' ? profile.bceid_username : profile.idir_username) ?? '';
  const firstName = profile.given_name ?? '';
  const lastName = profile.family_name ?? '';
  const displayName =
    profile.display_name ?? profile.name ?? [firstName, lastName].filter(Boolean).join(' ');
  const privileges = parsePrivileges(extractRoles(profile));
  return {
    userName,
    displayName,
    email: profile.email ?? '',
    idpProvider,
    privileges,
    // Every held role (stacking). OrgProvider narrows client-tied roles to the
    // active client via rolesForClient() once one is chosen.
    roles: Object.keys(privileges) as ROLE_TYPE[],
    firstName,
    lastName,
    businessName: profile.bceid_business_name,
    providerUsername: idpProvider
      ? `${idpProvider === 'BCEIDBUSINESS' ? 'BCEID' : 'IDIR'}\\${userName.toUpperCase()}`
      : undefined,
  };
};

export interface ClientOrg {
  clientNumber: string;
  /** Client-tied roles the user holds for this client. */
  roles: ROLE_TYPE[];
}

/**
 * Every forest client the user holds a client-tied role for, with those
 * roles, sorted by client number. Drives the org picker.
 */
export function listClientOrgs(privileges: USER_PRIVILEGE_TYPE): ClientOrg[] {
  const byClient = new Map<string, ROLE_TYPE[]>();
  for (const role of CLIENT_ROLES) {
    const clientNumbers = privileges[role];
    if (!Array.isArray(clientNumbers)) continue;
    for (const cn of clientNumbers) {
      byClient.set(cn, [...(byClient.get(cn) ?? []), role]);
    }
  }
  return [...byClient.entries()]
    .map(([clientNumber, roles]) => ({ clientNumber, roles }))
    .sort((a, b) => a.clientNumber.localeCompare(b.clientNumber));
}
