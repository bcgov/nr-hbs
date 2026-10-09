package ca.bc.gov.nrs.hbs.api.security;

/**
 * Centralised method-security expressions for {@code @PreAuthorize}.
 *
 * <p>Each constant is a named <em>capability</em> derived from the legacy
 * WebADE action grants ({@code hbs_action_lnk.sql}): a legacy
 * {@code web:/<area>/<path>} grant becomes the capability that guards the
 * equivalent REST endpoints. The frontend mirrors this table in
 * {@code src/routes/access.ts} — keep the two in sync.
 *
 * <p>Authorities are exposed as {@code ROLE_HBS_*} by
 * {@link CognitoGroupsAuthoritiesConverter}. Roles <b>stack</b> (legacy
 * behaviour), so a user holding several roles gets the union.
 *
 * <p>These are coarse role gates. Row-level scoping — a client user only
 * sees their own client's returns/invoices, a scale-site administrator only
 * their sites — is enforced in the service layer by {@link HbsAccessGuard}
 * using the active client number and the user's HBS data domains.
 *
 * <p>Read (GET) endpoints for a functional area use that area's
 * {@code *_VIEW} capability; writes use the narrower admin capability.
 */
public final class HbsAuthorities {

  private HbsAuthorities() {}

  private static final String ANY_BASE =
      "'HBS_MOF_USER','HBS_CLI_USER','HBS_SPC_USER'";
  private static final String MINISTRY_AND_CLIENT =
      "'HBS_MOF_USER','HBS_CLI_USER'";

  /** Any authenticated HBS user (all three base roles). */
  public static final String ANY_USER = "hasAnyRole(" + ANY_BASE + ")";

  /** Ministry staff only (legacy MOF_USER). */
  public static final String MINISTRY = "hasRole('HBS_MOF_USER')";

  // ── Queries tab (/opq/P400 + invoice/statement/timber mark/etc.) ──
  public static final String QUERIES_VIEW = "hasAnyRole(" + MINISTRY_AND_CLIENT + ")";

  // ── Scale Returns tab (/dac/*) ─────────────────────────────────────
  /** View summary + detail scale returns, submission status. */
  public static final String SCALE_RETURNS_VIEW = "hasAnyRole(" + ANY_BASE + ")";

  /** Add / update summary scale returns (P050/P100/P150, P053). */
  public static final String SUMMARY_RETURN_EDIT =
      "hasAnyRole('HBS_SUMM_DATA_CTL','HBS_SUMM_DATA_CORR','HBS_SCALE_ADMIN',"
          + "'HBS_BILL_ADMIN','HBS_CLI_SCALER','HBS_CLI_SITE_ADMIN',"
          + "'HBS_CLI_CRUISE_ADMIN','HBS_SPC_SUBM_AGNT')";

  /** Summary-return administration (/dac/summary/admin/*). */
  public static final String SUMMARY_RETURN_ADMIN = "hasRole('HBS_BILL_ADMIN')";

  /** Add / update detail scale returns (/dac/detail/add|update/*). */
  public static final String DETAIL_RETURN_EDIT =
      "hasAnyRole('HBS_DTL_DATA_ENT','HBS_SCALE_ADMIN','HBS_MOF_SCALER',"
          + "'HBS_CLI_SCALER','HBS_CLI_SITE_ADMIN')";

  /** Approve detail corrections / replacements (/dac/detail/admin/*). */
  public static final String DETAIL_RETURN_APPROVE =
      "hasAnyRole('HBS_BILL_ADMIN','HBS_INV_CORR_APP')";

  /** Submit a file of detail returns (P505 — the XML intake). */
  public static final String XML_SUBMIT =
      "hasAnyRole('HBS_DTL_DATA_ENT','HBS_MOF_SCALER','HBS_SCALE_ADMIN',"
          + "'HBS_CLI_SITE_ADMIN','HBS_CLI_SCALER','HBS_SPC_SFTWR_VNDR','HBS_SPC_SUBM_AGNT')";

  /** Batch slips (P280/P283). */
  public static final String BATCH_SLIP_EDIT =
      "hasAnyRole('HBS_BILL_ADMIN','HBS_DTL_DATA_ENT','HBS_PROD_CTL',"
          + "'HBS_SCALE_ADMIN','HBS_SUMM_DATA_CTL')";

