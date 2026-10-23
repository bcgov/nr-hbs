package ca.bc.gov.nrs.hbs.api.intake;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.bc.gov.nrs.hbs.api.intake.xml.ScaleXmlFixtures;
import ca.bc.gov.nrs.hbs.api.workflow.RecordingTableApi;
import ca.bc.gov.nrs.hbs.api.workflow.RecordingTableApi.Call;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.util.LinkedCaseInsensitiveMap;

/**
 * The B1012 port end to end against the real proc signatures: which procs it
 * calls, in which order, with which key arguments, and the format edits.
 */
class TransmissionUnpackerTest {

  private static final long TX = 501L;
  private static final Timestamp NOW = Timestamp.valueOf("2026-10-21 03:00:00");
  private static final TransmissionUnpacker.Submitter SCALER = new TransmissionUnpacker.Submitter(true, false);

  private RecordingTableApi api;
  private StubUnpackRepository repo;

  @BeforeEach
  void setUp() {
    api = new RecordingTableApi();
    repo = new StubUnpackRepository();
    repo.receivedTransmission(TX);
  }

  private TransmissionUnpacker.Outcome unpack(String xml) {
    return unpack(xml, SCALER);
  }

  private TransmissionUnpacker.Outcome unpack(String xml, TransmissionUnpacker.Submitter submitter) {
    UnpackContext ctx = new UnpackContext(api, repo, "HBSBATCH", NOW);
    return new TransmissionUnpacker(ctx).process(TX, ScaleXmlFixtures.bytes(xml), submitter);
  }

  private static String fixture() {
    return ScaleXmlFixtures.signed("all-types-v3.xml");
  }

  private List<Call> transmissionStores() {
    return api.callsTo("HBS_STORE_DTL_SCL_DOC_TRANS");
  }

  private List<String> errors(String proc) {
    return api.callsTo(proc).stream().map(c -> (String) c.arg("i_hbs_edit_err_message_code")).toList();
  }

