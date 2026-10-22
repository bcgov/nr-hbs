package ca.bc.gov.nrs.hbs.api.detailreturns.validation.form;

import ca.bc.gov.nrs.hbs.api.detailreturns.DetailAuthority.Caller;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.DocType;
import java.time.LocalDate;
import java.util.Date;

/**
 * The lookups the request-level form checks ({@link DetailFormValidator}) make:
 * the cross references of {@code $V/HBSValidator} / {@code HBSValidatorHelper} /
 * {@code DacHelper} / {@code InvoiceHelper} and the caller's identity and
 * data domains. Production: {@link JdbcFormLookups}; tests stub it.
 */
public interface FormLookups {

  /** {@code HBSValidatorHelper.isExistingTimberMark} ({@code InvoiceHelper.findMark}: TIMBER_MARK + MARK_BILLED_CLI). */
  boolean timberMarkExists(String timberMark);

  /** {@code InvoiceHelper.findScaleSite} found the site. */
  boolean scaleSiteExists(String scaleSiteId);

  /** {@code DacHelper.isValidLicence}: a QUANT_LICENSE of type S or R. */
  boolean scalerLicenceExists(String licenseNumber);

  /** {@code DacHelper.isValidPopStratYear} / {@code WeighSlipValidator.isBillableStratum}: the stratum is on file. */
  boolean stratumExists(String population, String stratum, Long samplingYear);

  /** {@code WeighSlipValidator.isBillableMark} (BT5884): known and not cash sale, cruise or area based. */
  boolean isBillableMark(String timberMark);

  /** {@code InvoiceHelper.isValidSPGCombination(species, product, grade, null, null)}. */
  boolean speciesProductGradeExists(String species, String product, String grade);

  /** {@code DacHelper.isActiveDetailDocumentVersion(ddn, type)}: exactly one active version. */
  boolean activeDocumentExists(String ddn, DocType type);

  /** {@code DacHelper.findDetailSubmitters(site, type, date)} is not empty. */
  boolean hasDetailSiteProfile(String scaleSiteId, DocType type, Date scaleDate);

  /**
   * {@code HBSErrors2Tag.getHbsEditError}: HBS_EDIT_ERR_MESSAGE_CODE.DESCRIPTION
   * for an {@code MSGCODE:<code>,<category>} message; null when not on file.
   */
  String editErrorDescription(String code, String category);

  /** The caller ({@code UssHelper.getUserFromSession}): HBS user id and roles. */
  Caller caller();

  /** {@code DetailScaleDocumentHelper.isScaleSiteAssociatedWithOrgUnit(site, user)}. */
  boolean isScaleSiteAssociatedWithOrgUnit(String scaleSiteId, Caller caller);

  /** {@code DetailScaleDocumentHelper.hasScaleSiteAuthorization(site, user)}. */
  boolean hasScaleSiteAuthorization(String scaleSiteId, Caller caller);

  /** {@code DetailScaleDocumentHelper.hasScalerAuthorization(primary, secondary, signing, original, user)}. */
  boolean hasScalerAuthorization(Caller caller, String... licences);

  /** "Today" (Pacific) for the seven-year limit ({@code DacValidator.compareForYearLimit}). */
  LocalDate today();
}
