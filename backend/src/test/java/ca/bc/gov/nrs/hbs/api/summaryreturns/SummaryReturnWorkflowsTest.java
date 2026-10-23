package ca.bc.gov.nrs.hbs.api.summaryreturns;

import static ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturnType.PIECE;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturnType.SAMPLE;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturnType.WEIGHT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.bc.gov.nrs.hbs.api.workflow.RecordingTableApi.Call;
import ca.bc.gov.nrs.hbs.api.workflow.WorkflowValidationException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The state workflows: proc sequences and the states they leave for the legacy batch. */
class SummaryReturnWorkflowsTest {

  private final SummaryFixtures f = new SummaryFixtures();
  private static final String USER = "IDIR\\TESTER";

  private SummaryReturn piece(String doc, String ver) {
    f.header(100, PIECE, doc, false);
    f.version(PIECE, 100, 1, ver, true);
    f.pieceSegregation(100, 1, 501, "FI", 10, "12.500");
    f.notation(PIECE, 100, 1, 601, 1, "note");
    return f.repo.loadActive(100);
  }

  private List<Call> calls(String proc) {
    return f.api.callsTo(proc);
  }

  private Call last(String proc) {
    List<Call> c = calls(proc);
    return c.get(c.size() - 1);
  }

  // ── Release ──────────────────────────────────────────────────────────────

  @Test
  void releaseWithoutErrorsGoesReadyAndIsAudited() {
    var outcome = f.workflows.release(piece("OIP", "INC"), USER);
    assertThat(outcome.active().versionState()).isEqualTo("RDY");
    Call store = last("HBS_STORE_PIECE_SCALE_SUMMARY");
    assertThat(store.arg("i_scl_rtn_version_state_code")).isEqualTo("RDY");
    assertThat(store.arg("i_update_userid")).isEqualTo(USER);
    assertThat(last("HBS_CREATE_AUDIT").arg("i_action_type_code")).isEqualTo("REL");
    assertThat(last("HBS_CREATE_AUDIT").arg("i_object_new_id")).isEqualTo("100,1");
    assertThat(calls("HBS_CREATE_SCL_RTN_AUDIT")).anySatisfy(c -> {
      assertThat(c.arg("i_field_name")).isEqualTo("Version_Status");
      assertThat(c.arg("i_old_value")).isEqualTo("INC");
      assertThat(c.arg("i_new_value")).isEqualTo("RDY");
    });
    assertThat(calls("HBS_CREATE_PIECE_SCL_VER_ERR")).isEmpty();
  }

  @Test
  void releaseWithEditErrorsGoesToErrorAndSavesTheErrors() {
    f.repo.versionErrors = List.of(7L);
    f.repo.segErrors = List.of(8L);
    f.edits.errors = List.of(EditError.version("REL", "SD17"), EditError.segregation(501, "VAL", "NP01"));
    var outcome = f.workflows.release(piece("OIP", "INC"), USER);
    assertThat(outcome.active().versionState()).isEqualTo("ERR");
    assertThat(last("HBS_REMOVE_PIECE_SCL_VER_ERR").arg("i_error_id")).isEqualTo(BigDecimal.valueOf(7));
    assertThat(last("HBS_REMOVE_PIECE_SCL_SEG_ERROR").arg("i_error_id")).isEqualTo(BigDecimal.valueOf(8));
    Call ver = last("HBS_CREATE_PIECE_SCL_VER_ERR");
    assertThat(ver.arg("i_hbs_edit_err_message_code")).isEqualTo("SD17");
    assertThat(ver.arg("i_document_control_number")).isEqualTo(BigDecimal.valueOf(100));
    Call seg = last("HBS_CREATE_PIECE_SCL_SEG_ERROR");
    assertThat(seg.arg("i_segregation_id")).isEqualTo(BigDecimal.valueOf(501));
    assertThat(seg.arg("i_hbs_edit_err_category_code")).isEqualTo("VAL");
    // errors are removed before they are re-created
    assertThat(f.api.names().indexOf("HBS_REMOVE_PIECE_SCL_VER_ERR"))
        .isLessThan(f.api.names().indexOf("HBS_CREATE_PIECE_SCL_VER_ERR"));
  }

