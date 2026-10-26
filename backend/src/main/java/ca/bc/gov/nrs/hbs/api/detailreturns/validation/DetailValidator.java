package ca.bc.gov.nrs.hbs.api.detailreturns.validation;

import ca.bc.gov.nrs.hbs.api.detailreturns.model.DetailDocument;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.DetailVersion;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.DocType;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.EditError;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.LogLine;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.Row;
import ca.bc.gov.nrs.hbs.api.detailreturns.state.Status;
import ca.bc.gov.nrs.hbs.api.detailreturns.store.DetailStore;
import ca.bc.gov.nrs.hbs.api.detailreturns.validation.ReferenceData.ClientLocationRef;
import ca.bc.gov.nrs.hbs.api.detailreturns.validation.ReferenceData.RateCheck;
import ca.bc.gov.nrs.hbs.api.detailreturns.validation.ReferenceData.ScaleSiteRef;
import ca.bc.gov.nrs.hbs.api.detailreturns.validation.ReferenceData.StratumRef;
import ca.bc.gov.nrs.hbs.api.detailreturns.validation.ReferenceData.TimberMarkRef;
import ca.bc.gov.nrs.hbs.api.detailreturns.validation.ReferenceData.WorksheetRef;
import ca.bc.gov.nrs.hbs.api.workflow.Audit;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Base of the detail-return edits, ported from
 * {@code $E/dac/detail/control/DetailScaleDocumentValidator} (the "edits" the
 * legacy app ran on Add, Save, Release and Hold, and that B1021 runs on
 * submitted versions). A validator never refuses the request: it records
 * {@link EditError}s on the document, which the workflow stores in the type's
 * error tables and uses to choose RDY or ERR on release.
 *
 * <p>Fields named {@code input_*} hold what was submitted; the edits copy a
 * valid input value to the real column ({@code validateAndCopyInputFields}).
 * Online entry writes the real columns directly, so most copies only matter
 * for versions that came in through XML.
 *
 * <p>Subclasses: {@link LogTallyValidator}, {@link SfpTallyValidator},
 * {@link WeighSlipValidator}, {@link SampleLogTallyValidator},
 * {@link ArrivalLedgerValidator}, {@link DepartureLedgerValidator}.
 */
public abstract class DetailValidator {

  /** {@code dac.common.Constants}. */
  public static final String COASTAL_SCHEDULE = "C";
  public static final String INTERIOR_SCHEDULE = "I";
  public static final String LOG_TYPE_WEIGH = "W";
  public static final String LOG_TYPE_SAMPLE = "S";

  /** {@code DacConstants.SCALE_BASED_CONIFEROUS_SRE_CODES} / {@code ..._DECIDUOUS_...}. */
  static final Set<String> SCALE_BASED_CONIFEROUS_SRE_CODES = Set.of("S", "A", "N");
  static final Set<String> SCALE_BASED_DECIDUOUS_SRE_CODES = Set.of("S", "A", "N");

  /** {@code DetailScaleDocumentVersion} event type codes. */
  public static final String PRIMARY_SCALE = "PS";
  public static final String FROM_SITE_DECK = "FD";
  public static final String TO_SITE_DECK = "TD";
  public static final String RED_TAG = "RT";
  public static final String REMOTE_RED_TAG = "RR";
  public static final String CHECK_SCALE = "CS";
  public static final String SAMPLE_SCALE = "SS";
  public static final String REMOTE_SAMPLE_SCALE = "RS";
  public static final String SB4_RESCALE = "4R";
  public static final String COMPANY_USE = "CU";
  public static final String PRIMARY_DEPARTURE = "PD";
  public static final String DEPARTURE = "DP";
  public static final String SCALED_BEFORE = "B4";

  /** {@code QuantLicense.SCALE_LICENSE_CODE}. */
  public static final String SCALE_LICENSE_CODE = "S";

  protected final DetailDocument document;
  protected final DetailVersion version;
  /** The version row (columns as in the version table). */
  protected final Row v;
  protected final ReferenceData ref;
  /** Document finders (check-scale originals, sampled weigh slips…). */
  protected final DetailStore store;
  protected final List<EditError> errors;
  /** "Today" for the date edits (Pacific); fixed per run so tests are stable. */
  protected final LocalDate today;

  protected DetailValidator(DetailDocument document, ValidationContext ctx) {
    this.document = document;
    this.version = document.version();
    this.v = version.row();
    this.ref = ctx.ref();
    this.store = ctx.store();
    this.errors = document.errors();
    this.today = ctx.today();
  }

