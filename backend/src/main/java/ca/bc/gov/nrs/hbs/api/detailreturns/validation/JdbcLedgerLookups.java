package ca.bc.gov.nrs.hbs.api.detailreturns.validation;

import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** {@link LedgerLookups} over Oracle (bound parameters only). */
@Component
public class JdbcLedgerLookups implements LedgerLookups {

  /**
   * {@code SummaryPieceScaleReturnVersionQuery.selectFieldDeckId} (BT 8253),
   * reduced to an existence check (the legacy finder only tested the size).
   */
  static final String FIELD_SCALE_ESTIMATE_EXISTS = """
      SELECT COUNT(*) FROM piece_scale_summary
       WHERE field_scale_deck_id = :fieldScaleDeckId
         AND timber_mark = :timberMark
         AND pssmry_type = 'FLDSCLEST'
         AND scl_rtn_category_code = 'FI'
         AND scl_rtn_version_state_code IN ('ISS','RDY','ERR','HLD','LCK','CAN')
         AND active_version_ind = 'Y'
      """;

  private final NamedParameterJdbcTemplate jdbc;

  public JdbcLedgerLookups(JdbcTemplate jdbc) {
    this.jdbc = new NamedParameterJdbcTemplate(jdbc);
  }

  @Override
  public boolean fieldScaleEstimateExists(String fieldScaleDeckId, String timberMark) {
    Integer n = jdbc.queryForObject(FIELD_SCALE_ESTIMATE_EXISTS,
        Map.of("fieldScaleDeckId", fieldScaleDeckId, "timberMark", timberMark), Integer.class);
    return n != null && n > 0;
  }
}
