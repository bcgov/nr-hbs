package ca.bc.gov.nrs.hbs.api.workflow;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Objects;

/**
 * Optimistic locking on {@code UPDATE_TIMESTAMP}, the only change marker the
 * HBS tables have (no REVISION_COUNT; docs/database.md "Locking"). The SPA
 * sends back the {@code updateTimestamp} it loaded; a workflow re-reads the row
 * inside its transaction and refuses when it differs, as the legacy
 * {@code StaleStateException} check did. Legacy batch writes stamp
 * UPDATE_TIMESTAMP too, so this also catches a batch job touching the record
 * between load and save.
 */
public final class RowLock {

  private RowLock() {
  }

  /**
   * Throws {@link StaleUpdateException} when {@code expected} (from the client)
   * is given and does not match {@code current} (from the database) to the
   * second — Oracle DATE precision.
   */
  public static void check(Object current, Object expected) {
    if (expected == null || (expected instanceof String s && s.isBlank())) {
      return; // the client did not send one (older screen); the legacy app did not check either
    }
    Long a = seconds(current);
    Long b = seconds(expected);
    if (!Objects.equals(a, b)) {
      throw new StaleUpdateException();
    }
  }

  static Long seconds(Object v) {
    if (v == null) return null;
    if (v instanceof Timestamp t) return t.getTime() / 1000;
    if (v instanceof java.util.Date d) return d.getTime() / 1000;
    if (v instanceof LocalDateTime t) return Timestamp.valueOf(t).getTime() / 1000;
    if (v instanceof Instant i) return i.getEpochSecond();
    if (v instanceof Number n) return n.longValue() > 100_000_000_000L ? n.longValue() / 1000 : n.longValue();
    String s = v.toString().trim();
    if (s.matches("\\d{4}-\\d{2}-\\d{2}")) {
      // A midnight DATE serialised without its time part.
      return Timestamp.valueOf(java.time.LocalDate.parse(s).atStartOfDay()).getTime() / 1000;
    }
    try {
      return Timestamp.valueOf(LocalDateTime.parse(s.replace(' ', 'T').replaceFirst("Z$", ""))).getTime() / 1000;
    } catch (DateTimeParseException ex) {
      try {
        return Instant.parse(s).getEpochSecond();
      } catch (DateTimeParseException ex2) {
        throw new IllegalArgumentException("Unreadable updateTimestamp '" + s + "'");
      }
    }
  }
}