  @Test
  void productionFileEndsUnpackedWithEveryDocumentCreated() {
    TransmissionUnpacker.Outcome outcome = unpack(fixture());

    assertThat(outcome.status()).isEqualTo("CSA");
    assertThat(outcome.documents()).isEqualTo(9);
    // UNP/PRC, counts, UNP/CSA — all in the caller's single transaction.
    List<Call> tx = transmissionStores();
    assertThat(tx).extracting(c -> c.arg("i_hbs_xml_trans_step_code") + "/" + c.arg("i_hbs_processing_status_code"))
        .containsExactly("UNP/PRC", "UNP/PRC", "UNP/CSA");
    Call counts = tx.get(1);
    assertThat(counts.arg("i_batch_count")).isEqualTo(new BigDecimal(9));
    assertThat(counts.arg("i_record_count")).isEqualTo(new BigDecimal(9));
    assertThat(counts.arg("i_client_number")).isEqualTo("00123456");
    assertThat(counts.arg("i_client_locn_code")).isEqualTo("00");
    assertThat(counts.arg("i_test_file_ind")).isEqualTo("N");
    assertThat(counts.arg("i_software_product")).isEqualTo("SCALEPRO");
    assertThat(counts.arg("i_update_userid")).isEqualTo("HBSBATCH");
    assertThat(counts.arg("i_recd_directory")).isEqualTo("input/00012345/501.xml"); // kept

    assertThat(api.callsTo("HBS_CREATE_DTL_DOC_BATCH")).hasSize(9)
        .extracting(c -> c.arg("i_detail_scale_doc_type_code"))
        .containsExactly("W", "W", "W", "P", "S", "P", "F", "A", "D");
    assertThat(api.callsTo("HBS_CREATE_DTL_DOC_BATCH")).allSatisfy(c -> {
      assertThat(c.arg("i_scale_site_id_nmbr")).isNull(); // set by the store after validation
      assertThat(c.arg("i_dtldocbat_type")).isEqualTo("DTLBAT");
    });
    assertThat(api.callsTo("HBS_STORE_DTL_DOC_BATCH")).hasSize(9)
        .allSatisfy(c -> assertThat(c.arg("i_scale_site_id_nmbr")).isEqualTo("S123"));
    assertThat(api.callsTo("HBS_CREATE_DTL_SCL_DOCUMENT")).hasSize(9)
        .allSatisfy(c -> assertThat(c.arg("i_scale_return_state_code")).isEqualTo("OIP"));
    assertThat(api.callsTo("HBS_CREATE_AUDIT")).hasSize(9)
        .allSatisfy(c -> assertThat(c.arg("i_action_type_code")).isEqualTo("INS"));
    assertThat(api.callsTo("HBS_CREATE_WEIGH_SLIP")).hasSize(3);
    assertThat(api.callsTo("HBS_CREATE_LOG_TALLY")).hasSize(2);
    assertThat(api.callsTo("HBS_STORE_LOG_TALLY")).hasSize(2);
    assertThat(api.callsTo("HBS_CREATE_LOG_TALLY_DETAIL")).hasSize(2);
    assertThat(api.callsTo("HBS_CREATE_LOG_TALLY_DTL_DEF")).hasSize(1);
    assertThat(api.callsTo("HBS_CREATE_SAMPLE_LOG_TALLY")).hasSize(1);
    assertThat(api.callsTo("HBS_STORE_SAMPLE_LOG_TALLY")).hasSize(1);
    assertThat(api.callsTo("HBS_CREATE_SAMPLE_TALLY_DETAIL")).hasSize(1);
    assertThat(api.callsTo("HBS_CREATE_SFP_TALLY")).hasSize(1);
    assertThat(api.callsTo("HBS_CREATE_SFP_LOG_DETAIL")).hasSize(1);
    assertThat(api.callsTo("HBS_CREATE_SCL_SITE_ARR_LEDGER")).hasSize(1);
    assertThat(api.callsTo("HBS_CREATE_DEPARTURE_LEDGER")).hasSize(1);
    assertThat(api.calls).noneMatch(c -> c.name().contains("FMT_ERR"));
    // every batch document ends accepted with its document number
    assertThat(api.callsTo("HBS_STORE_DTL_DOC_BATCH_DOC")).hasSize(9).allSatisfy(c -> {
      assertThat(c.arg("i_accepted_ind")).isEqualTo("Y");
      assertThat(c.arg("i_detail_document_number")).isNotNull();
    });
  }

  @Test
  void documentOrderIsBatchDocumentThenDocumentAuditVersionThenBatchDocumentUpdate() {
    unpack(fixture());
    List<String> names = api.names();
    int batdoc = names.indexOf("HBS_CREATE_DTL_DOC_BATCH_DOC");
    assertThat(names.subList(batdoc, batdoc + 5)).containsExactly("HBS_CREATE_DTL_DOC_BATCH_DOC",
        "HBS_CREATE_DTL_SCL_DOCUMENT", "HBS_CREATE_AUDIT", "HBS_CREATE_WEIGH_SLIP", "HBS_STORE_DTL_DOC_BATCH_DOC");
    int tally = names.indexOf("HBS_CREATE_LOG_TALLY");
    assertThat(names.subList(tally, tally + 5)).containsExactly("HBS_CREATE_LOG_TALLY",
        "HBS_CREATE_LOG_TALLY_DETAIL", "HBS_CREATE_LOG_TALLY_DETAIL", "HBS_CREATE_LOG_TALLY_DTL_DEF",
        "HBS_STORE_LOG_TALLY");
  }