  @Test
  void releaseOfAreaBasedReturnWithZeroAreaCutIsNotToBeInvoiced() {
    SummaryReturn r = piece("OIP", "INC");
    r.version().set("pssmry_type", "AREAEST").set("area_cut", BigDecimal.ZERO);
    var outcome = f.workflows.release(r, USER);
    assertThat(outcome.active().versionState()).isEqualTo("NTI");
  }

  @Test
  void releaseOfAreaBasedReturnGeneratesItsSegregations() {
    SummaryReturn r = piece("OIP", "INC");
    r.version().set("pssmry_type", "AREAEST").set("area_cut", new BigDecimal("12.5"));
    f.edits.generatedSegregations = List.of(new Row().set("segregation_id", null).set("psseg_type", "PRIMARY")
        .set("scale_species_code", "FI").set("scale_product_code", " ").set("scale_grade_code", "1")
        .set("number_of_pieces", 1).set("volume", new BigDecimal("40.0")).set("nmv_ind", "N")
        .set("other_description_type_code", null).set("timber_mark", null).set("sb4_ind", null)
        .set("avoidable_ind", null).set("input_species", null).set("input_product", null).set("input_grade", null)
        .set("input_bcomb_timber_mark", null).set("archive_timber_mark", null).set("input_other_desc", null));
    f.workflows.release(r, USER);
    // the fake repository does not apply writes, so the second save sees the old row again
    assertThat(calls("HBS_REMOVE_PIECE_SCL_SEG")).allSatisfy(
        c -> assertThat(c.arg("i_segregation_id")).isEqualTo(BigDecimal.valueOf(501)));
    assertThat(calls("HBS_CREATE_PIECE_SCL_SEG")).isNotEmpty().allSatisfy(c -> {
      assertThat(c.arg("i_segregation_id")).isNotNull();
      assertThat(c.arg("i_volume")).isEqualTo(new BigDecimal("40.0"));
    });
    assertThat(last("HBS_STORE_PIECE_SCALE_SUMMARY").arg("i_log_count")).isEqualTo(BigDecimal.ONE);
  }

  @Test
  void releaseOfCancelReplaceWithChangesPendsAndReleasesThePreviousInvoicedVersion() {
    f.header(100, PIECE, "CWC", false);
    f.version(PIECE, 100, 1, "ISS", false);
    f.version(PIECE, 100, 2, "INC", true);
    var outcome = f.workflows.release(f.repo.loadActive(100), USER);
    List<Call> stores = calls("HBS_STORE_PIECE_SCALE_SUMMARY");
    assertThat(stores).anySatisfy(c -> {
      assertThat(c.arg("i_version")).isEqualTo(BigDecimal.valueOf(1));
      assertThat(c.arg("i_scl_rtn_version_state_code")).isEqualTo("RDY");
      assertThat(c.arg("i_active_version_flag")).isEqualTo("Y");
    });
    assertThat(stores).anySatisfy(c -> {
      assertThat(c.arg("i_version")).isEqualTo(BigDecimal.valueOf(2));
      assertThat(c.arg("i_scl_rtn_version_state_code")).isEqualTo("PND");
      assertThat(c.arg("i_active_version_flag")).isEqualTo("N");
    });
    assertThat(outcome.errors()).isEmpty();
  }

  @Test
  void releasedWeightFinalBillMarksTheCyclicBilling() {
    f.header(200, WEIGHT, "OIP", false);
    f.version(WEIGHT, 200, 1, "INC", true).set("scl_rtn_category_code", "FB");
    f.repo.cyclicBilling = new Row().set("population_number", "1001").set("stratum_number", "01")
        .set("sampling_year", 2026).set("timber_mark", "ABC123").set("ytd_weight", 5000).set("final_bill_ind", "N")
        .set("entry_userid", "X").set("entry_timestamp", SummaryFixtures.LOADED);
    f.workflows.release(f.repo.loadActive(200), USER);
    assertThat(last("HBS_STORE_YTD_CYCLIC_BILLING").arg("i_final_bill_ind")).isEqualTo("Y");
  }