  public static LocalDate today() {
    return LocalDate.now(Audit.ZONE);
  }

  public List<EditError> errors() {
    return errors;
  }

  public boolean errorsFound() {
    return !errors.isEmpty();
  }

  // ── entry points (DetailScaleDocumentValidator public API) ────────

  /** {@code copyInputFieldsAndValidate}: P/W/S only when {@link #isEditApplicable}. */
  public void copyInputFieldsAndValidate() {
    DocType t = document.type();
    if (t == DocType.LOG_TALLY || t == DocType.WEIGH_SLIP || t == DocType.SAMPLE_LOG_TALLY) {
      if (isEditApplicable()) {
        validateAndCopyInputFields();
        validate();
      }
    } else {
      validateAndCopyInputFields();
      validate();
    }
  }

  /** {@code fieldDeckIdValidate} (BT 10013). */
  public void fieldDeckIdValidate() {
    validatFieldScaleDeckId();
  }

  /** {@code validateEventOrScaleDate} (BT 10747). */
  public void validateEventOrScaleDate() {
    validateEventDate();
  }

  /** {@code validateEventDate}: scale date before 1 January seven years back → SD02 (not for departures). */
  protected void validateEventDate() {
    if (document.type() != DocType.DEPARTURE_LEDGER) {
      Date scaleDate = v.date("scale_date");
      if (scaleDate != null && local(scaleDate).isBefore(dateLimit(7))) {
        errors.add(dateError(CrossReferenceEdit.Common.SCALE_DATE_LIMIT));
      }
    }
  }

  protected abstract void validate();

  /** (sic) legacy name; BT 10013. */
  protected abstract void validatFieldScaleDeckId();

  protected abstract void validateAndCopyInputFields();

  public abstract boolean isBillableScaleReturn();

  /**
   * {@code isEditApplicable}: no edits on an inactive non-ACC version, an
   * issued or cancelled version, a locked one (except under CWR, BT 10843), a
   * RDY/AWP one under CIP/CWR, or a RDY one under CWC.
   */
  public boolean isEditApplicable() {
    var s = version.state();
    String doc = document.state().value();
    if (s.isInactive() && !Status.Version.ACCEPTED.equals(s.value())) {
      return false;
    }
    boolean apply = true;
    if (s.isInvoiced() || s.isCancelled()) apply = false;
    if (s.isLocked() && !Status.Document.CANCEL_WITH_REPLACE_NO_CHANGES.equals(doc)) apply = false;
    if ((s.isReady() || s.isAwaitingApproval())
        && (Status.Document.CANCEL_IN_PROGRESS.equals(doc)
            || Status.Document.CANCEL_WITH_REPLACE_NO_CHANGES.equals(doc))) {
      apply = false;
    }
    if (s.isReady() && Status.Document.CANCEL_WITH_REPLACE_WITH_CHANGES.equals(doc)) apply = false;
    return apply;
  }

  // ── shared edits ──────────────────────────────────────────────────

  protected void validateInputScaleSite() {
    if (isValidScaleSite(v.str("input_scale_site"))) {
      v.set("scale_site_id_nmbr", v.str("input_scale_site"));
    } else {
      errors.add(scalingError(CrossReferenceEdit.Common.SCALE_SITE_XREF));
    }
  }

  /** {@code validateInputSource}: SB / ledger B4 → timber brand, otherwise timber mark. */
  protected void validateInputSource() {
    String event = v.str("scale_event_type_code");
    String source = v.str("input_source");
    if (SB4_RESCALE.equals(event) || (SCALED_BEFORE.equals(event) && document.type() == DocType.ARRIVAL_LEDGER)) {
      if (validateTimberBrand(source)) {
        v.set("timber_brand", source);
      }
    } else if (isValidTimberMark(source)) {
      v.set("timber_mark", source);
    } else {
      errors.add(scalingError(CrossReferenceEdit.Common.TIMBERMARK_XREF));
    }
  }

