package ca.bc.gov.nrs.hbs.api.scaledata;

import ca.bc.gov.nrs.hbs.api.scaledata.ScaleDataModels.DocumentCounts;
import ca.bc.gov.nrs.hbs.api.scaledata.ScaleDataModels.DocumentResult;
import ca.bc.gov.nrs.hbs.api.scaledata.ScaleDataModels.Page;
import ca.bc.gov.nrs.hbs.api.scaledata.ScaleDataModels.StatementItem;
import ca.bc.gov.nrs.hbs.api.scaledata.ScaleDataModels.TransmissionResult;
import ca.bc.gov.nrs.hbs.api.scaledata.ScaleDataModels.TransmissionSummary;
import ca.bc.gov.nrs.hbs.api.workflow.Audit;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The reads behind the scale data API. Plain SELECTs, every value bound.
 *
 * <p>Fence: an account sees a transmission only when it was received for one of
 * its clients ({@code RECD_CLIENT_NUMBER}, what the FTP folder or the API
 * header named), and a statement only when it was issued to one of them. Any
 * other id answers 404, never 403, so ids can't be probed.
 *
 * <p>A batch document ({@code DTL_DOC_BATCH_DOCUMENT}) is what the client sent;
 * the version it created is found through the version tables' indexed
 * {@code BATDOC_ID}, and its edit errors through the version's
 * (document number, version).
 */
@Repository
public class ScaleDataReads {

  /** The six version tables (DETAIL_SCALE_DOC_TYPE_CODE W, P, S, F, A, D). */
  static final List<String> VERSION_TABLES = List.of("WEIGH_SLIP", "LOG_TALLY", "SAMPLE_LOG_TALLY",
      "SPECIAL_FOREST_PRODUCT_TALLY", "SCL_SITE_ARRIVAL_LEDGER", "SCL_SITE_DEPARTURE_LEDGER");

  /** Version-level edit error tables, keyed by (DETAIL_DOCUMENT_NUMBER, VERSION). */
  static final Map<String, String> VERSION_ERROR_TABLES = new LinkedHashMap<>();

  /** Line-level error tables: version table → (line table, line error table), joined on LOGDTL_ID. */
  static final Map<String, String[]> LINE_ERROR_TABLES = new LinkedHashMap<>();

  static {
    VERSION_ERROR_TABLES.put("WEIGH_SLIP", "WEIGH_SLIP_ERROR");
    VERSION_ERROR_TABLES.put("LOG_TALLY", "LOG_TALLY_ERROR");
    VERSION_ERROR_TABLES.put("SAMPLE_LOG_TALLY", "SAMPLE_LOG_TALLY_ERROR");
    VERSION_ERROR_TABLES.put("SPECIAL_FOREST_PRODUCT_TALLY", "SFP_TALLY_ERROR");
    VERSION_ERROR_TABLES.put("SCL_SITE_ARRIVAL_LEDGER", "SCALE_SITE_ARVL_LDGR_ERROR");
    VERSION_ERROR_TABLES.put("SCL_SITE_DEPARTURE_LEDGER", "SCALE_SITE_DEP_LDGR_ERROR");
    LINE_ERROR_TABLES.put("LOG_TALLY", new String[] {"LOG_TALLY_DETAIL", "LOG_TALLY_DETAIL_ERROR"});
    LINE_ERROR_TABLES.put("SAMPLE_LOG_TALLY", new String[] {"SAMPLE_TALLY_DETAIL", "SAMPLE_TALLY_DETAIL_ERROR"});
    LINE_ERROR_TABLES.put("SPECIAL_FOREST_PRODUCT_TALLY", new String[] {"SFP_LOG_DETAIL", "SFP_LOG_TALLY_DETAIL_ERROR"});
  }

  /** The batch documents of one transmission. */
  static final String BATDOCS_OF_TRANSMISSION = """
      SELECT bd.batdoc_id
        FROM dtl_doc_batch b
        JOIN dtl_doc_batch_document bd ON bd.batch_id = b.batch_id
       WHERE b.transmission_id = :transmissionId""";

