package ca.bc.gov.nrs.hbs.api.summaryreturns;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * One row of an HBS table as the summary-return workflows handle it: column
 * name (lower case) → JDBC value. The legacy app carried each table in a value
 * object ({@code SummaryPieceScaleReturnVersion}, {@code PieceScaleSegregation},
 * …); a row keeps every column, so a STORE call built with
 * {@code TableApi.argsFromRow} re-sends what this port does not touch.
 */
public final class Row {

  private final Map<String, Object> values = new LinkedHashMap<>();

  public Row() {
  }

  /** Copy of a JDBC row (labels in any case). */
  public static Row of(Map<String, ?> source) {
    Row row = new Row();
    source.forEach(row::set);
    return row;
  }

  public Row copy() {
    return Row.of(values);
  }

  public Row set(String column, Object value) {
    values.put(column.toLowerCase(Locale.ROOT), value);
    return this;
  }

  public boolean has(String column) {
    return values.containsKey(column.toLowerCase(Locale.ROOT));
  }

  public Object get(String column) {
    return values.get(column.toLowerCase(Locale.ROOT));
  }

  /** Text value; blank strings are returned as they are (legacy distinguishes "" from null in a few edits). */
  public String str(String column) {
    Object v = get(column);
    return v == null ? null : v.toString();
  }

  public BigDecimal num(String column) {
    Object v = get(column);
    if (v == null) return null;
    if (v instanceof BigDecimal b) return b;
    if (v instanceof Number n) return new BigDecimal(n.toString());
    String s = v.toString().trim();
    return s.isEmpty() ? null : new BigDecimal(s);
  }

  public Long lng(String column) {
    BigDecimal n = num(column);
    return n == null ? null : n.longValue();
  }

  public Timestamp date(String column) {
    Object v = get(column);
    if (v == null) return null;
    if (v instanceof Timestamp t) return t;
    if (v instanceof java.util.Date d) return new Timestamp(d.getTime());
    if (v instanceof LocalDateTime t) return Timestamp.valueOf(t);
    if (v instanceof LocalDate d) return Timestamp.valueOf(d.atStartOfDay());
    throw new IllegalStateException(column + " is not a date: " + v);
  }

  /** Legacy Y/N indicator column; null is false, as {@code DbMap.flagToBoolean} read it. */
  public boolean flag(String column) {
    return "Y".equalsIgnoreCase(str(column));
  }

  public Map<String, Object> asMap() {
    return Collections.unmodifiableMap(values);
  }

  /** True when the column holds the same value (numbers compared by value, dates to the second). */
  public boolean same(String column, Object other) {
    return sameValue(get(column), other);
  }

  static boolean sameValue(Object a, Object b) {
    if (a == null || b == null) return a == b;
    if (a instanceof Number || b instanceof Number) {
      try {
        return new BigDecimal(a.toString()).compareTo(new BigDecimal(b.toString())) == 0;
      } catch (NumberFormatException ex) {
        return Objects.equals(a.toString(), b.toString());
      }
    }
    if (a instanceof java.util.Date || b instanceof java.util.Date
        || a instanceof LocalDateTime || b instanceof LocalDateTime) {
      return Objects.equals(seconds(a), seconds(b));
    }
    return Objects.equals(a.toString(), b.toString());
  }

  private static Long seconds(Object v) {
    if (v instanceof java.util.Date d) return d.getTime() / 1000;
    if (v instanceof LocalDateTime t) return Timestamp.valueOf(t).getTime() / 1000;
    if (v instanceof LocalDate d) return Timestamp.valueOf(d.atStartOfDay()).getTime() / 1000;
    return null;
  }

  @Override
  public String toString() {
    return values.toString();
  }
}
