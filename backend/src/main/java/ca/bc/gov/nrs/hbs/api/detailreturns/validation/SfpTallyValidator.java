package ca.bc.gov.nrs.hbs.api.detailreturns.validation;

import ca.bc.gov.nrs.hbs.api.detailreturns.model.DetailDocument;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.LogLine;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.Row;
import ca.bc.gov.nrs.hbs.api.detailreturns.validation.CrossReferenceEdit.CheckScale;
import ca.bc.gov.nrs.hbs.api.detailreturns.validation.CrossReferenceEdit.SpecialForestProduct;
import ca.bc.gov.nrs.hbs.api.detailreturns.validation.ReferenceData.TimberMarkRef;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Edits of a special forest product tally (type F), ported from
 * {@code $E/dac/detail/control/SFPTallyValidator} on top of
 * {@link LogTallyCommonValidator}. Version columns are
 * SPECIAL_FOREST_PRODUCT_TALLY's, line columns SFP_LOG_DETAIL's. Every error
 * is category SCL (legacy {@code createScalingError} / {@code createLogError}).
 *
 * <p>SPECIAL_FOREST_PRODUCT_TALLY has no ORIGINAL_DDN / ORIGINAL_DDV columns:
 * legacy kept them as transient fields of the domain object, so a check scale
 * always looks its original up by scaler licence and return number and the
 * row carries the result only for this run (the writer ignores columns its
 * proc does not have). SFP_LOG_DETAIL has no TIMBER_MARK or NMV_IND either
 * (legacy: transient null mark, NMV always TRUE).
 */
public class SfpTallyValidator extends LogTallyCommonValidator {

  /** {@code LogTallyCommon.ScaleMethod}: log scale, piece count, prorated, stack. */
  public static final String SCALE_METHOD_LOG_SCALE = "L";
  public static final String SCALE_METHOD_PIECE_COUNT = "C";
  public static final String SCALE_METHOD_PRORATED = "P";
  public static final String SCALE_METHOD_STACK = "K";

  /** {@code TimberMark.BEACHCOMB1} / {@code UNREGULATED_BEACHCOMB2} / {@code UNREGULATED_BEACHCOMB3}. */
  static final Set<String> BEACHCOMB_MARKS =
      Set.of("BCOMB1", TimberMarkRef.UNREGULATED_BEACHCOMB2, TimberMarkRef.UNREGULATED_BEACHCOMB3);

  public SfpTallyValidator(DetailDocument document, ValidationContext ctx, TallyLookups tally) {
    super(document, ctx, tally);
  }

  /** {@code isBillableScaleReturn}: only a primary scale (PS) is billable. */
  @Override
  public boolean isBillableScaleReturn() {
    return PRIMARY_SCALE.equals(v.str("scale_event_type_code"));
  }

  /** {@code validate}. */
  @Override
  protected void validate() {
    validateVersion();
    validateLogs();
  }

  /** {@code validateAndCopyInputFields}. */
  @Override
  protected void validateAndCopyInputFields() {
    validateAndCopyVersionInputFields();
    validateAndCopyLogsInputFields();
  }

  /** (sic) {@code validatFieldScaleDeckId}: empty in legacy. */
  @Override
  protected void validatFieldScaleDeckId() {
  }

  // ── version edits ─────────────────────────────────────────────────

  /** {@code validateVersion}, in legacy order. */
  protected void validateVersion() {
    validateScaleMethod();
    if (!empty(v.str("scale_site_id_nmbr"))) {
      validateScaleSite();
    }
    if (!empty(v.str("timber_mark"))) {
      validateTimberMark();
    }
    // BT 11301
    if (v.yes("field_scale_ind") != null) {
      validateFieldScaleDeckId(v.yes("field_scale_ind"), v.str("field_scale_deck_id"),
          v.yes("original_version_ind"));
    }
    validateScaleDate();
    validateRate();
    if (v.lng("parcel_identifier") != null || v.lng("parcel_count") != null) {
      validateParcelCountAndParcelId();
    }
    if (!empty(v.str("signing_license_number"))) {
      validateSigningScalerLicense();
    }
    if (isCheckScale()) {
      validateOriginal();
    }
    validateLogCount();
    validateProratedPieceCount();
  }

