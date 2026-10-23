package ca.bc.gov.nrs.hbs.api.detailreturns.validation;

import ca.bc.gov.nrs.hbs.api.detailreturns.model.DetailDocument;
import ca.bc.gov.nrs.hbs.api.detailreturns.validation.ReferenceData.PopulationRef;
import ca.bc.gov.nrs.hbs.api.detailreturns.validation.ReferenceData.ScaleSiteRef;
import ca.bc.gov.nrs.hbs.api.detailreturns.validation.ReferenceData.StratumRef;
import ca.bc.gov.nrs.hbs.api.detailreturns.validation.ReferenceData.TimberMarkRef;
import java.util.Date;
import java.util.Optional;

/**
 * Edits of a weigh slip, ported line by line from
 * {@code $E/dac/detail/control/WeighSlipValidator} (WEIGH_SLIP columns).
 *
 * <p>The population/stratum/year ("PSY") is billable only for a billable mark
 * on a SS/RS/PS/PD event; a billable PSY goes to POPULATION_NUMBER /
 * STRATUM_NUMBER / SAMPLING_YEAR, anything else to COMPANY_USE_STRATUM
 * ({@code validateStratumBillableOrNot}).
 *
 * <p>Legacy NullPointerExceptions on a missing weight (tare without gross,
 * grapple weight without tare or gross) aborted the whole edit run; here the
 * comparison that needs the missing weight is skipped.
 */
public class WeighSlipValidator extends DetailValidator {

  public static final String SAMPLE_TYPE_STATISTICALLY_CHOSEN = "S";
  public static final String SAMPLE_TYPE_FORCED = "F";
  public static final String SAMPLE_TYPE_COMPANY = "C";
  public static final String SAMPLE_TYPE_NONE = "N";

  protected final WeighSampleLookups weighSample;

  public WeighSlipValidator(DetailDocument document, ValidationContext ctx, WeighSampleLookups weighSample) {
    super(document, ctx);
    this.weighSample = weighSample;
  }

  // ── PSY ───────────────────────────────────────────────────────────

  /**
   * {@code validateInputPSY}: the three legacy branches (billable event with a
   * mark, billable event with a valid input source, anything else) all made
   * the same call. Legacy quirk kept: errors are raised only when the input
   * PSY is blank ({@code StringUtil.empty(inputPSY, false)}), which never
   * happens here because the caller requires a non-empty input — so the input
   * PSY is only split and copied; the errors come from {@link #validatePSY()}.
   */
  protected void validateInputPSY() {
    String inputPsy = v.str("input_psy");
    validateStratumBillableOrNot(inputPsy, blank(inputPsy)); // StringUtil.empty(inputPSY, false)
  }

  /** {@code validatePSY}: the stored PSY (or company-use stratum) with errors raised. */
  protected void validatePSY() {
    if (billableCheckAllowedOnEventType()) {
      if (v.str("company_use_stratum") == null) {
        validateStratumBillableOrNot(v.str("population_number"), v.str("stratum_number"), v.lng("sampling_year"));
      } else {
        validateStratumBillableOrNot(v.str("company_use_stratum"), true);
      }
    }
  }

