package ca.bc.gov.nrs.hbs.api.query;

import ca.bc.gov.nrs.hbs.api.exception.EntityNotFoundException;
import ca.bc.gov.nrs.hbs.api.struct.v1.PageableResponse;
import ca.bc.gov.nrs.hbs.api.util.RequestUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.sql.Clob;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Executes registry {@link QueryDefinition}s: assembles the whitelisted SQL
 * from the filters the caller filled, applies the client fence for industry
 * users, pages with {@code OFFSET/FETCH} + a {@code COUNT(*)}, and maps rows
 * to camelCase JSON objects (dates as ISO strings, as the SPA expects).
 */
@Service
@Slf4j
public class QueryService {

  private final NamedParameterJdbcTemplate jdbc;
  private final QueryRegistry registry;

  public QueryService(NamedParameterJdbcTemplate jdbc, QueryRegistry registry) {
    this.jdbc = jdbc;
    this.registry = registry;
  }

  /** Paged search. */
  public PageableResponse<Map<String, Object>> search(String queryId, Map<String, String> params,
      int page, int size, String sortBy, String sortDir) {
    QueryDefinition q = authorized(queryId);
    Built built = build(q, params);
    int pageSize = Math.max(1, Math.min(size <= 0 ? 20 : size, 500));
    int offset = Math.max(0, page) * pageSize;
    if (offset >= q.maxRows()) {
      throw new IllegalArgumentException("Page is beyond the " + q.maxRows() + "-row limit; refine the search");
    }
    Long total = jdbc.queryForObject("SELECT COUNT(*) FROM (" + built.sql + ")", built.params, Long.class);
    long capped = Math.min(total == null ? 0 : total, q.maxRows());
    built.params.addValue("hbsOffset", offset).addValue("hbsLimit", pageSize);
    String paged = built.sql + " ORDER BY " + orderBy(q, sortBy, sortDir)
        + " OFFSET :hbsOffset ROWS FETCH NEXT :hbsLimit ROWS ONLY";
    List<Map<String, Object>> rows = jdbc.query(paged, built.params, (rs, n) -> mapRow(rs));
    return PageableResponse.ofPage(rows, Math.max(0, page), pageSize, capped);
  }

  /** Every row (up to maxRows) — lookups, detail child tables. */
  public List<Map<String, Object>> list(String queryId, Map<String, String> params) {
    QueryDefinition q = authorized(queryId);
    Built built = build(q, params);
    built.params.addValue("hbsLimit", q.maxRows());
    String sql = built.sql + " ORDER BY " + q.defaultOrderBy() + " FETCH FIRST :hbsLimit ROWS ONLY";
    return jdbc.query(sql, built.params, (rs, n) -> mapRow(rs));
  }

  /** Exactly one row — record detail views. 404 when absent. */
  public Map<String, Object> single(String queryId, Map<String, String> params) {
    List<Map<String, Object>> rows = list(queryId, params);
    if (rows.isEmpty()) {
      throw new EntityNotFoundException(QueryDefinition.class, "query", queryId);
    }
    return rows.get(0);
  }

  /** CSV export of a search (the legacy screens' "Export"/"Download" buttons). */
  public String exportCsv(String queryId, Map<String, String> params) {
    List<Map<String, Object>> rows = list(queryId, params);
    StringWriter out = new StringWriter();
    try (CSVPrinter printer = new CSVPrinter(out, CSVFormat.DEFAULT)) {
      if (!rows.isEmpty()) {
        printer.printRecord(rows.get(0).keySet());
        for (Map<String, Object> row : rows) {
          printer.printRecord(row.values());
        }
      }
    } catch (IOException ex) {
      throw new IllegalStateException("CSV export failed", ex);
    }
    return out.toString();
  }

  private QueryDefinition authorized(String queryId) {
    QueryDefinition q = registry.query(queryId);
    q.capability().require();
    return q;
  }

  private record Built(String sql, MapSqlParameterSource params) {}

