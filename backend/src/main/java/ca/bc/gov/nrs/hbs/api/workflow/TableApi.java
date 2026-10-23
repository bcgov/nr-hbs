package ca.bc.gov.nrs.hbs.api.workflow;

import ca.bc.gov.nrs.hbs.api.dao.v1.StoredProcedureException;
import ca.bc.gov.nrs.hbs.api.workflow.ProcSignatures.Param;
import ca.bc.gov.nrs.hbs.api.workflow.ProcSignatures.Signature;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.CallableStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.CallableStatementCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Calls the legacy table-API procedures ({@code HBS_CREATE_* / HBS_STORE_* /
 * HBS_REMOVE_*}, plus the few business procs and functions) for the ported
 * workflow services and batch jobs. The app's DB role is SELECT-only on the HBS
 * tables, so this is the only way they write (AGENTS.md, docs/database.md).
 *
 * <p>Arguments are bound by the parameter names declared in nr-mof-db
 * ({@link ProcSignatures}), not by position. Every IN parameter must be given
 * (null included); an unknown or missing name is an {@link IllegalStateException}
 * before anything reaches the database. Calls join the caller's transaction.
 */
@Component
@Slf4j
public class TableApi {

  private final JdbcTemplate jdbc;
  private final ProcSignatures signatures;

  @Autowired
  public TableApi(JdbcTemplate jdbc) {
    this(jdbc, ProcSignatures.load());
  }

  protected TableApi(JdbcTemplate jdbc, ProcSignatures signatures) {
    this.jdbc = jdbc;
    this.signatures = signatures;
  }

  public ProcSignatures signatures() {
    return signatures;
  }

  /** {@code <sequence>.NEXTVAL}, as the legacy {@code UniqueIdQuery} did before each create. */
  public long nextId(String sequence) {
    if (!sequence.matches("[A-Za-z0-9_$.]+")) {
      throw new IllegalStateException("Invalid sequence name " + sequence);
    }
    Long id = jdbc.queryForObject("SELECT " + sequence + ".NEXTVAL FROM DUAL", Long.class);
    if (id == null) {
      throw new IllegalStateException(sequence + ".NEXTVAL returned null");
    }
    return id;
  }

  /** Runs a procedure; returns its OUT parameters by name (empty when it has none). */
  public Map<String, Object> call(String procedure, ProcArgs args) {
    Signature sig = signatures.get(procedure);
    if (sig.isFunction()) {
      throw new IllegalStateException(sig.name() + " is a function; use function(...)");
    }
    List<Object> values = bind(sig, args);
    return execute(sig, values, null);
  }

  /** Runs a function and returns its result, read as the given {@link Types} code. */
  public Object function(String function, ProcArgs args, int returnSqlType) {
    Signature sig = signatures.get(function);
    if (!sig.isFunction()) {
      throw new IllegalStateException(sig.name() + " is a procedure; use call(...)");
    }
    List<Object> values = bind(sig, args);
    return execute(sig, values, returnSqlType).get(RETURN_KEY);
  }

  /**
   * Arguments pre-filled from a row read with {@code SELECT *} (column labels in
   * any case): each {@code i_<column>} parameter gets that column's value. The
   * caller then overrides what changes. Parameters with no matching column stay
   * unset, so {@link #call} still insists they are given explicitly.
   */
  public ProcArgs argsFromRow(String procedure, Map<String, ?> row) {
    Signature sig = signatures.get(procedure);
    Map<String, Object> lower = new LinkedHashMap<>();
    row.forEach((k, v) -> lower.put(k.toLowerCase(Locale.ROOT), v));
    ProcArgs args = ProcArgs.of();
    for (Param p : sig.params()) {
      if (!p.isIn()) continue;
      String column = p.name().startsWith("i_") ? p.name().substring(2) : p.name();
      if (lower.containsKey(column)) {
        args.put(p.name(), lower.get(column));
      }
    }
    return args;
  }

  static final String RETURN_KEY = "$return";

  /** Validates names and converts values, in declared order (OUT-only params bind as null). */
  static List<Object> bind(Signature sig, ProcArgs args) {
    Set<String> declared = new TreeSet<>();
    sig.params().forEach(p -> declared.add(p.name()));
    Set<String> unknown = new TreeSet<>(args.asMap().keySet());
    unknown.removeAll(declared);
    if (!unknown.isEmpty()) {
      throw new IllegalStateException(sig.name() + " has no parameter(s) " + unknown + " (" + sig.source() + ")");
    }
    Set<String> missing = new TreeSet<>();
    List<Object> values = new ArrayList<>(sig.params().size());
    for (Param p : sig.params()) {
      if (p.isIn() && !args.has(p.name())) {
        missing.add(p.name());
        values.add(null);
      } else {
        values.add(p.isIn() ? convert(sig, p, args.get(p.name())) : null);
      }
    }
    if (!missing.isEmpty()) {
      throw new IllegalStateException(sig.name() + " needs parameter(s) " + missing + " (" + sig.source() + ")");
    }
    return values;
  }