  static final String TRANSMISSION = """
      SELECT t.transmission_id, t.recd_client_number, t.file_name, t.entry_timestamp, t.update_timestamp,
             t.hbs_xml_trans_step_code, t.hbs_processing_status_code, t.test_file_ind, t.batch_count
        FROM dtl_scl_doc_transmission t
       WHERE t.transmission_id = :transmissionId""";

  static final String BATCH_DOCUMENTS = """
      SELECT bd.batdoc_id, b.batch_id, b.detail_scale_doc_type_code AS doc_type, b.submitter_batch_id,
             bd.detail_document_number, bd.input_ddn, bd.input_dv, bd.entry_timestamp
        FROM dtl_doc_batch b
        JOIN dtl_doc_batch_document bd ON bd.batch_id = b.batch_id
       WHERE b.transmission_id = :transmissionId
       ORDER BY bd.batdoc_id""";

  static final String BATCHES = """
      SELECT b.batch_id, b.detail_scale_doc_type_code AS doc_type, b.submitter_batch_id
        FROM dtl_doc_batch b
       WHERE b.transmission_id = :transmissionId
       ORDER BY b.batch_id""";

  static final String TRANSMISSION_ERRORS = """
      SELECT e.hbs_edit_err_message_code AS code, m.description
        FROM transmission_format_error e
        LEFT JOIN hbs_edit_err_message_code m
          ON m.hbs_edit_err_message_code = e.hbs_edit_err_message_code
         AND m.hbs_edit_err_category_code = e.hbs_edit_err_category_code
       WHERE e.transmission_id = :transmissionId
       ORDER BY e.error_id""";

  static final String BATCH_ERRORS = """
      SELECT e.batch_id, e.hbs_edit_err_message_code AS code, m.description
        FROM dtl_bat_format_error e
        JOIN dtl_doc_batch b ON b.batch_id = e.batch_id
        LEFT JOIN hbs_edit_err_message_code m
          ON m.hbs_edit_err_message_code = e.hbs_edit_err_message_code
         AND m.hbs_edit_err_category_code = e.hbs_edit_err_category_code
       WHERE b.transmission_id = :transmissionId
       ORDER BY e.error_id""";

  static final String BATCH_DOCUMENT_ERRORS = """
      SELECT e.batdoc_id, e.hbs_edit_err_message_code AS code, m.description
        FROM dtl_bat_doc_format_error e
        LEFT JOIN hbs_edit_err_message_code m
          ON m.hbs_edit_err_message_code = e.hbs_edit_err_message_code
         AND m.hbs_edit_err_category_code = e.hbs_edit_err_category_code
       WHERE e.batdoc_id IN (%s)
       ORDER BY e.error_id""".formatted(BATDOCS_OF_TRANSMISSION);

  static final String VERSIONS = unionOf(VERSION_TABLES, table -> """
      SELECT v.batdoc_id, v.detail_document_number, v.version, v.scl_rtn_version_state_code AS state_code,
             NVL(v.update_timestamp, v.entry_timestamp) AS changed_at
        FROM %s v
       WHERE v.batdoc_id IN (%s)""".formatted(table, BATDOCS_OF_TRANSMISSION));

  static final String VERSION_ERRORS = "SELECT x.ddn, x.version, x.code, m.description FROM ("
      + unionOf(VERSION_ERROR_TABLES.keySet(), table -> """
          SELECT e.detail_document_number AS ddn, e.version, e.hbs_edit_err_message_code AS code,
                 e.hbs_edit_err_category_code AS category, e.error_id
            FROM %s e
           WHERE (e.detail_document_number, e.version) IN
                 (SELECT v.detail_document_number, v.version FROM %s v WHERE v.batdoc_id IN (%s))"""
          .formatted(VERSION_ERROR_TABLES.get(table), table, BATDOCS_OF_TRANSMISSION))
      + "\n UNION ALL\n"
      + unionOf(LINE_ERROR_TABLES.keySet(), table -> """
          SELECT l.detail_document_number AS ddn, l.version, e.hbs_edit_err_message_code AS code,
                 e.hbs_edit_err_category_code AS category, e.error_id
            FROM %s l
            JOIN %s e ON e.logdtl_id = l.logdtl_id
           WHERE (l.detail_document_number, l.version) IN
                 (SELECT v.detail_document_number, v.version FROM %s v WHERE v.batdoc_id IN (%s))"""
          .formatted(LINE_ERROR_TABLES.get(table)[0], LINE_ERROR_TABLES.get(table)[1], table,
              BATDOCS_OF_TRANSMISSION))
      + """
      ) x
        LEFT JOIN hbs_edit_err_message_code m
          ON m.hbs_edit_err_message_code = x.code
         AND m.hbs_edit_err_category_code = x.category
       ORDER BY x.ddn, x.version, x.error_id""";