  /** {@code validateProratedPieceCount}: PPC01 when SAMPLE_PIECE_COUNT is set and not &gt; 0. */
  protected void validateProratedPieceCount() {
    Long count = v.lng("sample_piece_count");
    if (count != null && !isValidProratedPieceCount(count)) {
      errors.add(scalingError(SpecialForestProduct.ProratedPieceCount.RANGE));
    }
  }

  public boolean isValidProratedPieceCount(Long samplePieceCount) {
    return samplePieceCount > 0;
  }

  /** {@code validateScaleMethod}: SM22 when HBS_SFP_SCALE_TYPE_CODE is not L, C, P or K. */
  protected void validateScaleMethod() {
    if (!isValidScaleMethod(v.str("hbs_sfp_scale_type_code"))) {
      errors.add(scalingError(SpecialForestProduct.SCALE_METHOD));
    }
  }

  /** {@code isValidScaleMethod}; legacy NPE'd on a null method (a required column), here it is invalid. */
  public boolean isValidScaleMethod(String scaleMethod) {
    return SCALE_METHOD_LOG_SCALE.equals(scaleMethod) || SCALE_METHOD_PIECE_COUNT.equals(scaleMethod)
        || SCALE_METHOD_PRORATED.equals(scaleMethod) || SCALE_METHOD_STACK.equals(scaleMethod);
  }

  /** {@code validateParcelCountAndParcelId}. */
  protected void validateParcelCountAndParcelId() {
    Long parcelId = v.lng("parcel_identifier");
    Long parcelCount = v.lng("parcel_count");
    validateParcelId(parcelId, parcelCount);
    validateParcelCount(parcelId, parcelCount);
  }

  /**
   * {@code validateAgainstOriginal}: the original of a replacing check scale
   * must not be a check scale (CHKS03) or already replaced (CHKS06) and must be
   * RDY under OIP/RCP/RNP (CHKS07). No date window (unlike the log tally): the
   * lookup by scaler and return number already limits the original to the 30
   * days before the check scale.
   */
  @Override
  protected DetailDocument validateAgainstOriginal(DetailDocument original) {
    if (original == null) {
      return null;
    }
    Row o = original.version().row();
    if (CHECK_SCALE.equals(o.str("scale_event_type_code"))) {
      errors.add(scalingError(CheckScale.ORIGINAL_CHECKSSCALE));
    } else if (Boolean.TRUE.equals(o.yes("check_replaces_original_ind"))) {
      errors.add(scalingError(CheckScale.CHECKSCALE));
    } else if (!validOriginalStateCombination(original)) {
      errors.add(scalingError(CheckScale.INVALID_STATE));
    }
    return original;
  }

  // ── line edits ────────────────────────────────────────────────────

  /** {@code validateLogs}. */
  protected void validateLogs() {
    for (LogLine log : logs()) {
      validateLog(log);
    }
  }

  /**
   * {@code validateLog}: beachcomb line marks, then each dimension that is
   * present — length (LT01), top (TP01/TPMax), butt (BT01/BTMax), stack
   * length (LN02), width (WD01), height (HT01) and conversion factor (CF01)
   * must not be negative.
   */
  protected void validateLog(LogLine log) {
    Row l = log.row();
    if (!empty(v.str("timber_mark")) && isBeachcomb(v.str("timber_mark"))) {
      validateLogTimbermark(log);
    }
    if (length(log) != null) {
      validateLength(log);
    }
    if (top(log) != null) {
      validateTop(log);
    }
    if (butt(log) != null) {
      validateButt(log);
    }
    if (l.num("stack_length") != null) {
      validateStack(log, l.num("stack_length"), SpecialForestProduct.Stack.LENGTH);
    }
    if (l.num("stack_width") != null) {
      validateStack(log, l.num("stack_width"), SpecialForestProduct.Stack.WIDTH);
    }
    if (l.num("stack_height") != null) {
      validateStack(log, l.num("stack_height"), SpecialForestProduct.Stack.HEIGHT);
    }
    if (l.num("conversion_factor") != null) {
      validateStack(log, l.num("conversion_factor"), SpecialForestProduct.Stack.CONVERSION_FACTOR);
    }
  }

