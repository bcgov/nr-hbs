package ca.bc.gov.nrs.hbs.api.detailreturns;

import ca.bc.gov.nrs.hbs.api.detailreturns.model.DetailDocument;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.DocType;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.Row;
import ca.bc.gov.nrs.hbs.api.detailreturns.state.DocumentState;
import ca.bc.gov.nrs.hbs.api.detailreturns.state.Status;
import ca.bc.gov.nrs.hbs.api.detailreturns.state.VersionState;
import ca.bc.gov.nrs.hbs.api.security.HbsRoles;
import ca.bc.gov.nrs.hbs.api.util.RequestUtil;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Who may press which detail-return button, ported from the legacy view tier:
 * <ul>
 *   <li>{@code $V/dac/detail/FunctionsComparator.isFieldAvailable} — whether
 *       the button applies to the document/version state ({@link #isAvailable});</li>
 *   <li>{@code FunctionsComparator.isAuthorized} — the role / data-domain rule
 *       of each button;</li>
 *   <li>{@code $V/cpm/IsScaleSiteAssociatedWithOrgUnitTag} — the extra gate
 *       {@code Buttons.jsp} wraps every button in except Approve/Reject;</li>
 *   <li>{@code $V/dac/detail/DetailScaleDocumentHelper.isDetailDocumentEditable}
 *       — whether the entry page may save ({@link #isEditable}).</li>
 * </ul>
 * Known legacy quirks kept: FunctionsComparator never set its signing licence
 * (so the signing scaler never authorizes a button), and the tag lets through a
 * user who is neither site admin nor scale admin.
 */
@Component
public class DetailAuthority {

  /** {@code FunctionsComparator.Button}. */
  public enum Button {
    APPROVE_CHANGE, CANCEL, CANCEL_REPLACE, CLEAR_DIGITAL_SIGNATURE_FAILURE, DISCARD, HOLD, REEDIT, RELEASE,
    REPLACE, SAVE
  }

  /** The caller as the checks need it: legacy HBS user id and canonical roles. */
  public record Caller(String userId, Set<String> roles) {
    public boolean has(String role) {
      return roles.contains(role);
    }

    public static Caller current() {
      return new Caller(RequestUtil.getCurrentAuditUserId().toUpperCase(), RequestUtil.getCurrentRoles());
    }
  }

  private final UserDomains domains;

  public DetailAuthority(UserDomains domains) {
    this.domains = domains;
  }

  // ── availability (FunctionsComparator.isFieldAvailable) ───────────

  public static boolean isAvailable(Button button, DocumentState doc, VersionState v) {
    if (Status.Version.INVALID_STATE.equals(doc.value())) {
      return false;
    }
    return switch (button) {
      case HOLD, REEDIT -> (v.isHoldAllowed() && (doc.isOriginalInProgress() || doc.isReplaceWithChangesInProgress()
          || doc.isReplaceWithNoChangesInProgress())) || v.isNotToBeBilled(); // BT 10843
      case CANCEL, CANCEL_REPLACE -> doc.isCancelAllowed() && v.isCancelAllowed();
      case REPLACE -> v.isCancelled() && v.isActive();
      case RELEASE, SAVE -> v.isHeld() || ((doc.isOriginalInProgress() || doc.isCancelAndReplaceWithChangesInProgress()
          || doc.isReplaceWithChangesInProgress()) && v.isIncomplete());
      case APPROVE_CHANGE -> v.isAwaitingApproval();
      case CLEAR_DIGITAL_SIGNATURE_FAILURE -> v.isSignatureFailed();
      case DISCARD -> v.isDiscardAllowed() && (doc.isOriginalInProgress()
          || (doc.isReplaceWithChangesInProgress() && !v.isAwaitingApproval())
          || (doc.isCancelAndReplaceWithChangesInProgress() && !v.isAwaitingApproval() && !v.isReady()));
    };
  }

  // ── authorization (FunctionsComparator.isAuthorized + Buttons.jsp tag) ──

  /** Shown = available, authorized and (except Approve/Reject) passing the site tag. */
  public boolean isShown(Button button, DetailDocument doc, Caller caller) {
    if (!isAvailable(button, doc.state(), doc.version().state())) {
      return false;
    }
    if (!isAuthorized(button, doc, caller)) {
      return false;
    }
    if (button == Button.APPROVE_CHANGE) {
      return true;
    }
    // SFP Cancel / Cancel and Replace were DEV/TEST-only buttons (Buttons.jsp showInEnvironment)
    return siteTag(doc.version().row().str("scale_site_id_nmbr"), primaryLicence(doc), caller);
  }

  /** 403 with the legacy wording unless {@link #isShown}. */
  public void require(Button button, DetailDocument doc, Caller caller) {
    if (!isShown(button, doc, caller)) {
      throw new AccessDeniedException("hbs.no_access_right: " + button + " is not available to you for "
          + doc.ddn() + " version " + doc.version().number() + " (" + doc.state() + ")");
    }
  }

  boolean isAuthorized(Button button, DetailDocument doc, Caller caller) {
    Row v = doc.version().row();
    String site = v.str("scale_site_id_nmbr");
    // the comparator was built with primary, secondary and original check-scale licences, never the signing one
    String primary = primaryLicence(doc);
    String secondary = v.str("secondary_license_number");
    String original = v.str("orig_chk_scl_license_number");
    return switch (button) {
      case APPROVE_CHANGE -> hasDistrictAuthorization(v.str("timber_mark"), caller)
          && caller.has(HbsRoles.INV_CORR_APP);
      case CLEAR_DIGITAL_SIGNATURE_FAILURE -> hasScaleSiteAuthorization(site, caller)
          && caller.has(HbsRoles.SCALE_ADMIN);
      case HOLD, REEDIT, CANCEL, REPLACE, CANCEL_REPLACE, DISCARD -> hasScaleSiteAuthorization(site, caller)
          || hasScalerAuthorization(caller, primary, secondary, null, original);
      case RELEASE, SAVE -> hasScaleSiteAuthorization(site, caller)
          || hasScalerAuthorization(caller, primary, secondary, null, original)
          || caller.has(HbsRoles.DTL_DATA_ENT);
    };
  }

  /** {@code Buttons.jsp}: the arrival ledger's licence is LICENSE_NUMBER, departures have none. */
  static String primaryLicence(DetailDocument doc) {
    if (doc.type() == DocType.ARRIVAL_LEDGER) return doc.version().row().str("license_number");
    if (doc.type() == DocType.DEPARTURE_LEDGER) return null;
    return doc.version().row().str("primary_license_number");
  }

  /** {@code IsScaleSiteAssociatedWithOrgUnitTag.doStartTag} (BT 6131 / BT 8492). */
  boolean siteTag(String scaleSite, String primaryLicence, Caller caller) {
    if (empty(scaleSite)) {
      return false;
    }
    if (!(caller.has(HbsRoles.CLI_SITE_ADMIN) || caller.has(HbsRoles.SCALE_ADMIN))) {
      return true;
    }
    if (isScaleSiteAssociatedWithOrgUnit(scaleSite, caller)) {
      return true;
    }
    if (caller.has(HbsRoles.CLI_SCALER) || caller.has(HbsRoles.MOF_SCALER)) {
      if (!empty(primaryLicence)) {
        return hasScalerAuthorization(caller, primaryLicence, null, null, null);
      }
      return true;
    }
    return false;
  }

  /** {@code DacHelper.hasScaleSiteAuthorization}. */
  public boolean hasScaleSiteAuthorization(String scaleSite, Caller caller) {
    if (empty(scaleSite)) return false;
    if (caller.has(HbsRoles.CLI_SITE_ADMIN)) {
      return domains.hasSiteDomain(caller.userId(), scaleSite);
    }
    if (caller.has(HbsRoles.SCALE_ADMIN)) {
      return isScaleSiteAssociatedWithOrgUnit(scaleSite, caller);
    }
    return false;
  }

  /** {@code DacHelper.isScaleSiteAssociatedWithOrgUnit}: site domain (site admin) or district domain (scale admin). */
  public boolean isScaleSiteAssociatedWithOrgUnit(String scaleSite, Caller caller) {
    boolean access = false;
    if (!empty(scaleSite) && caller.has(HbsRoles.CLI_SITE_ADMIN)
        && domains.hasSiteDomain(caller.userId(), scaleSite)) {
      access = true;
    }
    if (!empty(scaleSite) && caller.has(HbsRoles.SCALE_ADMIN)) {
      Long orgUnit = domains.scaleSiteOrgUnit(scaleSite);
      access = orgUnit != null && domains.districtDomains(caller.userId()).contains(orgUnit);
    }
    return access;
  }

  /** {@code DetailScaleDocumentHelper.hasScalerAuthorization(primary, secondary, signing, original, user)}. */
  public boolean hasScalerAuthorization(Caller caller, String... licences) {
    if (!(caller.has(HbsRoles.CLI_SCALER) || caller.has(HbsRoles.MOF_SCALER))) {
      return false;
    }
    for (String licence : licences) {
      if (!empty(licence) && domains.hasScalerKeyForLicence(caller.userId(), licence)) {
        return true;
      }
    }
    return false;
  }

  /** {@code DacHelper.hasDistrictAuthorization}: the mark's forest district is in the user's district domain. */
  public boolean hasDistrictAuthorization(String timberMark, Caller caller) {
    if (empty(timberMark)) return false;
    Long district = domains.markDistrict(timberMark);
    return district != null && domains.districtDomains(caller.userId()).contains(district);
  }

  /**
   * {@code DetailScaleDocumentHelper.isDetailDocumentEditable}: INC for data entry /
   * submission agents, or INC/HLD with site or scaler authority (the arrival
   * ledger's licence is LICENSE_NUMBER; the original licence is checked for
   * log and sample tallies only).
   */
  public boolean isEditable(DetailDocument doc, Caller caller) {
    VersionState s = doc.version().state();
    Row v = doc.version().row();
    boolean editable = s.isIncomplete() && (caller.has(HbsRoles.DTL_DATA_ENT) || caller.has(HbsRoles.SPC_SUBM_AGNT));
    if (s.isIncomplete() || s.isHeld()) {
      String primary = doc.type() == DocType.ARRIVAL_LEDGER ? v.str("license_number")
          : v.str("primary_license_number");
      String original = doc.type() == DocType.LOG_TALLY || doc.type() == DocType.SAMPLE_LOG_TALLY
          ? v.str("orig_chk_scl_license_number") : null;
      if (hasScaleSiteAuthorization(v.str("scale_site_id_nmbr"), caller)
          || hasScalerAuthorization(caller, primary, v.str("secondary_license_number"),
              v.str("signing_license_number"), original)) {
        editable = true;
      }
    }
    return editable;
  }

  /** Every button the caller may press on this document (for the SPA). */
  public Set<Button> shown(DetailDocument doc, Caller caller) {
    Set<Button> out = EnumSet.noneOf(Button.class);
    for (Button b : Button.values()) {
      if (isShown(b, doc, caller)) out.add(b);
    }
    return out;
  }

  /** {@code StringUtil.empty(String)} (the tags and DacHelper never trimmed): null or "" only. */
  private static boolean empty(String s) {
    return s == null || s.isEmpty();
  }
}
