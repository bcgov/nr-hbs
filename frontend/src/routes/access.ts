import type { FamLoginUser, ROLE_TYPE } from '@/context/auth/types';

/**
 * Capability matrix — mirrors backend
 * {@code ca.bc.gov.nrs.hbs.api.query.Capability} / {@code HbsAuthorities}.
 * Keep the two in sync. Derived 1:1 from the legacy WebADE action grants
 * (trunk/scripts/5.3.0/08/dml/hbs_action_lnk.sql); see
 * docs/roles-and-security.md.
 *
 * Unlike nr-fsp-new, HBS roles STACK: a user holds a base role
 * (HBS_MOF_USER / HBS_CLI_USER / HBS_SPC_USER) plus functional roles, and
 * a capability is granted when ANY held role grants it.
 */
const CAPABILITIES = {
  ANY_USER: ['HBS_MOF_USER', 'HBS_CLI_USER', 'HBS_SPC_USER'],
  MINISTRY: ['HBS_MOF_USER'],

  QUERIES_VIEW: ['HBS_MOF_USER', 'HBS_CLI_USER'],

  SCALE_RETURNS_VIEW: ['HBS_MOF_USER', 'HBS_CLI_USER', 'HBS_SPC_USER'],
  SUMMARY_RETURN_EDIT: [
    'HBS_SUMM_DATA_CTL', 'HBS_SUMM_DATA_CORR', 'HBS_SCALE_ADMIN', 'HBS_BILL_ADMIN',
    'HBS_CLI_SCALER', 'HBS_CLI_SITE_ADMIN', 'HBS_CLI_CRUISE_ADMIN', 'HBS_SPC_SUBM_AGNT',
  ],
  SUMMARY_RETURN_ADMIN: ['HBS_BILL_ADMIN'],
  DETAIL_RETURN_EDIT: [
    'HBS_DTL_DATA_ENT', 'HBS_SCALE_ADMIN', 'HBS_MOF_SCALER', 'HBS_CLI_SCALER', 'HBS_CLI_SITE_ADMIN',
  ],
  DETAIL_RETURN_APPROVE: ['HBS_BILL_ADMIN', 'HBS_INV_CORR_APP'],
  XML_SUBMIT: [
    'HBS_DTL_DATA_ENT', 'HBS_MOF_SCALER', 'HBS_SCALE_ADMIN', 'HBS_CLI_SITE_ADMIN',
    'HBS_CLI_SCALER', 'HBS_SPC_SFTWR_VNDR', 'HBS_SPC_SUBM_AGNT',
  ],
  BATCH_SLIP_EDIT: [
    'HBS_BILL_ADMIN', 'HBS_DTL_DATA_ENT', 'HBS_PROD_CTL', 'HBS_SCALE_ADMIN', 'HBS_SUMM_DATA_CTL',
  ],
  PRODUCTION_CONTROL: ['HBS_PROD_CTL'],

  SAMPLING_VIEW: ['HBS_MOF_USER', 'HBS_CLI_USER'],
  SAMPLING_ADMIN: ['HBS_SMP_ADMIN', 'HBS_CLI_SMP_ADMIN'],

  RATING_VIEW: ['HBS_MOF_USER', 'HBS_CLI_USER'],
  RATING_MINISTRY_VIEW: ['HBS_MOF_USER'],
  RATE_ADMIN: ['HBS_RATE_ADMIN'],
  WASTE_RATE_ADMIN: ['HBS_WRA_ADMIN'],

  BILLING_VIEW: ['HBS_MOF_USER', 'HBS_CLI_USER'],
  BILLING_ADMIN: ['HBS_BILL_ADMIN', 'HBS_SUPER_MGR'],
  FINAL_BILL_ADMIN: ['HBS_BILL_ADMIN', 'HBS_SUPER_MGR'],
  FIELD_SCALED_DECKS: ['HBS_BILL_ADMIN', 'HBS_SCALE_ADMIN', 'HBS_SUPER_MGR'],
  BILLING_FORCE: ['HBS_SUPER_MGR'],

  PROFILES_VIEW: ['HBS_MOF_USER', 'HBS_CLI_USER'],
  SCALE_SITE_ADMIN: ['HBS_SCALE_ADMIN'],
  PROFILE_BILLING_ADMIN: ['HBS_BILL_ADMIN'],
  SCALER_PROFILE_EDIT: ['HBS_SCALE_ADMIN', 'HBS_CLI_SCALER'],

  SCALE_CONTROL_VIEW: ['HBS_MOF_USER', 'HBS_CLI_USER'],
  SCALE_CONTROL_MINISTRY_VIEW: ['HBS_MOF_USER'],
  SCALE_CONTROL_ADMIN: ['HBS_SCALE_ADMIN'],
  ANOMALY_EDIT: ['HBS_SCALE_ADMIN', 'HBS_CLI_SCALER', 'HBS_CLI_SITE_ADMIN'],
  SOFTWARE_USE_VIEW: ['HBS_MOF_USER', 'HBS_CLI_USER', 'HBS_SPC_SFTWR_VNDR'],

  USER_ADMIN: ['HBS_MOF_ADMIN', 'HBS_MOF_MGR', 'HBS_CLI_ADMIN', 'HBS_SPC_ADMIN'],
  CACHE_ADMIN: ['HBS_PROD_CTL'],
} as const satisfies Record<string, readonly ROLE_TYPE[]>;

export type Capability = keyof typeof CAPABILITIES;

/** True when any of the user's (stacked) roles grants the capability. */
export function can(user: FamLoginUser | null | undefined, capability: Capability): boolean {
  const roles = user?.roles ?? [];
  return (CAPABILITIES[capability] as readonly string[]).some((r) => roles.includes(r as ROLE_TYPE));
}

/** True when the user holds any ministry (IDIR) role. */
export function isMinistry(user: FamLoginUser | null | undefined): boolean {
  return can(user, 'MINISTRY');
}

/**
 * Every authenticated HBS user lands on the Home dashboard (the legacy
 * home.jsp work-queue page), which renders the links their roles allow.
 */
export function defaultRouteForUser(_user: FamLoginUser | null | undefined): string {
  return '/home';
}
