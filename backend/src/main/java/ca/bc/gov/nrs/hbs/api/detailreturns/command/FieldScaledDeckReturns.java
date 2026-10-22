package ca.bc.gov.nrs.hbs.api.detailreturns.command;

import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * The detail returns one selected P842/P843/P844 row stands for — the
 * {@code DetailScaleDocumentSearchCriteria} {@code P840Action.setCriteria}
 * built and {@code DetailManager.findDetailScaleDocumentsByCriteria} ran through
 * the HBS2R651 (S), HBS2R601 (W) and HBS2R551 (P) search procs:
 * <ul>
 *   <li>active versions of the timber mark ("singlemark") with a field scale
 *       deck — the given one, or any when the deck is {@code *} (legacy "All":
 *       {@code FIELD_SCALE_DECK_ID IS NOT NULL});</li>
 *   <li>sample tallies: ISS, events SS/RS/CS ({@code SAMPLE_EVENTS});</li>
 *   <li>weigh slips: HLD/RDY/ERR/NTI with FIELD_SCALE_IND = 'Y', events PS/SS/RS/PD
 *       ({@code WEIGH_EVENTS});</li>
 *   <li>log tallies: HLD/RDY/ERR/NTI with FIELD_SCALE_IND = 'Y', the row's event type;</li>
 *   <li>sample and log tallies: the check-scale replacement rule ({@code chkScaleInd = Y});</li>
 *   <li>sample tallies and weigh slips: the row's Pop/Stratum/Year, or — for the
 *       "Unknown" row (legacy 9999/99/9999) — no population, stratum and year.</li>
 * </ul>
 * The legacy scale-date window ({@code getFromScaleDate}/{@code getToScaleDate})
 * was never filled by any page, so no date criteria apply.
 */
@Component
public class FieldScaledDeckReturns {

  /** One return found: DDN and version. */
  public record Found(String ddn, long version) {
  }

  /** What one selected row selects. {@code population == null} with {@code psyRow} = the Unknown row. */
  public record Selection(String docType, String timberMark, String deckId, boolean psyRow, String population,
                          String stratum, Long samplingYear, String eventCode) {
  }

  private static final String CHECK_SCALE_RULE =
      " AND ((a.scale_event_type_code = 'CS' AND a.check_replaces_original_ind = 'Y')"
          + " OR (a.scale_event_type_code <> 'CS' AND (a.check_replaces_original_ind = 'N'"
          + " OR a.check_replaces_original_ind IS NULL)))";

  private final NamedParameterJdbcTemplate jdbc;

  public FieldScaledDeckReturns(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** The SQL for a selection (exposed for tests). */
  static String sql(Selection s) {
    String table;
    StringBuilder where = new StringBuilder();
    switch (s.docType()) {
      case "S" -> {
        table = "sample_log_tally";
        where.append(" AND a.scl_rtn_version_state_code = 'ISS'")
            .append(" AND a.scale_event_type_code IN ('SS', 'RS', 'CS')").append(CHECK_SCALE_RULE);
      }
      case "W" -> {
        table = "weigh_slip";
        where.append(" AND a.scl_rtn_version_state_code IN ('HLD', 'RDY', 'ERR', 'NTI')")
            .append(" AND a.field_scale_ind = 'Y'")
            .append(" AND a.scale_event_type_code IN ('PS', 'SS', 'RS', 'PD')");
      }
      case "P" -> {
        table = "log_tally";
        where.append(" AND a.scl_rtn_version_state_code IN ('HLD', 'RDY', 'ERR', 'NTI')")
            .append(" AND a.field_scale_ind = 'Y'")
            .append(" AND a.scale_event_type_code = :eventCode").append(CHECK_SCALE_RULE);
      }
      default -> throw new IllegalArgumentException("docType " + s.docType());
    }
    if (s.psyRow()) {
      if (s.population() == null) {
        where.append(" AND a.population_number IS NULL AND a.stratum_number IS NULL AND a.sampling_year IS NULL");
      } else {
        where.append(" AND a.population_number = :population AND a.stratum_number = :stratum")
            .append(" AND a.sampling_year = :samplingYear");
      }
    }
    return "SELECT a.detail_document_number AS ddn, a.version FROM " + table + " a"
        + " WHERE a.active_version_ind = 'Y' AND a.timber_mark = :timberMark"
        + " AND a.field_scale_deck_id IS NOT NULL"
        + " AND (:deckId = '*' OR a.field_scale_deck_id = :deckId)" + where
        + " ORDER BY a.detail_document_number, a.version";
  }

  public List<Found> find(Selection s) {
    MapSqlParameterSource p = new MapSqlParameterSource()
        .addValue("timberMark", s.timberMark())
        .addValue("deckId", s.deckId())
        .addValue("eventCode", s.eventCode())
        .addValue("population", s.population())
        .addValue("stratum", s.stratum())
        .addValue("samplingYear", s.samplingYear());
    return jdbc.query(sql(s), p, (rs, n) -> new Found(rs.getString("ddn"), rs.getLong("version")));
  }
}