  @Test
  void releasedWeightFinalBillWithoutBillingActivityReportsLegacyMessage() {
    f.header(200, WEIGHT, "OIP", false);
    f.version(WEIGHT, 200, 1, "INC", true).set("scl_rtn_category_code", "FB");
    var outcome = f.workflows.release(f.repo.loadActive(200), USER);
    assertThat(outcome.messages()).containsExactly(SummaryReturnMessages.PSYM_REQUIRED);
  }

  @Test
  void approvingARateCorrectionExtendsTheDetailsThenReleasesWithClientEdits() {
    f.header(300, PIECE, "CWR", true);
    f.version(PIECE, 300, 1, "ISS", false);
    f.version(PIECE, 300, 2, "AWP", true);
    var outcome = f.workflows.release(f.repo.loadActive(300), USER);
    assertThat(f.details.calls).containsExactly("extend 300/2");
    assertThat(outcome.active().versionState()).isEqualTo("RDY");
  }

  // ── Hold ─────────────────────────────────────────────────────────────────

  @Test
  void holdSupersedesTheReadyVersionAndCreatesAHeldCopy() {
    SummaryReturn held = f.workflows.hold(piece("OIP", "RDY"), USER);
    assertThat(held.versionNo()).isEqualTo(2);
    Call old = last("HBS_STORE_PIECE_SCALE_SUMMARY");
    assertThat(old.arg("i_scl_rtn_version_state_code")).isEqualTo("SPR");
    assertThat(old.arg("i_active_version_flag")).isEqualTo("N");
    Call created = last("HBS_CREATE_PIECE_SCALE_SUMMARY");
    assertThat(created.arg("i_version")).isEqualTo(BigDecimal.valueOf(2));
    assertThat(created.arg("i_scl_rtn_version_state_code")).isEqualTo("HLD");
    assertThat(created.arg("i_active_version_flag")).isEqualTo("Y");
    assertThat(created.arg("i_client_number_paid_by")).isNull(); // BT 6258: not copied
    assertThat(last("HBS_CREATE_PIECE_SCL_SEG").arg("i_segregation_id")).isNotEqualTo(BigDecimal.valueOf(501));
    assertThat(last("HBS_CREATE_PIECE_SCL_NOTATION").arg("i_ntn_id")).isNotEqualTo(BigDecimal.valueOf(601));
    assertThat(calls("HBS_CREATE_AUDIT")).extracting(c -> c.arg("i_action_type_code")).containsExactly("HLD", "INS");
  }

  @Test
  void holdIsRefusedOutsideReady() {
    assertThatThrownBy(() -> f.workflows.hold(piece("OIP", "INC"), USER))
        .isInstanceOf(WorkflowValidationException.class).hasMessageContaining("Hold is not available");
    assertThat(f.api.calls).isEmpty();
  }

  // ── Discard ──────────────────────────────────────────────────────────────

  @Test
  void discardOfOriginalHidesIt() {
    SummaryReturn r = f.workflows.discard(piece("OIP", "INC"), USER);
    assertThat(r.docState()).isEqualTo("DIS");
    assertThat(last("HBS_STORE_HBS_SCALE_RETURN").arg("i_scale_return_status_code")).isEqualTo("DIS");
    Call v = last("HBS_STORE_PIECE_SCALE_SUMMARY");
    assertThat(v.arg("i_scl_rtn_version_state_code")).isEqualTo("DEL");
    assertThat(v.arg("i_visibility_indicator")).isEqualTo("N");
  }

  @Test
  void discardOfReplaceWithChangesReactivatesThePreviousInvoicedVersion() {
    f.header(100, PIECE, "RCP", false);
    f.version(PIECE, 100, 1, "CAN", false);
    f.version(PIECE, 100, 2, "INC", true);
    SummaryReturn active = f.workflows.discard(f.repo.loadActive(100), USER);
    assertThat(active.versionNo()).isEqualTo(1);
    assertThat(last("HBS_STORE_HBS_SCALE_RETURN").arg("i_scale_return_status_code")).isEqualTo("CCM");
    assertThat(calls("HBS_STORE_PIECE_SCALE_SUMMARY")).anySatisfy(c -> {
      assertThat(c.arg("i_version")).isEqualTo(BigDecimal.valueOf(2));
      assertThat(c.arg("i_scl_rtn_version_state_code")).isEqualTo("DEL");
      assertThat(c.arg("i_active_version_flag")).isEqualTo("N");
    }).anySatisfy(c -> {
      assertThat(c.arg("i_version")).isEqualTo(BigDecimal.valueOf(1));
      assertThat(c.arg("i_active_version_flag")).isEqualTo("Y");
      assertThat(c.arg("i_visibility_indicator")).isEqualTo("Y");
    });
  }