  /**
   * When a transmission's result last changed: the row itself, or any version
   * it created (a re-edit flips ERR → RDY and stamps the version). One
   * correlated scalar sub-query per version table (one level deep, indexed).
   */
  static final String UPDATED_AT = "GREATEST(NVL(t.update_timestamp, t.entry_timestamp), t.entry_timestamp"
      + String.join("", VERSION_TABLES.stream().map(table -> """
          ,
            NVL((SELECT MAX(NVL(v.update_timestamp, v.entry_timestamp))
                   FROM %s v
                   JOIN dtl_doc_batch_document bd ON bd.batdoc_id = v.batdoc_id
                   JOIN dtl_doc_batch b ON b.batch_id = bd.batch_id
                  WHERE b.transmission_id = t.transmission_id), t.entry_timestamp)""".formatted(table)).toList())
      + ")";

  static final String LIST = """
      SELECT * FROM (
        SELECT t.transmission_id, t.recd_client_number, t.file_name, t.entry_timestamp,
               t.hbs_xml_trans_step_code, t.hbs_processing_status_code, t.test_file_ind,
               %s AS updated_at
          FROM dtl_scl_doc_transmission t
         WHERE t.recd_client_number IN (:clients)
           AND t.entry_timestamp >= :lookbackFrom
           %%s)
       WHERE updated_at >= :updatedSince
         %%s
       ORDER BY updated_at DESC, transmission_id DESC
       FETCH FIRST :fetch ROWS ONLY""".formatted(UPDATED_AT);

  /** API status → fixed step/status condition (no user text in the SQL). */
  static final Map<String, String> STATUS_FILTER = Map.of(
      ScaleDataStatus.QUEUED, "AND t.hbs_xml_trans_step_code = 'RCD' AND t.hbs_processing_status_code <> 'CUS'",
      ScaleDataStatus.PROCESSING, "AND t.hbs_xml_trans_step_code <> 'RCD' AND t.hbs_processing_status_code <> 'CUS'"
          + " AND NOT (t.hbs_xml_trans_step_code = 'HBE' AND t.hbs_processing_status_code = 'CSA')",
      ScaleDataStatus.PROCESSED, "AND t.hbs_xml_trans_step_code = 'HBE' AND t.hbs_processing_status_code = 'CSA'",
      ScaleDataStatus.REJECTED, "AND t.hbs_processing_status_code = 'CUS'");

  static final String KEYSET = "AND (updated_at < :cursorAt OR (updated_at = :cursorAt AND transmission_id < :cursorId))";

  static final String STATEMENTS = """
      SELECT t.transmission_id, t.client_number, t.client_locn_code, t.creation_date,
             t.ps_invoice_count, t.ps_vol_statement_count, t.ws_invoice_count, t.ws_vol_statement_count,
             t.compiled_sample_stmt_count, t.ratio_statement_count, t.pdf_file_name
        FROM document_delivery_transmission t
       WHERE t.client_number IN (:clients)
         AND t.file_name IS NOT NULL
         AND t.creation_date IS NOT NULL
         AND t.creation_date >= :since
         %s
       ORDER BY t.creation_date DESC, t.transmission_id DESC
       FETCH FIRST :fetch ROWS ONLY""";

  static final String STATEMENT_KEYSET =
      "AND (t.creation_date < :cursorAt OR (t.creation_date = :cursorAt AND t.transmission_id < :cursorId))";

  static String unionOf(Collection<String> tables, java.util.function.Function<String, String> branch) {
    return String.join("\n UNION ALL\n", tables.stream().map(branch).toList());
  }