  static Object convert(Signature sig, Param p, Object v) {
    if (v == null) return null;
    String base = baseType(p);
    try {
      return switch (base) {
        case "NUMBER", "INTEGER", "PLS_INTEGER", "BINARY_INTEGER", "FLOAT" -> number(v);
        case "DATE", "TIMESTAMP" -> timestamp(v);
        case "VARCHAR2", "VARCHAR", "CHAR", "NVARCHAR2", "CLOB", "LONG" -> string(v);
        case "RAW" -> v instanceof byte[] ? v : fail(v);
        default -> throw new IllegalStateException("unsupported type " + p.type());
      };
    } catch (RuntimeException ex) {
      throw new IllegalStateException(sig.name() + "." + p.name() + " (" + p.type() + "): cannot bind "
          + v.getClass().getSimpleName() + " " + v + " — " + ex.getMessage(), ex);
    }
  }

  private static String baseType(Param p) {
    String t = p.type().toUpperCase(Locale.ROOT).replaceAll("\\s*\\(.*$", "");
    if (t.endsWith("%TYPE")) {
      return "VARCHAR2"; // only WEIGH_SLIP.INCOMING_LDS_NUMBER%TYPE today (a VARCHAR2 column)
    }
    return t;
  }

  private static Object fail(Object v) {
    throw new IllegalArgumentException("unexpected value type");
  }

  private static BigDecimal number(Object v) {
    if (v instanceof BigDecimal b) return b;
    if (v instanceof Integer || v instanceof Long || v instanceof Short || v instanceof Byte) {
      return BigDecimal.valueOf(((Number) v).longValue());
    }
    if (v instanceof BigInteger b) return new BigDecimal(b);
    if (v instanceof Double || v instanceof Float) return BigDecimal.valueOf(((Number) v).doubleValue());
    if (v instanceof String s && !s.isBlank()) return new BigDecimal(s.trim());
    throw new IllegalArgumentException("not a number");
  }

  private static Timestamp timestamp(Object v) {
    if (v instanceof Timestamp t) return t;
    if (v instanceof java.sql.Date d) return new Timestamp(d.getTime());
    if (v instanceof java.util.Date d) return new Timestamp(d.getTime());
    if (v instanceof LocalDateTime t) return Timestamp.valueOf(t);
    if (v instanceof LocalDate d) return Timestamp.valueOf(d.atStartOfDay());
    throw new IllegalArgumentException("not a date");
  }

  private static String string(Object v) {
    if (v instanceof String s) return s;
    if (v instanceof Character || v instanceof Number) return v.toString();
    throw new IllegalArgumentException("not text (Y/N indicators are passed as strings)");
  }

  static int sqlType(Param p) {
    return switch (baseType(p)) {
      case "NUMBER", "INTEGER", "PLS_INTEGER", "BINARY_INTEGER", "FLOAT" -> Types.NUMERIC;
      case "DATE", "TIMESTAMP" -> Types.TIMESTAMP;
      case "RAW" -> Types.VARBINARY;
      case "CLOB" -> Types.CLOB;
      default -> Types.VARCHAR;
    };
  }

  /** Executes the bound call. Overridden by tests to record calls instead of reaching Oracle. */
  protected Map<String, Object> execute(Signature sig, List<Object> values, Integer returnSqlType) {
    boolean fn = returnSqlType != null;
    String placeholders = String.join(",", java.util.Collections.nCopies(values.size(), "?"));
    String sql = fn ? "{? = call " + sig.name() + "(" + placeholders + ")}"
        : "{call " + sig.name() + "(" + placeholders + ")}";
    try {
      return jdbc.execute(sql, (CallableStatementCallback<Map<String, Object>>) cs -> {
        int offset = 1;
        if (fn) {
          cs.registerOutParameter(1, returnSqlType);
          offset = 2;
        }
        for (int i = 0; i < values.size(); i++) {
          Param p = sig.params().get(i);
          int type = sqlType(p);
          if (p.isOut()) cs.registerOutParameter(i + offset, type);
          if (p.isIn()) {
            Object v = values.get(i);
            if (v == null) cs.setNull(i + offset, type);
            else cs.setObject(i + offset, v, type);
          }
        }
        cs.execute();
        return outValues(cs, sig, fn, offset);
      });
    } catch (DataAccessException ex) {
      Throwable root = ex.getMostSpecificCause();
      String message = root instanceof SQLException s ? s.getMessage() : String.valueOf(root);
      log.warn("{} failed: {}", sig.name(), message);
      throw new StoredProcedureException(sig.name(), "workflow", message);
    }
  }

  private static Map<String, Object> outValues(CallableStatement cs, Signature sig, boolean fn, int offset)
      throws SQLException {
    Map<String, Object> out = new LinkedHashMap<>();
    if (fn) out.put(RETURN_KEY, cs.getObject(1));
    for (int i = 0; i < sig.params().size(); i++) {
      Param p = sig.params().get(i);
      if (p.isOut()) out.put(p.name(), cs.getObject(i + offset));
    }
    return out;
  }
}
