package ca.bc.gov.nrs.hbs.api.detailreturns.validation;

import ca.bc.gov.nrs.hbs.api.detailreturns.model.Row;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * {@link WeighSampleLookups} over Oracle (bound parameters only). The legacy
 * queries were concatenated strings built with {@code Query.appendWhereClause};
 * they are ported predicate by predicate with the same rules:
 * <ul>
 *   <li>a "forced" column ({@code appendWhereClause(.., true)}) is always
 *       compared; an empty value became {@code col = ''}, which Oracle treats
 *       as {@code col = NULL} (never true) — binding null keeps that;</li>
 *   <li>a plain {@code appendWhereClause} on a String skips an empty value;</li>
 *   <li>the {@code findByCriteria} finders skip null criteria.</li>
 * </ul>
 * Column names are constants; only values are bound.
 */
@Component
public class JdbcWeighSampleLookups implements WeighSampleLookups {

  /** BT 11361 version states of the cross checks ({@link WeighSampleLookups#MM_STATES}). */
  static final String MM_STATES_SQL = "scl_rtn_version_state_code IN ('ISS','RDY','NTI','ERR','HLD','LCK','DSF','AWP')";

  /** {@code SummaryPieceScaleReturnVersionQuery.selectFieldDeckId} (BT 8253), counted. */
  static final String FIELD_SCALE_ESTIMATES = """
      SELECT COUNT(*) FROM piece_scale_summary
       WHERE field_scale_deck_id = :fieldScaleDeckId
         AND timber_mark = :timberMark
         AND pssmry_type = 'FLDSCLEST'
         AND scl_rtn_category_code = 'FI'
         AND scl_rtn_version_state_code IN ('ISS','RDY','ERR','HLD','LCK','CAN')
         AND active_version_ind = 'Y'
      """;

  private final NamedParameterJdbcTemplate jdbc;

  public JdbcWeighSampleLookups(JdbcTemplate jdbc) {
    this.jdbc = new NamedParameterJdbcTemplate(jdbc);
  }

  /** {@code DetailManagerQuery.selectSampleLogTallies} (BT 10414). */
  @Override
  public long sampleTallyConflict(String scaleSite, String origRedtagScaleSite, String weighSlipNumber,
                                  String eventType, String timberMark, String population, String stratum,
                                  Long samplingYear, Long sampleWeight) {
    Where w1 = new Where();
    w1.forced("sampled_weigh_slip", weighSlipNumber);
    if (DetailValidator.SAMPLE_SCALE.equals(eventType)) {
      w1.optional("scale_site_id_nmbr", scaleSite);
    } else if (DetailValidator.REMOTE_SAMPLE_SCALE.equals(eventType)) {
      w1.optional("scale_site_id_nmbr", scaleSite);
      w1.optional("orig_redtag_scale_site_id_nmbr", origRedtagScaleSite);
    }
    Where w2 = w1.copy();
    if (eventType != null) {
      w2.optional("scale_event_type_code", eventType);
    }
    w2.forced("timber_mark", timberMark);
    w2.forced("population_number", population);
    w2.forced("stratum_number", stratum);
    w2.forced("sampling_year", samplingYear);
    if (sampleWeight != null) {
      w2.forced("sample_weight", sampleWeight);
    }
    String sql = "SELECT COUNT(*) AS error_state FROM"
        + " (SELECT COUNT(*) AS c FROM sample_log_tally" + w1.sql("a_") + " AND " + MM_STATES_SQL
        + " AND active_version_ind = 'Y') a,"
        + " (SELECT COUNT(*) AS c FROM sample_log_tally" + w2.sql("b_") + " AND " + MM_STATES_SQL
        + " AND active_version_ind = 'Y') b"
        + " WHERE (a.c > 1 AND b.c = 0) OR (a.c > 1 AND b.c > 1)";
    MapSqlParameterSource params = new MapSqlParameterSource();
    w1.bind(params, "a_");
    w2.bind(params, "b_");
    return count(sql, params);
  }

