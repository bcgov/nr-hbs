package ca.bc.gov.nrs.hbs.api.summaryreturns.generated;

import static ca.bc.gov.nrs.hbs.api.summaryreturns.generated.DetailFixtures.USER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.bc.gov.nrs.hbs.api.summaryreturns.Row;
import ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturn;
import ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturnRules;
import ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturnType;
import ca.bc.gov.nrs.hbs.api.workflow.RecordingTableApi.Call;
import ca.bc.gov.nrs.hbs.api.workflow.WorkflowValidationException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The detail extension of an approved rate correction: cancelling txns, event sequences, states, summary rows. */
class RateCorrectionExtensionTest {

  private final DetailFixtures f = new DetailFixtures();

  /** Version 1 invoiced, version 2 the CWR/AWP correction being approved (children cleared, log count 0). */
  private SummaryReturn awaitingApproval(SummaryReturnType type, String pssmryType) {
    f.s.header(400, type, "CWR", true);
    f.s.version(type, 400, 1, "ISS", false);
    Row v2 = f.s.version(type, 400, 2, "AWP", true);
    if (type == SummaryReturnType.PIECE) {
      v2.set("pssmry_type", pssmryType).set("log_count", 0);
    }
    return f.s.repo.load(400, 2);
  }

  private void correctable(DetailFamily fam, String... ddns) {
    f.repo.correctable.put(fam, java.util.Arrays.stream(ddns)
        .map(d -> new DetailDocumentRepository.Key(d, 1)).toList());
  }

  private List<Call> calls(String proc) {
    return f.s.api.callsTo(proc);
  }

  private static BigDecimal n(String v) {
    return new BigDecimal(v);
  }

