package ca.bc.gov.nrs.hbs.api.detailreturns.model;

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
 * One database row of a detail return (a version, a log line, a defect, the
 * document), keyed by lower-case column name exactly as {@code SELECT *}
 * returns it. The workflows edit rows in place and the writer hands them to the
 * table-API procs by column name ({@code TableApi.argsFromRow}), so a column
 * this code never touches is written back unchanged — the generated STORE
 * procs overwrite every column they are given.
 *
 * <p>Y/N indicator columns are read with {@link #yes} and written with
 * {@link #setYes}: the legacy entity beans mapped them to {@code Boolean}.
 */
public final class Row {

  private final Map<String, Object> values = new LinkedHashMap<>();

  public Row() {
  }

  public Row(Map<String, ?> source) {
    source.forEach(this::set);
  }

  public Row copy() {
    return new Row(values);
  }

  public Object get(String column) {
    return values.get(key(column));
  }

  public boolean has(String column) {
    return values.containsKey(key(column));
  }

  public Row set(String column, Object value) {
    values.put(key(column), value instanceof String s && s.isEmpty() ? null : value);
    return this;
  }

  public String str(String column) {
    Object v = get(column);
    return v == null ? null : v.toString();
  }

  /**
   * The legacy {@code StringUtil.empty(String)}: null or "" only — it keeps
   * whitespace ({@code keepWhitespace = true}), so a one-space code (the log
   * product, an input field the legacy actions set to " ") is NOT empty.
   */
  public boolean empty(String column) {
    String s = str(column);
    return s == null || s.isEmpty();
  }

  /** The legacy {@code StringUtil.empty(String, false)}: null or only whitespace (trims). */
  public boolean blank(String column) {
    String s = str(column);
    return s == null || s.trim().isEmpty();
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
    BigDecimal b = num(column);
    return b == null ? null : b.longValue();
  }

  public Timestamp date(String column) {
    Object v = get(column);
    if (v == null) return null;
    if (v instanceof Timestamp t) return t;
    if (v instanceof java.util.Date d) return new Timestamp(d.getTime());
    if (v instanceof LocalDateTime t) return Timestamp.valueOf(t);
    if (v instanceof LocalDate d) return Timestamp.valueOf(d.atStartOfDay());
    String s = v.toString().trim();
    if (s.isEmpty()) return null;
    if (s.length() == 10) return Timestamp.valueOf(LocalDate.parse(s).atStartOfDay());
    return Timestamp.valueOf(LocalDateTime.parse(s.replace(' ', 'T').replaceFirst("Z$", "")));
  }

  /** Y/N indicator as Boolean (null when the column is null). */
  public Boolean yes(String column) {
    String s = str(column);
    if (s == null) return null;
    return "Y".equalsIgnoreCase(s) || "true".equalsIgnoreCase(s);
  }

  public Row setYes(String column, Boolean value) {
    return set(column, value == null ? null : (value ? "Y" : "N"));
  }

  public Map<String, Object> asMap() {
    return Collections.unmodifiableMap(values);
  }

  /** True when the column holds the same value in both rows (numbers compared by value). */
  public static boolean same(Object a, Object b) {
    if (a instanceof Number x && b instanceof Number y) {
      return new BigDecimal(x.toString()).compareTo(new BigDecimal(y.toString())) == 0;
    }
    if (a instanceof java.util.Date x && b instanceof java.util.Date y) {
      return x.getTime() / 1000 == y.getTime() / 1000;
    }
    return Objects.equals(a, b);
  }

  private static String key(String column) {
    return column.toLowerCase(Locale.ROOT);
  }

  @Override
  public String toString() {
    return values.toString();
  }
}
