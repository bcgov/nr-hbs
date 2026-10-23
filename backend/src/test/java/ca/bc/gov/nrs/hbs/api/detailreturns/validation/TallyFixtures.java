package ca.bc.gov.nrs.hbs.api.detailreturns.validation;

import ca.bc.gov.nrs.hbs.api.detailreturns.InMemoryDetailRows;
import ca.bc.gov.nrs.hbs.api.detailreturns.StubReferenceData;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.DetailDocument;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.DetailVersion;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.DocType;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.EditError;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.LogLine;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.Row;
import ca.bc.gov.nrs.hbs.api.detailreturns.state.DocumentState;
import ca.bc.gov.nrs.hbs.api.detailreturns.state.VersionState;
import ca.bc.gov.nrs.hbs.api.detailreturns.store.DetailStore;
import ca.bc.gov.nrs.hbs.api.workflow.Audit;
import ca.bc.gov.nrs.hbs.api.workflow.RecordingTableApi;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;

/** Shared builders for the log / SFP tally validator tests. */
final class TallyFixtures {

  static final LocalDate TODAY = LocalDate.of(2026, 6, 15);
  static final Timestamp SCALE_DATE = ts("2026-06-01 00:00:00");
  static final Timestamp LONG_AGO = ts("2000-01-01 00:00:00");
  static final Timestamp FAR_FUTURE = ts("9999-12-31 00:00:00");

  final StubReferenceData ref = StubReferenceData.valid();
  final StubTallyLookups tally = new StubTallyLookups();
  final InMemoryDetailRows rows = new InMemoryDetailRows();
  final DetailStore store = new DetailStore(rows, new RecordingTableApi(), new Audit("HBSBATCH"));

  TallyFixtures() {
    tally.addSpg("FI", LogTallyCommonValidator.PRODUCT, "J", LONG_AGO, FAR_FUTURE);
    tally.addGrade("C", "J", LONG_AGO, FAR_FUTURE);
  }

  static Timestamp ts(String s) {
    return Timestamp.valueOf(s);
  }

  /** A clean LOG_TALLY version row (inputs already copied). */
  static Row logTallyRow() {
    return new Row().set("detail_document_number", "DDN1").set("version", 1L)
        .set("scl_rtn_version_state_code", "INC").set("active_version_ind", "Y").set("original_version_ind", "Y")
        .set("scale_event_type_code", "PS").set("scale_date", SCALE_DATE).set("scale_site_id_nmbr", "S001")
        .set("timber_mark", "ABC123").set("primary_license_number", "1234").set("primary_quant_lic_type_cd", "S")
        .set("field_scale_ind", "N").set("beachcomb_ind", "N").set("log_count", 1L);
  }

  /** A clean SPECIAL_FOREST_PRODUCT_TALLY version row. */
  static Row sfpTallyRow() {
    return new Row().set("detail_document_number", "DDN1").set("version", 1L)
        .set("scl_rtn_version_state_code", "INC").set("active_version_ind", "Y").set("original_version_ind", "Y")
        .set("scale_event_type_code", "PS").set("hbs_sfp_scale_type_code", "L").set("scale_date", SCALE_DATE)
        .set("scale_site_id_nmbr", "S001").set("timber_mark", "ABC123").set("primary_license_number", "1234")
        .set("primary_quant_lic_type_cd", "S").set("field_scale_ind", "N").set("log_count", 1L);
  }

  /** A clean LOG_TALLY_DETAIL row. */
  static Row logLine(long logNumber) {
    return new Row().set("logdtl_id", 100 + logNumber).set("log_number", logNumber).set("length", "5.0")
        .set("top", 20).set("butt", 25).set("scale_species_code", "FI")
        .set("scale_product_code", LogTallyCommonValidator.PRODUCT).set("scale_grade_code", "J")
        .set("input_species", "FI").set("input_grade", "J").set("nmv_ind", "N");
  }

  /** A clean SFP_LOG_DETAIL row (no timber mark / NMV columns). */
  static Row sfpLine(long sfpNumber) {
    return new Row().set("logdtl_id", 200 + sfpNumber).set("sfp_number", sfpNumber).set("prod_log_length", "5.0")
        .set("prod_log_top", 20).set("prod_log_butt", 25).set("scale_species_code", "FI")
        .set("scale_product_code", "XM").set("scale_grade_code", "J").set("input_species", "FI")
        .set("input_product", "XM").set("input_grade", "J");
  }

  static DetailDocument document(DocType type, Row versionRow, Row... lines) {
    VersionState vs = new VersionState(versionRow.str("scl_rtn_version_state_code"), true);
    DetailVersion version = new DetailVersion(versionRow, vs, true);
    for (Row line : lines) {
      version.logs().add(new LogLine(line));
    }
    Row docRow = new Row().set("detail_document_number", versionRow.str("detail_document_number"))
        .set("scale_return_state_code", "OIP").set("detail_scale_doc_type_code", type.code());
    return new DetailDocument(type, docRow, new DocumentState("OIP", vs), version, true);
  }

  ValidationContext ctx() {
    return new ValidationContext(ref, store, TODAY);
  }

  LogTallyValidator logTally(DetailDocument doc) {
    return new LogTallyValidator(doc, ctx(), tally);
  }

  SfpTallyValidator sfp(DetailDocument doc) {
    return new SfpTallyValidator(doc, ctx(), tally);
  }

  /** Registers an original document in the store and returns its active version row. */
  Row original(String ddn, DocType type, String docState, String versionState) {
    rows.addDocument(ddn, type, docState);
    return rows.addVersion(ddn, 1, versionState, true).set("primary_license_number", "5678")
        .set("return_number", 77L).set("scale_date", ts("2026-05-20 00:00:00")).set("scale_event_type_code", "PS");
  }

  static List<String> codes(List<EditError> errors) {
    return errors.stream().map(EditError::code).toList();
  }
}