  @Test
  void weighSlipsAreLockedSummarizedPerDayAndCancelled() {
    SummaryReturn ret = awaitingApproval(SummaryReturnType.WEIGHT, null);
    for (String ddn : List.of("WS1", "WS2")) {
      f.document(ddn, DetailFamily.WEIGH_SLIP, "OCM");
      f.version(DetailFamily.WEIGH_SLIP, ddn, 1, "ISS", true);
    }
    f.repo.versions.get(DetailFixtures.key(DetailFamily.WEIGH_SLIP, "WS1", 1))
        .set("gross_weight", 10000).set("tare_weight", 4000);
    f.repo.versions.get(DetailFixtures.key(DetailFamily.WEIGH_SLIP, "WS2", 1))
        .set("gross_weight", 9000).set("tare_weight", null); // net weight 0
    f.txn(DetailFamily.WEIGH_SLIP, 101, "WS1", 1, 400, 1, 1, "ORG", "ISS", "DR");
    f.txnSeg(DetailFamily.WEIGH_SLIP, 101, 1001, "FI", "12.345", "150.25", null);
    f.txn(DetailFamily.WEIGH_SLIP, 102, "WS2", 1, 400, 1, 1, "ORG", "ISS", "DR");
    f.txnSeg(DetailFamily.WEIGH_SLIP, 102, 1002, "HE", "3.000", "30.00", null);
    correctable(DetailFamily.WEIGH_SLIP, "WS1", "WS2");

    f.extension.extend(ret, USER);

    // the summary version and one daily load (both slips scaled on the 3rd), no audit
    assertThat(ret.dailyLoads()).singleElement().satisfies(l -> {
      assertThat(l.lng("day")).isEqualTo(3);
      assertThat(l.lng("number_of_loads")).isEqualTo(2);
      assertThat(l.lng("weight")).isEqualTo(6000);
    });
    Call load = calls("HBS_CREATE_WEI_SCL_DLY_LD_SUM").get(0);
    assertThat(load.arg("i_version")).isEqualTo(n("2"));
    assertThat(load.arg("i_weight")).isEqualTo(n("6000"));
    assertThat(calls("HBS_STORE_WEIGHT_SCL_SUMMARY").get(0).arg("i_scl_rtn_version_state_code")).isEqualTo("AWP");
    assertThat(calls("HBS_STORE_HBS_SCALE_RETURN")).hasSize(1);
    assertThat(calls("HBS_CREATE_AUDIT")).allSatisfy(a -> {
      assertThat(a.arg("i_hbs_object_type_code")).isEqualTo("DSR");
      assertThat(a.arg("i_action_type_code")).isEqualTo("LCK");
    });

    // detail documents: CWR, version LCK, audited ISS -> LCK
    assertThat(calls("HBS_STORE_WEIGH_SLIP")).hasSize(2).allSatisfy(c -> {
      assertThat(c.arg("i_scl_rtn_version_state_code")).isEqualTo("LCK");
      assertThat(c.arg("i_active_version_ind")).isEqualTo("Y");
    });
    assertThat(calls("HBS_STORE_DTL_SCL_DOCUMENT")).allSatisfy(c ->
        assertThat(c.arg("i_scale_return_state_code")).isEqualTo("CWR"));
    assertThat(calls("HBS_CREATE_SCL_RTN_AUDIT")).hasSize(2).allSatisfy(c -> {
      assertThat(c.arg("i_field_name")).isEqualTo("Version_Status");
      assertThat(c.arg("i_old_value")).isEqualTo("ISS");
      assertThat(c.arg("i_new_value")).isEqualTo("LCK");
    });

    // cancelling txns
    Call txn = calls("HBS_CREATE_WEIGH_SLIP_TXN").get(0);
    assertThat(txn.arg("i_hbs_txn_status_code")).isEqualTo("CRT");
    assertThat(txn.arg("i_hbs_txn_type_code")).isEqualTo("CAN");
    assertThat(txn.arg("i_debit_credit_type")).isEqualTo("CR");
    assertThat(txn.arg("i_interest_reversal_ind")).isEqualTo("N");
    assertThat(txn.arg("i_document_control_number")).isEqualTo(n("400"));
    assertThat(txn.arg("i_summary_document_version")).isEqualTo(n("2"));
    assertThat(txn.arg("i_detail_document_number")).isEqualTo("WS1");
    assertThat(txn.arg("i_detail_document_version")).isEqualTo(n("1"));
    assertThat(txn.arg("i_dtl_txn_id_cancelled_by")).isEqualTo(n("101"));
    assertThat(txn.arg("i_event_sequence")).isEqualTo(n("2"));
    assertThat(txn.arg("i_entry_userid")).isEqualTo(USER);
    Call seg = calls("HBS_CREATE_WEIGH_SLIP_TXN_SEG").get(0);
    assertThat(seg.arg("i_dtl_txn_id")).isEqualTo(txn.arg("i_dtl_txn_id"));
    assertThat(seg.arg("i_prorated_volume")).isEqualTo(n("-12.345"));
    assertThat(seg.arg("i_prorated_value")).isEqualTo(n("-150.25"));
    assertThat(seg.arg("i_weight_x_rate")).isEqualTo(n("-500.00"));
    assertThat(seg.arg("i_reserve_stmpg_amt_component")).isEqualTo(n("-100.00"));
    assertThat(seg.arg("i_bonus_bid_amount_component")).isEqualTo(n("-25.00"));
    assertThat(seg.arg("i_dev_levy_amount_component")).isNull();
    assertThat(seg.arg("i_rate")).isEqualTo(n("12.50"));
    assertThat(seg.arg("i_reserve_stmpg_rate_component")).isEqualTo(n("10.00"));
    assertThat(seg.arg("i_txn_seg_id")).isNull();

    // order: summary, then details, then txns
    List<String> names = f.s.api.names();
    assertThat(names.indexOf("HBS_STORE_WEIGHT_SCL_SUMMARY")).isLessThan(names.indexOf("HBS_STORE_WEIGH_SLIP"));
    assertThat(names.lastIndexOf("HBS_STORE_DTL_SCL_DOCUMENT")).isLessThan(names.indexOf("HBS_CREATE_WEIGH_SLIP_TXN"));
  }