  @Test
  void weighSlipVersionCarriesTheLegacyValues() {
    unpack(fixture());
    Call ws = api.callsTo("HBS_CREATE_WEIGH_SLIP").get(0);
    assertThat(ws.arg("i_detail_document_number")).isEqualTo("WS00000000001");
    assertThat(ws.arg("i_version")).isEqualTo(BigDecimal.ONE);
    assertThat(ws.arg("i_scl_rtn_version_state_code")).isEqualTo("ACC");
    assertThat(ws.arg("i_active_version_ind")).isEqualTo("N");
    assertThat(ws.arg("i_valid_signature_ind")).isEqualTo("N"); // B1021 verifies it
    assertThat(ws.arg("i_original_version_ind")).isEqualTo("Y");
    assertThat(ws.arg("i_scale_date")).isEqualTo(Timestamp.valueOf("2026-10-01 08:15:30"));
    assertThat(ws.arg("i_weigh_slip_number")).isEqualTo("1001");
    assertThat(ws.arg("i_gross_weight")).isEqualTo(new BigDecimal(42000));
    assertThat(ws.arg("i_input_cutblock_id")).isEqualTo("CB1");
    assertThat(ws.arg("i_field_scale_ind")).isEqualTo("N");
    assertThat(ws.arg("i_hbs_sample_type_code")).isEqualTo("N");
    assertThat(ws.arg("i_hbs_crypt_method_id")).isEqualTo(new BigDecimal(2));
    assertThat((String) ws.arg("i_hash_total")).hasSize(64);
    assertThat(ws.arg("i_input_signing_scaler_lic")).isEqualTo("AB12");
    assertThat(ws.arg("i_encrypted_signature")).isEqualTo(new byte[] {0x0A, 0x0B, 0x0C});
    assertThat(ws.arg("i_input_hbs_encr_key_id")).isEqualTo(BigDecimal.ONE);
    assertThat(ws.arg("i_hbs_rsa_padding_id")).isEqualTo(BigDecimal.ONE);
    assertThat(ws.arg("i_signing_date_time")).isEqualTo(Timestamp.valueOf("2026-10-01 08:20:00"));
    assertThat(ws.arg("i_hbs_encr_key_id")).isNull();
    assertThat(ws.arg("i_entry_userid")).isEqualTo("HBSBATCH");
    assertThat(ws.arg("i_batdoc_id")).isNotNull();
  }

  @Test
  void tallyRowsFollowTheLegacyEntityBeans() {
    unpack(fixture());
    Call lt = api.callsTo("HBS_CREATE_LOG_TALLY").get(0);
    assertThat(lt.arg("i_update_userid")).isNull(); // BT 4654
    assertThat(lt.arg("i_parcel_count")).isEqualTo(new BigDecimal(2));
    assertThat(lt.arg("i_log_count")).isEqualTo(new BigDecimal(2));
    assertThat(lt.arg("i_volume_calc_method_type")).isEqualTo("N");
    assertThat(lt.arg("i_return_number")).isEqualTo(new BigDecimal(17));
    Call store = api.callsTo("HBS_STORE_LOG_TALLY").get(0);
    assertThat(store.arg("i_net_volume")).isEqualTo(BigDecimal.ZERO);
    assertThat(store.arg("i_hash_total")).isEqualTo(lt.arg("i_hash_total"));
    Call log2 = api.callsTo("HBS_CREATE_LOG_TALLY_DETAIL").get(1);
    assertThat(log2.arg("i_nmv_ind")).isEqualTo("N");
    assertThat(log2.arg("i_deduction_count")).isEqualTo(BigDecimal.ONE);
    assertThat(log2.arg("i_net_volume")).isEqualTo(BigDecimal.valueOf(0.667f));
    Call defect = api.callsTo("HBS_CREATE_LOG_TALLY_DTL_DEF").get(0);
    assertThat(defect.arg("i_logdtl_id")).isEqualTo(log2.arg("i_logdtl_id"));
    assertThat(defect.arg("i_descriptor")).isEqualTo("BUTT ROT");

    Call pot = api.callsTo("HBS_CREATE_LOG_TALLY").get(1);
    assertThat(pot.arg("i_log_count")).isEqualTo(BigDecimal.ZERO);
    assertThat(pot.arg("i_scale_event_type_code")).isEqualTo("4R");

    Call sample = api.callsTo("HBS_CREATE_SAMPLE_LOG_TALLY").get(0);
    assertThat(sample.arg("i_sampled_weigh_slip")).isEqualTo("1001"); // leading zeros trimmed
    assertThat(sample.arg("i_sample_weight")).isEqualTo(new BigDecimal(5000));

    Call sfp = api.callsTo("HBS_CREATE_SFP_TALLY").get(0);
    assertThat(sfp.arg("i_hbs_sfp_scale_type_code")).isEqualTo("K");
    assertThat(sfp.arg("i_volume_calc_method_type")).isEqualTo("N");
    assertThat(sfp.arg("i_scale_site_id_nmbr")).isNull();
    assertThat(sfp.arg("i_orig_chk_scl_return_number")).isEqualTo("0");
    Call stack = api.callsTo("HBS_CREATE_SFP_LOG_DETAIL").get(0);
    assertThat(stack.arg("i_stack_length")).isEqualTo(new BigDecimal(100));
    assertThat(stack.arg("i_conversion_factor")).isEqualTo(BigDecimal.valueOf(0.8));
    assertThat(stack.arg("i_input_grade")).isEqualTo(" ");
  }

