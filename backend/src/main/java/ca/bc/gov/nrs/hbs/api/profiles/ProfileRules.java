package ca.bc.gov.nrs.hbs.api.profiles;

import ca.bc.gov.nrs.hbs.api.workflow.WorkflowValidationException;
import ca.bc.gov.nrs.hbs.api.workflow.WorkflowValidationException.Problem;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Shared pieces of the ported profile rules (legacy {@code presentation/cpm}
 * Struts actions): the legacy message texts, body parsing and the date
 * helpers ({@code DateRange}, {@code DateUtil.getEnclosingMonth},
 * {@code CpmHelper.isValidate}) the actions used.
 *
 * <p>Message texts are copied from {@code hbs-view/.../ApplicationResources.properties}.
 */
final class ProfileRules {

  private ProfileRules() {
  }

  // ---------------------------------------------------------------- legacy messages

  /** error.date.invalid */
  static final String DATE_INVALID = "Invalid Date";
  /** error.date.invalid2 (year below {@code uss.Constants.HBS_LOW_DATE} = 1900) */
  static final String YEAR_BEFORE_1900 = "Year cannot be prior to 1900";
  /** error.effdate.invalid */
  static final String EFFECTIVE_DATE_INVALID = "Invalid Effective Date";
  /** error.expdate.invalid */
  static final String EXPIRY_DATE_INVALID = "Invalid Expiry Date";
  /** error.profiledaterange.invalid */
  static final String EXPIRY_BEFORE_EFFECTIVE = "Expiry date cannot be before effective date.";
  /** error.expirydate.invalid */
  static final String RANGE_OVERLAPS_NEXT = "Date range cannot overlap an existing valid range";
  /** error.expirydate.invalid2 */
  static final String EXPIRY_BEFORE_TODAY = "Expiry date cannot occur before today";
  /** error.effectivedate.invalid3 */
  static final String EFFECTIVE_NOT_FUTURE = "Effective date must be a future date";
  /** error.effectivedate.invalid4 */
  static final String EFFECTIVE_NOT_FUTURE_FOR_UPDATE =
      "Effective date must be a future date to allow updating of the expiry date";
  /** error.cpmdaterange.effinvalid */
  static final String EFFECTIVE_NOT_FIRST_OF_MONTH = "Effective date must be the first day of the month";
  /** error.cpmdaterange.expinvalid */
  static final String EXPIRY_NOT_LAST_OF_MONTH = "Expiry date must be the last day of the month";
  /** error.clientnumber.required */
  static final String CLIENT_REQUIRED = "Client number and location are required.";
  /** errors.client-number-location */
  static final String CLIENT_LOCATION_INVALID = "Client Number and Location is invalid.";
  /** errors.required ({0} is required.) */
  static String required(String label) {
    return label + " is required.";
  }

  /** Legacy minimum year ({@code ca.bc.gov.mof.hbs.uss.Constants.HBS_LOW_DATE}). */
  static final int HBS_LOW_YEAR = 1900;

  // ---------------------------------------------------------------- problems

  /** Collects legacy errors; {@link #throwIfAny()} mirrors the actions' {@code if (errors.empty())} gates. */
  static final class Problems {
    private final List<Problem> list = new ArrayList<>();

    Problems add(String field, String message) {
      list.add(new Problem(field, message));
      return this;
    }

    boolean isEmpty() {
      return list.isEmpty();
    }

    void throwIfAny() {
      if (!list.isEmpty()) {
        throw new WorkflowValidationException(list);
      }
    }
  }

  // ---------------------------------------------------------------- body values

  static String text(Map<String, Object> body, String name) {
    Object v = body.get(name);
    if (v == null) return null;
    String s = v.toString().trim();
    return s.isEmpty() ? null : s;
  }

  static String upper(Map<String, Object> body, String name) {
    String s = text(body, name);
    return s == null ? null : s.toUpperCase(Locale.ROOT);
  }

  /** Legacy forms trimmed, upper-cased and left-padded codes with zeros ({@code StringUtil.leftPad(.., '0')}). */
  static String padded(Map<String, Object> body, String name, int width) {
    String s = upper(body, name);
    if (s == null || s.length() >= width) return s;
    return "0".repeat(width - s.length()) + s;
  }

  static Long number(Map<String, Object> body, String name) {
    String s = text(body, name);
    if (s == null) return null;
    try {
      return Long.valueOf(s);
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  /** The required numeric key of the record being changed (400 when missing). */
  static long key(Map<String, Object> body, String name) {
    Long id = number(body, name);
    if (id == null) {
      throw new IllegalArgumentException("'" + name + "' is required");
    }
    return id;
  }

  /**
   * A calendar date ({@code yyyy-MM-dd}, a longer ISO value is cut to its
   * date) or null when absent or not a real date (the legacy
   * {@code DateUtil.isValidCalendarDate} check).
   */
  static LocalDate date(Map<String, Object> body, String name) {
    String s = text(body, name);
    if (s == null) return null;
    try {
      return LocalDate.parse(s.length() > 10 ? s.substring(0, 10) : s);
    } catch (DateTimeParseException ex) {
      return null;
    }
  }

  // ---------------------------------------------------------------- dates

  /** DATE column value (JDBC Timestamp / Date / LocalDateTime) as LocalDateTime. */
  static LocalDateTime dateTime(Object v) {
    if (v == null) return null;
    if (v instanceof Timestamp t) return t.toLocalDateTime();
    if (v instanceof java.sql.Date d) return d.toLocalDate().atStartOfDay();
    if (v instanceof java.util.Date d) return new Timestamp(d.getTime()).toLocalDateTime();
    if (v instanceof LocalDateTime t) return t;
    if (v instanceof LocalDate d) return d.atStartOfDay();
    String s = v.toString().trim();
    return s.length() <= 10 ? LocalDate.parse(s).atStartOfDay() : LocalDateTime.parse(s.replace(' ', 'T'));
  }

  /** {@code CpmHelper.isValidate} inverted / {@code DateUtil.getEnclosingMonth().getEnd()}: the last day of its month. */
  static boolean isLastDayOfMonth(LocalDate d) {
    return d.getDayOfMonth() == d.lengthOfMonth();
  }

  /**
   * {@code DateRange.insideRange}: start ≤ value ≤ end (to the second). A row
   * whose own range is invalid (start after end) never contains anything.
   */
  static boolean insideRange(LocalDateTime start, LocalDateTime end, LocalDateTime value) {
    if (start == null || end == null || value == null || start.isAfter(end)) return false;
    return !value.isBefore(start) && !value.isAfter(end);
  }

  /** {@code expiry.after(next) || expiry.equals(next)}: the new range runs into the next profile. */
  static boolean reachesNext(LocalDateTime expiry, LocalDateTime nextEffective) {
    return nextEffective != null && !expiry.isBefore(nextEffective);
  }

  /** The updateTimestamp the SPA echoes back, tolerant of the date-only form QueryService uses at midnight. */
  static Object lockValue(Map<String, Object> body) {
    Object v = body.get("updateTimestamp");
    if (v instanceof String s && s.trim().length() == 10) {
      return s.trim() + "T00:00:00";
    }
    return v;
  }
}
