package ca.bc.gov.nrs.hbs.api.intake;

import java.sql.Timestamp;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * SELECTs of the unpack, ported from the legacy finders B1012 used. Table
 * names come from {@link #versionTable(String)} (a fixed whitelist), every
 * value is a bound parameter.
 */
@Repository
public class JdbcUnpackRepository implements UnpackRepository {

  private final NamedParameterJdbcTemplate jdbc;

  public JdbcUnpackRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** The version table of a document type ({@code DetailScaleDocumentQuery.getFromClause}). */
  static String versionTable(String docTypeCode) {
    return switch (docTypeCode) {
      case "W" -> "WEIGH_SLIP";
      case "P" -> "LOG_TALLY";
      case "S" -> "SAMPLE_LOG_TALLY";
      case "F" -> "SPECIAL_FOREST_PRODUCT_TALLY";
      case "A" -> "SCL_SITE_ARRIVAL_LEDGER";
      case "D" -> "SCL_SITE_DEPARTURE_LEDGER";
      default -> throw new IllegalArgumentException("Unknown document type " + docTypeCode);
    };
  }

  @Override
  public List<Pending> pendingTransmissions() {
    return jdbc.query("""
        SELECT transmission_id, recd_directory, recd_client_number
          FROM dtl_scl_doc_transmission
         WHERE hbs_xml_trans_step_code = :step
           AND hbs_processing_status_code = :status
           AND recd_directory LIKE :prefix
         ORDER BY transmission_id""",
        new MapSqlParameterSource().addValue("step", TransmissionUnpacker.STEP_RECEIVING)
            .addValue("status", TransmissionUnpacker.STATUS_SUCCESS)
            .addValue("prefix", "input/%"),
        (rs, i) -> new Pending(rs.getLong(1), rs.getString(2), rs.getString(3)));
  }

  @Override
  public Map<String, Object> transmission(long transmissionId) {
    return single("SELECT * FROM dtl_scl_doc_transmission WHERE transmission_id = :id",
        new MapSqlParameterSource("id", transmissionId));
  }

  @Override
  public Map<String, Object> detailScaleDocument(String ddn) {
    return single("SELECT * FROM detail_scale_document WHERE detail_document_number = :ddn",
        new MapSqlParameterSource("ddn", ddn));
  }

  @Override
  public List<Map<String, Object>> versions(String docTypeCode, String ddn) {
    return jdbc.queryForList("SELECT version, original_version_ind FROM " + versionTable(docTypeCode)
        + " WHERE detail_document_number = :ddn ORDER BY version", new MapSqlParameterSource("ddn", ddn));
  }

  @Override
  public Map<String, Object> versionRow(String docTypeCode, String ddn, long version) {
    return single("SELECT * FROM " + versionTable(docTypeCode)
        + " WHERE detail_document_number = :ddn AND version = :version",
        new MapSqlParameterSource("ddn", ddn).addValue("version", version));
  }

  @Override
  public Map<String, Object> pendingPaperBatch(String scaleSite, Date from, Date to, long documentCount) {
    // PaperBatchQuery.selectByCriteria: same calendar day for both dates, exact count, first row.
    StringBuilder sql = new StringBuilder("SELECT * FROM hbs_paper_batch WHERE paper_batch_status_code = 'PND'");
    MapSqlParameterSource p = new MapSqlParameterSource("count", documentCount);
    if (scaleSite != null && !scaleSite.isEmpty()) {
      sql.append(" AND scale_site_id_nmbr = :site");
      p.addValue("site", scaleSite);
    }
    if (from != null) {
      sql.append(" AND from_scale_date >= TRUNC(:fromDate) AND from_scale_date < TRUNC(:fromDate) + 1");
      p.addValue("fromDate", new Timestamp(from.getTime()));
    }
    if (to != null) {
      sql.append(" AND to_scale_date >= TRUNC(:toDate) AND to_scale_date < TRUNC(:toDate) + 1");
      p.addValue("toDate", new Timestamp(to.getTime()));
    }
    sql.append(" AND document_count = :count ORDER BY batch_id FETCH FIRST 1 ROWS ONLY");
    return single(sql.toString(), p);
  }

  @Override
  public Long rsaPaddingId(String description) {
    List<Long> ids = jdbc.queryForList(
        "SELECT hbs_rsa_padding_id FROM hbs_rsa_padding_scheme WHERE description = :d",
        new MapSqlParameterSource("d", description), Long.class);
    return ids.isEmpty() ? null : ids.get(0);
  }

  // ── ReferenceLookups (the format edits' finders) ───────────────────────

  @Override
  public boolean clientLocationExists(String clientNumber, String locnCode) {
    // ClientReader.findClientLocation: both left-padded with '0' (8 / 2 characters).
    // V_CLIENT_PUBLIC stands in for FOREST_CLIENT, which the app's role can't read.
    return exists("""
        SELECT COUNT(*) FROM v_client_public c, client_location l
         WHERE c.client_number = l.client_number
           AND c.client_number = :client AND l.client_locn_code = :locn""",
        new MapSqlParameterSource("client", leftPad(clientNumber, 8)).addValue("locn", leftPad(locnCode, 2)));
  }

  @Override
  public boolean activeDetailSubmitterExists(String scaleSite, String returnTypeCode) {
    // ClientProfileManager.findActiveDetailSubmittersByScaleSiteAndReturnCode: detail ('D')
    // authorizations from SCALE_SITE_PROFILE and TRADING_PARTNER_SUBMITTER, effective now (inclusive).
    return exists("""
        SELECT COUNT(*) FROM (
          SELECT ssp.effective_date, ssp.expiry_date
            FROM scale_site_profile ssp, scale_site ss
           WHERE ssp.scale_site_id_nmbr = ss.scale_site_id_nmbr
             AND ssp.scale_site_id_nmbr = :site AND ssp.hbs_return_type_code = :rt AND ssp.doc_type = 'D'
          UNION ALL
          SELECT tps.effective_date, tps.expiry_date
            FROM trading_partner_submitter tps
           WHERE tps.scale_site_id_nmbr = :site AND tps.hbs_return_type_code = :rt AND tps.tp_doc_type = 'D')
         WHERE effective_date <= SYSDATE AND expiry_date >= SYSDATE""",
        new MapSqlParameterSource("site", scaleSite).addValue("rt", returnTypeCode));
  }

  @Override
  public boolean scaleSiteExists(String scaleSite) {
    return exists("SELECT COUNT(*) FROM scale_site WHERE scale_site_id_nmbr = :site",
        new MapSqlParameterSource("site", scaleSite));
  }

  @Override
  public String existingDocumentType(String ddn) {
    List<String> types = jdbc.queryForList(
        "SELECT detail_scale_doc_type_code FROM detail_scale_document WHERE detail_document_number = :ddn",
        new MapSqlParameterSource("ddn", ddn), String.class);
    return types.isEmpty() ? null : types.get(0);
  }

  @Override
  public boolean versionExists(char documentType, String ddn, long version) {
    String table = switch (documentType) {
      case 'W' -> "WEIGH_SLIP";
      case 'S' -> "SAMPLE_LOG_TALLY";
      case 'F' -> "SPECIAL_FOREST_PRODUCT_TALLY";
      default -> "LOG_TALLY";
    };
    return exists("SELECT COUNT(*) FROM " + table + " WHERE detail_document_number = :ddn AND version = :version",
        new MapSqlParameterSource("ddn", ddn).addValue("version", version));
  }

  private boolean exists(String sql, MapSqlParameterSource p) {
    Long n = jdbc.queryForObject(sql, p, Long.class);
    return n != null && n > 0;
  }

  private Map<String, Object> single(String sql, MapSqlParameterSource p) {
    List<Map<String, Object>> rows = jdbc.queryForList(sql, p);
    return rows.isEmpty() ? null : rows.get(0);
  }

  static String leftPad(String s, int length) {
    if (s == null) {
      return null;
    }
    StringBuilder b = new StringBuilder(s);
    while (b.length() < length) {
      b.insert(0, '0');
    }
    return b.toString();
  }
}
