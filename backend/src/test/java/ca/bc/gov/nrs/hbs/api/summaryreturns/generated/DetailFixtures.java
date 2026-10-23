package ca.bc.gov.nrs.hbs.api.summaryreturns.generated;

import ca.bc.gov.nrs.hbs.api.summaryreturns.Row;
import ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryFixtures;
import ca.bc.gov.nrs.hbs.api.workflow.ProcSignatures.Param;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory detail documents for the generated-summary tests, on top of {@link SummaryFixtures}
 * (same {@code RecordingTableApi}). Document rows follow the {@code HBS_STORE_DTL_SCL_DOCUMENT}
 * calls, as the database would inside the workflow's transaction.
 */
class DetailFixtures {

  static final String USER = "IDIR\\TESTER";
  static final Timestamp LOADED = SummaryFixtures.LOADED;

  final SummaryFixtures s = new SummaryFixtures();
  final FakeDetails repo = new FakeDetails();
  final DetailDocumentWriter writer = new DetailDocumentWriter(s.api, s.audit);
  /** The detail-return workflow calls that follow each reset ({@code processReplacementVersionPendingAfterReset}). */
  final List<String> afterReset = new ArrayList<>();
  final DetailDocumentReset reset = new DetailDocumentReset(repo, writer,
      (ddn, version, user) -> afterReset.add(ddn + "/" + version));
  final RateCorrectionExtension extension = new RateCorrectionExtension(repo, writer, s.writer, s.audit);

  DetailFixtures() {
    s.api.answer("HBS_STORE_DTL_SCL_DOCUMENT", call -> {
      Row doc = repo.docs.get((String) call.arg("i_detail_document_number"));
      if (doc != null) doc.set("scale_return_state_code", call.arg("i_scale_return_state_code"));
      return Map.of();
    });
  }

  /** A row with every column the proc writes (null), so STORE calls built from it are complete. */
  Row fullRow(String proc, Map<String, String> renames) {
    Row r = new Row();
    for (Param p : s.api.signatures().get(proc).params()) {
      if (!p.isIn()) continue;
      r.set(renames.getOrDefault(p.name(), p.name().substring(2)), null);
    }
    return r;
  }

  Row document(String ddn, DetailFamily f, String state) {
    Row d = new Row().set("detail_document_number", ddn).set("scale_return_state_code", state)
        .set("detail_scale_doc_type_code", f.code).set("entry_userid", "IDIR\\ORIG").set("entry_timestamp", LOADED)
        .set("update_userid", "IDIR\\ORIG").set("update_timestamp", LOADED);
    repo.docs.put(ddn, d);
    return d;
  }

  Row version(DetailFamily f, String ddn, long version, String state, boolean active) {
    Row v = fullRow(f.storeVersionProc, f.versionRenames)
        .set("detail_document_number", ddn).set("version", version)
        .set("scl_rtn_version_state_code", state).set("active_version_ind", active ? "Y" : "N")
        .set("scale_date", Timestamp.valueOf("2026-09-03 10:15:00")).set("timber_mark", "ABC123")
        .set("entry_userid", "IDIR\\ORIG").set("entry_timestamp", LOADED)
        .set("update_userid", "IDIR\\ORIG").set("update_timestamp", LOADED);
    repo.versions.put(key(f, ddn, version), v);
    return v;
  }

  Row txn(DetailFamily f, long id, String ddn, long version, long dcn, long summaryVersion, long event,
      String type, String status, String drCr) {
    Row t = fullRow(f.storeTxnProc(), Map.of())
        .set("dtl_txn_id", id).set("hbs_txn_status_code", status).set("hbs_txn_type_code", type)
        .set("document_control_number", dcn).set("summary_document_version", summaryVersion)
        .set("detail_document_number", ddn).set("detail_document_version", version)
        .set("event_sequence", event).set("debit_credit_type", drCr)
        .set("entry_userid", "IDIR\\ORIG").set("entry_timestamp", LOADED);
    if (t.has("interest_reversal_ind")) t.set("interest_reversal_ind", "N");
    repo.txns.computeIfAbsent(f, k -> new ArrayList<>()).add(t);
    return t;
  }

