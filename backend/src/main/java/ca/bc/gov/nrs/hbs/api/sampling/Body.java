package ca.bc.gov.nrs.hbs.api.sampling;

import ca.bc.gov.nrs.hbs.api.workflow.WorkflowValidationException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Typed access to a command body (the SPA sends strings; dates as yyyy-MM-dd). */
final class Body {

  private final Map<String, Object> values;

  Body(Map<String, Object> values) {
    this.values = values == null ? Map.of() : values;
  }

  Object raw(String name) {
    return values.get(name);
  }

  /** Trimmed text, null when absent or blank. */
  String text(String name) {
    Object v = values.get(name);
    if (v == null) return null;
    String s = v.toString().trim();
    return s.isEmpty() ? null : s;
  }

  /** Text as typed (not trimmed), "" when absent — for the legacy " " grade code. */
  String untrimmed(String name) {
    Object v = values.get(name);
    return v == null ? "" : v.toString();
  }

  String upper(String name) {
    String s = text(name);
    return s == null ? null : s.toUpperCase(Locale.ROOT);
  }

  /** Y / true / on (checkboxes) → true. */
  boolean flag(String name) {
    String s = text(name);
    return s != null && (s.equalsIgnoreCase("Y") || s.equalsIgnoreCase("true") || s.equalsIgnoreCase("on"));
  }

  LocalDate date(String name) {
    String s = text(name);
    if (s == null) return null;
    try {
      return LocalDate.parse(s.length() > 10 ? s.substring(0, 10) : s);
    } catch (DateTimeParseException ex) {
      throw new WorkflowValidationException(name, "'" + s + "' is not a valid date (yyyy-mm-dd).");
    }
  }

  /** A required numeric id (keys sent by the SPA, not typed by the user). */
  long id(String name) {
    String s = text(name);
    if (s == null) throw new WorkflowValidationException(name, name + " is required.");
    try {
      return Long.parseLong(s);
    } catch (NumberFormatException ex) {
      throw new WorkflowValidationException(name, name + " must be numeric.");
    }
  }

  @SuppressWarnings("unchecked")
  List<Map<String, Object>> list(String name) {
    Object v = values.get(name);
    return v instanceof List<?> l ? (List<Map<String, Object>>) l : List.of();
  }
}
