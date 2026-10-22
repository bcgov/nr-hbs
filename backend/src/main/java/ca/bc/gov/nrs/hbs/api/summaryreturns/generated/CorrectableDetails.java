package ca.bc.gov.nrs.hbs.api.summaryreturns.generated;

import ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturnType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Reads about the detail documents behind a generated summary that can be rate-corrected:
 * {@code LogTallyQuery / SpecialForestProductTallyQuery / WeighSlipQuery
 * .selectTalliesBySummaryForCancelAndReplace(dcn, version, CORRECTABLE)} and their invoiced
 * transaction's segregations. Same predicate as {@code HBS_COUNT_CORRECTABLE_DETAILS}.
 */
@Component
public class CorrectableDetails {

  /** One detail family: txn table, txn segregation table, version table. */
  record Family(String txn, String txnSeg, String version) {}

  static final Family LOG_TALLY = new Family("log_tally_txn", "log_tally_txn_seg", "log_tally");
  static final Family SFP_TALLY = new Family("sfp_tally_txn", "sfp_tally_txn_seg", "special_forest_product_tally");
  static final Family WEIGH_SLIP = new Family("weigh_slip_txn", "weigh_slip_txn_seg", "weigh_slip");

  private final NamedParameterJdbcTemplate jdbc;

  public CorrectableDetails(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  static List<Family> families(SummaryReturnType type) {
    return switch (type) {
      case PIECE -> List.of(LOG_TALLY, SFP_TALLY);
      case WEIGHT -> List.of(WEIGH_SLIP);
      case SAMPLE -> List.of();
    };
  }

  /** The invoiced txns (dtl_txn_id) of the correctable detail documents of a summary version. */
  static String correctableTxns(Family f) {
    return """
        SELECT txn.dtl_txn_id FROM %1$s txn
         WHERE txn.document_control_number = :dcn AND txn.summary_document_version = :version
           AND txn.hbs_txn_status_code = 'ISS'
           AND NOT EXISTS (SELECT 1 FROM %1$s txn2
                            WHERE txn2.detail_document_number = txn.detail_document_number
                              AND txn2.detail_document_version = txn.detail_document_version
                              AND txn2.event_sequence > txn.event_sequence
                              AND txn2.hbs_txn_status_code IN ('CRT', 'FAL'))
           AND NOT EXISTS (SELECT 1 FROM %2$s v
                            WHERE v.detail_document_number = txn.detail_document_number
                              AND v.version = txn.detail_document_version
                              AND v.scl_rtn_version_state_code NOT IN ('ISS'))
           AND NOT EXISTS (SELECT 1 FROM detail_scale_document doc
                            WHERE doc.detail_document_number = txn.detail_document_number
                              AND doc.scale_return_state_code NOT IN ('OCM', 'RCC', 'RNC'))""".formatted(f.txn(), f.version());
  }

  /** {@code HBS_COUNT_CORRECTABLE_DETAILS(dcn, version) > 0} (piece: log tallies, else SFP tallies). */
  public boolean any(SummaryReturnType type, long dcn, long version) {
    for (Family f : families(type)) {
      Long n = jdbc.queryForObject("SELECT COUNT(*) FROM (" + correctableTxns(f) + ")", params(dcn, version), Long.class);
      if (n != null && n > 0) return true;
    }
    return false;
  }

  /** {@code FetchConfirmationDataTag.addAmountsFromDetails}: prorated volume / value of those txns' segregations. */
  public BigDecimal[] amounts(SummaryReturnType type, long dcn, long version) {
    BigDecimal volume = BigDecimal.ZERO;
    BigDecimal value = BigDecimal.ZERO;
    for (Family f : families(type)) {
      // findCorrectableDetailDocuments: SFP tallies only when there are no correctable log tallies
      if (f == SFP_TALLY && volume.signum() != 0) break;
      Map<String, Object> row = jdbc.queryForMap("SELECT NVL(SUM(s.prorated_volume), 0) AS volume,"
          + " NVL(SUM(s.prorated_value), 0) AS value FROM " + f.txnSeg() + " s WHERE s.dtl_txn_id IN ("
          + correctableTxns(f) + ")", params(dcn, version));
      volume = volume.add(new BigDecimal(row.get("VOLUME").toString()));
      value = value.add(new BigDecimal(row.get("VALUE").toString()));
    }
    return new BigDecimal[] {volume.setScale(3, java.math.RoundingMode.HALF_UP), value.setScale(2, java.math.RoundingMode.HALF_UP)};
  }

  private static MapSqlParameterSource params(long dcn, long version) {
    return new MapSqlParameterSource("dcn", dcn).addValue("version", version);
  }
}
