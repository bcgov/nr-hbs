package ca.bc.gov.nrs.hbs.api.detailreturns;

import ca.bc.gov.nrs.hbs.api.detailreturns.model.DocType;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.Row;
import ca.bc.gov.nrs.hbs.api.detailreturns.store.DetailRows;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * In-memory {@link DetailRows} for unit tests: the "database" as the test set
 * it up. Writes made through {@code RecordingTableApi} do not change it, so a
 * test asserts the proc calls, not the rows.
 */
public class InMemoryDetailRows implements DetailRows {

  public final Map<String, Row> documents = new HashMap<>();
  public final List<Row> versions = new ArrayList<>();
  public final List<Row> lines = new ArrayList<>();
  public final List<Row> defects = new ArrayList<>();
  /** error rows: {error_id, detail_document_number, version} or {error_id, logdtl_id}. */
  public final List<Row> versionErrors = new ArrayList<>();
  public final List<Row> lineErrors = new ArrayList<>();

  public Row addDocument(String ddn, DocType type, String state) {
    Row r = new Row().set("detail_document_number", ddn).set("scale_return_state_code", state)
        .set("detail_scale_doc_type_code", type.code()).set("entry_userid", "IDIR\\OLD")
        .set("entry_timestamp", java.sql.Timestamp.valueOf("2026-01-01 08:00:00"));
    documents.put(ddn, r);
    return r;
  }

  public Row addVersion(String ddn, long version, String status, boolean active) {
    Row r = new Row().set("detail_document_number", ddn).set("version", version)
        .set("scl_rtn_version_state_code", status).set("active_version_ind", active ? "Y" : "N")
        .set("original_version_ind", version == 1 ? "Y" : "N").set("valid_signature_ind", "Y")
        .set("dtl_doc_seq", 500 + version)
        .set("entry_userid", "IDIR\\OLD").set("entry_timestamp", java.sql.Timestamp.valueOf("2026-01-01 08:00:00"))
        .set("update_userid", "IDIR\\OLD").set("update_timestamp", java.sql.Timestamp.valueOf("2026-01-02 09:00:00"));
    versions.add(r);
    return r;
  }

  @Override
  public Optional<Row> documentRow(String ddn) {
    return Optional.ofNullable(documents.get(ddn)).map(Row::copy);
  }

  @Override
  public List<Row> versionRows(DocType type, String ddn) {
    return versions.stream().filter(r -> ddn.equals(r.str("detail_document_number")))
        .sorted(Comparator.comparing(r -> r.lng("version"))).map(Row::copy).toList();
  }

  @Override
  public List<Row> lineRows(DocType type, String ddn, long version) {
    return lines.stream().filter(r -> ddn.equals(r.str("detail_document_number"))
            && Objects.equals(r.lng("version"), version)).map(Row::copy).toList();
  }

  @Override
  public List<Row> defectRows(DocType type, long logdtlId) {
    return defects.stream().filter(r -> Objects.equals(r.lng("logdtl_id"), logdtlId)).map(Row::copy).toList();
  }

  @Override
  public List<Long> versionErrorIds(DocType type, String ddn, long version) {
    return versionErrors.stream().filter(r -> ddn.equals(r.str("detail_document_number"))
        && Objects.equals(r.lng("version"), version)).map(r -> r.lng("error_id")).toList();
  }

  @Override
  public List<Long> lineErrorIds(DocType type, long logdtlId) {
    return lineErrors.stream().filter(r -> Objects.equals(r.lng("logdtl_id"), logdtlId))
        .map(r -> r.lng("error_id")).toList();
  }
}
