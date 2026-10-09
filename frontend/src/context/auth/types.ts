/**
 * Recognised FAM / BC Gov SSO (Keycloak) client roles → HBS roles. Mirrors the
 * backend's ca.bc.gov.nrs.hbs.api.security.HbsRoles — keep the two lists in
 * sync. One role per legacy WebADE role (legacy MOF_USER → HBS_MOF_USER; legacy
 * HBS_BILL_ADMIN unchanged).
 *
 * Roles arrive in the access token's `client_roles`. Client-tied (BCeID) roles
 * are granted by FAM per forest client and carry the scope in the role name —
 * `HBS_CLI_SCALER_FOREST_CLIENT-00012345` — which roleScope.ts strips back to
 * the base role here.
 */
export const AVAILABLE_ROLES = [
  'HBS_CLI_CRUISE_ADMIN',
  'HBS_CLI_SITE_ADMIN',
  'HBS_CLI_SMP_ADMIN',
  'HBS_CLI_DOC_RCVR',
  'HBS_CLI_SCALER',
  'HBS_CLI_ADMIN',
  'HBS_CLI_USER',
  'HBS_SPC_SFTWR_VNDR',
  'HBS_SPC_SUBM_AGNT',
  'HBS_SPC_ADMIN',
  'HBS_SPC_USER',
  'HBS_DISABLED_BYPASS',
  'HBS_SUMM_DATA_CORR',
  'HBS_SUMM_DATA_CTL',
  'HBS_INV_CORR_APP',
  'HBS_DTL_DATA_ENT',
  'HBS_SCALE_ADMIN',
  'HBS_RATE_ADMIN',
  'HBS_BILL_ADMIN',
  'HBS_SUPER_MGR',
  'HBS_SMP_ADMIN',
  'HBS_WRA_ADMIN',
  'HBS_HELP_DESK',
  'HBS_PROD_CTL',
  'HBS_MOF_SCALER',
  'HBS_MOF_ADMIN',
  'HBS_MOF_USER',
  'HBS_MOF_MGR',
] as const;

export type ROLE_TYPE = (typeof AVAILABLE_ROLES)[number];

/** Display labels — the legacy User Services role-picker labels. */
export const ROLE_LABELS: Record<ROLE_TYPE, string> = {
  HBS_MOF_USER: 'Ministry User',
  HBS_MOF_ADMIN: 'Ministry User Administrator',
  HBS_MOF_MGR: 'Ministry User Manager',
  HBS_RATE_ADMIN: 'Rate Administrator',
  HBS_HELP_DESK: 'Help Desk',
  HBS_SUMM_DATA_CTL: 'Summary Data Control',
  HBS_SUMM_DATA_CORR: 'Summary Data Correction',
  HBS_DTL_DATA_ENT: 'Detail Data Entry',
  HBS_PROD_CTL: 'Production Control',
  HBS_BILL_ADMIN: 'Billing Administrator',
  HBS_SCALE_ADMIN: 'Scaling Administrator',
  HBS_SUPER_MGR: 'Super Manager',
  HBS_INV_CORR_APP: 'Invoice Correction Approver',
  HBS_MOF_SCALER: 'Ministry Check Scaler',
  HBS_SMP_ADMIN: 'Ministry Sample Plan Administrator',
  HBS_WRA_ADMIN: 'Waste/Residue Rate Administrator',
  HBS_DISABLED_BYPASS: 'Disabled-Mode Bypass',
  HBS_CLI_USER: 'Industry User',
  HBS_CLI_ADMIN: 'Industry User Administrator',
  HBS_CLI_SITE_ADMIN: 'Industry Site Administrator',
  HBS_CLI_SCALER: 'Industry Scaler',
  HBS_CLI_SMP_ADMIN: 'Industry Sample Plan Administrator',
  HBS_CLI_DOC_RCVR: 'Industry Document Receiver',
  HBS_CLI_CRUISE_ADMIN: 'Industry Cruise Based Administrator',
  HBS_SPC_USER: 'Other Industry User',
  HBS_SPC_ADMIN: 'Other Industry Administrator',
  HBS_SPC_SFTWR_VNDR: 'Software Vendor',
  HBS_SPC_SUBM_AGNT: 'Data Submission Agent',
};

type RoleValue = string[] | null;

export type USER_PRIVILEGE_TYPE = Partial<Record<ROLE_TYPE, RoleValue>>;

// HBS supports IDIR (ministry staff) and BCeID Business (licensees, scale
// sites, submission agents and scale-software vendors).
export const validIdpProviders = ['IDIR', 'BCEIDBUSINESS'] as const;

export type IdpProviderType = (typeof validIdpProviders)[number];

/**
 * Provider chosen on the Landing page — mapped to the Keycloak `kc_idp_hint`
 * alias in services/keycloak.ts ('idir' → azureidir, 'bceid' → bceidbusiness).
 */
export type LoginProvider = 'idir' | 'bceid';

export type FamLoginUser = {
  providerUsername?: string;
  userName?: string;
  displayName?: string;
  email?: string;
  idpProvider?: IdpProviderType;
  roles?: ROLE_TYPE[];
  authToken?: string;
  exp?: number;
  privileges: USER_PRIVILEGE_TYPE;
  firstName?: string;
  lastName?: string;
  /** Business BCeID users: the business the account belongs to. */
  businessName?: string;
};