  /**
   * {@code validateStratumBillableOrNot(psy, raiseError)}: ST03 not 8/10
   * characters, ST04 bad year, ST01 not on file (billable event and mark);
   * ST05 a PSY on file for a non-billable mark; ST02 a PSY on file for a
   * non-billable event. A billable PSY on file is copied to the
   * population/stratum/year and clears COMPANY_USE_STRATUM; otherwise the PSY
   * becomes the company-use stratum and population/stratum/year are cleared.
   */
  private void validateStratumBillableOrNot(String popStratYear, boolean raiseError) {
    boolean isBillableMark;
    boolean isValidPsy = false;
    boolean invalidYear = false;
    PopStratYear parser = PopStratYear.parse(popStratYear);
    String eventType = v.str("scale_event_type_code");
    String timberMark = v.str("timber_mark");

    isBillableMark = isBillableMark(timberMark);
    if (parser.isValid()) {
      try {
        if (isBillableStratum(parser.population(), parser.stratum(), parser.year())) {
          isValidPsy = true;
        }
      } catch (PopStratYear.InvalidYearException e) {
        invalidYear = true;
      }
    }

    boolean isBillableEventAndMark = false;
    if (isBillableEventType(eventType)) {
      if (isBillableMark) {
        if (!parser.isBillableLength() && raiseError) {
          errors.add(scalingError(CrossReferenceEdit.WeighSlip.PSY_LENGTH_LESS));
        } else if (invalidYear && raiseError) {
          errors.add(scalingError(CrossReferenceEdit.WeighSlip.PSY_LENGTH_TWO_DIGIT_YEAR));
        } else if (!isValidPsy && raiseError) {
          errors.add(scalingError(CrossReferenceEdit.WeighSlip.STRATUM_XREF));
        } else {
          isBillableEventAndMark = true;
        }
      } else if (isValidPsy && raiseError) {
        errors.add(scalingError(CrossReferenceEdit.WeighSlip.NON_BILLABLE_COMPANY));
      }
    } else if (isValidPsy && raiseError) {
      errors.add(scalingError(CrossReferenceEdit.WeighSlip.STRATUM_NON_BILLABLE));
    }

    if (isValidPsy && isBillableEventAndMark) {
      v.set("population_number", parser.population());
      v.set("stratum_number", parser.stratum());
      v.set("sampling_year", parser.yearOrNull());
      v.set("company_use_stratum", null);
    } else {
      v.set("company_use_stratum", parser.companyStratum());
      v.set("population_number", null);
      v.set("stratum_number", null);
      v.set("sampling_year", null);
    }
  }

  /** {@code validateStratumBillableOrNot(population, stratum, year)}: the parts joined, errors raised. */
  private void validateStratumBillableOrNot(String population, String stratum, Long year) {
    validateStratumBillableOrNot(PopStratYear.of(population, stratum, year).psy(), true);
  }

  /** {@code isBillableStratum(psy)}: an 8/10-character valid PSY whose stratum is on file. */
  public boolean isBillableStratum(String psy) {
    PopStratYear parser = PopStratYear.parse(psy);
    if (!parser.isBillableLength() || !parser.isValid()) {
      return false;
    }
    Long year = parser.yearOrNull();
    return year != null && isBillableStratum(parser.population(), parser.stratum(), year);
  }

  /** {@code isBillableStratum(population, stratum, year)}: the stratum is on file. */
  public boolean isBillableStratum(String population, String stratum, Long year) {
    return WeighSampleSupport.findStratum(ref, population, stratum, year).isPresent();
  }

  // ── weights, mark, sample type ────────────────────────────────────

  /** GW01: gross weight must be &gt; 0. */
  protected void validateGrossWeight() {
    if (v.lng("gross_weight") <= 0) {
      errors.add(scalingError(CrossReferenceEdit.WeighSlip.GROSS_WEIGHT));
    }
  }

  /** TW01: tare weight must be less than the gross weight. */
  protected void validateTareWeight() {
    Long gross = v.lng("gross_weight");
    if (gross != null && v.lng("tare_weight") >= gross) {
      errors.add(scalingError(CrossReferenceEdit.WeighSlip.TARE_WEIGHT));
    }
  }

  /** SDW01 grapple weight &lt;= tare; SDW02 grapple weight &gt;= gross (Hbs3 maintenance bug 1625). */
  protected void validateSampleDeductionWeight() {
    long deduction = v.lng("sample_deduction_weight");
    Long tare = v.lng("tare_weight");
    Long gross = v.lng("gross_weight");
    if (tare != null && deduction <= tare) {
      errors.add(scalingError(CrossReferenceEdit.WeighSlip.GRAPPLEWEIGHT_LESSTHAN_TAREWEIGHT));
    }
    if (gross != null && deduction >= gross) {
      errors.add(scalingError(CrossReferenceEdit.WeighSlip.GRAPPLEWEIGHT_MORETHAN_GROSSWEIGHT));
    }
  }

