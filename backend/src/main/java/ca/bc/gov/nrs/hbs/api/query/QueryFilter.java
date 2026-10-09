package ca.bc.gov.nrs.hbs.api.query;

/**
 * One declared criterion of a {@link QueryDefinition}.
 *
 * @param param    request parameter name (also the bind name)
 * @param fragment SQL appended when the value is present, using {@code :param},
 *                 e.g. {@code AND tm.timber_mark = :timberMark}
 * @param type     how the raw string is converted before binding
 * @param required 400 when missing (legacy "mandatory" prompts)
 */
public record QueryFilter(String param, String fragment, Type type, boolean required) {

  public enum Type {
    /** Bound as-is (trimmed). */
    STRING,
    /** Upper-cased — timber marks, licences, codes. */
    UPPER,
    /** Upper-cased and wrapped in % for LIKE (legacy "starts with / contains"). */
    LIKE,
    /** Upper-cased with a trailing % (legacy prefix search). */
    PREFIX,
    /** ISO yyyy-MM-dd → java.sql.Date. */
    DATE,
    /** ISO yyyy-MM-dd → java.sql.Date at end of day semantics (exclusive next day). */
    DATE_TO,
    /** BigDecimal. */
    NUMBER,
    /** Comma-separated list bound as an IN (:param) collection. */
    LIST
  }

  public static QueryFilter of(String param, String fragment, Type type) {
    return new QueryFilter(param, fragment, type, false);
  }

  public static QueryFilter eq(String param, String fragment) {
    return new QueryFilter(param, fragment, Type.STRING, false);
  }

  public static QueryFilter upper(String param, String fragment) {
    return new QueryFilter(param, fragment, Type.UPPER, false);
  }

  public static QueryFilter like(String param, String fragment) {
    return new QueryFilter(param, fragment, Type.LIKE, false);
  }

  public static QueryFilter date(String param, String fragment) {
    return new QueryFilter(param, fragment, Type.DATE, false);
  }

  public static QueryFilter dateTo(String param, String fragment) {
    return new QueryFilter(param, fragment, Type.DATE_TO, false);
  }

  public static QueryFilter number(String param, String fragment) {
    return new QueryFilter(param, fragment, Type.NUMBER, false);
  }

  public static QueryFilter required(String param, String fragment, Type type) {
    return new QueryFilter(param, fragment, type, true);
  }
}