  @Test
  void theEventSequenceFollowsTheDocumentsHighestAndTheLatestInvoicedDebitIsCancelled() {
    SummaryReturn ret = awaitingApproval(SummaryReturnType.WEIGHT, null);
    f.document("WS3", DetailFamily.WEIGH_SLIP, "RNC");
    f.version(DetailFamily.WEIGH_SLIP, "WS3", 1, "ISS", true);
    f.txn(DetailFamily.WEIGH_SLIP, 201, "WS3", 1, 400, 1, 1, "ORG", "ISS", "DR");
    f.txn(DetailFamily.WEIGH_SLIP, 202, "WS3", 1, 400, 1, 2, "CAN", "ISS", "CR");
    f.txn(DetailFamily.WEIGH_SLIP, 203, "WS3", 1, 400, 1, 3, "RWN", "ISS", "DR");
    f.txn(DetailFamily.WEIGH_SLIP, 204, "WS3", 0, 999, 1, 5, "ORG", "ISS", "DR"); // another version: counts for MAX
    f.txnSeg(DetailFamily.WEIGH_SLIP, 203, 2003, "FI", "1.000", "10.00", null);
    correctable(DetailFamily.WEIGH_SLIP, "WS3");

    f.extension.extend(ret, USER);

    Call txn = calls("HBS_CREATE_WEIGH_SLIP_TXN").get(0);
    assertThat(txn.arg("i_dtl_txn_id_cancelled_by")).isEqualTo(n("203"));
    assertThat(txn.arg("i_event_sequence")).isEqualTo(n("6"));
    assertThat(calls("HBS_STORE_DTL_SCL_DOCUMENT").get(0).arg("i_scale_return_state_code")).isEqualTo("CWR");
  }

  @Test
  void logTalliesBuildPrimarySegregationsAndPointTheCancellingSegregationsAtThem() {
    SummaryReturn ret = awaitingApproval(SummaryReturnType.PIECE, SummaryReturnRules.PSSMRY_DEFAULT_TYPE);
    f.document("LT1", DetailFamily.LOG_TALLY, "OCM");
    f.version(DetailFamily.LOG_TALLY, "LT1", 1, "ISS", true).set("beachcomb_ind", "N");
    f.document("LT2", DetailFamily.LOG_TALLY, "RCC");
    f.version(DetailFamily.LOG_TALLY, "LT2", 1, "ISS", true).set("beachcomb_ind", "N");
    f.log(DetailFamily.LOG_TALLY, "LT1", 1, 1, "FI", "1.250");
    f.log(DetailFamily.LOG_TALLY, "LT1", 1, 2, "FI", "0.750");
    f.log(DetailFamily.LOG_TALLY, "LT2", 1, 3, "CE", "2.000");
    f.txn(DetailFamily.LOG_TALLY, 301, "LT1", 1, 400, 1, 1, "ORG", "ISS", "DR");
    f.txnSeg(DetailFamily.LOG_TALLY, 301, 3001, "FI", "2.000", "40.00", 555L);
    f.txn(DetailFamily.LOG_TALLY, 302, "LT2", 1, 400, 1, 1, "ORG", "ISS", "DR");
    f.txnSeg(DetailFamily.LOG_TALLY, 302, 3002, "CE", "2.000", "60.00", 556L);
    correctable(DetailFamily.LOG_TALLY, "LT1", "LT2");

    f.extension.extend(ret, USER);

    assertThat(ret.version().lng("log_count")).isEqualTo(3);
    assertThat(ret.segregations()).hasSize(2);
    Row fi = ret.segregations().get(0);
    assertThat(fi.str("psseg_type")).isEqualTo("PRIMARY");
    assertThat(fi.lng("number_of_pieces")).isEqualTo(2);
    assertThat(fi.num("volume")).isEqualByComparingTo("2.000");
    assertThat(fi.str("nmv_ind")).isEqualTo("N");

    List<Call> created = calls("HBS_CREATE_PIECE_SCL_SEG");
    assertThat(created).hasSize(2);
    assertThat(created.get(0).arg("i_version")).isEqualTo(n("2"));
    assertThat(calls("HBS_STORE_PIECE_SCALE_SUMMARY").get(0).arg("i_log_count")).isEqualTo(n("3"));

    List<Call> segs = calls("HBS_CREATE_LOG_TALLY_TXN_SEG");
    assertThat(segs.get(0).arg("i_segregation_id")).isEqualTo(n(fi.lng("segregation_id").toString()));
    assertThat(segs.get(1).arg("i_segregation_id"))
        .isEqualTo(n(ret.segregations().get(1).lng("segregation_id").toString()));
    assertThat(segs.get(0).arg("i_prorated_volume")).isEqualTo(n("-2.000"));
    assertThat(segs.get(0).arg("i_dtlseg_id")).isNull();
    assertThat(segs.get(0).arg("i_reserve_stmpg_amt_component")).isEqualTo(n("-100.00"));
    assertThat(calls("HBS_CREATE_LOG_TALLY_TXN")).hasSize(2).allSatisfy(t -> {
      assertThat(t.arg("i_hbs_txn_type_code")).isEqualTo("CAN");
      assertThat(t.arg("i_event_sequence")).isEqualTo(n("2"));
    });
    assertThat(calls("HBS_STORE_LOG_TALLY")).allSatisfy(c ->
        assertThat(c.arg("i_scl_rtn_version_state_code")).isEqualTo("LCK"));
  }

