package ca.bc.gov.nrs.hbs.api.detailreturns.store;

import ca.bc.gov.nrs.hbs.api.detailreturns.model.DocType;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.Row;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * {@link DetailRows} over Oracle. Plain {@code SELECT *} so the rows carry every
 * column the table-API STORE procs need back (the legacy DAOs selected every
 * column too). Table names are constants of {@link DocType}; values are bound.
 */
@Component
public class JdbcDetailRows implements DetailRows {

  private final NamedParameterJdbcTemplate jdbc;

  public JdbcDetailRows(JdbcTemplate jdbc) {
    this.jdbc = new NamedParameterJdbcTemplate(jdbc);
  }

  private List<Row> rows(String sql, MapSqlParameterSource params) {
    return jdbc.query(sql, params, (rs, n) -> {
      Map<String, Object> m = new LinkedHashMap<>();
      var md = rs.getMetaData();
      for (int c = 1; c <= md.getColumnCount(); c++) {
        Object v = rs.getObject(c);
        if (v instanceof java.sql.Timestamp || v instanceof java.sql.Date) {
          v = rs.getTimestamp(c);
        } else if (v != null && v.getClass().getName().startsWith("oracle.sql.")) {
          v = rs.getTimestamp(c);
        }
        m.put(md.getColumnLabel(c), v);
      }
      return new Row(m);
    });
  }

  @Override
  public Optional<Row> documentRow(String ddn) {
    return rows("SELECT * FROM detail_scale_document WHERE detail_document_number = :ddn",
        new MapSqlParameterSource("ddn", ddn)).stream().findFirst();
  }

  @Override
  public List<Row> versionRows(DocType type, String ddn) {
    return rows("SELECT * FROM " + type.table() + " WHERE detail_document_number = :ddn ORDER BY version",
        new MapSqlParameterSource("ddn", ddn));
  }

  @Override
  public List<Row> lineRows(DocType type, String ddn, long version) {
    String order = type == DocType.SFP_TALLY ? "sfp_number" : "log_number";
    return rows("SELECT * FROM " + type.details().table()
            + " WHERE detail_document_number = :ddn AND version = :version ORDER BY " + order + ", logdtl_id",
        new MapSqlParameterSource("ddn", ddn).addValue("version", version));
  }

  @Override
  public List<Row> defectRows(DocType type, long logdtlId) {
    if (type.details() == null || type.details().defects() == null) {
      return List.of();
    }
    return rows("SELECT * FROM " + type.details().defects().table()
            + " WHERE logdtl_id = :id ORDER BY defect_number, log_defect_id",
        new MapSqlParameterSource("id", logdtlId));
  }

  @Override
  public List<Long> versionErrorIds(DocType type, String ddn, long version) {
    return jdbc.queryForList("SELECT error_id FROM " + type.errorTable()
            + " WHERE detail_document_number = :ddn AND version = :version ORDER BY error_id",
        new MapSqlParameterSource("ddn", ddn).addValue("version", version), Long.class);
  }

  @Override
  public List<Long> lineErrorIds(DocType type, long logdtlId) {
    return jdbc.queryForList("SELECT error_id FROM " + type.details().errorTable()
            + " WHERE logdtl_id = :id ORDER BY error_id",
        new MapSqlParameterSource("id", logdtlId), Long.class);
  }
}