  /** TM01, then for SS/RS: TM08 cash sale, else TM07 cruise based, else TM11 area based (BT5884). */
  @Override
  protected void validateTimberMark() {
    super.validateTimberMark();
    String event = v.str("scale_event_type_code");
    if (SAMPLE_SCALE.equals(event) || REMOTE_SAMPLE_SCALE.equals(event)) {
      // legacy logged and ignored TimberMarkNotFoundException
      Optional<TimberMarkRef> found = ref.timberMark(v.str("timber_mark"));
      if (found.isPresent()) {
        TimberMarkRef mark = found.get();
        if (mark.isCashSale()) {
          errors.add(relatedError(CrossReferenceEdit.WeighSlip.TIMBERMARK_CASH_SCALE));
        } else if (mark.isCruiseBased()) {
          errors.add(relatedError(CrossReferenceEdit.WeighSlip.TIMBERMARK_CRUISE_BASED));
        } else if (mark.isAreaBased()) {
          errors.add(relatedError(CrossReferenceEdit.WeighSlip.TIMBERMARK_AREA_BASED));
        }
      }
    }
  }

  /** SAM01: SS/RS need sample type F or S; SAM02: PS/PD need N or C. */
  protected void validateSampleType() {
    String event = v.str("scale_event_type_code");
    if (SAMPLE_SCALE.equals(event) || REMOTE_SAMPLE_SCALE.equals(event)) {
      if (!validSampleType(SAMPLE_TYPE_FORCED, SAMPLE_TYPE_STATISTICALLY_CHOSEN)) {
        errors.add(scalingError(CrossReferenceEdit.WeighSlip.SAMPLE_TYPE_SAMPLE));
      }
    }
    if (PRIMARY_SCALE.equals(event) || PRIMARY_DEPARTURE.equals(event)) {
      if (!validSampleType(SAMPLE_TYPE_NONE, SAMPLE_TYPE_COMPANY)) {
        errors.add(scalingError(CrossReferenceEdit.WeighSlip.SAMPLE_TYPE_PRIMARY));
      }
    }
  }

  private boolean validSampleType(String type1, String type2) {
    String type = v.str("hbs_sample_type_code");
    return type1.equals(type) || type2.equals(type);
  }

  /** {@code isEmptyPopStratYear}: no population, stratum or sampling year. */
  private boolean isEmptyPopStratYear() {
    return empty(v.str("population_number")) && empty(v.str("stratum_number")) && v.lng("sampling_year") == null;
  }

  /**
   * {@code validateStratumDate}: with a full PSY, re-split it (a bad year is
   * logged and ignored, as legacy), then SD04 scale date before the stratum's
   * effective date, else SD05 after the end of its expiry day.
   */
  protected void validateStratumDate() {
    if (!empty(v.str("population_number")) && !empty(v.str("stratum_number")) && v.lng("sampling_year") != null) {
      PopStratYear parser = PopStratYear.of(v.str("population_number"), v.str("stratum_number"),
          v.lng("sampling_year"));
      v.set("population_number", parser.population());
      v.set("stratum_number", parser.stratum());
      Long year = parser.yearOrNull();
      if (year != null) {
        v.set("sampling_year", year);
      }

      String pop = v.str("population_number");
      String strat = v.str("stratum_number");
      Long samplingYear = v.lng("sampling_year");
      StratumRef stratum = WeighSampleSupport.findStratum(ref, pop, strat, samplingYear).orElseThrow(() ->
          new IllegalStateException("Unexpected stratum removal. Population/Stratum/Year: " + pop + strat
              + samplingYear));
      Date scaleDate = v.date("scale_date");
      if (WeighSampleSupport.before(scaleDate, stratum.effectiveDate())) {
        errors.add(billingError(CrossReferenceEdit.WeighSlip.SCALE_DATE_STRATUM_EFF));
      } else if (WeighSampleSupport.after(scaleDate, ScaledLogXref.endDay(stratum.expiryDate()))) {
        errors.add(billingError(CrossReferenceEdit.WeighSlip.SCALE_DATE_STRATUM_EXP));
      }
    }
  }

  // ── billable ──────────────────────────────────────────────────────

  private static boolean alwaysBillableEventType(String eventType) {
    return SAMPLE_SCALE.equals(eventType) || REMOTE_SAMPLE_SCALE.equals(eventType);
  }

  private static boolean conditionallyBillableEventType(String eventType) {
    return PRIMARY_SCALE.equals(eventType) || PRIMARY_DEPARTURE.equals(eventType);
  }

