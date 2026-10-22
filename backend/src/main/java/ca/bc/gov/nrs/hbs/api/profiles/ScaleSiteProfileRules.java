package ca.bc.gov.nrs.hbs.api.profiles;

import ca.bc.gov.nrs.hbs.api.security.HbsRoles;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Rules shared by P322 (update) and P323 (add) of the trading partner
 * submitter ("scale site profile") screens.
 */
@Component
class ScaleSiteProfileRules {

  /** error.scales.invalid2 */
  static final String SITE_NOT_FOUND = "The scale site entered does not exist.";
  /** errors.scalesite-user-access2 */
  static final String SITE_NOT_AUTHORIZED = "User not authorized to add/edit scale site profile for this scale site.";
  /** error.effectivedate.invalid */
  static final String EFFECTIVE_IN_EXISTING_RANGE = "Effective date cannot occur in an existing scale site profile range.";
  /** error.client.exists */
  static final String DETAIL_CLIENT_NOT_BLANK = "Client Number and Location must be blank when inserting a detail record";
  /** error.clientorlocation.invalid */
  static final String TRADING_PARTNER_INVALID = "Client or client location is invalid.";

  private final ProfileReads reads;
  private final ProfileCaller caller;

  ScaleSiteProfileRules(ProfileReads reads, ProfileCaller caller) {
    this.reads = reads;
    this.caller = caller;
  }

  /**
   * {@code DacHelper.isScaleSiteAssociatedWithOrgUnit}: a CLI_SITE_ADMIN needs
   * the site in their site data domain; an HBS_SCALE_ADMIN needs the site's
   * org unit among their district data domains (when they have any, that
   * decides). Anyone else is refused.
   */
  boolean associatedWithCaller(String scaleSiteId, Map<String, Object> site) {
    if (scaleSiteId == null) return false;
    String userId = caller.userId();
    boolean access = false;
    if (caller.hasRole(HbsRoles.CLI_SITE_ADMIN) && reads.siteDataDomain(userId, scaleSiteId)) {
      access = true;
    }
    if (caller.hasRole(HbsRoles.SCALE_ADMIN) && site != null) {
      List<Long> districts = reads.districtDataDomains(userId);
      if (!districts.isEmpty()) {
        Object orgUnit = site.get("org_unit_no");
        access = orgUnit != null && districts.stream()
            .anyMatch(d -> d != null && d.longValue() == ((Number) orgUnit).longValue());
      }
    }
    return access;
  }

  /** {@code validateEffectiveDate} / {@code validateExpiryDate} of P322/P323 (year ≥ 1900, real date). */
  static void checkDate(ProfileRules.Problems problems, String field, LocalDate value, boolean present) {
    if (!present || value == null) {
      problems.add(field, ProfileRules.DATE_INVALID);
    } else if (value.getYear() < ProfileRules.HBS_LOW_YEAR) {
      problems.add(field, ProfileRules.YEAR_BEFORE_1900);
    }
  }
}