  @Test
  void ledgerEntriesGetGeneratedDocumentNumbers() {
    unpack(fixture());
    Call arl = api.callsTo("HBS_CREATE_SCL_SITE_ARR_LEDGER").get(0);
    assertThat((String) arl.arg("i_detail_document_number")).matches("ARL\\d{10}");
    assertThat(arl.arg("i_valid_signature_ind")).isEqualTo("Y");
    assertThat(arl.arg("i_input_source")).isEqualTo("TM123");
    assertThat(arl.arg("i_arrival_date")).isEqualTo(Timestamp.valueOf("2026-10-03 00:00:00"));
    Call dpl = api.callsTo("HBS_CREATE_DEPARTURE_LEDGER").get(0);
    assertThat((String) dpl.arg("i_detail_document_number")).matches("DEP\\d{10}");
    assertThat(dpl.arg("i_load_departure_number")).isEqualTo("D1");
    assertThat(api.callsTo("HBS_CREATE_DTL_SCL_DOCUMENT"))
        .anySatisfy(c -> assertThat(c.arg("i_detail_scale_doc_type_code")).isEqualTo("A"));
  }

  @Test
  void testFileGetsBatchesButNoScaleDocumentsExceptLedgers() {
    unpack(fixture().replace("<Type>P</Type>", "<Type>T</Type>"));
    assertThat(transmissionStores().get(1).arg("i_test_file_ind")).isEqualTo("Y");
    assertThat(api.callsTo("HBS_CREATE_WEIGH_SLIP")).isEmpty();
    assertThat(api.callsTo("HBS_CREATE_LOG_TALLY")).isEmpty();
    assertThat(api.callsTo("HBS_STORE_DTL_DOC_BATCH_DOC")).filteredOn(c -> "N".equals(c.arg("i_accepted_ind")))
        .hasSize(7);
    // B1012.processEntries ignores the production flag
    assertThat(api.callsTo("HBS_CREATE_SCL_SITE_ARR_LEDGER")).hasSize(1);
    assertThat(transmissionStores().get(2).arg("i_hbs_processing_status_code")).isEqualTo("CSA");
  }