  /** A transmission row, before the fence. */
  record Transmission(long id, String recdClientNumber, String fileName, LocalDateTime receivedAt,
      LocalDateTime updateTimestamp, String step, String status, boolean testFile, int batchCount) {
  }

  /** One batch document with the version it created (if any) and its errors. */
  record BatchDocument(long batdocId, long batchId, String docType, String batchNumber, String documentNumber,
      Integer version, String stateCode, LocalDateTime changedAt, List<ApiErrorItem> errors) {
  }

  private final NamedParameterJdbcTemplate jdbc;
  private final ScaleDataApiProperties properties;

  /** Rows per round trip: Oracle's JDBC default (10) costs a round trip per ten documents. */
  static final int FETCH_SIZE = 500;

  public ScaleDataReads(NamedParameterJdbcTemplate jdbc, ScaleDataApiProperties properties) {
    org.springframework.jdbc.core.JdbcTemplate template =
        new org.springframework.jdbc.core.JdbcTemplate(jdbc.getJdbcTemplate().getDataSource());
    template.setFetchSize(FETCH_SIZE);
    this.jdbc = new NamedParameterJdbcTemplate(template);
    this.properties = properties;
  }

  // ── one transmission ───────────────────────────────────────────────────

  /** The transmission if it was received for one of the account's clients. */
  public Optional<Transmission> transmission(long transmissionId, ApiAccount account) {
    return transmission(transmissionId).filter(t -> account.mayUse(t.recdClientNumber()));
  }

  Optional<Transmission> transmission(long transmissionId) {
    List<Transmission> rows = jdbc.query(TRANSMISSION, new MapSqlParameterSource("transmissionId", transmissionId),
        (rs, n) -> new Transmission(rs.getLong("transmission_id"), trim(rs.getString("recd_client_number")),
            rs.getString("file_name"), local(rs.getTimestamp("entry_timestamp")),
            local(rs.getTimestamp("update_timestamp")), rs.getString("hbs_xml_trans_step_code"),
            rs.getString("hbs_processing_status_code"), "Y".equals(rs.getString("test_file_ind")),
            rs.getInt("batch_count")));
    return rows.stream().findFirst();
  }

  /** The full result of a transmission the account may read. */
  public Optional<TransmissionResult> result(long transmissionId, ApiAccount account) {
    return transmission(transmissionId, account).map(this::result);
  }

  TransmissionResult result(Transmission t) {
    MapSqlParameterSource id = new MapSqlParameterSource("transmissionId", t.id());
    List<ApiErrorItem> errors = new ArrayList<>();
    jdbc.query(TRANSMISSION_ERRORS, id, (ResultSet rs) -> {
      errors.add(ApiErrorItem.transmission(rs.getString("code"), message(rs)));
    });

    Map<Long, String[]> batches = new LinkedHashMap<>();
    jdbc.query(BATCHES, id, (ResultSet rs) -> {
      batches.put(rs.getLong("batch_id"), new String[] {rs.getString("doc_type"), rs.getString("submitter_batch_id")});
    });
    java.util.Set<Long> rejectedBatches = new java.util.HashSet<>();
    jdbc.query(BATCH_ERRORS, id, (ResultSet rs) -> {
      long batchId = rs.getLong("batch_id");
      rejectedBatches.add(batchId);
      String[] b = batches.getOrDefault(batchId, new String[2]);
      errors.add(ApiErrorItem.batch(rs.getString("code"), message(rs), b[0], b[1]));
    });

    List<BatchDocument> documents = batchDocuments(t.id());
    int accepted = 0, ready = 0, error = 0, signatureFailed = 0, rejected = 0, testPassed = 0, other = 0;
    int documentErrors = 0;
    boolean truncated = false;
    LocalDateTime updated = latest(t.receivedAt(), t.updateTimestamp());
    for (BatchDocument d : documents) {
      String status = documentStatus(d, t.testFile());
      switch (status) {
        case "ACCEPTED" -> accepted++;
        case "READY" -> ready++;
        case "ERROR" -> error++;
        case "SIGNATURE_FAILED" -> signatureFailed++;
        case "REJECTED" -> rejected++;
        case "TEST_PASSED" -> testPassed++;
        default -> other++;
      }
      updated = latest(updated, d.changedAt());
      for (ApiErrorItem e : d.errors()) {
        if (documentErrors < properties.maxDocumentErrors()) {
          errors.add(e);
          documentErrors++;
        } else {
          truncated = true;
        }
      }
      if ("SIGNATURE_FAILED".equals(status) && d.errors().isEmpty()) {
        // DSF is a state, not always an error row: say why the document stopped.
        if (documentErrors < properties.maxDocumentErrors()) {
          errors.add(ApiErrorItem.document(null, "Scaler signature could not be verified.", d.docType(),
              d.documentNumber(), d.version()));
          documentErrors++;
        } else {
          truncated = true;
        }
      }
    }
    String status = ScaleDataStatus.transmission(t.step(), t.status());
    return new TransmissionResult(t.id(), t.recdClientNumber(), t.fileName(), offset(t.receivedAt()),
        offset(updated), status, t.step(), t.status(), t.testFile(), false, batches.size(), rejectedBatches.size(),
        documents.size(), new DocumentCounts(accepted, ready, error, signatureFailed, rejected, testPassed, other),
        errors, truncated, statusUrl(t.id()), documentsUrl(t.id()));
  }

