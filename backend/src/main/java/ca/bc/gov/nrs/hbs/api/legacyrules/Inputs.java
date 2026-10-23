package ca.bc.gov.nrs.hbs.api.legacyrules;

import ca.bc.gov.nrs.hbs.api.workflow.WorkflowValidationException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Map;

/**
 * Reading a workflow command's JSON body (and query criteria) the way the
 * legacy Struts forms did: trimmed strings, blank = absent, dates as
 * {@code yyyy-mm-dd} (a longer ISO value is cut to its date). Shared by the
 * billing, scale-control and submissions workflows (work package 7).
 */
public final class Inputs {

  private Inputs() {
  }

  /** Trimmed text, or null when absent/blank. */
  public static String text(Map<String, ?> body, String key) {
    Object v = body == null ? null : body.get(key);
    if (v == null) return null;
    String s = v.toString().trim();
    return s.isEmpty() ? null : s;
  }

  /** {@link #text} upper-cased (timber marks, codes, populations). */
  public static String upper(Map<String, ?> body, String key) {
    String s = text(body, key);
    return s == null ? null : s.toUpperCase(Locale.ROOT);
  }

  /** {@link #text}, refusing a blank value with {@code "<label> is required."} (errors.required). */
  public static String required(Map<String, ?> body, String key, String label) {
    String s = text(body, key);
    if (s == null) {
      throw new WorkflowValidationException(key, LegacyMessages.required(label));
    }
    return s;
  }

  /** A whole number, or null when blank; anything else is {@code "<label> must be a number."} (errors.long). */
  public static Long longValue(Map<String, ?> body, String key, String label) {
    String s = text(body, key);
    if (s == null) return null;
    try {
      return Long.parseLong(s);
    } catch (NumberFormatException ex) {
      throw new WorkflowValidationException(key, LegacyMessages.notANumber(label));
    }
  }

  /** A required whole number (record keys). */
  public static long requiredLong(Map<String, ?> body, String key, String label) {
    Long v = longValue(body, key, label);
    if (v == null) {
      throw new WorkflowValidationException(key, LegacyMessages.required(label));
    }
    return v;
  }

  /** A date ({@code yyyy-mm-dd…}), or null when blank; unparseable is {@code "<label> is not a valid date."} (errors.date). */
  public static LocalDate date(Map<String, ?> body, String key, String label) {
    String s = text(body, key);
    return s == null ? null : parseDate(s, key, label);
  }

  public static LocalDate parseDate(String s, String key, String label) {
    try {
      return LocalDate.parse(s.length() > 10 ? s.substring(0, 10) : s);
    } catch (DateTimeParseException ex) {
      throw new WorkflowValidationException(key, LegacyMessages.invalidDate(label));
    }
  }

  /** JDBC DATE/TIMESTAMP value → LocalDateTime (null-safe). */
  public static LocalDateTime dateTime(Object v) {
    if (v == null) return null;
    if (v instanceof java.sql.Timestamp t) return t.toLocalDateTime();
    if (v instanceof java.sql.Date d) return d.toLocalDate().atStartOfDay();
    if (v instanceof java.util.Date d) return new java.sql.Timestamp(d.getTime()).toLocalDateTime();
    if (v instanceof LocalDateTime t) return t;
    if (v instanceof LocalDate d) return d.atStartOfDay();
    String s = v.toString().trim();
    return s.length() <= 10 ? LocalDate.parse(s).atStartOfDay() : LocalDateTime.parse(s.replace(' ', 'T'));
  }

  /** JDBC DATE value → LocalDate (null-safe). */
  public static LocalDate localDate(Object v) {
    LocalDateTime t = dateTime(v);
    return t == null ? null : t.toLocalDate();
  }

  /** Case-insensitive row lookup (Oracle returns upper-case labels). */
  public static Object col(Map<String, ?> row, String column) {
    if (row == null) return null;
    Object v = row.get(column);
    if (v != null || row.containsKey(column)) return v;
    for (Map.Entry<String, ?> e : row.entrySet()) {
      if (e.getKey().equalsIgnoreCase(column)) return e.getValue();
    }
    return null;
  }

  public static String colText(Map<String, ?> row, String column) {
    Object v = col(row, column);
    return v == null ? null : v.toString();
  }
}