  @Test
  void unreadableFileFailsTheTransmissionWithFxa00() {
    TransmissionUnpacker.Outcome outcome = unpack("<HBS><transmission>");
    assertThat(outcome.status()).isEqualTo("CUS");
    assertThat(errors("HBS_CREATE_TX_FMT_ERR")).containsExactly("FXA00");
    assertThat(api.callsTo("HBS_CREATE_TX_FMT_ERR").get(0).arg("i_transmission_id")).isEqualTo(new BigDecimal(TX));
    assertThat(transmissionStores()).extracting(c -> c.arg("i_hbs_processing_status_code"))
        .containsExactly("PRC", "CUS");
    assertThat(api.callsTo("HBS_CREATE_DTL_DOC_BATCH")).isEmpty();
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource({
      "batch count,<BatchCount>9</BatchCount>,<BatchCount>8</BatchCount>,FXA01",
      "creator length,<Creator>0012345600</Creator>,<Creator>00123456</Creator>,FXA03"})
  void transmissionFormatEdits(String name, String from, String to, String edit) {
    TransmissionUnpacker.Outcome outcome = unpack(fixture().replace(from, to));
    assertThat(outcome.status()).isEqualTo("CUS");
    assertThat(errors("HBS_CREATE_TX_FMT_ERR")).containsExactly(edit);
    assertThat(transmissionStores()).hasSize(2); // no counts were stored
  }

  @Test
  void unknownClientLocationIsFxa03AndMissingSubmitterIsFxa02() {
    repo.clientLocationExists = false;
    assertThat(unpack(fixture()).formatError()).isEqualTo("FXA03");
    setUp();
    repo.sitesWithoutSubmitter.add("S123/S");
    assertThat(unpack(fixture()).formatError()).isEqualTo("FXA02");
  }

  @Test
  void softwareVendorCannotSendProductionFilesOnTheWeb() {
    TransmissionUnpacker.Outcome outcome = unpack(fixture(), new TransmissionUnpacker.Submitter(true, true));
    assertThat(outcome.formatError()).isEqualTo("FXA04");
    assertThat(transmissionStores()).extracting(c -> c.arg("i_hbs_processing_status_code"))
        .containsExactly("PRC", "PRC", "CUS"); // counts were stored before FXA04
    setUp();
    TransmissionUnpacker.Outcome test = unpack(fixture().replace("<Type>P</Type>", "<Type>T</Type>"),
        new TransmissionUnpacker.Submitter(true, true));
    assertThat(test.status()).isEqualTo("CSA");
  }

  @Test
  void batchEditsRejectTheBatchAndCarryOn() {
    repo.missingScaleSites.add("S124");
    String xml = fixture().replaceFirst("<BatchHeader><ScaleSite>S123</ScaleSite>",
        "<BatchHeader><ScaleSite>S124</ScaleSite>")
        .replace("<BatchControlTotal>30000</BatchControlTotal>", "<BatchControlTotal>30001</BatchControlTotal>");
    TransmissionUnpacker.Outcome outcome = unpack(xml);
    assertThat(outcome.status()).isEqualTo("CSA");
    assertThat(errors("HBS_CREATE_BATCH_FMT_ERR")).containsExactly("FBA01", "FBB05");
    assertThat(api.callsTo("HBS_STORE_DTL_DOC_BATCH")).hasSize(7);
    assertThat(api.callsTo("HBS_CREATE_WEIGH_SLIP")).hasSize(1); // only the WSO batch went through
  }

  @Test
  void documentEditsRejectTheDocumentWithItsInputs() {
    String xml = ScaleXmlFixtures.sign(ScaleXmlFixtures.raw("all-types-v3.xml"))
        .replace("<NetVolume>767</NetVolume><DefectCount>0</DefectCount></Log>\n        <LogCount>1</LogCount>",
            "<NetVolume>700</NetVolume><DefectCount>0</DefectCount></Log>\n        <LogCount>1</LogCount>");
    // the hash was computed before the change, so the sample tally also fails its hash — the
    // volume edit comes after the hash for samples (legacy order), so FTAH33 is reported
    TransmissionUnpacker.Outcome outcome = unpack(xml);
    assertThat(outcome.status()).isEqualTo("CSA");
    assertThat(errors("HBS_CREATE_BATCH_DOC_FMT_ERR_2")).containsExactly("FTAH33");
    Call err = api.callsTo("HBS_CREATE_BATCH_DOC_FMT_ERR_2").get(0);
    assertThat(err.arg("i_input_scale_site")).isEqualTo("S123");
    assertThat(err.arg("i_input_date")).isEqualTo("20261002");
    assertThat(err.arg("i_input_return_number")).isEqualTo("18");
    assertThat(err.arg("i_input_scale_event_type")).isEqualTo("SS");
    assertThat(api.callsTo("HBS_CREATE_SAMPLE_LOG_TALLY")).isEmpty();
  }

  @Test
  void logVolumeEditOnARecomputedHash() {
    String xml = ScaleXmlFixtures.sign(ScaleXmlFixtures.raw("all-types-v3.xml")
        .replace("<NetVolume>667</NetVolume>", "<NetVolume>600</NetVolume>"));
    unpack(xml);
    assertThat(errors("HBS_CREATE_BATCH_DOC_FMT_ERR_2")).containsExactly("FTAL09");
  }

  @Test
  void fieldInclusionAndDuplicateVersionEdits() {
    repo.existingVersions.add("WS00000000002/1");
    String xml = ScaleXmlFixtures.sign(ScaleXmlFixtures.raw("all-types-v3.xml")
        .replace("<SampleType>N</SampleType>",
            "<SampleType>N</SampleType>").replace("<PSY>P1-2026</PSY>\n      <SampleType>",
            "<PSY>P1-2026</PSY>\n      <Destination>S999</Destination>\n      <SampleType>"));
    unpack(xml);
    assertThat(errors("HBS_CREATE_BATCH_DOC_FMT_ERR_2")).containsExactly("FWAH18", "FWAH24");
  }

  @Test
  void ledgerEditsUseTheEntryInputs() {
    String xml = fixture().replace("<Source>tm123</Source>", "<Source>tm123</Source><CutblockID>CB9</CutblockID>");
    unpack(xml);
    assertThat(errors("HBS_CREATE_BATCH_DOC_FMT_ERR_2")).containsExactly("FLA08");
    Call err = api.callsTo("HBS_CREATE_BATCH_DOC_FMT_ERR_2").get(0);
    assertThat(err.arg("i_input_date")).isEqualTo("20261003");
    assertThat(err.arg("i_input_source")).isEqualTo("tm123");
    assertThat(api.callsTo("HBS_CREATE_SCL_SITE_ARR_LEDGER")).isEmpty();
  }

  @Test
  void scaleDatesOlderThanSevenYearsAreOldsd1() {
    String xml = ScaleXmlFixtures.sign(ScaleXmlFixtures.raw("all-types-v3.xml")
        .replace("<ScaleDate>2026-10-04</ScaleDate>", "<ScaleDate>2015-10-04</ScaleDate>"));
    unpack(xml);
    assertThat(errors("HBS_CREATE_BATCH_DOC_FMT_ERR_2")).containsExactly("OLDSD1");
    assertThat(api.callsTo("HBS_CREATE_SFP_TALLY")).isEmpty();
  }

  @Test
  void newVersionOfAnExistingDocumentTakesOverTheOriginalFlag() {
    Map<String, Object> doc = new LinkedCaseInsensitiveMap<>();
    doc.put("DETAIL_DOCUMENT_NUMBER", "WS00000000003");
    doc.put("SCALE_RETURN_STATE_CODE", "OCM");
    doc.put("DETAIL_SCALE_DOC_TYPE_CODE", "W");
    doc.put("ENTRY_USERID", "X");
    doc.put("ENTRY_TIMESTAMP", Timestamp.valueOf("2026-09-01 00:00:00"));
    doc.put("UPDATE_USERID", "X");
    doc.put("UPDATE_TIMESTAMP", Timestamp.valueOf("2026-09-01 00:00:00"));
    repo.documents.put("WS00000000003", doc);
    Map<String, Object> v2 = new LinkedCaseInsensitiveMap<>();
    v2.put("VERSION", BigDecimal.valueOf(2));
    v2.put("ORIGINAL_VERSION_IND", "Y");
    repo.versions.put("WS00000000003", List.of(v2));
    Map<String, Object> row = new LinkedCaseInsensitiveMap<>();
    for (var p : api.signatures().get("HBS_STORE_WEIGH_SLIP").params()) {
      row.put(p.name().substring(2).toUpperCase(), null);
    }
    row.put("DETAIL_DOCUMENT_NUMBER", "WS00000000003");
    row.put("VERSION", BigDecimal.valueOf(2));
    row.put("ORIGINAL_VERSION_IND", "Y");
    row.put("WEIGH_SLIP_NUMBER", "001003");
    repo.versionRows.put("WS00000000003/2", row);

    unpack(fixture());

    assertThat(api.callsTo("HBS_STORE_DTL_SCL_DOCUMENT")).singleElement().satisfies(c -> {
      assertThat(c.arg("i_scale_return_state_code")).isEqualTo("OCM");
      assertThat(c.arg("i_update_userid")).isEqualTo("HBSBATCH");
    });
    Call old = api.callsTo("HBS_STORE_WEIGH_SLIP").get(0);
    assertThat(old.arg("i_version")).isEqualTo(new BigDecimal(2));
    assertThat(old.arg("i_original_version_ind")).isEqualTo("N");
    assertThat(old.arg("i_weigh_slip_number")).isEqualTo("1003");
    assertThat(api.callsTo("HBS_CREATE_SCL_RTN_AUDIT")).singleElement()
        .satisfies(c -> assertThat(c.arg("i_new_value")).isEqualTo("1003"));
    Call created = api.callsTo("HBS_CREATE_WEIGH_SLIP").stream()
        .filter(c -> "WS00000000003".equals(c.arg("i_detail_document_number"))).findFirst().orElseThrow();
    assertThat(created.arg("i_original_version_ind")).isEqualTo("Y");
    assertThat(api.callsTo("HBS_CREATE_AUDIT")).extracting(c -> c.arg("i_action_type_code")).contains("UPD");
  }

  @Test
  void existingDocumentOfAnotherTypeIsFwah01() {
    Map<String, Object> doc = new LinkedCaseInsensitiveMap<>();
    doc.put("DETAIL_SCALE_DOC_TYPE_CODE", "P");
    repo.documents.put("WS00000000001", doc);
    unpack(fixture());
    assertThat(errors("HBS_CREATE_BATCH_DOC_FMT_ERR_2")).containsExactly("FWAH01");
  }

  @Test
  void matchingPaperBatchSlipBecomesMatched() {
    Map<String, Object> paper = new LinkedCaseInsensitiveMap<>();
    for (var p : api.signatures().get("HBS_STORE_HBS_PAPER_BATCH").params()) {
      paper.put(p.name().substring(2).toUpperCase(), null);
    }
    paper.put("BATCH_ID", BigDecimal.valueOf(77));
    paper.put("PAPER_BATCH_STATUS_CODE", "PND");
    repo.paperBatch = paper;
    unpack(fixture());
    assertThat(api.callsTo("HBS_STORE_HBS_PAPER_BATCH")).hasSize(9).first().satisfies(c -> {
      assertThat(c.arg("i_paper_batch_status_code")).isEqualTo("MAT");
      assertThat(c.arg("i_dtl_doc_batch_id")).isNotNull();
    });
  }

  @Test
  void onlyReceivedTransmissionsAreUnpacked() {
    repo.transmissions.get(TX).put("HBS_XML_TRANS_STEP_CODE", "UNP");
    assertThatThrownBy(() -> unpack(fixture())).isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Illegal transition");
    assertThat(api.calls).isEmpty();
  }
}
