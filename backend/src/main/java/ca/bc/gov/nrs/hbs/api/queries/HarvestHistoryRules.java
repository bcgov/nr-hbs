package ca.bc.gov.nrs.hbs.api.queries;

import ca.bc.gov.nrs.hbs.api.workflow.WorkflowValidationException.Problem;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

/**
 * The month-interval rules of the harvest history screens (P431 By Date of
 * Invoice / P441 By Date of Scale), from
 * {@code ear/hbs-view/…/presentation/opq/ftas/MarkMonthlyBillingAction.validateForm},
 * {@code getLimitDate}, {@code isBillingDateValid} and {@code isValidDateRange}.
 * Shared by the signed-in screens ({@link QueriesCriteriaGuard}) and the public
 * area ({@code PublicQueries}).
 */
public final class HarvestHistoryRules {

  static final DateTimeFormatter LEGACY_DATE = DateTimeFormatter.ofPattern("yyyy-MMM-dd", Locale.ENGLISH);
  /** {@code MarkMonthlyBillingAction.LIMITING_YEAR} / {@code FIXED_YEAR_LIMIT} (BT 9238). */
  private static final int LIMITING_YEARS = 15;
  private static final LocalDate FIXED_LIMIT = LocalDate.of(1995, 1, 1);

  private HarvestHistoryRules() {
  }

  /** {@code getLimitDate}: the later of 1 January of (this year − 15) and 1995-01-01. */
  public static LocalDate limitDate(LocalDate today) {
    LocalDate adjusted = LocalDate.of(today.getYear() - LIMITING_YEARS, 1, 1);
    return adjusted.isAfter(FIXED_LIMIT) ? adjusted : FIXED_LIMIT;
  }

  /**
   * The interval checks, in legacy order, each stopping the ones after it as
   * the legacy {@code periodDatesValid} flag did. The legacy form had year /
   * month drop-downs ({@code TimberMarkHelper.getBillingDateFrom/To}): From is
   * the first day of its month and To the last day of its month.
   *
   * @return the problems (empty when the interval is valid)
   */
  public static List<Problem> interval(String fromKey, String fromText, String toKey, String toText, LocalDate today) {
    LocalDate from;
    LocalDate to;
    try {
      from = month(fromText);
    } catch (DateTimeParseException ex) {
      // error.billingperiodfrom.invalid
      return List.of(new Problem(fromKey, "Invalid Billing From date."));
    }
    try {
      // TimberMarkHelper.getBillingDateTo: the last day of the To month.
      LocalDate toMonth = month(toText);
      to = toMonth == null ? null : toMonth.plusMonths(1).minusDays(1);
    } catch (DateTimeParseException ex) {
      // error.billingperiodto.invalid
      return List.of(new Problem(toKey, "Invalid Billing To date."));
    }
    if (from == null || to == null) {
      return List.of(new Problem(fromKey, QueriesMessages.BILLING_RANGE_REQUIRED));
    }
    LocalDate limit = limitDate(today);
    if (from.isBefore(limit)) {
      return List.of(new Problem(fromKey, QueriesMessages.billingDateTooOld(LEGACY_DATE.format(limit))));
    }
    // isBillingDateValid: not after the last day of the current month (from, then to; && short-circuits).
    LocalDate lastOfMonth = today.withDayOfMonth(today.lengthOfMonth());
    if (from.isAfter(lastOfMonth)) {
      return List.of(new Problem(fromKey, QueriesMessages.BILLING_DATE_FUTURE));
    }
    if (to.isAfter(lastOfMonth)) {
      return List.of(new Problem(toKey, QueriesMessages.BILLING_DATE_FUTURE));
    }
    // isValidDateRange
    if (from.isAfter(to)) {
      return List.of(new Problem(fromKey, QueriesMessages.BILLING_FROM_AFTER_TO));
    }
    // One year on the dates as entered (the screen takes days, not legacy's month drop-downs).
    LocalDate enteredFrom = LocalDate.parse(fromText.trim().substring(0, 10));
    LocalDate enteredTo = LocalDate.parse(toText.trim().substring(0, 10));
    if (enteredFrom.plusYears(1).isBefore(enteredTo)) {
      return List.of(new Problem(fromKey, QueriesMessages.RANGE_OVER_ONE_YEAR));
    }
    return List.of();
  }

  /** The first day of the From month, as the legacy form built it (null when blank / invalid). */
  static LocalDate fromDate(String text) {
    try {
      return month(text);
    } catch (DateTimeParseException ex) {
      return null;
    }
  }

  /** The last day of the To month (null when blank / invalid). */
  static LocalDate toDate(String text) {
    LocalDate m = fromDate(text);
    return m == null ? null : m.plusMonths(1).minusDays(1);
  }

  /** The first day of the month of an ISO date ({@code yyyy-mm-dd…}); null when blank. */
  static LocalDate month(String text) {
    if (text == null || text.isBlank()) {
      return null;
    }
    String s = text.trim();
    return LocalDate.parse(s.length() > 10 ? s.substring(0, 10) : s).withDayOfMonth(1);
  }
}
