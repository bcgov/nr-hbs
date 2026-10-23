package ca.bc.gov.nrs.hbs.api.detailreturns.validation;

import ca.bc.gov.nrs.hbs.api.detailreturns.model.DetailDocument;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.LogLine;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.Row;
import ca.bc.gov.nrs.hbs.api.detailreturns.validation.CrossReferenceEdit.CheckScale;
import ca.bc.gov.nrs.hbs.api.detailreturns.validation.CrossReferenceEdit.LogTallyCommon.Log;
import ca.bc.gov.nrs.hbs.api.detailreturns.validation.ReferenceData.PopulationRef;
import ca.bc.gov.nrs.hbs.api.detailreturns.validation.ReferenceData.StratumRef;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Edits of a sample scale log tally, ported line by line from
 * {@code $E/dac/detail/control/SampleLogTallyValidator} (SAMPLE_LOG_TALLY /
 * SAMPLE_TALLY_DETAIL columns). Shared log/check-scale edits come from
 * {@link LogTallyCommonValidator}; the weigh slip cross checks (BT 8637 /
 * 14566) and the BT 6519 expired-stratum allowance use {@link WeighSampleLookups}.
 *
 * <p>Legacy quirks kept: the stratum date edit only runs when INPUT_PSY is a
 * well-formed PSY (BT-8174), so a tally entered online without INPUT_PSY skips
 * it. Legacy NullPointerExceptions on a missing sample weight / net volume
 * (which aborted the run) are avoided: the ratio is skipped without a sample
 * weight (SW01 is already raised) and a missing line volume counts 0.
 */
public class SampleLogTallyValidator extends LogTallyCommonValidator {

  /** BT6519. */
  private static final long MILLIS_PER_DAY = 1000L * 60 * 60 * 24;
  public static final long ALLOCATED_DATE = 90;

  protected final WeighSampleLookups weighSample;

  public SampleLogTallyValidator(DetailDocument document, ValidationContext ctx, TallyLookups tally,
                                 WeighSampleLookups weighSample) {
    super(document, ctx, tally);
    this.weighSample = weighSample;
  }

  // ── stratum ───────────────────────────────────────────────────────

  /**
   * {@code validateStratumDate}: SD04 scale date before the stratum's
   * effective date; SD05 after the end of its expiry day — for a check scale
   * always, for a SS/RS sample only when no SS/RS weigh slip of this
   * stratum/slip number (at the scale site, or the originating site for RS)
   * was scaled inside the stratum period and within {@link #ALLOCATED_DATE}
   * days of the sample (BT6519).
   */
  protected void validateStratumDate() {
    String pop = v.str("population_number");
    String strat = v.str("stratum_number");
    Long year = v.lng("sampling_year");
    if (!empty(pop) && !empty(strat) && year != null && !isPopOrStratrNullOrEmpty(v.str("input_psy"))) {
      StratumRef stratum = WeighSampleSupport.findStratum(ref, pop, strat, year).orElseThrow(() ->
          new IllegalStateException("Unexpected stratum removal. Population/Stratum/Year: " + pop + strat + year));
      Date scaleDate = v.date("scale_date");
      if (WeighSampleSupport.before(scaleDate, stratum.effectiveDate())) {
        errors.add(billingError(CrossReferenceEdit.SampleLogTally.SCALE_DATE_STRATUM_EFF));
      }
      // BT6519 (25-02-2005)
      if (WeighSampleSupport.after(scaleDate, ScaledLogXref.endDay(stratum.expiryDate()))) {
        String event = v.str("scale_event_type_code");
        if (CHECK_SCALE.equals(event)) {
          errors.add(billingError(CrossReferenceEdit.SampleLogTally.SCALE_DATE_STRATUM_EXP));
        } else if (SAMPLE_SCALE.equals(event)) {
          expiredStratumAllowance(v.str("scale_site_id_nmbr"), SAMPLE_SCALE, stratum, scaleDate);
        } else if (REMOTE_SAMPLE_SCALE.equals(event)) {
          expiredStratumAllowance(v.str("orig_redtag_scale_site_id_nmbr"), REMOTE_SAMPLE_SCALE, stratum, scaleDate);
        }
      }
    }
  }

