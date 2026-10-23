package ca.bc.gov.nrs.hbs.api.summaryreturns.edits;

import static ca.bc.gov.nrs.hbs.api.summaryreturns.EditError.CATEGORY_RELATED;

import ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturn;
import ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturnRules;
import ca.bc.gov.nrs.hbs.api.summaryreturns.edits.Lookups.TimberMark;
import java.time.Clock;
import java.time.LocalDateTime;

/** {@code $E/dac/summary/control/SummaryBillableScaleReturnValidator}: the rate edits of piece and weight returns. */
abstract class BillableEdits extends ReturnEdits {

  protected final Rates rates;

  protected BillableEdits(SummaryReturn ret, Lookups lookups, Rates rates, Clock clock) {
    super(ret, lookups, clock);
    this.rates = rates;
  }

  /** {@code getScaleDateRange}: start and end of the period the rates must cover. */
  protected abstract LocalDateTime[] scaleDateRange();

  /** {@code ratesExist}: {@code RateManager.pieceScaleReturnHasRates} / {@code weightScaleReturnHasRates}. */
  protected abstract boolean ratesExist() throws Rates.RateException;

  /**
   * {@code validateRate}: for a non-private mark on a return that is not waste category, SD12 when
   * a rate change splits the scale period, else SD17 when a segregation has no rate; SD10 (no
   * stumpage), SD08 (no worksheet) and SD11 (overlapping override rules) from the rate lookup.
   * The mark contract-window edit (SD06) was removed in legacy (issue 1457) and is not ported.
   */
  protected void validateRate() {
    String markCode = str("timber_mark");
    if (empty(markCode)) return;
    TimberMark mark = requiredTimberMark(markCode, "validateRate: Timbermark not found.");
    String error = null;
    try {
      LocalDateTime[] range = scaleDateRange();
      if (!mark.isPrivateMark() && !SummaryReturnRules.CATEGORY_WASTE.equals(str("scl_rtn_category_code"))) {
        if (lookups.hasSplitRate(mark.timberMark(), range[0].toLocalDate(), range[1].toLocalDate())) {
          error = EditCodes.SD12;
        } else if (!ratesExist()) {
          error = EditCodes.SD17;
        }
      }
    } catch (Rates.RateException e) {
      error = switch (e.failure()) {
        case STUMPAGE_NOT_FOUND -> EditCodes.SD10;
        case WORKSHEET_NOT_FOUND -> EditCodes.SD08;
        case OVERLAPPING_RULES -> EditCodes.SD11;
      };
    }
    if (error != null) {
      versionError(CATEGORY_RELATED, error);
    }
  }
}
