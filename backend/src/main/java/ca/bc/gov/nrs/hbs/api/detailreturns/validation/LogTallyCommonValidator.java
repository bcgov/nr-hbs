package ca.bc.gov.nrs.hbs.api.detailreturns.validation;

import ca.bc.gov.nrs.hbs.api.detailreturns.model.DetailDocument;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.DocType;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.LogLine;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.Row;
import ca.bc.gov.nrs.hbs.api.detailreturns.state.Status;
import ca.bc.gov.nrs.hbs.api.detailreturns.validation.CrossReferenceEdit.CheckScale;
import ca.bc.gov.nrs.hbs.api.detailreturns.validation.CrossReferenceEdit.LogTallyCommon.Log;
import ca.bc.gov.nrs.hbs.api.detailreturns.validation.TallyLookups.ScaleGradeRef;
import ca.bc.gov.nrs.hbs.api.detailreturns.validation.TallyLookups.SpeciesProductGradeRef;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Edits shared by the log, sample and SFP tallies (port of
 * {@code $E/dac/detail/control/LogTallyCommonValidator}): originating site and
 * original scaler inputs, species/product/grade cross references (with
 * {@link ScaledLogXref}), log dimensions, check-scale originals, log count.
 *
 * <p>Line column mapping (legacy {@code LogDetail} getters): LOG_TALLY_DETAIL /
 * SAMPLE_TALLY_DETAIL have {@code length/top/butt} and no INPUT_PRODUCT (the
 * legacy constructor set it to {@link #PRODUCT}); SFP_LOG_DETAIL has
 * {@code prod_log_length/prod_log_top/prod_log_butt} and {@code input_product}.
 */
public abstract class LogTallyCommonValidator extends DetailValidator {

  /**
   * {@code LogTallyCommon.Product.LOG} = {@code SpeciesProductGrade.Product.LOG}:
   * one space ("this may change at some time").
   */
  public static final String PRODUCT = " ";
  public static final int TOP_BUTT_UPPER_LIMIT = 199;

  /** {@code DacConstants.INTERIOR_CB_GRADES} (BT 16523). */
  static final Set<String> INTERIOR_CB_GRADES = Set.of("7", "8");

  protected final TallyLookups tally;

  protected LogTallyCommonValidator(DetailDocument document, ValidationContext ctx, TallyLookups tally) {
    super(document, ctx);
    this.tally = tally;
  }

  /** OSS01; copies INPUT_ORIG_REDTAG_SCL_SITE to ORIG_REDTAG_SCALE_SITE_ID_NMBR. */
  protected void validateInputOriginatingScaleSite() {
    String input = v.str("input_orig_redtag_scl_site");
    if (isValidScaleSite(input)) {
      v.set("orig_redtag_scale_site_id_nmbr", input);
    } else {
      errors.add(scalingError(CrossReferenceEdit.LogTallyCommon.ORIGINATING_SACLE_SITE_XREF));
    }
  }

  /** OSL01; copies INPUT_ORIG_CHK_SCL_SCALER_LIC to ORIG_CHK_SCL_LICENSE_NUMBER (type S). */
  protected void validateInputOriginalScalerLicense() {
    String input = v.str("input_orig_chk_scl_scaler_lic");
    if (isValidScalerLicense(input)) {
      v.set("orig_chk_scl_license_number", input);
      v.set("orig_chk_scl_quant_lic_type_cd", SCALE_LICENSE_CODE);
    } else {
      errors.add(scalingError(CrossReferenceEdit.LogTallyCommon.ORIGINAL_SCALER_LICENSE_XREF));
    }
  }

  /**
   * {@code validateInputSpeciesProductGrade} (BT 7012): uses the scale codes
   * when all three are set, the input codes otherwise; SP01 not on file, SP02
   * not effective on the scale date and, with a timber mark, CG01/IG01 grade
   * not in the mark's coastal/interior schedule, CG02/IG02 not effective. A
   * found combination is copied to the scale codes (product {@link #PRODUCT},
   * or the input product on an SFP line).
   *
   * <p>Legacy quirk kept: on an SFP line the scale product is overwritten with
   * INPUT_PRODUCT even when the scale codes were used.
   *
   * @return the error code logged for this line, or null (legacy returns the
   *         errorType so each SPG error is logged once per tally)
   */
  protected String validateInputSpeciesProductGrade(LogLine log, Date scaleDate, String timberMark,
                                                    List<String> loggedErrorList, String gradeScheduleType) {
    Row l = log.row();
    String errorType = null;
    String species;
    String grade;
    String product;
    if (l.str("scale_species_code") == null || l.str("scale_product_code") == null
        || l.str("scale_grade_code") == null) {
      species = l.str("input_species");
      grade = l.str("input_grade");
      product = inputProduct(log);
    } else {
      species = l.str("scale_species_code");
      grade = l.str("scale_grade_code");
      product = l.str("scale_product_code");
    }
    if (ScaledLogXref.isInputSpeciesProductGradeValidationNeeded(species, product, grade)) {
      SpeciesProductGradeRef spg = ScaledLogXref.getSpeciesProductGrade(tally, species, product, grade);
      if (spg != null) {
        l.set("scale_product_code", isSfpLine() ? inputProduct(log) : PRODUCT);
        l.set("scale_grade_code", grade);
        l.set("scale_species_code", species);
        if (!ScaledLogXref.isSpeciesProductGradeEffective(scaleDate, spg)) {
          errorType = logSpgError(log, loggedErrorList, Log.SPECIES_PRODUCT_GRADE_NOT_EFFECT);
        } else if (timberMark != null) {
          ScaleGradeRef sub = ScaledLogXref.findGradeSubInfo(tally, l.str("scale_grade_code"), gradeScheduleType);
          if (sub == null) {
            if (COASTAL_SCHEDULE.equalsIgnoreCase(gradeScheduleType)) {
              errorType = logSpgError(log, loggedErrorList, Log.TIMBER_MARK_COASTAL_GRADE);
            } else if (INTERIOR_SCHEDULE.equalsIgnoreCase(gradeScheduleType)) {
              errorType = logSpgError(log, loggedErrorList, Log.TIMBER_MARK_INTERIOR_GRADE);
            }
          } else if (!ScaledLogXref.insideDateRange(scaleDate, sub.effectiveDate(), sub.expiryDate())) {
            if (COASTAL_SCHEDULE.equalsIgnoreCase(gradeScheduleType)) {
              errorType = logSpgError(log, loggedErrorList, Log.TIMBER_MARK_COASTAL_GRADE_NOT_EFFECT);
            } else if (INTERIOR_SCHEDULE.equalsIgnoreCase(gradeScheduleType)) {
              errorType = logSpgError(log, loggedErrorList, Log.TIMBER_MARK_INTERIOR_GRADE_NOT_EFFECT);
            }
          }
        }
      } else {
        errorType = logSpgError(log, loggedErrorList, Log.SPECIES_PRODUCT_GRADE_NOT_EXIT);
      }
    }
    return errorType;
  }

  /** GR01 when the input species/product/grade combination is not on file. */
  protected void validateSpeciesProductGrade(LogLine log) {
    Row l = log.row();
    if (!isValidSpeciesProductGrade(l.str("input_species"), inputProduct(log), l.str("input_grade"))) {
      errors.add(logError(log, Log.SPECIES_PRODUCT_GRADE));
    }
  }

  /** {@code isValidSpeciesProductGrade}: the combination exists (dates not checked). */
  public boolean isValidSpeciesProductGrade(String species, String product, String grade) {
    return tally.speciesProductGrade(product, grade, species).isPresent();
  }

  /** LT01: length must be &gt; 0 and &lt; 100 (legacy NPE on null; here a null length fails). */
  protected void validateLength(LogLine log) {
    if (!isValidLength(log)) {
      errors.add(logError(log, Log.LENGTH));
    }
  }

  public boolean isValidLength(LogLine log) {
    BigDecimal length = length(log);
    if (length == null) return false;
    float f = length.floatValue();
    return !(f <= 0.0 || f >= 100.0);
  }

  /** TP01 top &lt;= 0; TPMax top &gt; {@link #TOP_BUTT_UPPER_LIMIT}. */
  protected void validateTop(LogLine log) {
    Integer top = intValue(top(log));
    if (top == null || top <= 0) {
      errors.add(logError(log, Log.TOP));
    }
    if (top != null && top > TOP_BUTT_UPPER_LIMIT) {
      errors.add(logError(log, Log.TOP_MAX));
    }
  }

  /** BT01 butt &lt;= 0; BTMax butt &gt; {@link #TOP_BUTT_UPPER_LIMIT}. */
  protected void validateButt(LogLine log) {
    Integer butt = intValue(butt(log));
    if (butt == null || butt <= 0) {
      errors.add(logError(log, Log.BUTT));
    }
    if (butt != null && butt > TOP_BUTT_UPPER_LIMIT) {
      errors.add(logError(log, Log.BUTT_MAX));
    }
  }

  /** ISB789: an interior cruise-based grade (7, 8) on the line. */
  protected void validateGrade(LogLine log) {
    String grade = log.row().str("scale_grade_code");
    if (grade != null && INTERIOR_CB_GRADES.contains(grade)) { // Arrays.asList.contains(null) is false
      errors.add(logError(log, HbsEditError.ISB789));
    }
  }

  // ── check scale ───────────────────────────────────────────────────

  /**
   * {@code getOriginalDocumentByDDN}: the active version of ORIGINAL_DDN; sets
   * ORIGINAL_DDV. CHKS11 when the document has no active version; null without
   * an error when the document is unknown or has several active versions
   * (legacy swallowed those DetailScaleDocumentExceptions).
   */
  private DetailDocument getOriginalDocumentByDdn() {
    String ddn = v.str("original_ddn");
    if (!store.exists(ddn)) {
      return null;
    }
    Optional<DetailDocument> found;
    try {
      found = store.findActive(ddn);
    } catch (IllegalStateException multipleActive) {
      return null;
    }
    if (found.isEmpty()) {
      errors.add(scalingError(CheckScale.NO_ACTIVE_ORIGINAL));
      return null;
    }
    v.set("original_ddv", found.get().version().number());
    return found.get();
  }

  /** CHKS12 / CHKS13: the original's primary scaler and return number must match the check scale's. */
  protected DetailDocument validateOriginalRetrievedByOriginalDDN() {
    DetailDocument original = getOriginalDocumentByDdn();
    if (original != null) {
      boolean mismatch = false;
      Row o = original.version().row();
      // legacy: getOrigChkSclLicenseNumber().equals(...) (NPE when null)
      if (!Objects.equals(v.str("orig_chk_scl_license_number"), o.str("primary_license_number"))) {
        errors.add(scalingError(CheckScale.SCALER_LICENSE));
        mismatch = true;
      }
      if (!Objects.equals(v.lng("orig_chk_scl_return_number"), o.lng("return_number"))) {
        errors.add(scalingError(CheckScale.RETURN_NUMBER));
        mismatch = true;
      }
      original = mismatch ? null : original;
    }
    return original;
  }

  /**
   * {@code DetailManagerBean.findActiveDocumentByScalerAndReturnNumber}: CHKS01
   * none, CHKS02 several (ORIGINAL_DDV cleared); otherwise sets ORIGINAL_DDN/DDV.
   */
  protected DetailDocument validateOriginalRetrievedByOriginalScalerLicenseAndReturnNumber() {
    List<String> ddns = tally.activeOriginalsByScalerAndReturnNumber(document.type(),
        v.str("orig_chk_scl_license_number"), v.lng("orig_chk_scl_return_number"), v.date("scale_date"));
    if (ddns.size() > 1) {
      errors.add(scalingError(CheckScale.MULTIPLE_ORIGINALS));
      v.set("original_ddv", null);
      return null;
    }
    Optional<DetailDocument> original = ddns.isEmpty() ? Optional.empty() : store.findActive(ddns.get(0));
    if (original.isEmpty()) {
      errors.add(scalingError(CheckScale.NO_ORIGINAL));
      v.set("original_ddv", null);
      return null;
    }
    v.set("original_ddn", original.get().ddn());
    v.set("original_ddv", original.get().version().number());
    return original.get();
  }

  protected abstract DetailDocument validateAgainstOriginal(DetailDocument original);

  /**
   * {@code validateOriginal}: find the original (by ORIGINAL_DDN, else by
   * scaler licence + return number); CHKS00 when the check scaler is the
   * original scaler, otherwise the type's edits against the original when the
   * check replaces it.
   */
  protected void validateOriginal() {
    DetailDocument original;
    if (!empty(v.str("original_ddn"))) {
      original = validateOriginalRetrievedByOriginalDDN();
    } else {
      original = validateOriginalRetrievedByOriginalScalerLicenseAndReturnNumber();
    }
    String primary = v.str("primary_license_number");
    if (original != null && !empty(primary)) {
      if (primary.equals(v.str("orig_chk_scl_license_number"))) {
        errors.add(scalingError(CheckScale.EQUAL_LICENCENO));
      } else if (Boolean.TRUE.equals(v.yes("check_replaces_original_ind"))) {
        validateAgainstOriginal(original);
      }
    }
  }

  /** LC01 (Issue 952): LOG_COUNT must equal the number of log lines. */
  protected final void validateLogCount() {
    Long count = v.lng("log_count");
    if (count != null && count != version.logs().size()) {
      errors.add(scalingError(CrossReferenceEdit.LogTallyCommon.LOG_COUNT));
    }
  }

  /** The original is OIP / RCP / RNP (BT 7867) with a RDY active version. */
  protected boolean validOriginalStateCombination(DetailDocument activeDocument) {
    String documentState = activeDocument.state().value();
    String versionState = activeDocument.version().state().value();
    return (Status.Document.ORIGINAL_IN_PROGRESS.equals(documentState)
        || Status.Document.REPLACE_WITH_CHANGES_IN_PROGRESS.equals(documentState)
        || Status.Document.REPLACE_NO_CHANGES_IN_PROGRESS.equals(documentState))
        && Status.Version.READY.equals(versionState);
  }

  protected boolean isCheckScale() {
    return CHECK_SCALE.equals(v.str("scale_event_type_code"));
  }

  @Override
  public void copyInputFieldsAndValidate() {
    if (isEditApplicable()) {
      super.copyInputFieldsAndValidate();
    }
  }

  /** {@code logSPGError}: log each SPG error type once per tally. */
  public String logSpgError(LogLine log, List<String> loggedErrorList, String errorType) {
    if (!loggedErrorList.contains(errorType)) {
      errors.add(logError(log, errorType));
      return errorType;
    }
    return null;
  }

  /**
   * {@code ScaledLogXrefValidator.getGradeScheduleType}: "" without a mark or a
   * scale date, otherwise {@link ReferenceData#gradeScheduleType}.
   */
  protected String gradeScheduleType(String timberMark, Date scaleDate) {
    if (empty(timberMark) || scaleDate == null) {
      return "";
    }
    return ref.gradeScheduleType(timberMark, scaleDate);
  }

  // ── line columns (legacy LogDetail getters) ───────────────────────

  protected boolean isSfpLine() {
    return document.type() == DocType.SFP_TALLY;
  }

  /** {@code getInputProduct}: INPUT_PRODUCT on SFP lines, {@link #PRODUCT} on log / sample lines. */
  protected String inputProduct(LogLine log) {
    return isSfpLine() ? log.row().str("input_product") : PRODUCT;
  }

  protected BigDecimal length(LogLine log) {
    return log.row().num(isSfpLine() ? "prod_log_length" : "length");
  }

  protected BigDecimal top(LogLine log) {
    return log.row().num(isSfpLine() ? "prod_log_top" : "top");
  }

  protected BigDecimal butt(LogLine log) {
    return log.row().num(isSfpLine() ? "prod_log_butt" : "butt");
  }

  /** {@code getNmvInd}: SFP lines have no NMV_IND column and the legacy constructor set it to TRUE. */
  protected Boolean nmvInd(LogLine log) {
    if (isSfpLine() && !log.row().has("nmv_ind")) {
      return Boolean.TRUE;
    }
    return log.row().yes("nmv_ind");
  }

  private static Integer intValue(BigDecimal b) {
    return b == null ? null : b.intValue();
  }
}