  protected boolean validateTimberBrand(String timberBrand) {
    boolean valid = true;
    if (isExistingTimberBrand(timberBrand)) {
      if (hasReferenceToScaleSite(timberBrand)) {
        if (document.type() == DocType.LOG_TALLY || document.type() == DocType.ARRIVAL_LEDGER) {
          valid = false;
          errors.add(scalingError(CrossReferenceEdit.LogTallyCommon.TIMBERBRAND_SAME_SCALE_SITE));
        } else if (document.type() == DocType.WEIGH_SLIP) {
          valid = false;
          errors.add(scalingError(CrossReferenceEdit.WeighSlip.TIMBERBRAND_SAME_SCALE_SITE));
        }
      }
    } else {
      valid = false;
      errors.add(scalingError(CrossReferenceEdit.Common.TIMBERBRAND_XREF));
    }
    return valid;
  }

  protected void validateInputCutBlockId() {
    String mark = v.str("timber_mark");
    String input = v.str("input_cutblock_id");
    if (!empty(mark) && ref.cutBlockExists(input, mark)) {
      v.set("cut_block_id", input);
    } else {
      errors.add(scalingError(CrossReferenceEdit.Common.CUTBLOCK_INVALID));
    }
  }

  protected void validateInputPrimaryScalerLicense() {
    String input = v.str("input_primary_scaler_lic");
    if (isValidScalerLicense(input)) {
      v.set(primaryLicenceColumn(), input);
      v.set(primaryLicenceTypeColumn(), SCALE_LICENSE_CODE);
    } else {
      errors.add(scalingError(CrossReferenceEdit.Common.PRIMARY_SCALER_LICENSE_XREF));
    }
  }

  protected void validateInputSecondaryScalerLicense() {
    String input = v.str("input_secondary_scaler_lic");
    if (isValidScalerLicense(input)) {
      v.set("secondary_license_number", input);
      v.set("secondary_quant_lic_type_cd", SCALE_LICENSE_CODE);
    } else {
      errors.add(scalingError(CrossReferenceEdit.Common.SECONDARY_SCALER_LICENSE_XREF));
    }
  }

  protected void validateInputSigningScalerLicense() {
    String input = v.str("input_signing_scaler_lic");
    if (isValidScalerLicense(input)) {
      v.set("signing_license_number", input);
      v.set("signing_quant_lic_type_cd", SCALE_LICENSE_CODE);
    } else {
      errors.add(scalingError(CrossReferenceEdit.Common.SIGNING_SCALER_LICENSE_XREF));
    }
  }

  /** The arrival ledger keeps its primary scaler in LICENSE_NUMBER / QUANT_LIC_TYPE_CD. */
  protected String primaryLicenceColumn() {
    return document.type() == DocType.ARRIVAL_LEDGER ? "license_number" : "primary_license_number";
  }

  protected String primaryLicenceTypeColumn() {
    return document.type() == DocType.ARRIVAL_LEDGER ? "quant_lic_type_cd" : "primary_quant_lic_type_cd";
  }

  protected void validateTimberMark() {
    if (ref.timberMark(v.str("timber_mark")).isEmpty()) {
      errors.add(scalingError(CrossReferenceEdit.Common.TIMBERMARK_XREF));
    }
  }

  /** {@code validateScaleSite}: waste site → SSZZ; dedicated cruise-based site → CBSS00. */
  protected void validateScaleSite() {
    String id = v.str("scale_site_id_nmbr");
    ScaleSiteRef site = ref.scaleSite(id).orElseThrow(() ->
        new IllegalStateException("Unexpected Scale Site removal. Scale Site Id: " + id));
    if (ScaleSiteRef.SCALE_SITE_WASTE.equals(site.scaleSiteIdNmbr())) {
      errors.add(scalingError(CrossReferenceEdit.Common.SCALE_SITE_WASTE));
    }
    if (site.isDedicatedCruiseBasedSite()) {
      errors.add(scalingError(HbsEditError.CBSS00));
    }
  }

  protected void validateCruiseBasedScaleSite(String scaleSiteId, String errorCode) {
    ScaleSiteRef site = ref.scaleSite(scaleSiteId).orElseThrow(() ->
        new IllegalStateException("Unexpected Scale Site removal. Scale Site Id: " + scaleSiteId));
    if (site.isDedicatedCruiseBasedSite()) {
      errors.add(scalingError(errorCode));
    }
  }

  /** SGL02: the signing scaler must be the primary or the secondary scaler. */
  protected void validateSigningScalerLicense() {
    String signing = v.str("signing_license_number");
    if (!empty(signing) && !signing.equals(v.str("primary_license_number"))
        && !signing.equals(v.str("secondary_license_number"))) {
      errors.add(scalingError(CrossReferenceEdit.Common.SIGNING_SCALER_LICENSE_MATCH));
    }
  }