  /** The BT6519 loop over {@code WeighSlipEntityHome.findByCriteria}: SD05 unless a matching slip counts. */
  private void expiredStratumAllowance(String site, String eventType, StratumRef stratum, Date sampleDate) {
    List<Row> slips = weighSample.weighSlipsByCriteria(site, v.str("population_number"), v.str("stratum_number"),
        v.lng("sampling_year"), v.str("sampled_weigh_slip"));
    long count = 1;
    if (!slips.isEmpty()) {
      Date effective = stratum.effectiveDate();
      Date expiry = stratum.expiryDate();
      for (Row slip : slips) {
        Date slipDate = slip.date("scale_date");
        if (eventType.equals(slip.str("scale_event_type_code"))) {
          float days = daysDifference(sampleDate, slipDate);
          // legacy precedence: after(eff) || (equals(eff) && before(endDay(exp))) || equals(exp)
          if (WeighSampleSupport.after(slipDate, effective)
              || WeighSampleSupport.same(slipDate, effective)
                  && WeighSampleSupport.before(slipDate, ScaledLogXref.endDay(expiry))
              || WeighSampleSupport.same(slipDate, expiry)) {
            if (days <= ALLOCATED_DATE) {
              count++;
            }
          }
        }
      }
      if (count == 1) {
        errors.add(billingError(CrossReferenceEdit.SampleLogTally.SCALE_DATE_STRATUM_EXP));
      }
    }
    if (slips.isEmpty()) {
      errors.add(billingError(CrossReferenceEdit.SampleLogTally.SCALE_DATE_STRATUM_EXP));
    }
  }

  /**
   * {@code validateInputPSY}: ST01 for a malformed input PSY (BT-8174), a bad
   * year or a stratum not on file; a stratum on file is copied to the
   * population/stratum/year. (The legacy bad-year branch, which would add a
   * second ST01, is unreachable: BT-8174 already rejects an unreadable year.)
   */
  protected void validateInputPSY() {
    String inputPsy = v.str("input_psy");
    if (isPopOrStratrNullOrEmpty(inputPsy)) {
      errors.add(scalingError(CrossReferenceEdit.SampleLogTally.STRATUM_XREF));
    } else {
      PopStratYear parser = PopStratYear.parse(inputPsy);
      String population = parser.population();
      String stratum = parser.stratum();
      Long year = null;
      try {
        year = parser.year();
      } catch (PopStratYear.InvalidYearException e) {
        errors.add(scalingError(CrossReferenceEdit.SampleLogTally.STRATUM_XREF));
      }
      if (WeighSampleSupport.findStratum(ref, population, stratum, year).isPresent()) {
        v.set("population_number", population);
        v.set("stratum_number", stratum);
        v.set("sampling_year", year);
      } else {
        errors.add(scalingError(CrossReferenceEdit.SampleLogTally.STRATUM_XREF));
      }
    }
  }

  /**
   * {@code isPopOrStratrNullOrEmpty} (BT-8174): true when the year cannot be
   * read, the trimmed PSY is shorter than 8, the population/stratum is short,
   * or the population or stratum mixes letters and digits.
   */
  static boolean isPopOrStratrNullOrEmpty(String inputPsy) {
    boolean checkNullOrEmpty = false;
    PopStratYear parser = PopStratYear.parse(inputPsy);
    String population = parser.population();
    String stratum = parser.stratum();
    if (parser.yearOrNull() == null) {
      checkNullOrEmpty = true;
    }
    if (inputPsy == null || inputPsy.trim().length() < 8) {
      checkNullOrEmpty = true;
    } else if (population.trim().length() < 4 || stratum.trim().length() < 2) {
      checkNullOrEmpty = true;
    } else if (!(PopStratYear.allLetters(population) ^ PopStratYear.allNumbers(population))
        || !(PopStratYear.allLetters(stratum) ^ PopStratYear.allNumbers(stratum))) {
      checkNullOrEmpty = true;
    }
    return checkNullOrEmpty;
  }

  /** {@code validatedInputPSY}: population, stratum and year are set. */
  private boolean validatedInputPSY() {
    return !empty(v.str("population_number")) && !empty(v.str("stratum_number")) && v.lng("sampling_year") != null;
  }

  // ── version edits ─────────────────────────────────────────────────