  Row txnSeg(DetailFamily f, long txnId, long segId, String species, String volume, String value, Long segregationId) {
    Row seg = new Row().set("dtl_txn_seg_id", segId).set("dtl_txn_id", txnId)
        .set("scale_species_code", species).set("scale_product_code", "LO").set("scale_grade_code", "U")
        .set("segregation_id", segregationId).set("dtlseg_id", 77L).set("txn_seg_id", 88L)
        .set("prorated_volume", new BigDecimal(volume)).set("prorated_value", new BigDecimal(value))
        .set("rate", new BigDecimal("12.50")).set("rate_source", "STUMPAGE")
        .set("reserve_stmpg_rate_component", new BigDecimal("10.00"))
        .set("bonus_bid_rate_component", new BigDecimal("2.50"))
        .set("dev_levy_rate_component", null).set("silv_levy_rate_component", BigDecimal.ZERO)
        .set("reserve_stmpg_amount_component", new BigDecimal("100.00"))
        .set("bonus_bid_amount_component", new BigDecimal("25.00"))
        .set("dev_levy_amount_component", null).set("silv_levy_amount_component", BigDecimal.ZERO)
        .set("weight_x_rate", new BigDecimal("500.00"));
    repo.txnSegs.computeIfAbsent(txnId, k -> new ArrayList<>()).add(seg);
    return seg;
  }

  Row log(DetailFamily f, String ddn, long version, long id, String species, String volume) {
    Row l = new Row().set("logdtl_id", id).set("detail_document_number", ddn).set("version", version)
        .set("scale_species_code", species).set("scale_product_code", "LO").set("scale_grade_code", "U")
        .set("net_volume", new BigDecimal(volume)).set("nmv_ind", "N").set("timber_mark", null);
    repo.logs.computeIfAbsent(key(f, ddn, version), k -> new ArrayList<>()).add(l);
    return l;
  }

  static String key(DetailFamily f, String ddn, long version) {
    return f + ":" + ddn + ":" + version;
  }

  /** Serves detail rows from maps (copies, as a database would). */
  static class FakeDetails extends DetailDocumentRepository {
    final Map<String, Row> docs = new HashMap<>();
    final Map<String, Row> versions = new LinkedHashMap<>();
    final Map<DetailFamily, List<Row>> txns = new HashMap<>();
    final Map<Long, List<Row>> txnSegs = new HashMap<>();
    final Map<String, List<Row>> logs = new HashMap<>();
    final List<Row> previousSegs = new ArrayList<>();
    final Map<DetailFamily, List<Key>> correctable = new HashMap<>();

    FakeDetails() {
      super(null);
    }

    @Override
    List<Key> keysByGeneratedSummary(DetailFamily f, long dcn, long summaryVersion) {
      List<Key> keys = new ArrayList<>();
      txns.getOrDefault(f, List.of()).stream()
          .filter(t -> t.lng("document_control_number") == dcn && t.lng("summary_document_version") == summaryVersion)
          .sorted(java.util.Comparator.comparing((Row t) -> t.lng("dtl_txn_id")))
          .map(t -> new Key(t.str("detail_document_number"), t.lng("detail_document_version")))
          .filter(k -> !keys.contains(k)).forEach(keys::add);
      return keys;
    }

    @Override
    List<Key> correctableKeys(DetailFamily f, long dcn, long summaryVersion) {
      return correctable.getOrDefault(f, List.of());
    }

    @Override
    Optional<Row> document(String ddn) {
      return Optional.ofNullable(docs.get(ddn)).map(Row::copy);
    }

    @Override
    Optional<Row> version(DetailFamily f, String ddn, long version) {
      return Optional.ofNullable(versions.get(key(f, ddn, version))).map(Row::copy);
    }

    @Override
    List<Row> versionsInState(DetailFamily f, String ddn, String state) {
      return versions.entrySet().stream().filter(e -> e.getKey().startsWith(f + ":" + ddn + ":"))
          .map(Map.Entry::getValue).filter(v -> state.equals(v.str("scl_rtn_version_state_code")))
          .map(Row::copy).toList();
    }

    @Override
    List<Row> txns(DetailFamily f, String ddn, long version) {
      return txns.getOrDefault(f, List.of()).stream()
          .filter(t -> ddn.equals(t.str("detail_document_number")) && t.lng("detail_document_version") == version)
          .map(Row::copy).toList();
    }

    @Override
    List<Row> txnSegregations(DetailFamily f, long dtlTxnId) {
      return txnSegs.getOrDefault(dtlTxnId, List.of()).stream().map(Row::copy).toList();
    }

    @Override
    List<Row> logs(DetailFamily f, String ddn, long version) {
      return logs.getOrDefault(key(f, ddn, version), List.of()).stream().map(Row::copy).toList();
    }

    @Override
    long nextEventSequence(DetailFamily f, String ddn) {
      return txns.getOrDefault(f, List.of()).stream().filter(t -> ddn.equals(t.str("detail_document_number")))
          .mapToLong(t -> t.lng("event_sequence")).max().orElse(0) + 1;
    }

    @Override
    List<Row> pieceSegregationsOfDocument(long dcn) {
      return previousSegs.stream().map(Row::copy).toList();
    }
  }
}
