package ca.bc.gov.nrs.hbs.api.detailreturns.validation;

import ca.bc.gov.nrs.hbs.api.detailreturns.model.DetailDocument;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.LogLine;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.Row;
import ca.bc.gov.nrs.hbs.api.detailreturns.validation.CrossReferenceEdit.CheckScale;
import ca.bc.gov.nrs.hbs.api.detailreturns.validation.CrossReferenceEdit.LogTallyCommon.Log;
import ca.bc.gov.nrs.hbs.api.detailreturns.validation.ReferenceData.ClientLocationRef;
import ca.bc.gov.nrs.hbs.api.detailreturns.validation.ReferenceData.TimberMarkRef;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Edits of a piece-scale log tally (type P), ported from
 * {@code $E/dac/detail/control/LogTallyValidator} on top of
 * {@link LogTallyCommonValidator}. Version columns are LOG_TALLY's, line
 * columns LOG_TALLY_DETAIL's. Every error is category SCL unless noted
 * (legacy {@code createScalingError} / {@code createLogError}).
 */
public class LogTallyValidator extends LogTallyCommonValidator {

  public LogTallyValidator(DetailDocument document, ValidationContext ctx, TallyLookups tally) {
    super(document, ctx, tally);
  }

  // ── DetailScaleDocumentValidator overrides ────────────────────────

  /** {@code validateAndCopyInputFields}: version inputs, line inputs, then species/product/grade (BT 7012). */
  @Override
  protected void validateAndCopyInputFields() {
    validateAndCopyVersionInputFields();
    validateAndCopyLogsInputFields();
    validateSpg();
  }

  /** {@code validate}. */
  @Override
  protected void validate() {
    validateVersion();
    validateLogs();
  }

  /** (sic) {@code validatFieldScaleDeckId}: empty in legacy ("TODO Auto-generated method stub"). */
  @Override
  protected void validatFieldScaleDeckId() {
  }

  /**
   * {@code isBillableScaleReturn} (BT 15036): a check scale is billable when it
   * replaces the original, is not a field scale and has a billable mark; any
   * other tally when its event is PS/FD/RT/RR, it is not a field scale, the
   * mark is billable and it is not replaced by a check scale.
   */
  @Override
  public boolean isBillableScaleReturn() {
    if (isCheckScale()) {
      boolean billable = isCheckReplacesOriginalInd();
      billable = billable && !isFieldScale();
      billable = billable && isBillableMark(v.str("timber_mark"));
      return billable;
    }
    boolean billable = isBillableEventType();
    billable = billable && !isFieldScale();
    billable = billable && isBillableMark(v.str("timber_mark"));
    Boolean replaces = v.yes("check_replaces_original_ind");
    if (replaces != null) {
      billable = billable && !replaces;
    }
    return billable;
  }

  /** {@code isCheckReplacesOriginalInd}: null is false. */
  public boolean isCheckReplacesOriginalInd() {
    return Boolean.TRUE.equals(v.yes("check_replaces_original_ind"));
  }

  /** {@code DetailScaleBillableDocumentVersion.isFieldScale}: null is false. */
  private boolean isFieldScale() {
    return Boolean.TRUE.equals(v.yes("field_scale_ind"));
  }

  /** {@code isBillableEventType}: PS, FD, RT, RR. */
  private boolean isBillableEventType() {
    String event = v.str("scale_event_type_code");
    return PRIMARY_SCALE.equals(event) || FROM_SITE_DECK.equals(event) || RED_TAG.equals(event)
        || REMOTE_RED_TAG.equals(event);
  }

  // ── input fields ──────────────────────────────────────────────────

  /**
   * {@code validateAndCopyVersionInputFields}: each input is validated and
   * copied only while its real column is empty. The cut block is copied
   * without validation (CR 63 commented {@code validateInputCutBlockId} out).
   * BT 8253: a field deck id must be upper-case alphanumeric (FSX03) and, if
   * so, belong to an active field-scale estimate summary of the mark (FSX01).
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
    if (empty(v.str("cut_block_id")) && !empty(v.str("input_cutblock_id"))) {
      v.set("cut_block_id", v.str("input_cutblock_id"));
    }
    if (empty(v.str("orig_redtag_scale_site_id_nmbr")) && !empty(v.str("input_orig_redtag_scl_site"))) {
      validateInputOriginatingScaleSite();
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
    String deckId = v.str("field_scale_deck_id");
    if (!empty(deckId)) {
      if (!isAlphaNumeric(deckId) || !deckId.equals(deckId.toUpperCase())) {
        errors.add(scalingError(CrossReferenceEdit.LogTally.FIELD_DECK_CASE));
      } else {
        validateFieldDeckId();
      }
    }
  }

  /** {@code validateFieldDeckId} (BT 8253): FSX01 when no active field-scale estimate has this deck and mark. */
  protected void validateFieldDeckId() {
    if (tally.fieldDeckCount(v.str("field_scale_deck_id"), v.str("timber_mark")) == 0) {
      errors.add(scalingError(CrossReferenceEdit.LogTally.FIELD_DECK_ID));
    }
  }