  /** SW01: sample weight must be present and &gt; 0. */
  protected void validateSampleWeight() {
    Long weight = v.lng("sample_weight");
    if (weight == null || weight <= 0) {
      errors.add(scalingError(CrossReferenceEdit.SampleLogTally.SAMPLE_WEIGHT));
    }
  }

  /** TM01, TM08 cash sale, then TM07 cruise based or else TM11 area based (BT5884). */
  @Override
  protected void validateTimberMark() {
    super.validateTimberMark();
    String mark = v.str("timber_mark");
    if (isCashSaleMark(mark)) {
      errors.add(relatedError(CrossReferenceEdit.SampleLogTally.TIMBERMARK_CASH_SCALE));
    }
    if (isCruiseBasedMark(mark)) {
      errors.add(relatedError(CrossReferenceEdit.SampleLogTally.TIMBERMARK_CRUISE_BASED));
    } else if (isAreaBasedMark(mark)) {
      errors.add(relatedError(CrossReferenceEdit.SampleLogTally.TIMBERMARK_AREA_BASED));
    }
  }

  /**
   * {@code isBillableScaleReturn}: a check scale is billable when it replaces
   * the original; another sample when it is not replaced by a check scale.
   */
  @Override
  public boolean isBillableScaleReturn() {
    Boolean replaces = v.yes("check_replaces_original_ind");
    if (isCheckScale()) {
      return Boolean.TRUE.equals(replaces);
    } else if (replaces != null) {
      return !replaces;
    }
    return true;
  }

