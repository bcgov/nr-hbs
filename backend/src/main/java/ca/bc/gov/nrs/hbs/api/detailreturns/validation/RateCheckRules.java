package ca.bc.gov.nrs.hbs.api.detailreturns.validation;

import ca.bc.gov.nrs.hbs.api.detailreturns.validation.ReferenceData.RateCheck;
import ca.bc.gov.nrs.hbs.api.workflow.Audit;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;

/**
 * The decision logic of {@code plu.RateManagerBean.hasRate} with the database
 * reads behind a {@link Source}, so it can be unit tested. The legacy
 * exceptions become {@link RateCheck} values, the way
 * {@code DetailScaleDocumentValidator.validateRate} turned them into errors:
 * {@code OverlappingRulesException} → {@link RateCheck#OVERLAPPING_RULES} (SD11),
 * {@code StumpageNotFoundException} → {@link RateCheck#NO_STUMPAGE} (SD10),
 * {@code false} → {@link RateCheck#NO_RATE} (SD08).
 *
 * <p>All dates are days: the legacy {@code DateRange.startDayToEndDay} of a
 * single date, formatted {@code DD-MON-YYYY} into the SQL, i.e. compared on the
 * day only.
 */
public final class RateCheckRules {

  /** {@code OverrideRateRule.TYPE_RATEDATE}: rate the scale on another date. */
  public static final String TYPE_RATEDATE = "RTDTRL";
  /** {@code OverrideRateRule.TYPE_OVERRIDE_RULE}: a rate exists, no appraisal needed. */
  public static final String TYPE_OVERRIDE_RULE = "OVRRDRULE";
  /** {@code OverrideRateRule.STATUS_ACTIVE}. */
  public static final String RULE_STATUS_ACTIVE = "ACT";
  /** {@code AppraisalWorksheet.STATUS_CONFIRMED}. */
  public static final String WORKSHEET_CONFIRMED = "CNF";

  /** An OVERRIDE_RATING_RULE row ({@code OVRRDRTRL_TYPE}, {@code OVERRIDE_RATE_DATE} as a day). */
  public record OverrideRule(String type, LocalDate overrideRateDate) {
  }

  /** An APP_WORKSHEET row's validity (APP_EFFECTIVE_DATE / EXPIRY_DATE as days). */
  public record WorksheetPeriod(LocalDate appEffectiveDate, LocalDate expiryDate) {
  }

  /** The reads {@code hasRate} makes. */
  public interface Source {

    /**
     * {@code OverrideRateRuleQuery.selectByMarkAndDates}: ACT rules of the mark
     * whose scale period contains {@code scaleDay} and processing period
     * contains {@code processingDay}, for the deck (or deck-less when the deck
     * is empty).
     */
    List<OverrideRule> activeOverrideRules(String timberMark, LocalDate scaleDay, LocalDate processingDay,
                                           String fieldScaleDeckId);

    /** {@code AppraisalWorksheetQuery.selectByTimberMarkAndDateRange(mark, day..day, CNF)}. */
    List<WorksheetPeriod> confirmedWorksheets(String timberMark, LocalDate day);

    /** {@code findStumpageRatesForAppraisal}: APP_STMPG_RATE rows of (mark, appraisal effective day). */
    int stumpageRateCount(String timberMark, LocalDate appEffectiveDay);
  }

  private RateCheckRules() {
  }

  /**
   * {@code RateManagerBean.hasRate(timberMark, startDayToEndDay(scaleDate), fieldScaleDeckId)}.
   *
   * @param today the processing date ({@code new Date()} in legacy), Pacific
   */
  public static RateCheck hasRate(Source source, String timberMark, LocalDate scaleDay, String fieldScaleDeckId,
                                  LocalDate today) {
    if (scaleDay == null) {
      // legacy: DateRange.startDayToEndDay(null) threw a NullPointerException that
      // validateRate caught and logged -- no rate error at all
      return RateCheck.HAS_RATE;
    }
    if (timberMark == null) {
      return RateCheck.NO_RATE;
    }
    // findActiveOverrideRateRule(mark, scaleDateRange.getEnd(), deck)
    List<OverrideRule> rules = source.activeOverrideRules(timberMark, scaleDay, today, fieldScaleDeckId);
    if (rules.size() > 1) {
      // OverlappingRulesException escapes hasRate
      return RateCheck.OVERLAPPING_RULES;
    }
    OverrideRule rule = rules.isEmpty() ? null : rules.get(0);
    if (rule != null && TYPE_OVERRIDE_RULE.equals(rule.type())) {
      // an appraisal is not necessary when the rule type is override rule
      return RateCheck.HAS_RATE;
    }
    LocalDate day = scaleDay;
    if (rule != null && TYPE_RATEDATE.equals(rule.type())) { // overrideDate(rule)
      if (rule.overrideRateDate() == null) {
        // legacy: startDayToEndDay(null) NPE, caught and logged by validateRate
        return RateCheck.HAS_RATE;
      }
      day = rule.overrideRateDate();
    }
    return hasAppraisalAndRate(source, timberMark, day);
  }

  /** {@code RateManagerBean.hasAppraisalAndRate} + {@code findConfirmedWorksheet} for a single day. */
  static RateCheck hasAppraisalAndRate(Source source, String timberMark, LocalDate day) {
    List<WorksheetPeriod> sheets = source.confirmedWorksheets(timberMark, day);
    if (sheets.isEmpty()) {
      // AppraisalWorksheetNotFoundException → no rate
      return RateCheck.NO_RATE;
    }
    if (sheets.size() > 1) {
      // a single date: OverlappingWorksheetsException → no rate (logged as abnormal)
      return RateCheck.NO_RATE;
    }
    WorksheetPeriod sheet = sheets.get(0);
    if (!encompassesIgnoreTime(sheet, day)) {
      // SplitRateException; validateRate turned it into an IllegalStateException.
      // Cannot happen for a sheet the day query returned (kept for fidelity).
      throw new IllegalStateException("RateManager.hasRate threw a SplitRateException for a single scale date");
    }
    if (source.stumpageRateCount(timberMark, sheet.appEffectiveDate()) == 0) {
      // StumpageNotFoundException
      return RateCheck.NO_STUMPAGE;
    }
    return RateCheck.HAS_RATE;
  }

  /** {@code new DateRange(effective, expiry).encompassesIgnoreTime(day..day)}. */
  static boolean encompassesIgnoreTime(WorksheetPeriod sheet, LocalDate day) {
    if (sheet.appEffectiveDate() == null || sheet.expiryDate() == null
        || sheet.appEffectiveDate().isAfter(sheet.expiryDate())) {
      // the legacy DateRange constructor rejected these (IllegalArgumentException)
      return false;
    }
    return !sheet.appEffectiveDate().isAfter(day) && !day.isAfter(sheet.expiryDate());
  }

  /**
   * The Pacific calendar day of a date ({@code DateUtil.startDay}); null stays
   * null. Same zone as {@code DetailValidator.local}.
   */
  public static LocalDate day(Date d) {
    return d == null ? null : Instant.ofEpochMilli(d.getTime()).atZone(Audit.ZONE).toLocalDate();
  }

  /** Today in Pacific time (the legacy {@code new Date()} processing date). */
  public static LocalDate today() {
    return LocalDate.now(Audit.ZONE);
  }
}