  /** One page of a transmission's documents, in file order, optionally of one status. */
  public Page<DocumentResult> documents(Transmission t, String status, String cursor, int limit) {
    long after = cursor == null ? 0 : Cursors.decodeId(cursor);
    List<DocumentResult> items = new ArrayList<>();
    long last = after;
    boolean more = false;
    for (BatchDocument d : batchDocuments(t.id())) {
      if (d.batdocId() <= after) {
        continue;
      }
      String s = documentStatus(d, t.testFile());
      if (status != null && !status.equals(s)) {
        continue;
      }
      if (items.size() == limit) {
        more = true;
        break;
      }
      items.add(new DocumentResult(d.docType(), d.batchNumber(), d.documentNumber(), d.version(), s, d.stateCode(),
          offset(d.changedAt()), d.errors()));
      last = d.batdocId();
    }
    return new Page<>(items, more ? Cursors.encodeId(last) : null);
  }

  static String documentStatus(BatchDocument d, boolean testFile) {
    if (d.stateCode() != null) {
      return ScaleDataStatus.version(d.stateCode());
    }
    if (!d.errors().isEmpty()) {
      return "REJECTED";
    }
    return testFile ? "TEST_PASSED" : "OTHER";
  }

  /** Every batch document of a transmission with its version and errors (four indexed queries). */
  List<BatchDocument> batchDocuments(long transmissionId) {
    MapSqlParameterSource id = new MapSqlParameterSource("transmissionId", transmissionId);
    record Raw(long batdocId, long batchId, String docType, String batchNumber, String ddn, String inputDdn,
        String inputDv, LocalDateTime entered) {
    }
    List<Raw> raws = jdbc.query(BATCH_DOCUMENTS, id, (rs, n) -> new Raw(rs.getLong("batdoc_id"),
        rs.getLong("batch_id"), rs.getString("doc_type"), rs.getString("submitter_batch_id"),
        rs.getString("detail_document_number"), rs.getString("input_ddn"), rs.getString("input_dv"),
        local(rs.getTimestamp("entry_timestamp"))));
    if (raws.isEmpty()) {
      return List.of();
    }
    Map<Long, List<Object[]>> versionsByBatdoc = new HashMap<>();
    jdbc.query(VERSIONS, id, (ResultSet rs) -> {
      versionsByBatdoc.computeIfAbsent(rs.getLong("batdoc_id"), k -> new ArrayList<>()).add(new Object[] {
          rs.getString("detail_document_number"), rs.getInt("version"), rs.getString("state_code"),
          local(rs.getTimestamp("changed_at"))});
    });
    Map<Long, List<String[]>> formatErrors = new HashMap<>();
    jdbc.query(BATCH_DOCUMENT_ERRORS, id, (ResultSet rs) -> {
      formatErrors.computeIfAbsent(rs.getLong("batdoc_id"), k -> new ArrayList<>())
          .add(new String[] {rs.getString("code"), message(rs)});
    });
    Map<String, List<String[]>> versionErrors = new HashMap<>();
    jdbc.query(VERSION_ERRORS, id, (ResultSet rs) -> {
      versionErrors.computeIfAbsent(rs.getString("ddn") + "#" + rs.getInt("version"), k -> new ArrayList<>())
          .add(new String[] {rs.getString("code"), message(rs)});
    });

    List<BatchDocument> out = new ArrayList<>(raws.size());
    for (Raw r : raws) {
      Integer inputVersion = parseVersion(r.inputDv());
      Object[] version = pickVersion(versionsByBatdoc.get(r.batdocId()), inputVersion);
      String ddn = version != null ? (String) version[0] : r.ddn() != null ? r.ddn() : r.inputDdn();
      Integer v = version != null ? (Integer) version[1] : inputVersion;
      List<ApiErrorItem> errors = new ArrayList<>();
      for (String[] e : formatErrors.getOrDefault(r.batdocId(), List.of())) {
        errors.add(ApiErrorItem.document(e[0], e[1], r.docType(), ddn, v));
      }
      if (version != null) {
        for (String[] e : versionErrors.getOrDefault(ddn + "#" + v, List.of())) {
          errors.add(ApiErrorItem.document(e[0], e[1], r.docType(), ddn, v));
        }
      }
      out.add(new BatchDocument(r.batdocId(), r.batchId(), r.docType(), r.batchNumber(), ddn, v,
          version == null ? null : (String) version[2],
          version == null ? r.entered() : (LocalDateTime) version[3], List.copyOf(errors)));
    }
    return out;
  }