  /** {@code validateAndCopyLogsInputFields}. */
  protected void validateAndCopyLogsInputFields() {
    for (LogLine log : logs()) {
      validateAndCopyLogInputFields(log);
    }
  }

  /** {@code validateAndCopyLogInputFields}: a beachcomb mark typed on a line without a mark. */
  protected void validateAndCopyLogInputFields(LogLine log) {
    Row l = log.row();
    if (!empty(l.str("input_bcomb_timber_mark")) && empty(l.str("timber_mark"))) {
      validateInputBeachcombTimberMark(log);
    }
  }

  /**
   * {@code validateInputBeachcombTimberMark}: on a beachcomb tally, a line's
   * INPUT_BCOMB_TIMBER_MARK that is on file is copied to TIMBER_MARK (no error
   * otherwise; {@link #validateLogTimbermark} reports the empty mark).
   */
  protected void validateInputBeachcombTimberMark(LogLine log) {
    if (!empty(v.str("timber_mark")) && isBeachcomb()) {
      String input = log.row().str("input_bcomb_timber_mark");
      if (isValidTimberMark(input)) {
        log.row().set("timber_mark", input);
      }
    }
  }

  /**
   * {@code validateSPG} (BT 7012): species/product/grade of every line against
   * the mark's grade schedule; each SPG error code is logged once per tally.
   */
  protected void validateSpg() {
    List<String> errorTypeLogged = new ArrayList<>(16);
    String gradeScheduleType = gradeScheduleType(v.str("timber_mark"), v.date("scale_date"));
    for (LogLine log : logs()) {
      String errorType = validateInputSpeciesProductGrade(log, v.date("scale_date"), v.str("timber_mark"),
          errorTypeLogged, gradeScheduleType);
      if (errorType != null) {
        errorTypeLogged.add(errorType);
      }
    }
  }

  // ── version edits ─────────────────────────────────────────────────

  /** {@code validateVersion}, in legacy order. */
  protected void validateVersion() {
    String site = v.str("scale_site_id_nmbr");
    if (!empty(site)) {
      validateScaleSite();
      validateCopyToClientAndLocation(scaleSiteOwner(site)); // BT 6495
    }
    if (!empty(v.str("orig_redtag_scale_site_id_nmbr"))) {
      validateCruiseBasedScaleSite(v.str("orig_redtag_scale_site_id_nmbr"), HbsEditError.CBOS00);
    }
    if (!empty(v.str("timber_mark"))) {
      validateTimberMark();
    }
    if (!empty(v.str("timber_mark")) && !empty(v.str("scale_site_id_nmbr"))) {
      validateBillToClientAndLocation(deriveBillToClientLocation(v.str("timber_mark"),
          v.str("scale_site_id_nmbr"))); // BT 6495
    }
    // BT 11301
    if (v.yes("field_scale_ind") != null) {
      String deck = v.str("field_scale_deck_id");
      if (deck != null) {
        v.set("field_scale_deck_id", deck.trim());
      }
      validateFieldScaleDeckId(v.yes("field_scale_ind"), v.str("field_scale_deck_id"),
          v.yes("original_version_ind"));
    }
    validateScaleDate();
    if (!empty(v.str("timber_mark"))) {
      validateRate();
    }
    if (v.lng("parcel_identifier") != null || v.lng("parcel_count") != null) {
      validateParcelCountAndParcelId();
    }
    if (!empty(v.str("signing_license_number"))) {
      validateSigningScalerLicense();
    }
    if (!empty(v.str("red_tag_weigh_slip"))) {
      validateWeighSlip(v.str("red_tag_weigh_slip"));
    }
    if (isCheckScale()) {
      validateOriginal();
    }
    validateLogCount();
  }

  /**
   * {@code validateTimberMark}: the shared TM edit, then BC01 on a beachcomb
   * tally whose mark is not a beachcomb (B17) mark, BC02 the other way round.
   */
  @Override
  protected void validateTimberMark() {
    super.validateTimberMark();
    if (Boolean.TRUE.equals(v.yes("beachcomb_ind"))) {
      if (!isBeachcomb()) {
        errors.add(scalingError(CrossReferenceEdit.LogTally.NOT_BEACHCOMB));
      }
    } else if (isBeachcomb()) {
      errors.add(scalingError(CrossReferenceEdit.LogTally.BEACHCOMB));
    }
  }

  /**
   * {@code isBeachcomb} (BT 10941): the tally's mark has file type B17. Legacy
   * ignored its argument and read the tally's mark; a mark not on file threw
   * ("Timber mark not found"), kept here as an {@link IllegalStateException}.
   */
  private boolean isBeachcomb() {
    String mark = v.str("timber_mark");
    TimberMarkRef found = ref.timberMark(mark).orElseThrow(() ->
        new IllegalStateException("Timber mark not found: " + mark));
    return TimberMarkRef.BEACHCOMB_FILETYPE.equals(found.fileTypeCode());
  }

