package ca.bc.gov.nrs.hbs.api.billing;

import ca.bc.gov.nrs.hbs.api.legacyrules.LegacyMessages;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * The force-date checks of the legacy Process Scheduling screens, ported
 * line by line — including their quirks — because the legacy batch relies on
 * the dates they let through:
 *
 * <ul>
 *   <li>{@link #summarization} — {@code P811Action.validateForm} /
 *       {@code P813Action.validateForm} (identical);</li>
 *   <li>{@link #ratioComputation} — {@code P823Action.validateForm}, whose
 *       operator precedence differs ({@code (days<0)||(days>28) && MTH}: any
 *       earlier date in the same year reports the Monthly message, and a
 *       later year other than December→January passes).</li>
 * </ul>
 * "Today" is the server day in Pacific time, as the legacy app server's.
 */
public final class ForceDateRules {

  public static final String MONTHLY = "MTH";
  public static final String BI_WEEKLY = "BWK";
  public static final String DAILY = "DLY";

  private ForceDateRules() {
  }

  /** P811/P813: the force year must be 2000–2099 (error.forceyear.invalid). */
  public static String year(LocalDate force) {
    return force.getYear() < 2000 || force.getYear() > 2099 ? LegacyMessages.FORCE_YEAR_INVALID : null;
  }

  /**
   * P811/P813 window from today by frequency: Monthly 0–28 days, Bi-Weekly
   * 0–14, Daily 0–1. A later year is only accepted for a December → January
   * rollover ({@code 31 + forceDay - todayDay}). Unknown frequency: no check.
   */
  public static List<String> summarization(LocalDate today, LocalDate force, String frequency) {
    List<String> errors = new ArrayList<>();
    String year = year(force);
    if (year != null) {
      errors.add(year);
      return errors;
    }
    String windowMessage = windowMessage(frequency);
    if (windowMessage == null) {
      return errors;
    }
    int limit = limit(frequency);
    if (today.getYear() == force.getYear()) {
      int days = force.getDayOfYear() - today.getDayOfYear();
      if (days < 0 || days > limit) errors.add(windowMessage);
    } else if (today.getYear() < force.getYear()) {
      if (today.getMonthValue() == 12 && force.getMonthValue() == 1) {
        int days = 31 + force.getDayOfMonth() - today.getDayOfMonth();
        if (days > limit) errors.add(windowMessage);
      } else {
        errors.add(windowMessage);
      }
    } else {
      errors.add(windowMessage);
    }
    return errors;
  }

  /**
   * P823: year 2000–2099, the (quirky) frequency window, then the force date
   * must be after the profile's LAST_RATIO_STMT_DATE ("… Period Start") and
   * before its NEXT_RATIO_STMT_DATE ("… Period End"). The force date is
   * compared at 00:00, as P823Form built it.
   */
  public static List<String> ratioComputation(LocalDate today, LocalDate force, String frequency,
      LocalDateTime lastRatioStmtDate, LocalDateTime nextRatioStmtDate) {
    List<String> errors = new ArrayList<>();
    String year = year(force);
    if (year != null) {
      errors.add(year);
      return errors;
    }
    if (today.getYear() == force.getYear()) {
      int days = force.getDayOfYear() - today.getDayOfYear();
      if (days < 0 || (days > 28 && MONTHLY.equals(frequency))) {
        errors.add(LegacyMessages.FORCE_DATE_MONTHLY);
      } else if (days > 14 && BI_WEEKLY.equals(frequency)) {
        errors.add(LegacyMessages.FORCE_DATE_BIWEEKLY);
      } else if (days > 1 && DAILY.equals(frequency)) {
        errors.add(LegacyMessages.FORCE_DATE_DAILY);
      }
    } else if (today.getYear() < force.getYear() && today.getMonthValue() == 12 && force.getMonthValue() == 1) {
      int days = 31 + force.getDayOfMonth() - today.getDayOfMonth();
      if (days > 28 && MONTHLY.equals(frequency)) {
        errors.add(LegacyMessages.FORCE_DATE_MONTHLY);
      } else if (days > 14 && BI_WEEKLY.equals(frequency)) {
        errors.add(LegacyMessages.FORCE_DATE_BIWEEKLY);
      } else if (days > 1 && DAILY.equals(frequency)) {
        errors.add(LegacyMessages.FORCE_DATE_DAILY);
      }
    }
    LocalDateTime forceAt = force.atStartOfDay();
    if (lastRatioStmtDate != null && !forceAt.isAfter(lastRatioStmtDate)) {
      errors.add(LegacyMessages.FORCE_BEFORE_PERIOD_START);
    }
    if (nextRatioStmtDate != null && !forceAt.isBefore(nextRatioStmtDate)) {
      errors.add(LegacyMessages.FORCE_AFTER_PERIOD_END);
    }
    return errors;
  }

  private static int limit(String frequency) {
    return switch (frequency) {
      case MONTHLY -> 28;
      case BI_WEEKLY -> 14;
      default -> 1;
    };
  }

  private static String windowMessage(String frequency) {
    if (frequency == null) return null;
    return switch (frequency) {
      case MONTHLY -> LegacyMessages.FORCE_DATE_MONTHLY;
      case BI_WEEKLY -> LegacyMessages.FORCE_DATE_BIWEEKLY;
      case DAILY -> LegacyMessages.FORCE_DATE_DAILY;
      default -> null;
    };
  }
}