  /** {@code billableCheckAllowedOnEventType}: PS/PD need a mark (or input source); other events always. */
  private boolean billableCheckAllowedOnEventType() {
    String event = v.str("scale_event_type_code");
    String mark = v.str("timber_mark") != null ? v.str("timber_mark") : v.str("input_source");
    return (conditionallyBillableEventType(event) && !empty(mark)) || !conditionallyBillableEventType(event);
  }

  /** {@code isBillableEventType()} (BT 8253): SS/RS/PS/PD with a billable mark. */
  public boolean isBillableEventType() {
    String event = v.str("scale_event_type_code");
    if (alwaysBillableEventType(event) || conditionallyBillableEventType(event)) {
      return isBillableMark(v.str("timber_mark"));
    }
    return false;
  }

  /** {@code isBillableEventType(eventType)} (BT 8658): SS, RS, PS or PD. */
  public boolean isBillableEventType(String eventType) {
    return conditionallyBillableEventType(eventType) || alwaysBillableEventType(eventType);
  }

  /** {@code isBillableScaleReturn}: no errors, a billable event and mark, and not a field scale. */
  @Override
  public boolean isBillableScaleReturn() {
    if (errorsFound()) {
      return false;
    }
    boolean fieldScale = Boolean.TRUE.equals(v.yes("field_scale_ind"));
    return isBillableEventType() && !fieldScale;
  }

  // ── destination, copy, validate ───────────────────────────────────

  /**
   * {@code validateDestination} (BT 5954): upper-cases DESTINATION; SSZZ waste
   * site, CBDS00 dedicated cruise-based site, SS01 not on file.
   */
  protected void validateDestination() {
    if (v.str("destination") != null) {
      String destination = v.str("destination").toUpperCase();
      v.set("destination", destination);
      Optional<ScaleSiteRef> site = ref.scaleSite(destination);
      if (site.isPresent()) {
        if (ScaleSiteRef.SCALE_SITE_WASTE.equals(site.get().scaleSiteIdNmbr())) {
          errors.add(scalingError(CrossReferenceEdit.Common.SCALE_SITE_WASTE));
        }
        if (site.get().isDedicatedCruiseBasedSite()) {
          errors.add(scalingError(HbsEditError.CBDS00));
        }
      } else {
        errors.add(scalingError(CrossReferenceEdit.Common.SCALE_SITE_XREF));
      }
    }
  }

  @Override
  protected void validateAndCopyInputFields() {
    // required field for all scale event type codes
    if (empty(v.str("scale_site_id_nmbr"))) {
      validateInputScaleSite();
    }

    // not required for all event types (inclusion edits already done)
    if (empty(v.str("timber_mark")) && empty(v.str("timber_brand")) && !empty(v.str("input_source"))) {
      validateInputSource();
    } else if (!empty(v.str("timber_brand"))) {
      validateTimberBrand(v.str("timber_brand"));
    }

    // cut block: copied without the edit (CR 63, 2003-02-20)
    if (empty(v.str("cut_block_id")) && !empty(v.str("input_cutblock_id"))) {
      v.set("cut_block_id", v.str("input_cutblock_id"));
    }

    // required for all event types but Company Use (not permitted)
    if (isEmptyPopStratYear() && empty(v.str("company_use_stratum")) && !empty(v.str("input_psy"))) {
      validateInputPSY();
    }

    if (empty(v.str("primary_license_number"))) {
      validateInputPrimaryScalerLicense();
    }

    if (empty(v.str("secondary_license_number")) && !empty(v.str("input_secondary_scaler_lic"))) {
      validateInputSecondaryScalerLicense();
    }

    if (empty(v.str("signing_license_number")) && !empty(v.str("input_signing_scaler_lic"))) {
      validateInputSigningScalerLicense();
    }
  }