  /** SD03: scale date after today. */
  protected void validateScaleDate() {
    if (!isDateNotAfterToday(v.date("scale_date"))) {
      errors.add(scalingError(CrossReferenceEdit.Common.SCALE_DATE_CURRENT_DATE));
    }
  }

  protected void validateInputDestinationSite(String destination) {
    if (!isValidScaleSite(destination)) {
      errors.add(scalingError(CrossReferenceEdit.Common.DESTINATION_SITE_XREF));
    }
  }

  protected void validateArrivalDate(Date arrivalDate) {
    if (!isDateNotAfterToday(arrivalDate)) {
      errors.add(scalingError(CrossReferenceEdit.Common.ARRIVAL_DATE_CURRENT_DATE));
    }
  }

  protected void validateArrivalLimitDate(Date arrivalDate) {
    if (local(arrivalDate).isBefore(dateLimit(6))) {
      errors.add(dateError(CrossReferenceEdit.Common.ARRIVAL_LIMIT_DATE));
    }
  }

  protected void validateDepartureLimitDate(Date departureDate) {
    if (local(departureDate).isBefore(dateLimit(6))) {
      errors.add(dateError(CrossReferenceEdit.Common.DEPARTURE_LIMIT_DATE));
    }
  }

  protected void validateDepartureDate(Date departureDate) {
    if (!isDateNotAfterToday(departureDate)) {
      errors.add(scalingError(CrossReferenceEdit.Common.DEPARTURE_DATE_CURRENT_DATE));
    }
  }

  /** (sic) {@code validateTimberBran}. */
  protected void validateTimberBran(String timberBrand) {
    if (!isExistingTimberBrand(timberBrand)) {
      errors.add(scalingError(CrossReferenceEdit.Common.TIMBERBRAND_XREF));
    }
  }

  protected void validateOrigScaleSite(String origScaleSite) {
    if (!isValidScaleSite(origScaleSite)) {
      errors.add(scalingError(CrossReferenceEdit.LogTallyCommon.ORIGINATING_SACLE_SITE_XREF));
    }
  }

  /** FSD01 field scale without deck; FSD02 deck on a non-field-scaled original. */
  protected void validateFieldScaleDeckId(Boolean fieldScaleInd, String fieldScaleDeckId, Boolean originalInd) {
    if (Boolean.TRUE.equals(fieldScaleInd)) {
      if (empty(fieldScaleDeckId)) {
        errors.add(scalingError(CrossReferenceEdit.Common.FIELD_SCALE_DECK_ID));
      }
    } else if (Boolean.TRUE.equals(originalInd) && !empty(fieldScaleDeckId)) {
      errors.add(scalingError(CrossReferenceEdit.Common.FIELD_SCALE_DECK_ID_NOT_PRESENT));
    }
  }

  protected void validateScalerLicence() {
    if (!isValidScalerLicense(v.str("input_primary_scaler_lic"))) {
      errors.add(scalingError(CrossReferenceEdit.Common.PRIMARY_SCALER_LICENSE_XREF));
    }
  }

  /** WSN01: weigh slip number must be numeric ({@code StringUtil.isNumeric}). */
  protected void validateWeighSlip(String weighSlip) {
    if (!isNumeric(weighSlip)) {
      errors.add(scalingError(CrossReferenceEdit.Common.WS_NUMBER_INVALID));
    }
  }