  @Test
  void beachcombTalliesKeyByNmvAndMarkAndFindTheSegregationThroughTheSummarysPreviousSegregations() {
    SummaryReturn ret = awaitingApproval(SummaryReturnType.PIECE, SummaryReturnRules.PSSMRY_DEFAULT_TYPE);
    f.document("BC1", DetailFamily.LOG_TALLY, "OCM");
    f.version(DetailFamily.LOG_TALLY, "BC1", 1, "ISS", true).set("beachcomb_ind", "Y");
    f.log(DetailFamily.LOG_TALLY, "BC1", 1, 1, "FI", "1.000").set("nmv_ind", "Y").set("timber_mark", "MARK1");
    f.log(DetailFamily.LOG_TALLY, "BC1", 1, 2, "FI", "1.000").set("nmv_ind", "N").set("timber_mark", "MARK1");
    f.txn(DetailFamily.LOG_TALLY, 401, "BC1", 1, 400, 1, 1, "ORG", "ISS", "DR");
    f.txnSeg(DetailFamily.LOG_TALLY, 401, 4001, "FI", "1.000", "10.00", 900L);
    f.repo.previousSegs.add(new Row().set("segregation_id", 900L).set("scale_species_code", "FI")
        .set("scale_product_code", "LO").set("scale_grade_code", "U").set("nmv_ind", "Y").set("timber_mark", "MARK1"));
    correctable(DetailFamily.LOG_TALLY, "BC1");

    f.extension.extend(ret, USER);

    assertThat(ret.segregations()).hasSize(2).allSatisfy(s -> {
      assertThat(s.str("psseg_type")).isEqualTo("BCOMB");
      assertThat(s.str("timber_mark")).isEqualTo("MARK1");
    });
    Row nmv = ret.segregations().stream().filter(s -> "Y".equals(s.str("nmv_ind"))).findFirst().orElseThrow();
    assertThat(calls("HBS_CREATE_LOG_TALLY_TXN_SEG").get(0).arg("i_segregation_id"))
        .isEqualTo(n(nmv.lng("segregation_id").toString()));
  }