  @Override
  protected void validate() {
    // BT 8637 cross checks
    validateXCheckLogTally();
    validateXCheckSampleLogTally();

    // BT 8253
    String deck = v.str("field_scale_deck_id");
    if (!empty(deck)) {
      if (!WeighSampleSupport.isAlphaNumeric(deck) || !deck.equals(deck.toUpperCase())) {
        errors.add(scalingError(CrossReferenceEdit.WeighSlip.FIELD_DECK_ID_CASE));
      } else {
        validateFieldDeck();
      }
      if (!blank(v.str("population_number")) && v.lng("sampling_year") != null) { // empty(pop, false)
        PopulationRef population = WeighSampleSupport.findPopulation(ref, v.str("population_number"),
            v.lng("sampling_year"));
        if (population.isDefaultRatio()) {
          errors.add(scalingError(CrossReferenceEdit.WeighSlip.FIELD_DECK_FOR_DEFAULT_RATIO));
        }
      }
    }

    if (!empty(v.str("scale_site_id_nmbr"))) {
      validateScaleSite();
    }

    // BT 5954
    String eventType = v.str("scale_event_type_code");
    if (REMOTE_SAMPLE_SCALE.equals(eventType) || SAMPLE_SCALE.equals(eventType)) {
      if (!blank(v.str("population_number")) && v.lng("sampling_year") != null) { // empty(pop, false)
        PopulationRef population = WeighSampleSupport.findPopulation(ref, v.str("population_number"),
            v.lng("sampling_year"));
        if (population.isDefaultRatio()) {
          errors.add(scalingError(CrossReferenceEdit.WeighSlip.DEFAULT_RATIO));
        }
      }
    }

    if (!empty(v.str("destination"))) {
      if (REMOTE_SAMPLE_SCALE.equals(eventType) || PRIMARY_DEPARTURE.equals(eventType)
          || REMOTE_RED_TAG.equals(eventType) || DEPARTURE.equals(eventType)) {
        validateDestination();
      }
    }

    validateWeighSlip(v.str("weigh_slip_number"));
    validateScaleDate();

    // BT 5790
    if (!SB4_RESCALE.equals(eventType) && !DEPARTURE.equals(eventType) && !COMPANY_USE.equals(eventType)
        && !RED_TAG.equals(eventType) && !REMOTE_RED_TAG.equals(eventType)) {
      validateRate();
    }

    if (v.lng("gross_weight") != null) {
      validateGrossWeight();
    }
    if (v.lng("tare_weight") != null) {
      validateTareWeight();
    }
    if (v.lng("sample_deduction_weight") != null) {
      validateSampleDeductionWeight();
    }

    // BT 5790
    if (!SB4_RESCALE.equals(eventType) && !DEPARTURE.equals(eventType) && !COMPANY_USE.equals(eventType)) {
      if (!empty(v.str("timber_mark"))) {
        validateTimberMark();
        validateBillToClientAndLocation(billToFromTimberMark(v.str("timber_mark"))); // BT 6495
      }
    }

    if (Boolean.TRUE.equals(v.yes("field_scale_ind")) && empty(v.str("field_scale_deck_id"))) {
      errors.add(scalingError(CrossReferenceEdit.Common.FIELD_SCALE_DECK_ID));
    }
    // BT11301
    if (Boolean.TRUE.equals(v.yes("original_version_ind")) && Boolean.FALSE.equals(v.yes("field_scale_ind"))) {
      if (v.str("field_scale_deck_id") != null) {
        v.set("field_scale_deck_id", v.str("field_scale_deck_id").trim());
      }
      if (!empty(v.str("field_scale_deck_id"))) {
        errors.add(scalingError(CrossReferenceEdit.Common.FIELD_SCALE_DECK_ID_NOT_PRESENT));
      }
    }

    if (!empty(v.str("hbs_sample_type_code"))) {
      validateSampleType();
    }

    if (v.str("signing_license_number") != null) {
      validateSigningScalerLicense();
    }

    if (!isEmptyPopStratYear() || !empty(v.str("company_use_stratum"))) {
      validatePSY();
    }

    validateStratumDate();

    if (!isEmptyPopStratYear()) {
      // BT 6495
      validateCopyToClientAndLocation(copyToFromStratum(v.str("population_number"), v.str("stratum_number"),
          v.lng("sampling_year")));
      // BT 7012
      validateGradeConstraint(v.str("timber_mark"), v.date("scale_date"), v.str("population_number"),
          v.str("stratum_number"), v.lng("sampling_year"), LOG_TYPE_WEIGH);
    }
  }