  /**
   * {@code validateRate}: scale-based SRE codes on the confirmed appraisal
   * (ISB1C/ISB1D interior, CSB1C/CSB1D coastal), TM12 for an area-based mark
   * with an appraisal, SD08/SD11/SD10 when the mark has no / several /
   * no-stumpage rate on the scale date. Sample tallies skip the rate edits.
   */
  protected void validateRate() {
    String markCode = v.str("timber_mark");
    Optional<TimberMarkRef> found = ref.timberMark(markCode);
    if (found.isEmpty()) {
      return;
    }
    TimberMarkRef mark = found.get();
    boolean sample = document.type() == DocType.SAMPLE_LOG_TALLY;
    Date scaleDate = v.date("scale_date");
    if (!sample) {
      List<WorksheetRef> appraisals = ref.confirmedWorksheets(markCode, scaleDate);
      if (!mark.isPrivateMark() && !appraisals.isEmpty()) {
        WorksheetRef ws = appraisals.get(0);
        if (INTERIOR_SCHEDULE.equals(ws.appraisalMthdCd())) {
          if (!mark.isCruiseBased()) {
            validateScaleBasedSreCodes(ws, HbsEditError.ISB1C, HbsEditError.ISB1D);
          }
        } else if (COASTAL_SCHEDULE.equals(ws.appraisalMthdCd())) {
          validateScaleBasedSreCodes(ws, HbsEditError.CSB1C, HbsEditError.CSB1D);
        }
      }
      if (mark.isAreaBased() && !appraisals.isEmpty()) {
        errors.add(relatedError(HbsEditError.TM12));
      }
    }
    if (!mark.isPrivateMark() && !sample && !mark.isCashSale() && !mark.isCruiseBased() && !mark.isAreaBased()) {
      RateCheck rate = ref.rateCheck(markCode, scaleDate, v.str("field_scale_deck_id"));
      switch (rate) {
        case NO_RATE -> errors.add(billingError(CrossReferenceEdit.Common.SCALE_DATE_NO_RATE));
        case OVERLAPPING_RULES -> errors.add(billingError(CrossReferenceEdit.Common.SCALE_DATE_MULTI_RATE));
        case NO_STUMPAGE -> errors.add(billingError(CrossReferenceEdit.Common.SCALE_DATE_NO_STUMPAGE_RATE));
        case HAS_RATE -> { }
      }
    }
  }

  private void validateScaleBasedSreCodes(WorksheetRef ws, String conifErrorCode, String decidErrorCode) {
    if (!SCALE_BASED_CONIFEROUS_SRE_CODES.contains(ws.conifStandRateEligibilityCode())) {
      errors.add(scalingError(conifErrorCode));
    }
    if (!SCALE_BASED_DECIDUOUS_SRE_CODES.contains(ws.decidStandRateEligibilityCode())) {
      errors.add(scalingError(decidErrorCode));
    }
  }

  /** PI02 / PI00 / PI01. */
  protected void validateParcelId(Long parcelId, Long parcelCount) {
    if (parcelId == null && parcelCount == null) return;
    if (parcelId != null && parcelId <= 0) {
      errors.add(scalingError(CrossReferenceEdit.Common.PARCEL_ID_LOWER_BOUND));
    }
    if (parcelId == null) {
      errors.add(scalingError(CrossReferenceEdit.Common.PARCEL_ID_PARCEL_COUNT_DEPENDENCY));
    }
    if (parcelId != null && parcelCount != null && parcelId.compareTo(parcelCount) > 0) {
      errors.add(scalingError(CrossReferenceEdit.LogTally.PARCEL_ID));
    }
  }

  /** PC02 / PC00. */
  protected void validateParcelCount(Long parcelId, Long parcelCount) {
    if (parcelId == null && parcelCount == null) return;
    if (parcelCount != null && parcelCount <= 0) {
      errors.add(scalingError(CrossReferenceEdit.Common.PARCEL_COUNT_LOWER_BOUND));
    }
    if (parcelCount == null) {
      errors.add(scalingError(CrossReferenceEdit.Common.PARCEL_COUNT_PARCEL_ID_DEPENDENCY));
    }
  }

  /** {@code getScaleSiteOwner}: the site owner's client location (null when unknown). */
  protected ClientLocationRef scaleSiteOwner(String scaleSiteId) {
    if (empty(scaleSiteId)) return null;
    Optional<ScaleSiteRef> site = ref.scaleSite(scaleSiteId);
    if (site.isEmpty()) return null;
    return clientLocation(site.get().ownerCliNumber(), site.get().ownerCliLocnCd());
  }

  /** {@code deriveBillToClientAndLocationFromTimberMark}. */
  protected ClientLocationRef billToFromTimberMark(String timberMark) {
    if (empty(timberMark)) return null;
    TimberMarkRef mark = ref.timberMark(timberMark).orElseThrow(() ->
        new IllegalStateException("DetailScaleDocumentValidator.deriveBillToClientAndLocationFromTimberMark"));
    return clientLocation(mark.clientNumber(), mark.clientLocnCode());
  }

  /** {@code deriveCopyToClientLocationFromStratum}. */
  protected ClientLocationRef copyToFromStratum(String population, String stratum, Long year) {
    if (empty(population) || empty(stratum) || year == null) return null;
    StratumRef s = WeighSampleSupport.findStratum(ref, population, stratum, year).orElseThrow(() ->
        new IllegalStateException("DetailScaleDocumentValidator.deriveCopyToClientLocationFromStratum"));
    return clientLocation(s.clientNumber(), s.clientLocnCode());
  }