  /** The version the batch document created: the one whose number it sent, else the first. */
  static Object[] pickVersion(List<Object[]> versions, Integer inputVersion) {
    if (versions == null || versions.isEmpty()) {
      return null;
    }
    Object[] first = versions.get(0);
    for (Object[] v : versions) {
      if (inputVersion != null && inputVersion.equals(v[1])) {
        return v;
      }
      if ((Integer) v[1] < (Integer) first[1]) {
        first = v;
      }
    }
    return first;
  }

  static Integer parseVersion(String s) {
    if (s == null || !s.trim().matches("\\d{1,9}")) {
      return null;
    }
    return Integer.valueOf(s.trim());
  }

  // ── lists ──────────────────────────────────────────────────────────────

  /** Files received for the clients whose result changed at or after {@code updatedSince}, newest change first. */
  public Page<TransmissionSummary> list(List<String> clients, OffsetDateTime updatedSince, String status,
      String cursor, int limit) {
    LocalDateTime since = pacific(updatedSince);
    LocalDateTime lookback = LocalDateTime.now(Audit.ZONE).minus(properties.listLookback());
    MapSqlParameterSource p = new MapSqlParameterSource("clients", clients)
        .addValue("updatedSince", Timestamp.valueOf(since))
        .addValue("lookbackFrom", Timestamp.valueOf(since.isBefore(lookback) ? since : lookback))
        .addValue("fetch", limit + 1);
    String keyset = "";
    if (cursor != null) {
      Cursors.Position c = Cursors.decode(cursor);
      p.addValue("cursorAt", Timestamp.valueOf(c.at())).addValue("cursorId", c.id());
      keyset = KEYSET;
    }
    String sql = LIST.formatted(status == null ? "" : STATUS_FILTER.get(status), keyset);
    List<Object[]> rows = jdbc.query(sql, p, (rs, n) -> new Object[] {
        new TransmissionSummary(rs.getLong("transmission_id"), trim(rs.getString("recd_client_number")),
            rs.getString("file_name"), offset(local(rs.getTimestamp("entry_timestamp"))),
            offset(local(rs.getTimestamp("updated_at"))),
            ScaleDataStatus.transmission(rs.getString("hbs_xml_trans_step_code"),
                rs.getString("hbs_processing_status_code")),
            rs.getString("hbs_xml_trans_step_code"), rs.getString("hbs_processing_status_code"),
            "Y".equals(rs.getString("test_file_ind")), statusUrl(rs.getLong("transmission_id")),
            documentsUrl(rs.getLong("transmission_id"))),
        local(rs.getTimestamp("updated_at"))});
    return page(rows, limit, (TransmissionSummary s, LocalDateTime at) -> Cursors.encode(at, s.transmissionId()));
  }

