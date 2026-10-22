package ca.bc.gov.nrs.hbs.api.summaryreturns;

import ca.bc.gov.nrs.hbs.api.exception.EntityNotFoundException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Reads summary returns for the workflows (the legacy finders of
 * {@code SummaryScaleReturnManagerBean} and {@code SummaryScaleReturnVersionQuery}).
 * Plain SQL, bound parameters, no schema prefix. Table names come from
 * {@link SummaryReturnType} constants, never from input.
 *
 * <p>Methods are overridable so the workflow tests can serve rows from memory.
 */
@Component
@Slf4j
public class SummaryReturnRepository {

  private final NamedParameterJdbcTemplate jdbc;

  public SummaryReturnRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  protected List<Row> rows(String sql, Map<String, ?> params) {
    return jdbc.queryForList(sql, new MapSqlParameterSource(params)).stream().map(Row::of).toList();
  }

  // ── Document and versions ────────────────────────────────────────────────

  /** {@code findSummaryScaleReturnByPrimaryKey}. */
  public Optional<Row> header(long dcn) {
    return rows("SELECT * FROM hbs_scale_return WHERE document_control_number = :dcn", Map.of("dcn", dcn))
        .stream().findFirst();
  }

  public Optional<Row> version(SummaryReturnType type, long dcn, long version) {
    return rows("SELECT * FROM " + type.versionTable() + " WHERE document_control_number = :dcn AND version = :version",
        Map.of("dcn", dcn, "version", version)).stream().findFirst();
  }

  /**
   * {@code selectActiveVersion}: the version with ACTIVE_VERSION_IND = 'Y'. Where data fixes left
   * more than one (docs/pinch-points.md §12) the highest is used, as the legacy finder returned the
   * last row it read.
   */
  public Optional<Row> activeVersion(SummaryReturnType type, long dcn) {
    List<Row> active = rows("SELECT * FROM " + type.versionTable()
        + " WHERE document_control_number = :dcn AND active_version_ind = 'Y' ORDER BY version", Map.of("dcn", dcn));
    if (active.size() > 1) {
      log.warn("Summary return {} has {} active versions; using the highest", dcn, active.size());
    }
    return active.isEmpty() ? Optional.empty() : Optional.of(active.get(active.size() - 1));
  }

  /** {@code selectMaxSequence}: MAX(version) of the document. */
  public long maxVersion(SummaryReturnType type, long dcn) {
    Long max = jdbc.queryForObject("SELECT MAX(version) FROM " + type.versionTable()
        + " WHERE document_control_number = :dcn", new MapSqlParameterSource("dcn", dcn), Long.class);
    return max == null ? 0 : max;
  }

  /** {@code selectPreviousInvoicedVersion}: the latest ISS/CAN version below {@code version}. */
  public Optional<Long> previousInvoicedVersion(SummaryReturnType type, long dcn, long version) {
    return jdbc.queryForList("SELECT version FROM " + type.versionTable()
            + " WHERE document_control_number = :dcn AND scl_rtn_version_state_code IN ('ISS', 'CAN')"
            + " AND version < :version ORDER BY version DESC",
        new MapSqlParameterSource("dcn", dcn).addValue("version", version), Long.class).stream().findFirst();
  }

  /** {@code selectNextPendingVersion}: a PND version above {@code version}. */
  public Optional<Long> nextPendingVersion(SummaryReturnType type, long dcn, long version) {
    return jdbc.queryForList("SELECT version FROM " + type.versionTable()
            + " WHERE document_control_number = :dcn AND scl_rtn_version_state_code = 'PND'"
            + " AND version > :version ORDER BY version",
        new MapSqlParameterSource("dcn", dcn).addValue("version", version), Long.class).stream().findFirst();
  }

  /** Number of versions of the document ({@code findVersions(...).size()}). */
  public long versionCount(SummaryReturnType type, long dcn) {
    Long n = jdbc.queryForObject("SELECT COUNT(*) FROM " + type.versionTable()
        + " WHERE document_control_number = :dcn", new MapSqlParameterSource("dcn", dcn), Long.class);
    return n == null ? 0 : n;
  }