  private ClientLocationRef clientLocation(String client, String locn) {
    if (empty(client) || empty(locn)) return null;
    return ref.clientLocation(client, locn).orElse(null);
  }

  /** BT03 / BT02 / BT04 on the bill-to client. */
  protected void validateBillToClientAndLocation(ClientLocationRef billTo) {
    if (billTo == null) {
      errors.add(formatError(HbsEditError.BT03));
    } else if (billTo.isDeactivated()) {
      errors.add(relatedError(HbsEditError.BT02));
    } else if (billTo.isExpired()) {
      errors.add(relatedError(HbsEditError.BT04));
    }
  }

  /** CT03 / CT02 / CT04 on the copy-to client. */
  protected void validateCopyToClientAndLocation(ClientLocationRef copyTo) {
    if (copyTo == null) {
      errors.add(formatError(HbsEditError.CT03));
    } else if (copyTo.isDeactivated()) {
      errors.add(relatedError(HbsEditError.CT02));
    } else if (copyTo.isExpired()) {
      errors.add(relatedError(HbsEditError.CT04));
    }
  }

  /**
   * {@code validateGradeConstraint}: the stratum's grade constraint must match
   * the mark's grade schedule (CG03/IG03 sample, CG04/IG04 weigh slip).
   */
  protected void validateGradeConstraint(String timberMark, Date scaleDate, String populationNo, String stratumNo,
                                         Long sampleYear, String logType) {
    if (empty(populationNo) || empty(stratumNo) || sampleYear == null || scaleDate == null || empty(timberMark)) {
      return;
    }
    if (!canStratumValidationPerform(timberMark, populationNo, stratumNo, sampleYear)) {
      return;
    }
    String schedule = ref.gradeScheduleType(timberMark, scaleDate);
    StratumRef stratum = WeighSampleSupport.findStratum(ref, populationNo, stratumNo, sampleYear).orElseThrow();
    if (schedule == null || !schedule.equalsIgnoreCase(stratum.gradeConstraint())) {
      boolean coastal = COASTAL_SCHEDULE.equalsIgnoreCase(schedule);
      if (LOG_TYPE_SAMPLE.equalsIgnoreCase(logType)) {
        errors.add(error(CrossReferenceEdit.CategoryCode.CATEGORY_CODE_SCALING, coastal
            ? CrossReferenceEdit.LogTallyCommon.Log.TIMBER_MARK_COASTAL_STRATUM
            : CrossReferenceEdit.LogTallyCommon.Log.TIMBER_MARK_INTERIOR_STRATUM));
      } else if (LOG_TYPE_WEIGH.equalsIgnoreCase(logType)) {
        errors.add(error(CrossReferenceEdit.CategoryCode.CATEGORY_CODE_SCALING, coastal
            ? CrossReferenceEdit.LogTallyCommon.Log.WEIGH_TIMBER_MARK_COASTAL_STRATUM
            : CrossReferenceEdit.LogTallyCommon.Log.WEIGH_TIMBER_MARK_INTERIOR_STRATUM));
      }
    }
  }

  protected boolean canStratumValidationPerform(String timberMark, String populationNo, String stratumNo,
                                                Long sampleYear) {
    return ref.timberMark(timberMark).isPresent()
        && WeighSampleSupport.findStratum(ref, populationNo, stratumNo, sampleYear).isPresent();
  }

  // ── lookups (legacy is* helpers) ──────────────────────────────────

  protected boolean isValidScaleSite(String scaleSiteId) {
    return scaleSiteId != null && ref.scaleSite(scaleSiteId).isPresent();
  }

  protected boolean isWasteScaleSite(String scaleSiteId) {
    ScaleSiteRef site = ref.scaleSite(scaleSiteId).orElseThrow(() ->
        new IllegalStateException("Unexpected Scale Site removal. Scale Site Id: " + scaleSiteId));
    return ScaleSiteRef.SCALE_SITE_WASTE.equals(site.scaleSiteIdNmbr());
  }

  public boolean isValidTimberMark(String mark) {
    return !empty(mark) && ref.timberMark(mark).isPresent();
  }

