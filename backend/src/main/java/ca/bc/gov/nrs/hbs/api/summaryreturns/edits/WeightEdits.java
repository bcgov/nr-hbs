package ca.bc.gov.nrs.hbs.api.summaryreturns.edits;

import static ca.bc.gov.nrs.hbs.api.summaryreturns.EditError.CATEGORY_FORMAT;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.EditError.CATEGORY_LOOKUP;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.EditError.CATEGORY_RELATED;

import ca.bc.gov.nrs.hbs.api.summaryreturns.Row;
import ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturn;
import ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturnRules;
import ca.bc.gov.nrs.hbs.api.summaryreturns.edits.Lookups.Client;
import ca.bc.gov.nrs.hbs.api.summaryreturns.edits.Lookups.ClientLocation;
import ca.bc.gov.nrs.hbs.api.summaryreturns.edits.Lookups.Stratum;
import ca.bc.gov.nrs.hbs.api.summaryreturns.edits.Lookups.TimberMark;
import ca.bc.gov.nrs.hbs.api.summaryreturns.edits.Lookups.Worksheet;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * {@code $E/dac/summary/control/WeightScaleValidator}. All its errors are version errors; the
 * legacy validator raised none on the daily load summaries.
 */
class WeightEdits extends BillableEdits {

  WeightEdits(SummaryReturn ret, Lookups lookups, Rates rates, Clock clock) {
    super(ret, lookups, rates, clock);
  }

  private String category() {
    return str("scl_rtn_category_code");
  }

  private String population() {
    return str("population_number");
  }

  private String stratumNo() {
    return str("stratum_number");
  }

  private Long year() {
    return version.lng("sampling_year");
  }

  // ── Input fields ─────────────────────────────────────────────────────────

  /** {@code validateAndMoveInputFields}: timber mark (TM01), population / stratum / year (ST01). */
  @Override
  protected void validateAndMoveInputFields() {
    if (empty(str("timber_mark"))) {
      if (onFileTimberMark(str("input_timber_mark"))) {
        version.set("timber_mark", str("input_timber_mark"));
      } else {
        versionError(CATEGORY_LOOKUP, EditCodes.TM01);
      }
    }
    if (emptyPopStratumYear()) {
      validateAndMoveInputPopStratumYear();
    }
  }

  /** {@code validateAndMoveInputPopStratumYear}: ST01 when the input stratum is not on file or the year is not numeric. */
  private void validateAndMoveInputPopStratumYear() {
    try {
      Long year = convertYearToLong(str("input_sampling_year"));
      if (stratum(str("input_population"), str("input_stratum"), year).isPresent()) {
        setPopStratumYearFromInput();
      } else {
        versionError(CATEGORY_LOOKUP, EditCodes.ST01);
      }
    } catch (InvalidYearException e) {
      versionError(CATEGORY_LOOKUP, EditCodes.ST01);
    }
  }

  // ── validate() ───────────────────────────────────────────────────────────

  /**
   * {@code validate}: timber mark, cruise-based scale site (CBSS00), scale date (SD03, SD13),
   * stratum dates (SD04 / SD05) and grade constraint (CG04 / IG04), clients, rates, sum of loads
   * (RT01 / RT02). The return-category edit is a no-op in legacy (its error is commented out).
   */
  @Override
  protected void validate() {
    validateTimberMark();
    validateNotCruiseBasedScaleSite();
    validateScaleDate();
    if (!emptyPopStratumYear()) {
      validateAgainstStratum();
      validateGradeConstraint(str("timber_mark"), population(), stratumNo(), year(), true);
    }
    validateClients();
    validateRate();
    validateSumOfLoadsAgainstCategory();
  }

  /**
   * {@code validateTimbermark}: TM04 (cash sale); TM09 (cruise based) or TM10 + TM12 (area based,
   * with a confirmed appraisal); stand-rate eligibility codes (ISB1C/D, CSB1C/D); FB01 for a final
   * bill on a mark exempt from final billing (bug 667). A mark not on file (including an empty one
   * after TM01) was a runtime exception in legacy.
   */
  private void validateTimberMark() {
    String markCode = str("timber_mark");
    TimberMark mark = requiredTimberMark(markCode, "Valid timbermark not found!");
    if (mark.isCashSale()) {
      versionError(CATEGORY_RELATED, EditCodes.TM04);
    }
    List<Worksheet> appraisals = lookups.confirmedWorksheets(markCode, scaleDate().toLocalDate());
    if (mark.isCruiseBased()) {
      versionError(CATEGORY_RELATED, EditCodes.TM09);
    } else if (mark.isAreaBased()) {
      versionError(CATEGORY_RELATED, EditCodes.TM10);
      if (!appraisals.isEmpty()) {
        versionError(CATEGORY_RELATED, EditCodes.TM12);
      }
    }
    if (!mark.isPrivateMark() && !appraisals.isEmpty()) {
      Worksheet ws = appraisals.get(0);
      if (EditCodes.INTERIOR_SCHEDULE.equals(ws.appraisalMthdCd())) {
        if (!mark.isCruiseBased()) {
          validateScaleBasedSreCodes(ws, EditCodes.ISB1C, EditCodes.ISB1D);
        }
      } else if (EditCodes.COASTAL_SCHEDULE.equals(ws.appraisalMthdCd())) {
        validateScaleBasedSreCodes(ws, EditCodes.CSB1C, EditCodes.CSB1D);
      }
    }
    if (SummaryReturnRules.CATEGORY_FINAL_BILL.equals(category()) && exemptFromFinalBilling(mark)) {
      versionError(CATEGORY_FORMAT, EditCodes.FB01);
    }
  }

