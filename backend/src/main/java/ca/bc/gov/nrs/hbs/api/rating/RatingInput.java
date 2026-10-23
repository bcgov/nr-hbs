package ca.bc.gov.nrs.hbs.api.rating;

import ca.bc.gov.nrs.hbs.api.workflow.RowLock;
import ca.bc.gov.nrs.hbs.api.workflow.WorkflowValidationException;
import ca.bc.gov.nrs.hbs.api.workflow.WorkflowValidationException.Problem;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reading a command body the way the legacy Struts forms did, plus the
 * problem list the legacy {@code ActionErrors} was. Kept package-private:
 * these are the Rating screens' conventions, not a general framework.
 */
final class RatingInput {

  /** SCALE_PRODUCT_CODE for logs: a single space ({@code SpeciesProductGrade.Product.LOG}). */
  static final String LOGS = " ";

  private RatingInput() {
  }

  /** Trimmed text, or null when absent/blank. */
  static String text(Map<String, Object> body, String key) {
    Object v = body.get(key);
    if (v == null) return null;
    String s = v.toString().trim();
    return s.isEmpty() ? null : s;
  }

  static String upper(Map<String, Object> body, String key) {
    String s = text(body, key);
    return s == null ? null : s.toUpperCase(Locale.ROOT);
  }

  /**
   * A species / grade select where blank (or the legacy "all" option) means
   * "All" — stored as null (P204Action / P212Action: {@code equals("all") → null}).
   */
  static String allOrCode(Map<String, Object> body, String key) {
    String s = upper(body, key);
    return s == null || "ALL".equals(s) ? null : s;
  }

  /**
   * SCALE_PRODUCT_CODE: logs is the single-space code, which a trim would
   * lose; any whitespace-only value is logs, null/"" is "not chosen".
   */
  static String product(Map<String, Object> body, String key) {
    Object v = body.get(key);
    if (v == null) return null;
    String s = v.toString();
    if (s.isEmpty()) return null;
    if (s.isBlank()) return LOGS;
    return s.trim().toUpperCase(Locale.ROOT);
  }

  /** The value of a date field: absent, unreadable, or a day. */
  record DateValue(LocalDate date, boolean present) {
    boolean invalid() {
      return present && date == null;
    }

    boolean missing() {
      return !present;
    }
  }

  /** {@code yyyy-MM-dd} (a longer ISO timestamp is cut to its date, as the SPA's date fields send). */
  static DateValue date(Map<String, Object> body, String key) {
    String s = text(body, key);
    if (s == null) return new DateValue(null, false);
    try {
      return new DateValue(LocalDate.parse(s.length() > 10 ? s.substring(0, 10) : s), true);
    } catch (DateTimeParseException ex) {
      return new DateValue(null, true);
    }
  }

  /**
   * {@code RuleHelper.isValidDate} / the P203 year checks: a four-digit year
   * no later than 2099 (and, for P203 process dates, no earlier than 2000).
   */
  static boolean validYear(LocalDate d, int minYear) {
    return d != null && d.getYear() >= Math.max(minYear, 1000) && d.getYear() <= 2099;
  }

  /** A number field: absent, unreadable, or a value (with its text, for the decimal-places check). */
  record NumberValue(BigDecimal value, String raw) {
    boolean missing() {
      return raw == null;
    }

    boolean invalid() {
      return raw != null && value == null;
    }
  }

  static NumberValue number(Map<String, Object> body, String key) {
    String s = text(body, key);
    if (s == null) return new NumberValue(null, null);
    try {
      return new NumberValue(new BigDecimal(s.replace(",", "")), s);
    } catch (NumberFormatException ex) {
      return new NumberValue(null, s);
    }
  }

  static Long id(Map<String, Object> body, String key) {
    String s = text(body, key);
    if (s == null) {
      throw new WorkflowValidationException(key, RatingMessages.required(key));
    }
    try {
      return new BigDecimal(s).longValueExact();
    } catch (NumberFormatException | ArithmeticException ex) {
      throw new WorkflowValidationException(key, key + " is invalid.");
    }
  }

  /** Digits after the decimal point in what the user typed ({@code HBSValidatorHelper.isValidPrecision}). */
  static int decimals(String raw) {
    int dot = raw.indexOf('.');
    return dot < 0 ? 0 : raw.length() - dot - 1;
  }

  /**
   * {@link RowLock#check} with the SPA's value as the query layer serialises
   * it: a timestamp at midnight is sent as a bare {@code yyyy-MM-dd}, which
   * RowLock cannot parse, so it is read as that day at 00:00:00.
   */
  static void checkLock(Object current, Map<String, Object> body) {
    Object expected = body.get("updateTimestamp");
    if (expected instanceof String s && s.trim().matches("\\d{4}-\\d{2}-\\d{2}")) {
      expected = s.trim() + "T00:00:00";
    }
    RowLock.check(current, expected);
  }

  /** The legacy ActionErrors: problems collected in order, thrown together. */
  static final class Problems {
    private final List<Problem> list = new ArrayList<>();

    Problems add(String field, String message) {
      list.add(new Problem(field, message));
      return this;
    }

    boolean isEmpty() {
      return list.isEmpty();
    }

    int size() {
      return list.size();
    }

    void throwIfAny() {
      if (!list.isEmpty()) {
        throw new WorkflowValidationException(list);
      }
    }
  }
}