  @Test
  void discardOfReadyCancelReplaceWithChangesDiscardsThePendingCorrection() {
    f.header(100, PIECE, "CWC", false);
    f.version(PIECE, 100, 1, "RDY", true);
    f.version(PIECE, 100, 2, "PND", false);
    SummaryReturn active = f.workflows.discard(f.repo.loadActive(100), USER);
    assertThat(active.versionNo()).isEqualTo(1);
    assertThat(active.versionState()).isEqualTo("ISS");
    assertThat(last("HBS_STORE_HBS_SCALE_RETURN").arg("i_scale_return_status_code")).isEqualTo("OCM");
    assertThat(calls("HBS_STORE_PIECE_SCALE_SUMMARY")).anySatisfy(c -> {
      assertThat(c.arg("i_version")).isEqualTo(BigDecimal.valueOf(2));
      assertThat(c.arg("i_scl_rtn_version_state_code")).isEqualTo("DEL");
    });
  }

  @Test
  void discardOfFailedGeneratedSummaryResetsItsDetails() {
    f.header(300, WEIGHT, "OIP", true);
    f.version(WEIGHT, 300, 1, "HLD", true);
    SummaryReturn r = f.workflows.discard(f.repo.loadActive(300), USER);
    assertThat(f.details.calls).containsExactly("reset 300");
    assertThat(r.docState()).isEqualTo("DIS");
    assertThat(last("HBS_STORE_WEIGHT_SCL_SUMMARY").arg("i_active_version_ind")).isEqualTo("Y");
  }

  // ── Cancel / replace ─────────────────────────────────────────────────────

  @Test
  void cancelPutsTheInvoicedVersionInCancelInProgressReady() {
    f.workflows.cancel(piece("OCM", "ISS"), USER);
    assertThat(last("HBS_STORE_HBS_SCALE_RETURN").arg("i_scale_return_status_code")).isEqualTo("CIP");
    assertThat(last("HBS_STORE_PIECE_SCALE_SUMMARY").arg("i_scl_rtn_version_state_code")).isEqualTo("RDY");
    assertThat(last("HBS_CREATE_AUDIT").arg("i_action_type_code")).isEqualTo("CAN");
  }

  @Test
  void cancelOfZeroAreaCutAreaBasedReturnIsNti() {
    SummaryReturn r = piece("OCM", "ISS");
    r.version().set("pssmry_type", "AREAEST").set("area_cut", BigDecimal.ZERO);
    f.workflows.cancel(r, USER);
    assertThat(last("HBS_STORE_PIECE_SCALE_SUMMARY").arg("i_scl_rtn_version_state_code")).isEqualTo("NTI");
  }

  @Test
  void replaceAndCancelReplaceWithoutChangesStayOnTheSameVersion() {
    f.workflows.replaceWithoutChanges(piece("CCM", "CAN"), USER);
    assertThat(last("HBS_STORE_HBS_SCALE_RETURN").arg("i_scale_return_status_code")).isEqualTo("RNP");
    f.api.calls.clear();
    f.workflows.cancelAndReplaceWithoutChanges(piece("OCM", "ISS"), USER);
    assertThat(last("HBS_STORE_HBS_SCALE_RETURN").arg("i_scale_return_status_code")).isEqualTo("CWR");
    assertThat(last("HBS_STORE_PIECE_SCALE_SUMMARY").arg("i_scl_rtn_version_state_code")).isEqualTo("RDY");
    assertThat(calls("HBS_CREATE_PIECE_SCALE_SUMMARY")).isEmpty();
  }