  /**
   * {@code DropoutMarkProcessor.isExemptFromFinalBilling} (DR084): not renewable and not a final
   * billable dropout mark of the stratum's population. {@code TimberMark.isRenewable} threw on a
   * null quota type.
   */
  private boolean exemptFromFinalBilling(TimberMark mark) {
    if (mark.quotaTypeCode() == null) {
      throw legacyFailure("TimberMark.isRenewable: null QUOTA_TYPE_CODE for " + mark.timberMark());
    }
    return !mark.isRenewable() && !lookups.finalBillableDropoutMark(population(), year(), mark.timberMark());
  }

  /** {@code validateScaleDate}: SD03 (after today); SD13 for each daily load after the scale day. */
  private void validateScaleDate() {
    long lastScaleDay = scaleDate().getDayOfMonth();
    if (!scaleDateNotInFuture()) {
      versionError(CATEGORY_RELATED, EditCodes.SD03);
    }
    for (Row load : ret.dailyLoads()) {
      if (load.lng("day") > lastScaleDay) {
        versionError(CATEGORY_RELATED, EditCodes.SD13);
      }
    }
  }

  /** {@code validateAgainstStratum}: SD04 before the stratum's effective date, else SD05 after its expiry. */
  private void validateAgainstStratum() {
    Stratum stratum = requiredStratum(population(), stratumNo(), year(), "WeightScaleValidator.validateAgainstStratum");
    LocalDateTime scaleDate = scaleDate();
    if (scaleDate.isBefore(stratum.effectiveDate())) {
      versionError(CATEGORY_RELATED, EditCodes.SD04);
    } else if (scaleDate.isAfter(stratum.expiryDate())) {
      versionError(CATEGORY_RELATED, EditCodes.SD05);
    }
  }

  /** {@code validateSumOfLoadsAgainstCategory}: RT01 (primary with no weight), RT02 (final / nil bill with weight). */
  private void validateSumOfLoadsAgainstCategory() {
    long weightSum = 0;
    for (Row load : ret.dailyLoads()) {
      Long weight = load.lng("weight");
      if (weight == null) {
        throw legacyFailure("validateSumOfLoadsAgainstCategory: null daily load weight");
      }
      weightSum += weight;
    }
    String c = category();
    if (SummaryReturnRules.CATEGORY_PRIMARY.equals(c) && weightSum == 0) {
      versionError(CATEGORY_RELATED, EditCodes.RT01);
    }
    if ((SummaryReturnRules.CATEGORY_NIL_BILL.equals(c) || SummaryReturnRules.CATEGORY_FINAL_BILL.equals(c))
        && weightSum > 0) {
      versionError(CATEGORY_RELATED, EditCodes.RT02);
    }
  }

  // ── Clients ──────────────────────────────────────────────────────────────

  /** {@code validateClients}: BT01, bill-to location, CT01, copy-to location. */
  @Override
  protected void validateClients() {
    validateBillToClient(deriveBillToClient());
    validateBillToClientAndLocation(deriveBillToClientLocation());
    validateCopyToClient(deriveCopyToClient());
    validateCopyToClientAndLocation(deriveCopyToClientLocation());
  }

  private Client deriveBillToClient() {
    return empty(str("client_number_paid_by")) ? deriveBillToClientFromTimberMark()
        : fetchClient(str("client_number_paid_by"));
  }

  private ClientLocation deriveBillToClientLocation() {
    return empty(str("client_number_paid_by")) ? deriveBillToClientAndLocationFromTimberMark()
        : fetchClientLocation(str("client_number_paid_by"), str("client_locn_code_paid_by"));
  }

  /** {@code deriveCopyToClient}: copied-to, else the stratum owner. */
  private Client deriveCopyToClient() {
    if (notEmpty(str("client_number_copied_to"))) {
      return fetchClient(str("client_number_copied_to"));
    }
    if (emptyPopStratumYear()) return null;
    return fetchClient(requiredStratum(population(), stratumNo(), year(),
        "WeightScaleValidator.validateCopyToClientFromStratum").clientNumber());
  }

  private ClientLocation deriveCopyToClientLocation() {
    if (notEmpty(str("client_number_copied_to"))) {
      return fetchClientLocation(str("client_number_copied_to"), str("client_locn_code_copied_to"));
    }
    if (emptyPopStratumYear()) return null;
    Stratum stratum = requiredStratum(population(), stratumNo(), year(),
        "WeightScaleValidator.validateCopyToClientFromStratum");
    return fetchClientLocation(stratum.clientNumber(), stratum.clientLocnCode());
  }

  // ── Rates ────────────────────────────────────────────────────────────────

  /** {@code getScaleDateRange}: from the earliest load day of the month (or the scale day) to the scale date. */
  @Override
  protected LocalDateTime[] scaleDateRange() {
    LocalDateTime scaleDate = scaleDate();
    int start = scaleDate.getDayOfMonth();
    for (Row load : ret.dailyLoads()) {
      int day = load.lng("day").intValue();
      if (day < start) start = day;
    }
    return new LocalDateTime[] {scaleDate.withDayOfMonth(start), scaleDate};
  }

  /** {@code ratesExist}: {@code weightScaleReturnHasRates} over the stratum's latest ratios. */
  @Override
  protected boolean ratesExist() throws Rates.RateException {
    return rates.weightScaleReturnHasRates(ret, lookups.latestRatios(population(), stratumNo(), year()));
  }
}