  @Test
  void sfpTalliesCountTheirPiecesAndCarryNoRates() {
    SummaryReturn ret = awaitingApproval(SummaryReturnType.PIECE, SummaryReturnRules.PSSMRY_SFP_TYPE);
    f.document("SF1", DetailFamily.SFP_TALLY, "OCM");
    f.version(DetailFamily.SFP_TALLY, "SF1", 1, "ISS", true);
    f.log(DetailFamily.SFP_TALLY, "SF1", 1, 1, "CE", "0.500").set("piece_count", 12);
    f.log(DetailFamily.SFP_TALLY, "SF1", 1, 2, "CE", "0.250").set("piece_count", 3);
    f.txn(DetailFamily.SFP_TALLY, 501, "SF1", 1, 400, 1, 1, "ORG", "ISS", "DR");
    f.txnSeg(DetailFamily.SFP_TALLY, 501, 5001, "CE", "0.750", "9.00", 600L);
    correctable(DetailFamily.SFP_TALLY, "SF1");

    f.extension.extend(ret, USER);

    assertThat(ret.version().lng("log_count")).isEqualTo(2);
    assertThat(ret.segregations()).singleElement().satisfies(s -> {
      assertThat(s.lng("number_of_pieces")).isEqualTo(15);
      assertThat(s.num("volume")).isEqualByComparingTo("0.750");
    });
    Call seg = calls("HBS_CREATE_SFP_TALLY_TXN_SEG").get(0);
    assertThat(seg.arg("i_prorated_volume")).isEqualTo(n("-0.750"));
    assertThat(seg.arg("i_prorated_value")).isEqualTo(n("-9.00"));
    assertThat(calls("HBS_STORE_SFP_TALLY").get(0).arg("i_scl_rtn_version_state_code")).isEqualTo("LCK");
    assertThat(calls("HBS_CREATE_SFP_TALLY_TXN").get(0).arg("i_interest_reversal_ind")).isEqualTo("N");
  }

  @Test
  void anSfpTxnWithoutSegregationsStopsTheExtension() {
    SummaryReturn ret = awaitingApproval(SummaryReturnType.PIECE, SummaryReturnRules.PSSMRY_SFP_TYPE);
    f.document("SF2", DetailFamily.SFP_TALLY, "OCM");
    f.version(DetailFamily.SFP_TALLY, "SF2", 1, "ISS", true);
    f.txn(DetailFamily.SFP_TALLY, 601, "SF2", 1, 400, 1, 1, "ORG", "ISS", "DR");
    correctable(DetailFamily.SFP_TALLY, "SF2");

    assertThatThrownBy(() -> f.extension.extend(ret, USER))
        .isInstanceOf(IllegalStateException.class).hasMessageStartingWith("Cancelled Transaction has no segregations");
    assertThat(f.s.api.calls).isEmpty();
  }

  @Test
  void aDetailThatIsNoLongerInvoicedIsAnIllegalTransition() {
    SummaryReturn ret = awaitingApproval(SummaryReturnType.WEIGHT, null);
    f.document("WS4", DetailFamily.WEIGH_SLIP, "OCM");
    f.version(DetailFamily.WEIGH_SLIP, "WS4", 1, "LCK", true);
    f.txn(DetailFamily.WEIGH_SLIP, 701, "WS4", 1, 400, 1, 1, "ORG", "ISS", "DR");
    correctable(DetailFamily.WEIGH_SLIP, "WS4");

    assertThatThrownBy(() -> f.extension.extend(ret, USER))
        .isInstanceOf(WorkflowValidationException.class)
        .hasMessageStartingWith("cancelAndReplaceWithNoChangesRequested(): Illegal transition from locked (LCK) active");
    assertThat(f.s.api.calls).isEmpty();
  }

  @Test
  void noCorrectableDetailsWritesNothing() {
    SummaryReturn ret = awaitingApproval(SummaryReturnType.WEIGHT, null);
    f.extension.extend(ret, USER);
    assertThat(f.s.api.calls).isEmpty();
  }

  @Test
  void theFamilyFollowsTheSummaryType() {
    assertThat(RateCorrectionExtension.family(awaitingApproval(SummaryReturnType.PIECE, "SMRYDOC")))
        .isEqualTo(DetailFamily.LOG_TALLY);
    DetailFixtures g = new DetailFixtures();
    g.s.header(1, SummaryReturnType.PIECE, "CWR", true);
    g.s.version(SummaryReturnType.PIECE, 1, 2, "AWP", true).set("pssmry_type", "SFPSMRY");
    assertThat(RateCorrectionExtension.family(g.s.repo.load(1, 2))).isEqualTo(DetailFamily.SFP_TALLY);
    g.s.header(2, SummaryReturnType.SAMPLE, "CWR", true);
    g.s.version(SummaryReturnType.SAMPLE, 2, 2, "AWP", true);
    assertThatThrownBy(() -> RateCorrectionExtension.family(g.s.repo.load(2, 2)))
        .isInstanceOf(WorkflowValidationException.class);
  }
}
