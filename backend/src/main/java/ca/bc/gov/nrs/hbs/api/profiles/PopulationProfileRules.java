package ca.bc.gov.nrs.hbs.api.profiles;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Population profile (P332 / P333) date rules: the ratio-statement schedule
 * and the messages of {@code P333Action} / {@code P332Action}.
 *
 * <p>The legacy screens stored the expiry date and the next ratio statement
 * date at <b>23:59:59</b>. The ratio batch selects profiles with
 * {@code next_ratio_stmt_date < runDate} ({@code PopulationProfileQuery}),
 * so the time of day matters: a midnight value would run the statement a
 * day early. These values are bound as timestamps, never truncated.
 */
final class PopulationProfileRules {

  private PopulationProfileRules() {
  }

  static final LocalTime END_OF_DAY = LocalTime.of(23, 59, 59);

  // ApplicationResources.properties
  /** error.populationprofileeffectivedate.invalid */
  static final String EFFECTIVE_YEAR_INVALID = "Effective year is not valid.";
  /** error.populationprofileeffectiveday.invalid */
  static final String EFFECTIVE_NOT_FIRST = "Effective day must be the first day of the month.";
  /** error.populationprofiledate.invalidate */
  static final String EFFECTIVE_BEFORE_CURRENT_MONTH = "Effective date cannot precede current month.";
  /**
   * error.populationprofileexpirydate.invalid. The key is defined twice in
   * ApplicationResources ("Expiry Year is not valid." then this text); the
   * last definition wins, so the legacy screens showed this text for the
   * expiry-year check too.
   */
  static final String EXPIRY_BEFORE_NEXT_RUN = "Expiry Date must be after nextRun date.";
  /** error.populationprofileexpiryday.invalid */
  static final String EXPIRY_NOT_LAST = "Expiry day must be the last day of the month.";
  /** error.wasteresiduedaterange.invalidDateRange (the overlap check reused it) */
  static final String DATE_OVERLAPPING = "Date overlapping.";
  /** errors.pop-year-ref (validation.xml popYearCrossRef) */
  static final String POPULATION_YEAR_INVALID = "Population / Year combination is invalid.";
  /** errors.date.effective-before-expiry (validation.xml effectiveBeforeExpiry) */
  static final String EFFECTIVE_AFTER_EXPIRY = "Effective date should be before Expiry date.";

  /** errors.date ({0} is not a valid date.) */
  static String notADate(String label) {
    return label + " is not a valid date.";
  }

  /** errors.integer ({0} must be numeric.) */
  static String notNumeric(String label) {
    return label + " must be numeric.";
  }

  /** Years the legacy P332/P333 accepted (2000–2099). */
  static boolean yearInRange(LocalDate d) {
    return d.getYear() >= 2000 && d.getYear() <= 2099;
  }

  /**
   * {@code P333Action.hbsPerform} (BT4677): the first ratio statement date.
   * Monthly → the last day of the effective month; bi-weekly → the 15th;
   * daily (or anything else) → the effective day itself; always at 23:59:59.
   */
  static LocalDateTime firstNextRatioStmtDate(LocalDate effective, String frequency) {
    int day = switch (frequency == null ? "" : frequency) {
      case "MTH" -> effective.lengthOfMonth();
      case "BWK" -> 15;
      default -> effective.getDayOfMonth();
    };
    return effective.withDayOfMonth(day).atTime(END_OF_DAY);
  }

  /** {@code P333Form.setExpiryDate} / {@code P332Form.setExpiryDate}: the expiry day at 23:59:59. */
  static LocalDateTime expiryAt(LocalDate expiry) {
    return expiry.atTime(END_OF_DAY);
  }
}
