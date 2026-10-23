package ca.bc.gov.nrs.hbs.api.summaryreturns;

import static ca.bc.gov.nrs.hbs.api.security.HbsRoles.BILL_ADMIN;
import static ca.bc.gov.nrs.hbs.api.security.HbsRoles.CLI_CRUISE_ADMIN;
import static ca.bc.gov.nrs.hbs.api.security.HbsRoles.CLI_SCALER;
import static ca.bc.gov.nrs.hbs.api.security.HbsRoles.CLI_SITE_ADMIN;
import static ca.bc.gov.nrs.hbs.api.security.HbsRoles.MOF_SCALER;
import static ca.bc.gov.nrs.hbs.api.security.HbsRoles.SCALE_ADMIN;
import static ca.bc.gov.nrs.hbs.api.security.HbsRoles.SPC_SUBM_AGNT;
import static ca.bc.gov.nrs.hbs.api.security.HbsRoles.SUMM_DATA_CORR;
import static ca.bc.gov.nrs.hbs.api.security.HbsRoles.SUMM_DATA_CTL;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.ScaleReturnState.HELD;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.ScaleReturnState.INCOMPLETE;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.ScaleReturnState.ORIGINAL_IN_PROGRESS;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.ScaleReturnState.CANCEL_WITH_REPLACE_WITH_CHANGES;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Who may touch which summary return. Ports the per-user checks the legacy screens made
 * before showing a button or the edit page:
 * <ul>
 *   <li>{@code DacHelper.hasScaleSiteAuthorization / isScaleSiteAssociatedWithOrgUnit},
 *       {@code hasDistrictAuthorization}, {@code hasScalerAuthorization / isUserValidForLicence}
 *       (the HBS_USER_* data domains and scaler auth keys);</li>
 *   <li>{@code SummaryScaleReturnHelper.isEditablePieceScaleReturn / isEditableScaleReturn}
 *       (view tier) — who gets the P053/P103/P153 edit page;</li>
 *   <li>the industry fence every summary-return query applies (the active client is the
 *       return's Bill To, Copy To, mark holder, site owner or stratum owner).</li>
 * </ul>
 * Lookups are overridable for tests.
 */
@Component
public class SummaryReturnAccess {

  /** {@code errors.no_access_right}-style refusal for a record outside the caller's client. */
  public static final String NOT_YOUR_RETURN = "You do not have access to this summary scale return.";

  private final NamedParameterJdbcTemplate jdbc;

  public SummaryReturnAccess(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  // ── Data-domain lookups ──────────────────────────────────────────────────

  /** HBS_USER_SITE_DATA_DOMAIN has the (user, site) pair ({@code UssHelper2.findSiteDataDomainByPrimaryKey}). */
  public boolean siteInUserDomain(String userId, String scaleSite) {
    return exists("SELECT COUNT(*) FROM hbs_user_site_data_domain WHERE user_id = :u AND scale_site_id_nmbr = :s",
        Map.of("u", userId, "s", scaleSite));
  }

  /** The scale site's district is one of the user's HBS_USER_DIST_DATA_DOMAIN districts (BT 8492). */
  public boolean siteInUserDistricts(String userId, String scaleSite) {
    return exists("SELECT COUNT(*) FROM scale_site ss JOIN hbs_user_dist_data_domain d ON d.org_unit_no = ss.org_unit_no"
        + " WHERE ss.scale_site_id_nmbr = :s AND d.user_id = :u", Map.of("u", userId, "s", scaleSite));
  }

  /** The mark's forest district is one of the user's districts ({@code DacHelper.hasDistrictAuthorization}). */
  public boolean markInUserDistricts(String userId, String timberMark) {
    return exists("SELECT COUNT(*) FROM mark_billed_cli m JOIN hbs_user_dist_data_domain d ON d.org_unit_no = m.forest_district"
        + " WHERE m.timber_mark = :m AND d.user_id = :u", Map.of("u", userId, "m", timberMark));
  }

  /**
   * {@code isUserValidForLicence} → {@code HbsScalerAuthKeyQuery.selectByCriteria1}: the user's latest
   * scaler auth key belongs to a client holding the licence (the scale date is passed but not used).
   */
  public boolean licenceInUserAuthKeys(String userId, String licence) {
    return exists("SELECT COUNT(*) FROM hbs_scaler_auth_key k WHERE k.user_id = :u"
        + " AND k.expiry_date = (SELECT MAX(x.expiry_date) FROM hbs_scaler_auth_key x WHERE x.user_id = :u)"
        + " AND k.client_number IN (SELECT q.client_number FROM quant_license q WHERE q.license_number = :l)",
        Map.of("u", userId, "l", licence));
  }

  /** Forest-client numbers the return is visible to under the industry fence (catalog {@code OWN_FENCE}). */
  public java.util.Set<String> associatedClients(SummaryReturn ret) {
    Row v = ret.version();
    java.util.Set<String> clients = new java.util.HashSet<>();
    Stream.of(v.str("client_number_paid_by"), v.str("client_number_copied_to")).filter(Objects::nonNull).forEach(clients::add);
    markHolder(v.str("timber_mark")).ifPresent(clients::add);
    siteOwner(v.str("scale_site_id_nmbr")).ifPresent(clients::add);
    if (v.get("population_number") != null) {
      stratumOwner(v.str("population_number"), v.str("stratum_number"), v.lng("sampling_year")).ifPresent(clients::add);
    }
    return clients;
  }

  public Optional<String> markHolder(String timberMark) {
    if (timberMark == null) return Optional.empty();
    return first("SELECT client_number FROM mark_billed_cli WHERE timber_mark = :m", Map.of("m", timberMark));
  }

  public Optional<String> siteOwner(String scaleSite) {
    if (scaleSite == null) return Optional.empty();
    return first("SELECT owner_cli_number FROM scale_site WHERE scale_site_id_nmbr = :s", Map.of("s", scaleSite));
  }

  public Optional<String> stratumOwner(String population, String stratum, Long year) {
    if (population == null || stratum == null || year == null) return Optional.empty();
    return first("SELECT client_number FROM stratum WHERE population_number = :p AND stratum_number = :s"
        + " AND sampling_year = :y", Map.of("p", population, "s", stratum, "y", year));
  }

  private boolean exists(String sql, Map<String, ?> params) {
    Long n = jdbc.queryForObject(sql, new MapSqlParameterSource(params), Long.class);
    return n != null && n > 0;
  }

  private Optional<String> first(String sql, Map<String, ?> params) {
    return jdbc.queryForList(sql, new MapSqlParameterSource(params), String.class).stream()
        .filter(Objects::nonNull).findFirst();
  }

  // ── Legacy authorization rules ───────────────────────────────────────────

  /**
   * {@code DacHelper.hasScaleSiteAuthorization}: CLI_SITE_ADMIN with the site in its site domain,
   * or HBS_SCALE_ADMIN with the site's district in its district domain.
   */
  public boolean hasScaleSiteAuthorization(SummaryCaller caller, String scaleSite) {
    if (scaleSite == null || scaleSite.isEmpty()) return false; // StringUtil.empty: no trim
    if (caller.has(CLI_SITE_ADMIN)) return siteInUserDomain(caller.userId(), scaleSite);
    if (caller.has(SCALE_ADMIN)) return siteInUserDistricts(caller.userId(), scaleSite);
    return false;
  }

  /** {@code DacHelper.hasScalerAuthorization(licence, user, date)}: CLI/MOF scaler holding an auth key for it. */
  public boolean hasScalerAuthorization(SummaryCaller caller, String licence) {
    if (licence == null || licence.isEmpty()) return false; // StringUtil.empty: no trim
    return caller.hasAny(CLI_SCALER, MOF_SCALER) && licenceInUserAuthKeys(caller.userId(), licence);
  }

  /** {@code DacHelper.hasDistrictAuthorization(timberMark, user)}. */
  public boolean hasDistrictAuthorization(SummaryCaller caller, String timberMark) {
    return timberMark != null && markInUserDistricts(caller.userId(), timberMark);
  }

  /**
   * {@code SummaryScaleReturnHelper.isEditablePieceScaleReturn} (view tier): whether the caller gets
   * the update page (P053) for a piece return in this state.
   */
  public boolean isEditablePiece(SummaryCaller caller, SummaryReturn ret) {
    String pssmry = ret.pssmryType();
    if (SummaryReturnRules.PSSMRY_DEFAULT_TYPE.equals(pssmry) || SummaryReturnRules.PSSMRY_SFP_TYPE.equals(pssmry)
        || ret.generated()) {
      return isEditable(caller, ret);
    }
    String doc = ret.docState();
    String ver = ret.versionState();
    boolean oip = ORIGINAL_IN_PROGRESS.equals(doc);
    if (caller.has(CLI_CRUISE_ADMIN)) {
      return oip && (INCOMPLETE.equals(ver) || HELD.equals(ver)); // BT15212
    }
    if (INCOMPLETE.equals(ver) && oip && caller.has(SPC_SUBM_AGNT)) {
      return true;
    }
    if (INCOMPLETE.equals(ver) || HELD.equals(ver)) {
      if (caller.has(SUMM_DATA_CORR)) return true;
      Row v = ret.version();
      if (oip && hasScaleSiteAuthorization(caller, v.str("scale_site_id_nmbr"))) return true;
      if (oip && hasScalerAuthorization(caller, v.str("license_number"))) return true;
      return CANCEL_WITH_REPLACE_WITH_CHANGES.equals(doc) && caller.has(BILL_ADMIN)
          && hasDistrictAuthorization(caller, v.str("timber_mark"));
    }
    return false;
  }

  /**
   * {@code SummaryScaleReturnHelper.isEditableScaleReturn}: never generated returns; SUMM_DATA_CTL
   * may edit INC, SUMM_DATA_CORR INC or HLD.
   */
  public boolean isEditable(SummaryCaller caller, SummaryReturn ret) {
    if (ret.generated()) return false;
    String ver = ret.versionState();
    if (INCOMPLETE.equals(ver) && caller.has(SUMM_DATA_CTL)) return true;
    return (INCOMPLETE.equals(ver) || HELD.equals(ver)) && caller.has(SUMM_DATA_CORR);
  }

  /** The edit page rule for any type. */
  public boolean mayEdit(SummaryCaller caller, SummaryReturn ret) {
    return ret.type() == SummaryReturnType.PIECE ? isEditablePiece(caller, ret) : isEditable(caller, ret);
  }

  /** Industry callers only act on returns associated with their active client. */
  public void requireVisible(SummaryCaller caller, SummaryReturn ret) {
    if (caller.ministry()) return;
    if (caller.clientNumber() == null || caller.clientNumber().isBlank()
        || !associatedClients(ret).contains(caller.clientNumber())) {
      throw new AccessDeniedException(NOT_YOUR_RETURN);
    }
  }
}