  private Built build(QueryDefinition q, Map<String, String> raw) {
    Map<String, String> in = raw == null ? Map.of() : raw;
    StringBuilder sql = new StringBuilder(q.sql());
    MapSqlParameterSource params = new MapSqlParameterSource();
    // Viewer binds available to every query — FOI severing in SQL:
    //   CASE WHEN :hbsIsMinistry = 'Y' OR fc.client_type_code <> 'I'
    //          OR fc.client_number = :hbsViewerClient
    //        THEN fc.client_name ELSE 'Not Releasable' END
    boolean ministry = RequestUtil.isMinistryUser();
    params.addValue("hbsIsMinistry", ministry ? "Y" : "N");
    params.addValue("hbsViewerClient", ministry ? "" : RequestUtil.getCurrentClientNumber());
    params.addValue("hbsUserType", RequestUtil.getLegacyUserType());
    // Legacy HBS_USER.USER_ID form (IDIR\NAME / BCEID\NAME, upper-cased) —
    // for the "associated sites / districts" data-domain filters, e.g.
    //   AND ss.scale_site_id IN (SELECT scale_site_id FROM hbs_user_site_data_domain
    //                             WHERE user_id = :hbsUserId)
    params.addValue("hbsUserId", RequestUtil.getCurrentAuditUserId().toUpperCase());
    for (QueryFilter f : q.filters()) {
      String value = in.get(f.param());
      if (!StringUtils.hasText(value)) {
        if (f.required()) {
          throw new IllegalArgumentException("'" + f.param() + "' is required");
        }
        continue;
      }
      params.addValue(f.param(), convert(f.param(), value.trim(), f.type()));
      sql.append('\n').append(f.fragment());
    }
    if (q.clientScope() != null && !RequestUtil.isMinistryUser()) {
      String client = RequestUtil.getCurrentClientNumber();
      if (!StringUtils.hasText(client)) {
        throw new AccessDeniedException("hbs.no_access_right: no client scope on token");
      }
      params.addValue("scopeClientNumber", client);
      sql.append('\n').append(q.clientScope());
    }
    return new Built(sql.toString(), params);
  }

  static Object convert(String name, String value, QueryFilter.Type type) {
    try {
      return switch (type) {
        case STRING -> value;
        case UPPER -> value.toUpperCase();
        case LIKE -> "%" + value.toUpperCase().replace("*", "%") + "%";
        case PREFIX -> value.toUpperCase().replace("*", "%") + "%";
        case DATE -> java.sql.Date.valueOf(LocalDate.parse(value));
        case DATE_TO -> java.sql.Date.valueOf(LocalDate.parse(value).plusDays(1));
        case NUMBER -> new BigDecimal(value);
        case LIST -> Arrays.stream(value.split(",")).map(String::trim)
            .filter(StringUtils::hasText).map(String::toUpperCase).toList();
      };
    } catch (DateTimeParseException | NumberFormatException ex) {
      throw new IllegalArgumentException("'" + name + "' has an invalid value");
    }
  }

  private static String orderBy(QueryDefinition q, String sortBy, String sortDir) {
    String expression = sortBy == null ? null : q.sortColumns().get(sortBy);
    if (expression == null) return q.defaultOrderBy();
    String dir = "desc".equalsIgnoreCase(sortDir) ? "DESC" : "ASC";
    return expression + " " + dir + " NULLS LAST";
  }

  static Map<String, Object> mapRow(ResultSet rs) throws SQLException {
    ResultSetMetaData md = rs.getMetaData();
    Map<String, Object> row = new LinkedHashMap<>();
    for (int i = 1; i <= md.getColumnCount(); i++) {
      String key = camel(md.getColumnLabel(i));
      Object value = rs.getObject(i);
      if (value instanceof Timestamp ts) {
        value = ts.toLocalDateTime().getHour() == 0 && ts.toLocalDateTime().getMinute() == 0
            && ts.toLocalDateTime().getSecond() == 0
            ? ts.toLocalDateTime().toLocalDate().toString()
            : ts.toLocalDateTime().toString();
      } else if (value instanceof java.sql.Date d) {
        value = d.toLocalDate().toString();
      } else if (value instanceof Clob clob) {
        value = clob.getSubString(1, (int) Math.min(clob.length(), Integer.MAX_VALUE));
      } else if (value != null && value.getClass().getName().startsWith("oracle.sql")) {
        value = rs.getString(i);
      }
      row.put(key, value);
    }
    return row;
  }

  /** {@code TIMBER_MARK} / {@code timber_mark} → {@code timberMark}; existing camelCase kept. */
  static String camel(String label) {
    if (label == null) return "";
    if (!label.contains("_") && !label.equals(label.toUpperCase())) return label;
    List<String> parts = new ArrayList<>(Arrays.asList(label.toLowerCase().split("_")));
    StringBuilder sb = new StringBuilder(parts.remove(0));
    for (String p : parts) {
      if (!p.isEmpty()) sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
    }
    return sb.toString();
  }
}
