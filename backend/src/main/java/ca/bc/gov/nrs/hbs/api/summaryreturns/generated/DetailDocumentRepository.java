package ca.bc.gov.nrs.hbs.api.summaryreturns.generated;

import ca.bc.gov.nrs.hbs.api.summaryreturns.Row;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Reads of the detail documents behind a generated summary (the legacy {@code DetailManagerBean}
 * finders these actions use). Plain SQL, bound parameters, no schema prefix; table names come from
 * {@link DetailFamily} constants. Every method is overridable so the tests serve rows from memory.
 */
@Component
public class DetailDocumentRepository {

  /** One detail document version: DDN and version. */
  record Key(String ddn, long version) {}

  private final NamedParameterJdbcTemplate jdbc;

  public DetailDocumentRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  protected List<Row> rows(String sql, Map<String, ?> params) {
    return jdbc.queryForList(sql, new MapSqlParameterSource(params)).stream().map(Row::of).toList();
  }

  /**
   * {@code find{LogTally,WeighSlip,SampleLogTally}DocumentsByGeneratedSummary}: the detail versions
   * of every txn (any status) against the summary version ({@code find*TxnsByCriteria} +
   * {@code findDetailsByTxns}), each version once, in txn order.
   */
  List<Key> keysByGeneratedSummary(DetailFamily f, long dcn, long summaryVersion) {
    List<Row> txns = rows("SELECT detail_document_number, detail_document_version FROM " + f.txnTable
            + " WHERE document_control_number = :dcn AND summary_document_version = :version ORDER BY dtl_txn_id",
        Map.of("dcn", dcn, "version", summaryVersion));
    return distinct(txns);
  }

  /**
   * {@code findCorrectable{LogTallies,SFPTallies,WeighSlips}(dcn, version)}: the detail versions of
   * {@link CorrectableDetails#correctableTxns} ({@code selectTalliesBySummaryForCancelAndReplace(.., CORRECTABLE)}).
   */
  List<Key> correctableKeys(DetailFamily f, long dcn, long summaryVersion) {
    CorrectableDetails.Family cf = switch (f) {
      case LOG_TALLY -> CorrectableDetails.LOG_TALLY;
      case SFP_TALLY -> CorrectableDetails.SFP_TALLY;
      case WEIGH_SLIP -> CorrectableDetails.WEIGH_SLIP;
      case SAMPLE_LOG_TALLY -> throw new IllegalArgumentException("Sample tallies are never rate-corrected");
    };
    List<Row> txns = rows("SELECT t.detail_document_number, t.detail_document_version FROM " + f.txnTable
            + " t WHERE t.dtl_txn_id IN (" + CorrectableDetails.correctableTxns(cf) + ")"
            + " ORDER BY t.detail_document_number, t.detail_document_version",
        Map.of("dcn", dcn, "version", summaryVersion));
    return distinct(txns);
  }

  private static List<Key> distinct(List<Row> txns) {
    Set<Key> keys = new LinkedHashSet<>();
    for (Row t : txns) {
      keys.add(new Key(t.str("detail_document_number"), t.lng("detail_document_version")));
    }
    return new ArrayList<>(keys);
  }

  /** {@code DETAIL_SCALE_DOCUMENT} row. */
  Optional<Row> document(String ddn) {
    return rows("SELECT * FROM detail_scale_document WHERE detail_document_number = :ddn", Map.of("ddn", ddn))
        .stream().findFirst();
  }

  Optional<Row> version(DetailFamily f, String ddn, long version) {
    return rows("SELECT * FROM " + f.versionTable + " WHERE detail_document_number = :ddn AND version = :version",
        Map.of("ddn", ddn, "version", version)).stream().findFirst();
  }

  /** {@code findVersionsByStatus(ddn, status, docType)}. */
  List<Row> versionsInState(DetailFamily f, String ddn, String state) {
    return rows("SELECT * FROM " + f.versionTable
            + " WHERE detail_document_number = :ddn AND scl_rtn_version_state_code = :state ORDER BY version",
        Map.of("ddn", ddn, "state", state));
  }

  /** The txns of one detail version ({@code find*TxnByDetailVersionId}). */
  List<Row> txns(DetailFamily f, String ddn, long version) {
    return rows("SELECT * FROM " + f.txnTable
            + " WHERE detail_document_number = :ddn AND detail_document_version = :version ORDER BY event_sequence",
        Map.of("ddn", ddn, "version", version));
  }

  /** A txn's segregations ({@code getTxnSegregations()}). */
  List<Row> txnSegregations(DetailFamily f, long dtlTxnId) {
    return rows("SELECT * FROM " + f.txnSegTable + " WHERE dtl_txn_id = :id ORDER BY dtl_txn_seg_id",
        Map.of("id", dtlTxnId));
  }

  /** {@code LogTally / SpecialForestProductTally.getLogs()}. */
  List<Row> logs(DetailFamily f, String ddn, long version) {
    if (f.detailTable == null) return List.of();
    return rows("SELECT * FROM " + f.detailTable
            + " WHERE detail_document_number = :ddn AND version = :version ORDER BY logdtl_id",
        Map.of("ddn", ddn, "version", version));
  }

  /**
   * {@code DetailManagerBean.nextTxnSequence} ({@code DetailTransactionQuery.selectMaxSequence}):
   * MAX(event_sequence) over every version of the document, plus one (1 when there is none).
   */
  long nextEventSequence(DetailFamily f, String ddn) {
    Long max = jdbc.queryForObject("SELECT MAX(event_sequence) FROM " + f.txnTable
        + " WHERE detail_document_number = :ddn", new MapSqlParameterSource("ddn", ddn), Long.class);
    return (max == null ? 0 : max) + 1;
  }

  /**
   * BT 5193 ({@code LogTallyExtender.extendRateCorrection}): every {@code PIECE_SCL_SEGREGATION} of
   * the summary document, all versions ({@code PieceScaleSegregationEntityHome.findByCriteria(dcn, ...)}).
   */
  List<Row> pieceSegregationsOfDocument(long dcn) {
    return rows("SELECT * FROM piece_scl_segregation WHERE document_control_number = :dcn ORDER BY segregation_id",
        Map.of("dcn", dcn));
  }

  /** {@code findDetailScaleDocumentAndVersionByPrimaryKey}: document, version and its txns. */
  DetailDocument load(DetailFamily f, Key key) {
    Row doc = document(key.ddn()).orElseThrow(() -> notFound(key));
    Row version = version(f, key.ddn(), key.version()).orElseThrow(() -> notFound(key));
    return new DetailDocument(f, doc, version, new ArrayList<>(txns(f, key.ddn(), key.version())));
  }

  private static IllegalStateException notFound(Key key) {
    return new IllegalStateException("Detail scale document " + key.ddn() + " version " + key.version() + " not found");
  }
}