  /** {@code DetailManagerQuery.selectLogTallies} (BT 8637). */
  @Override
  public long redTagLogTallyCount(String scaleSite, String origRedtagScaleSite, String weighSlipNumber,
                                  String eventType) {
    Where w = new Where();
    w.forced("red_tag_weigh_slip", weighSlipNumber);
    w.optional("scale_event_type_code", eventType);
    if (DetailValidator.RED_TAG.equals(eventType)) {
      w.optional("scale_site_id_nmbr", scaleSite);
    } else if (DetailValidator.REMOTE_RED_TAG.equals(eventType)) {
      w.optional("scale_site_id_nmbr", scaleSite);
      w.optional("orig_redtag_scale_site_id_nmbr", origRedtagScaleSite);
    }
    String sql = "SELECT COUNT(*) FROM log_tally" + w.sql("") + " AND " + MM_STATES_SQL
        + " AND active_version_ind = 'Y'";
    return count(sql, w.bind(new MapSqlParameterSource(), ""));
  }

  @Override
  public long fieldScaleEstimateCount(String fieldScaleDeckId, String timberMark) {
    return count(FIELD_SCALE_ESTIMATES,
        new MapSqlParameterSource("fieldScaleDeckId", fieldScaleDeckId).addValue("timberMark", timberMark));
  }

  /** {@code DetailManagerQuery.selectWeighSlips} (BT 8637 / 14566). */
  @Override
  public long sampledWeighSlipCount(String scaleSite, String originatingScaleSite, String weighSlipNumber,
                                    String eventType, boolean checkReplaceFlag) {
    Where w = new Where();
    w.forced("weigh_slip_number", weighSlipNumber);
    if (DetailValidator.SAMPLE_SCALE.equals(eventType)) {
      w.forced("scale_site_id_nmbr", scaleSite);
    }
    if (checkReplaceFlag) {
      if (DetailValidator.REMOTE_SAMPLE_SCALE.equals(eventType)) {
        w.forced("destination", scaleSite);
      }
    } else if (DetailValidator.REMOTE_SAMPLE_SCALE.equals(eventType)) {
      w.forced("scale_site_id_nmbr", originatingScaleSite);
    }
    String sql = "SELECT COUNT(*) FROM weigh_slip" + w.sql("") + " AND " + MM_STATES_SQL
        + " AND active_version_ind = 'Y'";
    return count(sql, w.bind(new MapSqlParameterSource(), ""));
  }

  /**
   * {@code DetailManagerBean.findWeighSlips(weighSlipNumber, scaleSite)}: the
   * first of {@code WeighSlipEntityHome.findByCriteria} (states MM_ERRORS,
   * site, number, active). The legacy finder had no ORDER BY; the callers only
   * use it after the count found exactly one, so the order added here only
   * makes the choice deterministic.
   */
  @Override
  public Optional<Row> sampledWeighSlip(String weighSlipNumber, String scaleSite) {
    Where w = new Where();
    w.raw(MM_STATES_SQL);
    w.optional("scale_site_id_nmbr", scaleSite);
    w.optional("weigh_slip_number", weighSlipNumber);
    w.raw("active_version_ind = 'Y'");
    String sql = "SELECT * FROM weigh_slip" + w.sql("") + " ORDER BY detail_document_number, version";
    return rows(sql, w.bind(new MapSqlParameterSource(), "")).stream().findFirst();
  }

  /** {@code WeighSlipEntityHome.findByCriteria} with the BT 6519 criteria (no state or active filter). */
  @Override
  public List<Row> weighSlipsByCriteria(String scaleSite, String populationNumber, String stratumNumber,
                                        Long samplingYear, String weighSlipNumber) {
    Where w = new Where();
    w.raw("1 = 1");
    if (scaleSite != null) w.optional("scale_site_id_nmbr", scaleSite);
    if (populationNumber != null) w.optional("population_number", populationNumber);
    if (stratumNumber != null) w.optional("stratum_number", stratumNumber);
    if (samplingYear != null) w.forced("sampling_year", samplingYear);
    if (weighSlipNumber != null) w.optional("weigh_slip_number", weighSlipNumber);
    String sql = "SELECT detail_document_number, version, scale_event_type_code, scale_date FROM weigh_slip"
        + w.sql("") + " ORDER BY detail_document_number, version";
    return rows(sql, w.bind(new MapSqlParameterSource(), ""));
  }