  // ── Children ─────────────────────────────────────────────────────────────

  public List<Row> segregations(SummaryReturnType type, long dcn, long version) {
    if (!type.hasSegregations()) return List.of();
    return rows("SELECT * FROM " + type.segregationTable()
            + " WHERE document_control_number = :dcn AND version = :version ORDER BY segregation_id",
        Map.of("dcn", dcn, "version", version));
  }

  public List<Row> notations(SummaryReturnType type, long dcn, long version) {
    return rows("SELECT * FROM " + type.notationTable()
            + " WHERE document_control_number = :dcn AND version = :version ORDER BY notation_number, ntn_id",
        Map.of("dcn", dcn, "version", version));
  }

  public List<Row> dailyLoads(SummaryReturnType type, long dcn, long version) {
    if (type != SummaryReturnType.WEIGHT) return List.of();
    return rows("SELECT * FROM " + SummaryReturnType.DAILY_LOAD_TABLE
            + " WHERE document_control_number = :dcn AND version = :version ORDER BY day",
        Map.of("dcn", dcn, "version", version));
  }

  // ── Edit errors (for removeScaleReturnErrors) ────────────────────────────

  /** {@code getVersionErrors(type, dcn, null)}: the version errors of every version of the document. */
  public List<Long> versionErrorIds(SummaryReturnType type, long dcn) {
    return jdbc.queryForList("SELECT error_id FROM " + type.versionErrorTable()
        + " WHERE document_control_number = :dcn", new MapSqlParameterSource("dcn", dcn), Long.class);
  }

  /** {@code getSegregationErrors}: the errors of the given segregations. */
  public List<Long> segregationErrorIds(SummaryReturnType type, List<Long> segregationIds) {
    if (type.segregationErrorTable() == null || segregationIds.isEmpty()) return List.of();
    return jdbc.queryForList("SELECT error_id FROM " + type.segregationErrorTable()
        + " WHERE segregation_id IN (:ids)", new MapSqlParameterSource("ids", segregationIds), Long.class);
  }

  /** {@code getLoadSummaryErrors(dailyLoadSummary)}: the errors of the given days of a weight version. */
  public List<Long> dailyLoadErrorIds(long dcn, long version, List<Long> days) {
    if (days.isEmpty()) return List.of();
    return jdbc.queryForList("SELECT error_id FROM " + SummaryReturnType.LOAD_ERROR_TABLE
            + " WHERE document_control_number = :dcn AND version = :version AND day IN (:days)",
        new MapSqlParameterSource("dcn", dcn).addValue("version", version).addValue("days", days), Long.class);
  }

  /**
   * The latest transaction of a version as the legacy confirmation page displayed it
   * ({@code SummaryScaleReturnVersion.getLatestTransaction} + {@code SummaryScaleReturnHelper.createDisplayTransaction}):
   * event sequence, DR/CR, type, invoice (statement for sample), volume / value (sums of the txn
   * segregations; sample volume from its segregations) and sample weight.
   */
  public Optional<Row> latestTransaction(SummaryReturnType type, long dcn, long version) {
    String sql = switch (type) {
      case PIECE -> """
          SELECT t.trx_id, t.event_sequence, t.debit_credit_type, t.hbs_txn_type_code, t.invoice_number,
                 (SELECT SUM(s.volume) FROM piece_scl_txn_segregation s WHERE s.trx_id = t.trx_id) AS volume,
                 (SELECT SUM(s.value) FROM piece_scl_txn_segregation s WHERE s.trx_id = t.trx_id) AS value,
                 CAST(NULL AS NUMBER) AS weight
            FROM piece_scl_smry_txn t
           WHERE t.document_control_number = :dcn AND t.version = :version
           ORDER BY t.event_sequence DESC""";
      case WEIGHT -> """
          SELECT t.trx_id, t.event_sequence, t.debit_credit_type, t.hbs_txn_type_code, t.invoice_number,
                 (SELECT SUM(s.volume) FROM weight_scl_txn_segregation s WHERE s.trx_id = t.trx_id) AS volume,
                 (SELECT SUM(s.value) FROM weight_scl_txn_segregation s WHERE s.trx_id = t.trx_id) AS value,
                 CAST(NULL AS NUMBER) AS weight
            FROM weight_scl_smry_txn t
           WHERE t.document_control_number = :dcn AND t.version = :version
           ORDER BY t.event_sequence DESC""";
      case SAMPLE -> """
          SELECT t.trx_id, t.event_sequence, t.debit_credit_type, t.hbs_txn_type_code,
                 t.statement_number AS invoice_number,
                 (SELECT SUM(g.volume) FROM sample_segregation g
                   WHERE g.document_control_number = t.document_control_number AND g.version = t.version) AS volume,
                 CAST(NULL AS NUMBER) AS value,
                 (SELECT s.net_weight FROM sample_scale_summary s
                   WHERE s.document_control_number = t.document_control_number AND s.version = t.version) AS weight
            FROM sample_scl_smry_txn t
           WHERE t.document_control_number = :dcn AND t.version = :version
           ORDER BY t.event_sequence DESC""";
    };
    return rows(sql, Map.of("dcn", dcn, "version", version)).stream().findFirst();
  }