  /** Statement transmissions issued to the clients since {@code since}, newest first. */
  public Page<StatementItem> statements(List<String> clients, OffsetDateTime since, String cursor, int limit) {
    LocalDateTime from = pacific(since);
    LocalDateTime sevenYears = LocalDateTime.now(Audit.ZONE).minusYears(7);
    MapSqlParameterSource p = new MapSqlParameterSource("clients", clients)
        .addValue("since", Timestamp.valueOf(from.isBefore(sevenYears) ? sevenYears : from))
        .addValue("fetch", limit + 1);
    String keyset = "";
    if (cursor != null) {
      Cursors.Position c = Cursors.decode(cursor);
      p.addValue("cursorAt", Timestamp.valueOf(c.at())).addValue("cursorId", c.id());
      keyset = STATEMENT_KEYSET;
    }
    List<Object[]> rows = jdbc.query(STATEMENTS.formatted(keyset), p, (rs, n) -> {
      long id = rs.getLong("transmission_id");
      LocalDateTime created = local(rs.getTimestamp("creation_date"));
      boolean hasPdf = rs.getString("pdf_file_name") != null;
      return new Object[] {
          new StatementItem(id, trim(rs.getString("client_number")), trim(rs.getString("client_locn_code")),
              offset(created), documentTypes(rs),
              hasPdf ? ScaleDataApi.BASE + "/statements/" + id + ".pdf" : null,
              ScaleDataApi.BASE + "/statements/" + id + ".xml"),
          created};
    });
    return page(rows, limit, (StatementItem s, LocalDateTime at) -> Cursors.encode(at, s.transmissionId()));
  }

  static List<String> documentTypes(ResultSet rs) throws SQLException {
    List<String> types = new ArrayList<>();
    if (rs.getInt("ps_invoice_count") > 0) types.add("PSI");
    if (rs.getInt("ps_vol_statement_count") > 0) types.add("PSV");
    if (rs.getInt("ws_invoice_count") > 0) types.add("WSI");
    if (rs.getInt("ws_vol_statement_count") > 0) types.add("WSV");
    if (rs.getInt("compiled_sample_stmt_count") > 0) types.add("CSS");
    if (rs.getInt("ratio_statement_count") > 0) types.add("RS");
    return types;
  }

  @SuppressWarnings("unchecked")
  private static <T> Page<T> page(List<Object[]> rows, int limit,
      java.util.function.BiFunction<T, LocalDateTime, String> cursorOf) {
    List<T> items = new ArrayList<>();
    for (int i = 0; i < Math.min(limit, rows.size()); i++) {
      items.add((T) rows.get(i)[0]);
    }
    String next = null;
    if (rows.size() > limit && !items.isEmpty()) {
      Object[] last = rows.get(limit - 1);
      next = cursorOf.apply((T) last[0], (LocalDateTime) last[1]);
    }
    return new Page<>(items, next);
  }

  // ── helpers ────────────────────────────────────────────────────────────

  static String statusUrl(long id) {
    return ScaleDataApi.BASE + "/submissions/" + id;
  }

  static String documentsUrl(long id) {
    return statusUrl(id) + "/documents";
  }

  private static String message(ResultSet rs) throws SQLException {
    String description = rs.getString("description");
    return description == null ? "Edit " + rs.getString("code") + " failed." : description.trim();
  }

  static LocalDateTime latest(LocalDateTime a, LocalDateTime b) {
    if (a == null) return b;
    if (b == null) return a;
    return b.isAfter(a) ? b : a;
  }

  static LocalDateTime local(Timestamp ts) {
    return ts == null ? null : ts.toLocalDateTime();
  }

  /** Oracle DATEs hold Pacific wall-clock time (Audit.ZONE). */
  static OffsetDateTime offset(LocalDateTime t) {
    return t == null ? null : t.atZone(Audit.ZONE).toOffsetDateTime();
  }

  static LocalDateTime pacific(OffsetDateTime t) {
    return t.atZoneSameInstant(Audit.ZONE).toLocalDateTime().withNano(0);
  }

  private static String trim(String s) {
    return s == null ? null : s.trim();
  }
}