  @Test
  void replaceWithChangesCreatesAnIncompleteVersionAndDeactivatesTheOld() {
    SummaryReturn next = f.workflows.replaceWithChanges(piece("OCM", "ISS"), USER);
    assertThat(next.versionNo()).isEqualTo(2);
    assertThat(last("HBS_STORE_PIECE_SCALE_SUMMARY").arg("i_active_version_flag")).isEqualTo("N");
    assertThat(last("HBS_STORE_HBS_SCALE_RETURN").arg("i_scale_return_status_code")).isEqualTo("RCP");
    Call created = last("HBS_CREATE_PIECE_SCALE_SUMMARY");
    assertThat(created.arg("i_scl_rtn_version_state_code")).isEqualTo("INC");
    assertThat(calls("HBS_CREATE_AUDIT")).extracting(c -> c.arg("i_action_type_code")).containsExactly("RRD", "RRD");
  }

  @Test
  void replaceWithChangesIsRefusedForReplacedReturns() {
    assertThatThrownBy(() -> f.workflows.replaceWithChanges(piece("RNC", "ISS"), USER))
        .isInstanceOf(WorkflowValidationException.class);
  }

  @Test
  void cancelAndReplaceWithChangesCopiesSampleSegregations() {
    f.header(400, SAMPLE, "OCM", false);
    f.version(SAMPLE, 400, 1, "ISS", true);
    SummaryReturn next = f.workflows.cancelAndReplaceWithChanges(f.repo.loadActive(400), USER);
    assertThat(next.docState()).isEqualTo("CWC");
    assertThat(last("HBS_CREATE_SAMPLE_SCL_SUMMARY").arg("i_scl_rtn_version_state_code")).isEqualTo("INC");
  }

  // ── Rate corrections ─────────────────────────────────────────────────────

  @Test
  void requestRateCorrectionCreatesAnAwaitingApprovalVersionWithoutChildren() {
    f.header(300, PIECE, "OCM", true);
    f.version(PIECE, 300, 1, "ISS", true);
    f.pieceSegregation(300, 1, 900, "FI", 1, "1.0");
    SummaryReturn next = f.workflows.requestRateCorrection(f.repo.loadActive(300), USER);
    assertThat(next.state()).isEqualTo(new ScaleReturnState("CWR", "AWP", true));
    assertThat(calls("HBS_CREATE_PIECE_SCL_SEG")).isEmpty();
    assertThat(last("HBS_CREATE_PIECE_SCALE_SUMMARY").arg("i_log_count")).isEqualTo(BigDecimal.ZERO);
    assertThat(calls("HBS_CREATE_AUDIT")).extracting(c -> c.arg("i_action_type_code")).containsExactly("REQ", "RRD");
  }

  @Test
  void requestRateCorrectionNeedsCorrectableDetails() {
    f.header(300, PIECE, "OCM", true);
    f.version(PIECE, 300, 1, "ISS", true);
    f.details.eligible = false;
    assertThatThrownBy(() -> f.workflows.requestRateCorrection(f.repo.loadActive(300), USER))
        .hasMessage(SummaryReturnMessages.RATE_CORRECTION_CONFIRM_NO_DETAILS);
  }

  @Test
  void requestRateCorrectionOnlyForGeneratedReturns() {
    assertThatThrownBy(() -> f.workflows.requestRateCorrection(piece("OCM", "ISS"), USER))
        .hasMessageContaining("is not a generated summary");
  }

  @Test
  void rejectRateCorrectionRestoresThePreviousVersion() {
    f.header(300, PIECE, "CWR", true);
    f.version(PIECE, 300, 1, "ISS", false);
    f.version(PIECE, 300, 2, "AWP", true);
    SummaryReturn active = f.workflows.rejectRateCorrection(f.repo.loadActive(300), USER);
    assertThat(active.versionNo()).isEqualTo(1);
    assertThat(last("HBS_STORE_HBS_SCALE_RETURN").arg("i_scale_return_status_code")).isEqualTo("OCM");
    assertThat(calls("HBS_STORE_PIECE_SCALE_SUMMARY")).anySatisfy(c -> {
      assertThat(c.arg("i_version")).isEqualTo(BigDecimal.valueOf(2));
      assertThat(c.arg("i_scl_rtn_version_state_code")).isEqualTo("DEL");
      assertThat(c.arg("i_visibility_indicator")).isEqualTo("N");
    });
  }
}