  /** {@code DetailManagerQuery.selectFieldScaleDeckId} (BT 10013). */
  @Override
  public String sampledWeighSlipFieldScaleDeckId(String scaleSite, String weighSlipNumber, String eventType) {
    Where w = new Where();
    if (weighSlipNumber != null) {
      w.forced("weigh_slip_number", weighSlipNumber);
    }
    if (eventType != null) {
      if (DetailValidator.SAMPLE_SCALE.equals(eventType)) {
        w.forced("scale_site_id_nmbr", scaleSite);
      } else if (DetailValidator.REMOTE_SAMPLE_SCALE.equals(eventType)) {
        w.forced("destination", scaleSite);
      }
    }
    // legacy appended " AND ..." even with no WHERE (an SQL error); the states keep this well formed
    w.raw(MM_STATES_SQL);
    w.raw("active_version_ind = 'Y'");
    String sql = "SELECT DECODE(COUNT(*), 1, MAX(field_scale_deck_id), NULL) FROM weigh_slip" + w.sql("");
    List<String> found = jdbc.queryForList(sql, w.bind(new MapSqlParameterSource(), ""), String.class);
    return found.isEmpty() ? null : found.get(0);
  }

  // ── helpers ───────────────────────────────────────────────────────

  private long count(String sql, MapSqlParameterSource params) {
    Number n = jdbc.queryForObject(sql, params, Number.class);
    return n == null ? 0L : n.longValue();
  }

  private List<Row> rows(String sql, MapSqlParameterSource params) {
    return jdbc.query(sql, params, (rs, n) -> {
      Map<String, Object> m = new LinkedHashMap<>();
      var md = rs.getMetaData();
      for (int c = 1; c <= md.getColumnCount(); c++) {
        Object v = rs.getObject(c);
        if (v instanceof Timestamp || v instanceof java.sql.Date
            || (v != null && v.getClass().getName().startsWith("oracle.sql."))) {
          v = rs.getTimestamp(c);
        }
        m.put(md.getColumnLabel(c), v);
      }
      return new Row(m);
    });
  }

  /** A WHERE clause of constant column names and bound values ({@code Query.appendWhereClause}). */
  static final class Where {

    private final List<String> predicates = new ArrayList<>();
    private final Map<String, Object> values = new LinkedHashMap<>();

    Where copy() {
      Where c = new Where();
      c.predicates.addAll(predicates);
      c.values.putAll(values);
      return c;
    }

    /** {@code appendWhereClause(.., true)}: always compared; empty binds null (never matches). */
    void forced(String column, Object value) {
      Object bound = value instanceof String s && s.isEmpty() ? null : value;
      predicates.add(column + " = :" + column);
      values.put(column, bound);
    }

    /** {@code appendWhereClause(String)}: skipped when the value is empty. */
    void optional(String column, String value) {
      if (value != null && !value.isEmpty()) {
        forced(column, value);
      }
    }

    void raw(String predicate) {
      predicates.add(predicate);
    }

    /** The clause with parameter names prefixed (so two clauses can share one statement). */
    String sql(String prefix) {
      if (predicates.isEmpty()) return "";
      List<String> out = new ArrayList<>();
      for (String p : predicates) {
        out.add(p.replaceAll(":([a-z_]+)", ":" + prefix + "$1"));
      }
      return " WHERE " + String.join(" AND ", out);
    }

    MapSqlParameterSource bind(MapSqlParameterSource params, String prefix) {
      // a null (legacy "col = ''") is bound as a typed NULL so the driver accepts it
      values.forEach((k, v) -> {
        if (v == null) {
          params.addValue(prefix + k, null, Types.VARCHAR);
        } else {
          params.addValue(prefix + k, v);
        }
      });
      return params;
    }

    List<String> predicates() {
      return predicates;
    }

    Map<String, Object> values() {
      return values;
    }
  }
}