  /** {@code validateAndCopyVersionInputFields}. */
  protected void validateAndCopyVersionInputFields() {
    if (empty(v.str("scale_site_id_nmbr"))) {
      validateInputScaleSite();
    }
    if (empty(v.str("timber_mark"))) {
      validateInputSource();
    }
    // MR03 bug 1361: copy the input PSY when the PSY is not yet set
    if (!empty(v.str("input_psy")) && !validatedInputPSY()) {
      validateInputPSY();
    }
    if (empty(v.str("orig_redtag_scale_site_id_nmbr")) && !empty(v.str("input_orig_redtag_scl_site"))) {
      validateInputOriginatingScaleSite();
    }
    // BT 8261
    if (!empty(v.str("orig_redtag_scale_site_id_nmbr"))) {
      validateOrgScaleSite();
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
    validateXCheckWeighSlip(); // BT 8637
  }

  /** {@code validateVersion}. */
  protected void validateVersion() {
    if (!empty(v.str("population_number")) && !empty(v.str("stratum_number"))) {
      PopulationRef population = WeighSampleSupport.findPopulation(ref, v.str("population_number"),
          v.lng("sampling_year"));
      if (population.isDefaultRatio()) {
        errors.add(scalingError(CrossReferenceEdit.SampleLogTally.DEFAULT_RATIO));
      }
    }
    if (!empty(v.str("scale_site_id_nmbr"))) {
      validateScaleSite();
    }
    if (!empty(v.str("orig_redtag_scale_site_id_nmbr"))) {
      validateCruiseBasedScaleSite(v.str("orig_redtag_scale_site_id_nmbr"), HbsEditError.CBOS00);
    }
    if (!empty(v.str("timber_mark"))) {
      validateTimberMark();
      validateBillToClientAndLocation(billToFromTimberMark(v.str("timber_mark"))); // BT 6495
    }
    // BT6495
    if (!empty(v.str("population_number")) && !empty(v.str("stratum_number")) && v.lng("sampling_year") != null) {
      validateCopyToClientAndLocation(copyToFromStratum(v.str("population_number"), v.str("stratum_number"),
          v.lng("sampling_year")));
    }
    validateSampleWeight();
    validateScaleDate();
    validateStratumDate();
    validateRate();
    if (v.str("signing_license_number") != null) {
      validateSigningScalerLicense();
    }
    if (!empty(v.str("sampled_weigh_slip"))) {
      validateWeighSlip(v.str("sampled_weigh_slip"));
    }
    if (isCheckScale()) {
      validateOriginal();
    }
    validateLogCount();
    // BT 7012
    validateGradeConstraint(v.str("timber_mark"), v.date("scale_date"), v.str("population_number"),
        v.str("stratum_number"), v.lng("sampling_year"), LOG_TYPE_SAMPLE);
  }

  /**
   * {@code validateAgainstOriginal}: CHKSD1 original scaled after the check,
   * CHKSD2 more than 45 days before; for a CS: CHKS03 original is a check
   * scale, CHKS06 already replaced, CHKS07 bad state, CHKS08 different site or
   * sampled slip, CHKS09 different mark/PSY/sample weight; then MM07/MM08 (no)
   * and MM17/MM18 (several) active weigh slips sampled (BT 14566).
   */
  @Override
  protected DetailDocument validateAgainstOriginal(DetailDocument originalActiveDocument) {
    if (originalActiveDocument == null) return null;
    Row original = originalActiveDocument.version().row();
    Date scaleDate = v.date("scale_date");
    Date checkScaleDate = Timestamp.valueOf(new Timestamp(scaleDate.getTime()).toLocalDateTime().minusDays(45));

    if (WeighSampleSupport.after(original.date("scale_date"), scaleDate)) {
      errors.add(scalingError(CheckScale.AFTER_CHECKSCALE));
    } else if (WeighSampleSupport.before(original.date("scale_date"), checkScaleDate)) {
      errors.add(scalingError(CheckScale.BEFORE_CHECKSCALE));
    } else if (CHECK_SCALE.equals(v.str("scale_event_type_code"))) {
      if (CHECK_SCALE.equals(original.str("scale_event_type_code"))) {
        errors.add(scalingError(CheckScale.ORIGINAL_CHECKSSCALE));
      } else if (Boolean.TRUE.equals(original.yes("check_replaces_original_ind"))) {
        errors.add(scalingError(CheckScale.CHECKSCALE));
      } else if (!validOriginalStateCombination(originalActiveDocument)) {
        errors.add(scalingError(CheckScale.INVALID_STATE));
      } else if (!orEmpty(original.str("scale_site_id_nmbr")).equals(v.str("scale_site_id_nmbr"))
          || !orEmpty(original.str("sampled_weigh_slip")).equals(v.str("sampled_weigh_slip"))) {
        errors.add(scalingError(CheckScale.SCALE_SITE));
      } else if (!orEmpty(original.str("timber_mark")).equals(v.str("timber_mark"))
          || !orEmpty(original.str("population_number")).equals(v.str("population_number"))
          || !orEmpty(original.str("stratum_number")).equals(v.str("stratum_number"))
          || !orZero(original.lng("sampling_year")).equals(v.lng("sampling_year"))
          || !orZero(original.lng("sample_weight")).equals(v.lng("sample_weight"))) {
        errors.add(scalingError(CheckScale.POPULATION));
      }

      String originalEvent = original.str("scale_event_type_code");
      if (SAMPLE_SCALE.equals(originalEvent)) {
        long count = weighSample.sampledWeighSlipCount(v.str("scale_site_id_nmbr"), null,
            v.str("sampled_weigh_slip"), SAMPLE_SCALE, true); // BT 14566
        if (count == 0) {
          errors.add(lookupError(CheckScale.LOCAL_SAMPLE));
        }
        if (count > 1) {
          errors.add(lookupError(CheckScale.WEIGH_SLIP_FOUND_MORE_CHECK_SCALE));
        }
      } else if (REMOTE_SAMPLE_SCALE.equals(originalEvent)) {
        long count = weighSample.sampledWeighSlipCount(v.str("scale_site_id_nmbr"), null,
            v.str("sampled_weigh_slip"), REMOTE_SAMPLE_SCALE, true); // BT 14566
        if (count == 0) {
          errors.add(lookupError(CheckScale.REMOTE_SAMPLE));
        }
        if (count > 1) {
          errors.add(lookupError(CheckScale.WEIGH_SLIP_FOUND_MORE_REMOTE_CHECK_SCALE));
        }
      }
    }
    return originalActiveDocument;
  }

  private static String orEmpty(String s) {
    return s == null ? "" : s;
  }

  private static Long orZero(Long l) {
    return l == null ? Long.valueOf(0) : l;
  }

  // ── logs ──────────────────────────────────────────────────────────

  /** {@code validateLog}: LT01, TP01/TPMax, BT01/BTMax, ISB789. */
  protected void validateLog(LogLine log) {
    validateLength(log);
    validateTop(log);
    validateButt(log);
    validateGrade(log);
  }

  /** {@code validateAndCopyLogsInputFields}: species/product/grade of each line (each SPG error once per tally). */
  protected void validateAndCopyLogsInputFields() {
    List<String> errorTypeLogged = new ArrayList<>(16);
    String gradeScheduleType = gradeScheduleType(v.str("timber_mark"), v.date("scale_date"));
    for (LogLine log : version.logs()) {
      String errorType = validateInputSpeciesProductGrade(log, v.date("scale_date"), v.str("timber_mark"),
          errorTypeLogged, gradeScheduleType);
      if (errorType != null) {
        errorTypeLogged.add(errorType);
      }
    }
  }

  /**
   * {@code validateLogs} (BT 5216): the line edits, LG00 duplicate log number
   * (BT 14847), then SR01 (lookup) when the sample volume / weight ratio is 10
   * m³/t or more.
   */
  protected void validateLogs() {
    Long weight = v.lng("sample_weight");
    BigDecimal sampleVolume = BigDecimal.ZERO;
    Set<Long> logNumbers = new HashSet<>();
    for (LogLine log : version.logs()) {
      validateLog(log);
      if (!logNumbers.add(log.row().lng("log_number"))) {
        errors.add(logError(log, Log.DUPLICATE_LOG_NUMBER));
      }
      BigDecimal net = log.row().num("net_volume");
      // legacy: new BigDecimal(getNetVolume().floatValue()) — float precision kept
      sampleVolume = sampleVolume.add(new BigDecimal(net == null ? 0f : net.floatValue()));
    }
    if (weight != null && !WeighSampleSupport.isValidRatio(sampleVolume, new BigDecimal(weight))) {
      errors.add(lookupError(CrossReferenceEdit.SampleLogTally.SAMPLE_RATIO));
    }
  }

  @Override
  protected void validateAndCopyInputFields() {
    validateAndCopyVersionInputFields();
    validateAndCopyLogsInputFields();
  }

  /** {@code validate}: version edits, then log edits and the sample ratio (BT 5216). */
  @Override
  protected void validate() {
    validateVersion();
    validateLogs();
  }

  // ── originating site, weigh slip cross checks, deck ───────────────

  /** {@code validateOrgScaleSite} (BT 8261): SSZZ waste originating site, SS01 not on file. */
  protected void validateOrgScaleSite() {
    String orgScaleSite = v.str("orig_redtag_scale_site_id_nmbr").toUpperCase();
    if (ref.scaleSite(orgScaleSite).isPresent()) {
      if (ReferenceData.ScaleSiteRef.SCALE_SITE_WASTE.equalsIgnoreCase(orgScaleSite)) {
        errors.add(scalingError(CrossReferenceEdit.Common.SCALE_SITE_WASTE));
      }
    } else {
      errors.add(scalingError(CrossReferenceEdit.Common.SCALE_SITE_XREF));
    }
  }

  /** {@code validateLocalWeighSlip} (BT 8637 / 14566): MM01 none, MM11 several, else the MM09/MM2x checks. */
  private void validateLocalWeighSlip() {
    long count = weighSample.sampledWeighSlipCount(v.str("scale_site_id_nmbr"),
        v.str("orig_redtag_scale_site_id_nmbr"), v.str("sampled_weigh_slip"), v.str("scale_event_type_code"), false);
    if (count == 0) {
      errors.add(lookupError(CrossReferenceEdit.SampleLogTally.WEIGH_SLIP));
    } else if (count > 1) {
      errors.add(lookupError(CrossReferenceEdit.SampleLogTally.WEIGH_SLIP_FOUND_MORE));
    } else {
      // legacy NPE when the finder returned nothing; skipped here
      weighSample.sampledWeighSlip(v.str("sampled_weigh_slip"), v.str("scale_site_id_nmbr"))
          .ifPresent(this::checkForErrors);
    }
  }

  /** {@code validateRemoteWeighSlip} (BT 8637 / 14566): MM03 none, MM13 several, else the MM10/MM4x checks. */
  private void validateRemoteWeighSlip() {
    long count = weighSample.sampledWeighSlipCount(v.str("scale_site_id_nmbr"),
        v.str("orig_redtag_scale_site_id_nmbr"), v.str("sampled_weigh_slip"), v.str("scale_event_type_code"), false);
    if (count == 0) {
      errors.add(lookupError(CrossReferenceEdit.SampleLogTally.WEIGH_SLIP_REMOTE));
    } else if (count > 1) {
      errors.add(lookupError(CrossReferenceEdit.SampleLogTally.WEIGH_SLIP_FOUND_MORE_REMOTE));
    } else {
      weighSample.sampledWeighSlip(v.str("sampled_weigh_slip"), v.str("orig_redtag_scale_site_id_nmbr"))
          .ifPresent(this::checkForErrors);
    }
  }

  /** {@code validateXCheckWeighSlip} (BT 8637). */
  protected void validateXCheckWeighSlip() {
    String event = v.str("scale_event_type_code");
    if (SAMPLE_SCALE.equals(event)) {
      validateLocalWeighSlip();
    } else if (REMOTE_SAMPLE_SCALE.equals(event)) {
      validateRemoteWeighSlip();
    }
  }

  /**
   * {@code checkForErrors} (BT 14566 / BT11534): the sampled weigh slip must
   * match the tally — scale date not after the sample (MM09/MM10, scaling),
   * event type, population, sampling year, stratum, mark, net weight (gross −
   * grapple weight, or gross − tare) and, for RS, destination = scale site
   * (MM21-MM26 / MM41-MM47, lookup).
   */
  void checkForErrors(Row weighSlip) {
    long gross = weighSlip.lng("gross_weight") == null ? 0 : weighSlip.lng("gross_weight");
    long tare = weighSlip.lng("tare_weight") == null ? 0 : weighSlip.lng("tare_weight");
    long sampleDed = weighSlip.lng("sample_deduction_weight") == null ? 0 : weighSlip.lng("sample_deduction_weight");
    long netWeight = sampleDed != 0 ? gross - sampleDed : gross - tare;

    boolean local = SAMPLE_SCALE.equals(v.str("scale_event_type_code"));
    boolean remote = REMOTE_SAMPLE_SCALE.equals(v.str("scale_event_type_code"));
    if (!local && !remote) {
      return;
    }
    if (isSampleTallyScaleDateLessThenWeightSlipScaleDate(weighSlip)) {
      errors.add(scalingError(local
          ? CrossReferenceEdit.SampleLogTally.WEIGH_SLIP_FOUND_UNMATCHING_SAMPLE_BEFORE_SLIP
          : CrossReferenceEdit.SampleLogTally.WEIGH_SLIP_REMOTE_FOUND_UNMATCHING_SAMPLE_BEFORE_SLIP));
    }
    String wsEvent = weighSlip.str("scale_event_type_code");
    if (wsEvent != null && !wsEvent.equals(v.str("scale_event_type_code"))) {
      errors.add(lookupError(local ? CrossReferenceEdit.SampleLogTally.WEIGH_SLIP_FOUND_UNMATCHING_EVENTTYPE
          : CrossReferenceEdit.SampleLogTally.WEIGH_SLIP_REMOTE_FOUND_UNMATCHING_EVENTTYPE));
    }
    if (differs(weighSlip.str("population_number"), v.str("population_number"))) {
      errors.add(lookupError(local ? CrossReferenceEdit.SampleLogTally.WEIGH_SLIP_FOUND_UNMATCHING_POP
          : CrossReferenceEdit.SampleLogTally.WEIGH_SLIP_REMOTE_FOUND_UNMATCHING_POP));
    }
    Long wsYear = weighSlip.lng("sampling_year");
    Long year = v.lng("sampling_year");
    if ((wsYear == null && year != null) || (year == null && wsYear != null)
        || (wsYear != null && !wsYear.toString().equals(year.toString()))) {
      errors.add(lookupError(local ? CrossReferenceEdit.SampleLogTally.WEIGH_SLIP_FOUND_UNMATCHING_YEAR
          : CrossReferenceEdit.SampleLogTally.WEIGH_SLIP_REMOTE_FOUND_UNMATCHING_YEAR));
    }
    if (differs(weighSlip.str("stratum_number"), v.str("stratum_number"))) {
      errors.add(lookupError(local ? CrossReferenceEdit.SampleLogTally.WEIGH_SLIP_FOUND_UNMATCHING_STRAT
          : CrossReferenceEdit.SampleLogTally.WEIGH_SLIP_REMOTE_FOUND_UNMATCHING_STRAT));
    }
    if (differs(weighSlip.str("timber_mark"), v.str("timber_mark"))) {
      errors.add(lookupError(local ? CrossReferenceEdit.SampleLogTally.WEIGH_SLIP_FOUND_UNMATCHING_TIMBERMARK
          : CrossReferenceEdit.SampleLogTally.WEIGH_SLIP_REMOTE_FOUND_UNMATCHING_TIMBERMARK));
    }
    // legacy NPE on a missing sample weight; here a missing weight does not match
    Long sampleWeight = v.lng("sample_weight");
    if (sampleWeight == null || !Long.toString(netWeight).equals(sampleWeight.toString())) {
      errors.add(lookupError(local ? CrossReferenceEdit.SampleLogTally.WEIGH_SLIP_FOUND_UNMATCHING_WEIGHTS
          : CrossReferenceEdit.SampleLogTally.WEIGH_SLIP_REMOTE_FOUND_UNMATCHING_WEIGHTS));
    }
    if (remote) {
      String destination = weighSlip.str("destination");
      if (destination != null && !destination.equals(v.str("scale_site_id_nmbr"))) {
        errors.add(lookupError(CrossReferenceEdit.SampleLogTally.WEIGH_SLIP_REMOTE_FOUND_UNMATCHING_DESTINATION));
      }
    }
  }

  /** The legacy three-way StringUtil.empty (no trim) / equals comparison of a weigh slip and tally value. */
  private static boolean differs(String ws, String slt) {
    return (ScaledLogXref.legacyEmpty(ws) && !ScaledLogXref.legacyEmpty(slt))
        || (ScaledLogXref.legacyEmpty(slt) && !ScaledLogXref.legacyEmpty(ws))
        || (!ScaledLogXref.legacyEmpty(ws) && !ws.equals(slt));
  }

  /** BT11534: the tally's scale day is before the weigh slip's (times ignored). */
  private boolean isSampleTallyScaleDateLessThenWeightSlipScaleDate(Row weighSlip) {
    Date wsDate = weighSlip.date("scale_date");
    if (wsDate == null) {
      return false;
    }
    LocalDate ws = new Timestamp(wsDate.getTime()).toLocalDateTime().toLocalDate();
    LocalDate slt = new Timestamp(v.date("scale_date").getTime()).toLocalDateTime().toLocalDate();
    return slt.isBefore(ws);
  }

  /** {@code daysDifference} (BT6519): |sample − weigh slip| in days, as a float. */
  static float daysDifference(Date sampleDate, Date weighSlipDate) {
    float deltaMillis = sampleDate.getTime() - weighSlipDate.getTime();
    deltaMillis = deltaMillis / MILLIS_PER_DAY;
    return Math.abs(deltaMillis);
  }

  /**
   * {@code validatFieldScaleDeckId} (BT 10013): SS/RS copy the field deck of
   * the one active weigh slip sampled; a CS by another scaler takes the local
   * slip's deck, else the remote slip's, else none; a CS by the original
   * scaler gets none.
   */
  @Override
  protected void validatFieldScaleDeckId() {
    String event = v.str("scale_event_type_code");
    String site = v.str("scale_site_id_nmbr");
    String slip = v.str("sampled_weigh_slip");
    if (REMOTE_SAMPLE_SCALE.equals(event) || SAMPLE_SCALE.equals(event)) {
      v.set("field_scale_deck_id", weighSample.sampledWeighSlipFieldScaleDeckId(site, slip, event));
    } else if (CHECK_SCALE.equals(event)) {
      // legacy getPrimaryLicenseNumber().equals(...) threw on a missing primary scaler
      String primary = v.str("primary_license_number");
      if (primary == null || !primary.equals(v.str("orig_chk_scl_license_number"))) {
        String local = weighSample.sampledWeighSlipFieldScaleDeckId(site, slip, SAMPLE_SCALE);
        String remote = weighSample.sampledWeighSlipFieldScaleDeckId(site, slip, REMOTE_SAMPLE_SCALE);
        if (!empty(local)) {
          v.set("field_scale_deck_id", local);
        } else if (!empty(remote)) {
          v.set("field_scale_deck_id", remote);
        } else {
          v.set("field_scale_deck_id", "");
        }
      } else {
        v.set("field_scale_deck_id", "");
      }
    }
  }

}
