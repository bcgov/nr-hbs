package ca.bc.gov.nrs.hbs.api.queries;

import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Legacy {@code XmlConstants.CURRENT_XSL_VERSION}: the TRANS_VERSION of the latest
 * HBS_TS_VERSION row ({@code HbsFileQuery.getLatestTSVersionQuery}), used to render
 * merged documents (P451/P456 "Generate PDF", B2065 transmission PDFs). Legacy read
 * it once per JVM; it is cached here for the same reason (a new version means a
 * release with new stylesheets).
 */
@Component
public class StatementXslVersion {

  private static final String LATEST = """
      SELECT h.trans_version
        FROM hbs_ts_version h
       WHERE h.hbs_ts_version_id = (SELECT MAX(t.hbs_ts_version_id) FROM hbs_ts_version t)""";

  private final NamedParameterJdbcTemplate jdbc;
  private volatile String current;

  public StatementXslVersion(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public String current() {
    String v = current;
    if (v == null) {
      List<String> rows = jdbc.queryForList(LATEST, new MapSqlParameterSource(), String.class);
      if (rows.isEmpty() || rows.get(0) == null) {
        throw new IllegalStateException("Error finding active version number");
      }
      v = rows.get(0).trim();
      current = v;
    }
    return v;
  }
}