  /** {@code isBillableMark} (BT5884): not cash sale, cruise based or area based. */
  public boolean isBillableMark(String mark) {
    return ref.timberMark(mark).map(m -> !m.isCashSale() && !m.isCruiseBased() && !m.isAreaBased()).orElse(false);
  }

  protected boolean isCashSaleMark(String mark) {
    return ref.timberMark(mark).map(TimberMarkRef::isCashSale).orElse(false);
  }

  protected boolean isCruiseBasedMark(String mark) {
    return ref.timberMark(mark).map(TimberMarkRef::isCruiseBased).orElse(false);
  }

  protected boolean isAreaBasedMark(String mark) {
    return ref.timberMark(mark).map(TimberMarkRef::isAreaBased).orElse(false);
  }

  public boolean isExistingTimberBrand(String timberBrand) {
    return !empty(timberBrand) && ref.scaleSiteByTimberBrand(timberBrand).isPresent();
  }

  /** The brand belongs to the version's own scale site. */
  public boolean hasReferenceToScaleSite(String timberBrand) {
    return ref.scaleSiteByTimberBrand(timberBrand)
        .map(s -> s.scaleSiteIdNmbr().equals(v.str("scale_site_id_nmbr")))
        .orElse(false);
  }

  protected boolean isValidScalerLicense(String licence) {
    return !empty(licence) && ref.scalerLicenceExists(licence);
  }

  // ── error factories (createScalingError … createFormatError) ──────

  protected EditError error(String category, String code) {
    return EditError.version(category, code);
  }

  protected EditError scalingError(String code) {
    return error(CrossReferenceEdit.CategoryCode.CATEGORY_CODE_SCALING, code);
  }

  protected EditError dateError(String code) {
    return error(CrossReferenceEdit.CategoryCode.CATEGORY_CODE_DATE, code);
  }

  protected EditError lookupError(String code) {
    return error(CrossReferenceEdit.CategoryCode.CATEGORY_CODE_LOOKUP, code);
  }

  protected EditError relatedError(String code) {
    return error(CrossReferenceEdit.CategoryCode.CATEGORY_CODE_RELATED, code);
  }

  protected EditError formatError(String code) {
    return error(CrossReferenceEdit.CategoryCode.CATEGORY_CODE_FORMAT, code);
  }

  protected EditError billingError(String code) {
    return error(CrossReferenceEdit.CategoryCode.CATEGORY_CODE_BILLING, code);
  }

  /** A log-line error: category SCL (legacy {@code createLogError}). */
  protected EditError logError(LogLine line, String code) {
    return EditError.line(CrossReferenceEdit.CategoryCode.CATEGORY_CODE_SCALING, code, line);
  }

  // ── small helpers ─────────────────────────────────────────────────

  /**
   * {@code StringUtil.empty(String)}: null or "" — whitespace is kept
   * ({@code keepWhitespace = true}), so " " is NOT empty. Use {@link #blank}
   * where the legacy call was {@code StringUtil.empty(s, false)}.
   */
  protected static boolean empty(String s) {
    return s == null || s.isEmpty();
  }

  /** {@code StringUtil.empty(s, false)}: null or only whitespace (trims). */
  protected static boolean blank(String s) {
    return s == null || s.trim().isEmpty();
  }

  /** {@code StringUtil.isNumeric}: every character a digit (empty is not numeric). */
  protected static boolean isNumeric(String s) {
    if (s == null || s.isEmpty()) return false;
    for (char c : s.toCharArray()) {
      if (!Character.isDigit(c)) return false;
    }
    return true;
  }

  /**
   * The calendar day of a DATE column. JDBC builds an Oracle DATE (no zone) in
   * the JVM's default zone, so it is read back in that zone — the wall clock
   * the DB holds. The container runs in Pacific (TZ), so this is the Pacific
   * day there, and the same day on a UTC CI runner.
   */
  protected static LocalDate local(Date d) {
    return d.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
  }

  /** {@code DacValidator.isScaleDateBeforeCurrentDate}: the day is not after today. */
  protected boolean isDateNotAfterToday(Date d) {
    return d == null || !local(d).isAfter(today);
  }

  /** {@code DateUtil.getDateLimit(years)}: 1 January of (this year − years). */
  protected LocalDate dateLimit(int years) {
    return LocalDate.of(today.getYear() - years, 1, 1);
  }

  /** Every log line of a tally version. */
  protected List<LogLine> logs() {
    return new ArrayList<>(version.logs());
  }
}