  /** {@code validateParcelCountAndParcelId}. */
  protected void validateParcelCountAndParcelId() {
    Long parcelId = v.lng("parcel_identifier");
    Long parcelCount = v.lng("parcel_count");
    validateParcelId(parcelId, parcelCount);
    validateParcelCount(parcelId, parcelCount);
  }

  /** {@code deriveBillToClientLocation} (BT 6495): BCOMB2 / BCOMB3 bill the site owner, others the mark's client. */
  private ClientLocationRef deriveBillToClientLocation(String mark, String scaleSiteId) {
    if (TimberMarkRef.UNREGULATED_BEACHCOMB2.equals(mark) || TimberMarkRef.UNREGULATED_BEACHCOMB3.equals(mark)) {
      return scaleSiteOwner(scaleSiteId);
    }
    return billToFromTimberMark(mark);
  }

  /**
   * {@code validateAgainstOriginal}: the original of a replacing check scale
   * must be scaled within the 45 days before it (CHKSD1 after, CHKSD2 before),
   * not be a check scale (CHKS03), an SB4 rescale (CHKS04) or company use
   * (CHKS05), not be replaced already (CHKS06), and be RDY under OIP/RCP/RNP
   * (CHKS07). Only the first failure is reported.
   *
   * <p>Legacy NPE'd on a missing scale date; here the date edits are skipped.
   */
  @Override
  protected DetailDocument validateAgainstOriginal(DetailDocument original) {
    if (original == null) {
      return null;
    }
    Row o = original.version().row();
    Date checkDate = v.date("scale_date");
    Date originalDate = o.date("scale_date");
    boolean dates = checkDate != null && originalDate != null;
    // DateUtil.addDays(scaleDate, -45): calendar days on the wall clock
    Date limit = dates ? Timestamp.valueOf(ScaledLogXref.wall(checkDate).minusDays(45)) : null;
    String event = o.str("scale_event_type_code");
    if (dates && originalDate.after(checkDate)) {
      errors.add(scalingError(CheckScale.AFTER_CHECKSCALE));
    } else if (dates && originalDate.before(limit)) {
      errors.add(scalingError(CheckScale.BEFORE_CHECKSCALE));
    } else if (CHECK_SCALE.equals(event)) {
      errors.add(scalingError(CheckScale.ORIGINAL_CHECKSSCALE));
    } else if (SB4_RESCALE.equals(event)) {
      errors.add(scalingError(CheckScale.CHECK_SB4));
    } else if (COMPANY_USE.equals(event)) {
      errors.add(scalingError(CheckScale.CHECK_COMPANY));
    } else if (Boolean.TRUE.equals(o.yes("check_replaces_original_ind"))) {
      errors.add(scalingError(CheckScale.CHECKSCALE));
    } else if (!validOriginalStateCombination(original)) {
      errors.add(scalingError(CheckScale.INVALID_STATE));
    }
    return original;
  }

  // ── line edits ────────────────────────────────────────────────────

  /** {@code validateLogs}: every line, plus LG00 on a repeated log number (BT 14847). */
  protected void validateLogs() {
    Set<Object> logNumbers = new HashSet<>();
    for (LogLine log : logs()) {
      validateLog(log);
      if (!logNumbers.add(log.row().lng("log_number"))) {
        errors.add(logError(log, Log.DUPLICATE_LOG_NUMBER));
      }
    }
  }

  /** {@code validateLog}: beachcomb line marks, length, top, butt, interior cruise-based grade. */
  protected void validateLog(LogLine log) {
    if (!empty(v.str("timber_mark")) && isBeachcomb()) {
      validateLogTimbermark(log);
    }
    validateLength(log);
    validateTop(log);
    validateButt(log);
    validateGrade(log);
  }

  /**
   * {@code validateLogTimbermark}: TM02 line without a mark; NM04 a beachcomb
   * mark on a no-mark-visible line; NM02 no beachcomb mark and NMV not set.
   */
  protected void validateLogTimbermark(LogLine log) {
    Row l = log.row();
    if (empty(l.str("timber_mark"))) {
      errors.add(logError(log, CrossReferenceEdit.Common.TIMBERMARK_INACTIVE));
    }
    if (!empty(l.str("input_bcomb_timber_mark")) && Boolean.TRUE.equals(nmvInd(log))) {
      errors.add(logError(log, CrossReferenceEdit.LogTally.Log.NMV_N));
    }
    if (empty(l.str("input_bcomb_timber_mark")) && !Boolean.TRUE.equals(nmvInd(log))) {
      errors.add(logError(log, CrossReferenceEdit.LogTally.Log.NMV_Y));
    }
  }

  /** {@code StringUtil.isAlphaNumeric}: non-empty and every character a letter or digit. */
  static boolean isAlphaNumeric(String s) {
    if (s == null || s.isEmpty()) return false;
    for (int i = 0; i < s.length(); i++) {
      if (!Character.isLetterOrDigit(s.charAt(i))) return false;
    }
    return true;
  }
}