  /**
   * {@code validateStackLength / Width / Height / ConversionFactor}: a negative
   * value. Legacy compared the Long dimensions as {@code Long} (a fraction read
   * from the NUMBER column was truncated), so a value in (-1, 0) passes.
   */
  private void validateStack(LogLine log, BigDecimal value, String code) {
    boolean negative = SpecialForestProduct.Stack.CONVERSION_FACTOR.equals(code)
        ? value.doubleValue() < 0
        : value.longValue() < 0;
    if (negative) {
      errors.add(logError(log, code));
    }
  }

  /** {@code isBeachcomb}: BCOMB1, BCOMB2 or BCOMB3 (by mark code, not file type). */
  private static boolean isBeachcomb(String mark) {
    return BEACHCOMB_MARKS.contains(mark);
  }

  /**
   * {@code validateLogTimbermark}: NM04 a mark on a no-mark-visible line, NM02
   * neither. With SFP_LOG_DETAIL's transient mark (null) and NMV (TRUE) neither
   * fires; kept for parity.
   */
  protected void validateLogTimbermark(LogLine log) {
    String mark = log.row().str("timber_mark");
    if (!empty(mark) && Boolean.TRUE.equals(nmvInd(log))) {
      errors.add(logError(log, CrossReferenceEdit.LogTally.Log.NMV_N));
    }
    if (empty(mark) && !Boolean.TRUE.equals(nmvInd(log))) {
      errors.add(logError(log, CrossReferenceEdit.LogTally.Log.NMV_Y));
    }
  }

  // ── input fields ──────────────────────────────────────────────────

  /**
   * {@code validateAndCopyVersionInputFields}: as the log tally without the
   * originating site and field deck; the cut block (INPUT_CUT_BLOCK_ID here) is
   * copied without validation (CR 63).
   */
  protected void validateAndCopyVersionInputFields() {
    if (empty(v.str("scale_site_id_nmbr"))) {
      validateInputScaleSite();
    }
    if (empty(v.str("timber_mark")) && empty(v.str("timber_brand")) && !empty(v.str("input_source"))) {
      validateInputSource();
    } else if (!empty(v.str("timber_brand"))) {
      validateTimberBrand(v.str("timber_brand"));
    }
    if (empty(v.str("cut_block_id")) && !empty(v.str("input_cut_block_id"))) {
      v.set("cut_block_id", v.str("input_cut_block_id"));
    }
    if (empty(v.str("primary_license_number"))) {
      validateInputPrimaryScalerLicense();
    }
    if (empty(v.str("orig_chk_scl_license_number")) && !empty(v.str("input_orig_chk_scl_scaler_lic"))) {
      validateInputOriginalScalerLicense();
    }
    if (empty(v.str("secondary_license_number")) && !empty(v.str("input_secondary_scaler_lic"))) {
      validateInputSecondaryScalerLicense();
    }
    if (empty(v.str("signing_license_number")) && !empty(v.str("input_signing_scaler_lic"))) {
      validateInputSigningScalerLicense();
    }
  }

  /**
   * {@code validateAndCopyLogsInputFields} (BT 7012): species/product/grade of
   * every line, each SPG error once per tally. Legacy passed a null timber mark,
   * so the grade-schedule edits (CG01/IG01/CG02/IG02) never run on SFP lines.
   */
  protected void validateAndCopyLogsInputFields() {
    List<String> errorTypeLogged = new ArrayList<>(16);
    String gradeScheduleType = gradeScheduleType(v.str("timber_mark"), v.date("scale_date"));
    for (LogLine log : logs()) {
      String errorType = validateInputSpeciesProductGrade(log, v.date("scale_date"), null, errorTypeLogged,
          gradeScheduleType);
      if (errorType != null) {
        errorTypeLogged.add(errorType);
      }
    }
  }
}