  /** Processing parameters, alert banner, printer names (/dac/srt/admin/*, P038). */
  public static final String PRODUCTION_CONTROL = "hasRole('HBS_PROD_CTL')";

  // ── Stratum Planner tab (/smp/*) ──────────────────────────────────
  public static final String SAMPLING_VIEW = "hasAnyRole(" + MINISTRY_AND_CLIENT + ")";
  public static final String SAMPLING_ADMIN = "hasAnyRole('HBS_SMP_ADMIN','HBS_CLI_SMP_ADMIN')";

  // ── Rating tab (/plu/*) ───────────────────────────────────────────
  public static final String RATING_VIEW = "hasAnyRole(" + MINISTRY_AND_CLIENT + ")";
  /** Override rates + district default rates (P200/P210 — ministry only). */
  public static final String RATING_MINISTRY_VIEW = MINISTRY;
  public static final String RATE_ADMIN = "hasRole('HBS_RATE_ADMIN')";
  public static final String WASTE_RATE_ADMIN = "hasRole('HBS_WRA_ADMIN')";

  // ── Billing tab (/ivs/*) ──────────────────────────────────────────
  public static final String BILLING_VIEW = "hasAnyRole(" + MINISTRY_AND_CLIENT + ")";
  /** Delivery + mark holder profiles, invoice admin (/ivs/admin/*). */
  public static final String BILLING_ADMIN = "hasAnyRole('HBS_BILL_ADMIN','HBS_SUPER_MGR')";
  /** Final bills (P831–P838). */
  public static final String FINAL_BILL_ADMIN = "hasAnyRole('HBS_BILL_ADMIN','HBS_SUPER_MGR')";
  /** Field-scaled decks (P840–P844). */
  public static final String FIELD_SCALED_DECKS =
      "hasAnyRole('HBS_BILL_ADMIN','HBS_SCALE_ADMIN','HBS_SUPER_MGR')";
  /** Force summarization / ratio computation (P811/P813/P823). */
  public static final String BILLING_FORCE = "hasRole('HBS_SUPER_MGR')";

  // ── Profiles (/cpm/*) ─────────────────────────────────────────────
  public static final String PROFILES_VIEW = "hasAnyRole(" + MINISTRY_AND_CLIENT + ")";
  public static final String SCALE_SITE_ADMIN = "hasRole('HBS_SCALE_ADMIN')";
  public static final String PROFILE_BILLING_ADMIN = "hasRole('HBS_BILL_ADMIN')";
  /** Scaler profiles: scale admin for anyone, a client scaler for their own. */
  public static final String SCALER_PROFILE_EDIT = "hasAnyRole('HBS_SCALE_ADMIN','HBS_CLI_SCALER')";

  // ── Scale Control tab (/opq/audit/*) ──────────────────────────────
  public static final String SCALE_CONTROL_VIEW = "hasAnyRole(" + MINISTRY_AND_CLIENT + ")";
  public static final String SCALE_CONTROL_MINISTRY_VIEW = MINISTRY;
  public static final String SCALE_CONTROL_ADMIN = "hasRole('HBS_SCALE_ADMIN')";
  /** Comment on / resolve scale anomalies (P911). */
  public static final String ANOMALY_EDIT =
      "hasAnyRole('HBS_SCALE_ADMIN','HBS_CLI_SCALER','HBS_CLI_SITE_ADMIN')";
  /** Software-use registry (P991–P993). */
  public static final String SOFTWARE_USE_VIEW =
      "hasAnyRole(" + MINISTRY_AND_CLIENT + ",'HBS_SPC_SFTWR_VNDR')";

  // ── Administration ────────────────────────────────────────────────
  /** User data domains — district / scale site / client location (replaces User Services). */
  public static final String USER_ADMIN =
      "hasAnyRole('HBS_MOF_ADMIN','HBS_MOF_MGR','HBS_CLI_ADMIN','HBS_SPC_ADMIN')";
  /** Reference-data cache (/cache/*). */
  public static final String CACHE_ADMIN = "hasRole('HBS_PROD_CTL')";
}