  // ── cross checks (BT 8637 / 10414 / 8253) ─────────────────────────

  /**
   * {@code calculateSampleWeight} (BT 8637): gross − grapple weight, or gross −
   * tare when there is no (or a zero) grapple weight; missing weights count 0.
   */
  Long calculateSampleWeight() {
    long gross = 0;
    long deduction = 0;
    Long sdw = v.lng("sample_deduction_weight");
    if (sdw == null || sdw == 0) {
      if (v.lng("tare_weight") != null) {
        deduction = v.lng("tare_weight");
      }
    } else if (sdw > 0) {
      deduction = sdw;
    }
    if (v.lng("gross_weight") != null) {
      gross = v.lng("gross_weight");
    }
    return gross - deduction;
  }

  /**
   * {@code validateXCheckSampleLogTally} (BT 10414): MM05 when the sample
   * tallies of this SS/RS slip conflict. Without a population/stratum/year the
   * company-use stratum is split (a bad year searches population "" /
   * stratum "" / no year, which never matches).
   */
  protected void validateXCheckSampleLogTally() {
    String population;
    String stratum;
    Long samplingYear;
    if (isEmptyPopStratYear()) {
      PopStratYear parser = PopStratYear.parse(v.str("company_use_stratum"));
      population = parser.population();
      stratum = parser.stratum();
      samplingYear = parser.yearOrNull();
      if (samplingYear == null) {
        population = "";
        stratum = "";
      }
    } else {
      population = v.str("population_number");
      stratum = v.str("stratum_number");
      samplingYear = v.lng("sampling_year");
    }
    String event = v.str("scale_event_type_code");
    if (SAMPLE_SCALE.equals(event)) {
      long errorState = weighSample.sampleTallyConflict(v.str("scale_site_id_nmbr"), null,
          v.str("weigh_slip_number"), event, v.str("timber_mark"), population, stratum, samplingYear,
          calculateSampleWeight());
      if (errorState == 1) {
        errors.add(scalingError(CrossReferenceEdit.WeighSlip.LOCAL_REMOTE_SAMPLE));
      }
    } else if (REMOTE_SAMPLE_SCALE.equals(event)) {
      long errorState = weighSample.sampleTallyConflict(v.str("destination"), v.str("scale_site_id_nmbr"),
          v.str("weigh_slip_number"), event, v.str("timber_mark"), population, stratum, samplingYear,
          calculateSampleWeight());
      if (errorState == 1) {
        errors.add(scalingError(CrossReferenceEdit.WeighSlip.LOCAL_REMOTE_SAMPLE));
      }
    }
  }

  /** {@code validateXCheckLogTally} (BT 8637): MM06 when more than one red-tag log tally uses this slip. */
  protected void validateXCheckLogTally() {
    String event = v.str("scale_event_type_code");
    if (RED_TAG.equals(event)) {
      long count = weighSample.redTagLogTallyCount(v.str("scale_site_id_nmbr"), null, v.str("weigh_slip_number"),
          event);
      if (count > 1) {
        errors.add(scalingError(CrossReferenceEdit.WeighSlip.LOCAL_REMOTE_REDTAG));
      }
    } else if (REMOTE_RED_TAG.equals(event)) {
      long count = weighSample.redTagLogTallyCount(v.str("destination"), v.str("scale_site_id_nmbr"),
          v.str("weigh_slip_number"), event);
      if (count > 1) {
        errors.add(scalingError(CrossReferenceEdit.WeighSlip.LOCAL_REMOTE_REDTAG));
      }
    }
  }

  /** {@code validateFieldDeck} (BT 8253): FSX02 when no field-scale estimate exists for the deck and mark. */
  protected void validateFieldDeck() {
    if (weighSample.fieldScaleEstimateCount(v.str("field_scale_deck_id"), v.str("timber_mark")) == 0) {
      errors.add(scalingError(CrossReferenceEdit.WeighSlip.FIELD_DECK_ID));
    }
  }

  /** Empty in legacy (BT 10013 only applies to sample tallies). */
  @Override
  protected void validatFieldScaleDeckId() {
  }
}