  /** {@code CyclicBillingManager.findYtdCyclicBilling} (BT7327). */
  public Optional<Row> cyclicBilling(String population, String stratum, Long year, String timberMark) {
    if (population == null || stratum == null || year == null || timberMark == null) return Optional.empty();
    return rows("SELECT * FROM ytd_cyclic_billing WHERE population_number = :p AND stratum_number = :s"
        + " AND sampling_year = :y AND timber_mark = :m", Map.of("p", population, "s", stratum, "y", year, "m", timberMark))
        .stream().findFirst();
  }

  // ── Assembled returns ────────────────────────────────────────────────────

  public SummaryReturn load(Row header, Row version) {
    SummaryReturnType type = SummaryReturnType.fromCode(header.str("hbs_return_type_code"));
    long dcn = header.lng("document_control_number");
    long v = version.lng("version");
    return new SummaryReturn(type, header, version,
        segregations(type, dcn, v), notations(type, dcn, v), dailyLoads(type, dcn, v));
  }

  /** {@code findScaleReturnWithVersion}. */
  public SummaryReturn load(long dcn, long version) {
    Row header = header(dcn).orElseThrow(() -> notFound(dcn));
    SummaryReturnType type = SummaryReturnType.fromCode(header.str("hbs_return_type_code"));
    Row row = version(type, dcn, version).orElseThrow(() -> notFound(dcn));
    return load(header, row);
  }

  /** {@code findScaleReturnWithActiveVersion}. */
  public SummaryReturn loadActive(long dcn) {
    Row header = header(dcn).orElseThrow(() -> notFound(dcn));
    SummaryReturnType type = SummaryReturnType.fromCode(header.str("hbs_return_type_code"));
    Row row = activeVersion(type, dcn).orElseThrow(() -> notFound(dcn));
    return load(header, row);
  }

  /** {@code findScaleReturnWithPreviousInvoicedVersion}: the document with its previous ISS/CAN version. */
  public Optional<SummaryReturn> loadPreviousInvoiced(SummaryReturn current) {
    return previousInvoicedVersion(current.type(), current.dcn(), current.versionNo())
        .map(v -> load(current.header().copy(), version(current.type(), current.dcn(), v).orElseThrow()));
  }

  /** {@code findScaleReturnWithNextPendingVersion}: the document with its next PND version. */
  public Optional<SummaryReturn> loadNextPending(SummaryReturn current) {
    return nextPendingVersion(current.type(), current.dcn(), current.versionNo())
        .map(v -> load(current.header().copy(), version(current.type(), current.dcn(), v).orElseThrow()));
  }

  private static EntityNotFoundException notFound(long dcn) {
    return new EntityNotFoundException(SummaryReturn.class, "documentControlNumber", String.valueOf(dcn));
  }
}
