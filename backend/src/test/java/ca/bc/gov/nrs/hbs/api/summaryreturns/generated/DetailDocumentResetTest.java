package ca.bc.gov.nrs.hbs.api.summaryreturns.generated;

import static ca.bc.gov.nrs.hbs.api.summaryreturns.generated.DetailFixtures.USER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturn;
import ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturnType;
import ca.bc.gov.nrs.hbs.api.workflow.RecordingTableApi.Call;
import ca.bc.gov.nrs.hbs.api.workflow.WorkflowValidationException;
import java.util.List;
import org.junit.jupiter.api.Test;

/** {@code discardGeneratedFailed}'s detail side: locked check, failed txn, state codes written. */
class DetailDocumentResetTest {

  private final DetailFixtures f = new DetailFixtures();

  private SummaryReturn generated(SummaryReturnType type) {
    f.s.header(300, type, "OIP", true);
    f.s.version(type, 300, 1, "FAL", true);
    return f.s.repo.load(300, 1);
  }

  private List<Call> calls(String proc) {
    return f.s.api.callsTo(proc);
  }

  @Test
  void originalLogTallyIsFailedThenBackToReady() {
    SummaryReturn ret = generated(SummaryReturnType.PIECE);
    f.document("LT1", DetailFamily.LOG_TALLY, "OIP");
    f.version(DetailFamily.LOG_TALLY, "LT1", 1, "LCK", true);
    f.txn(DetailFamily.LOG_TALLY, 11, "LT1", 1, 250, 1, 1, "ORG", "ISS", "DR");
    f.txn(DetailFamily.LOG_TALLY, 12, "LT1", 1, 300, 1, 2, "ORG", "CRT", "DR");

    f.reset.reset(ret, USER);

    assertThat(f.s.api.names()).containsExactly("HBS_STORE_LOG_TALLY_TXN", "HBS_CREATE_AUDIT",
        "HBS_CREATE_SCL_RTN_AUDIT", "HBS_STORE_LOG_TALLY", "HBS_STORE_DTL_SCL_DOCUMENT");
    Call txn = calls("HBS_STORE_LOG_TALLY_TXN").get(0);
    assertThat(txn.arg("i_dtl_txn_id")).isEqualTo(new java.math.BigDecimal(12)); // the latest txn only
    assertThat(txn.arg("i_hbs_txn_status_code")).isEqualTo("FAL");
    assertThat(txn.arg("i_hbs_txn_type_code")).isEqualTo("ORG");

    Call audit = calls("HBS_CREATE_AUDIT").get(0);
    assertThat(audit.arg("i_hbs_object_type_code")).isEqualTo("DSR");
    assertThat(audit.arg("i_action_type_code")).isEqualTo("REQ");
    assertThat(audit.arg("i_object_new_id")).isEqualTo("LT1,1");
    Call detail = calls("HBS_CREATE_SCL_RTN_AUDIT").get(0);
    assertThat(detail.arg("i_field_name")).isEqualTo("Version_Status");
    assertThat(detail.arg("i_old_value")).isEqualTo("LCK");
    assertThat(detail.arg("i_new_value")).isEqualTo("RDY");
    assertThat(detail.arg("i_hbs_object_type_code")).isEqualTo("DSR");

    Call version = calls("HBS_STORE_LOG_TALLY").get(0);
    assertThat(version.arg("i_scl_rtn_version_state_code")).isEqualTo("RDY");
    assertThat(version.arg("i_active_version_ind")).isEqualTo("Y");
    assertThat(version.arg("i_update_userid")).isEqualTo(USER);
    assertThat(version.arg("i_timber_mark")).isEqualTo("ABC123");
    assertThat(calls("HBS_STORE_DTL_SCL_DOCUMENT").get(0).arg("i_scale_return_state_code")).isEqualTo("OIP");
  }

  @Test
  void cancelAndReplaceWeighSlipGoesBackToOriginalComplete() {
    SummaryReturn ret = generated(SummaryReturnType.WEIGHT);
    f.document("WS1", DetailFamily.WEIGH_SLIP, "CWR");
    f.version(DetailFamily.WEIGH_SLIP, "WS1", 1, "LCK", true);
    f.txn(DetailFamily.WEIGH_SLIP, 21, "WS1", 1, 300, 1, 2, "CAN", "CRT", "CR");

    f.reset.reset(ret, USER);

    assertThat(calls("HBS_STORE_WEIGH_SLIP_TXN").get(0).arg("i_hbs_txn_status_code")).isEqualTo("FAL");
    assertThat(calls("HBS_STORE_WEIGH_SLIP").get(0).arg("i_scl_rtn_version_state_code")).isEqualTo("ISS");
    assertThat(calls("HBS_STORE_DTL_SCL_DOCUMENT").get(0).arg("i_scale_return_state_code")).isEqualTo("OCM");
  }

  @Test
  void replaceWithoutChangesSampleTallyGoesBackToAwaitingApproval() {
    SummaryReturn ret = generated(SummaryReturnType.SAMPLE);
    f.document("ST1", DetailFamily.SAMPLE_LOG_TALLY, "RNP");
    f.version(DetailFamily.SAMPLE_LOG_TALLY, "ST1", 2, "LCK", true);
    f.txn(DetailFamily.SAMPLE_LOG_TALLY, 31, "ST1", 2, 300, 1, 3, "RWN", "CRT", "DR");

    f.reset.reset(ret, USER);

    assertThat(calls("HBS_STORE_SAMPLE_TALLY_TXN").get(0).arg("i_hbs_txn_status_code")).isEqualTo("FAL");
    Call v = calls("HBS_STORE_SAMPLE_LOG_TALLY").get(0);
    assertThat(v.arg("i_scl_rtn_version_state_code")).isEqualTo("AWP");
    assertThat(v.arg("i_version")).isEqualTo(new java.math.BigDecimal(2));
    assertThat(calls("HBS_STORE_DTL_SCL_DOCUMENT").get(0).arg("i_scale_return_state_code")).isEqualTo("RNP");
  }

  @Test
  void replaceWithChangesAlsoSavesTheLatestCancelledVersion() {
    SummaryReturn ret = generated(SummaryReturnType.PIECE);
    f.document("LT2", DetailFamily.LOG_TALLY, "RCP");
    f.version(DetailFamily.LOG_TALLY, "LT2", 1, "CAN", false);
    f.version(DetailFamily.LOG_TALLY, "LT2", 2, "LCK", true);
    f.txn(DetailFamily.LOG_TALLY, 41, "LT2", 2, 300, 1, 3, "RWC", "CRT", "DR");

    f.reset.reset(ret, USER);

    List<Call> versions = calls("HBS_STORE_LOG_TALLY");
    assertThat(versions).hasSize(2);
    assertThat(versions.get(0).arg("i_scl_rtn_version_state_code")).isEqualTo("AWP");
    assertThat(versions.get(1).arg("i_version")).isEqualTo(new java.math.BigDecimal(1));
    assertThat(versions.get(1).arg("i_scl_rtn_version_state_code")).isEqualTo("CAN");
    assertThat(versions.get(1).arg("i_active_version_ind")).isEqualTo("N");
    assertThat(calls("HBS_CREATE_AUDIT")).hasSize(2);
    assertThat(calls("HBS_CREATE_SCL_RTN_AUDIT")).hasSize(1); // the cancelled version did not change
  }

  @Test
  void cancelAndReplaceWithChangesFailsOnItsCorrectionPendingVersionAsLegacyDid() {
    // Legacy defect kept: the PND version is reset with the document state just saved (OCM).
    SummaryReturn ret = generated(SummaryReturnType.PIECE);
    f.document("LT3", DetailFamily.LOG_TALLY, "CWC");
    f.version(DetailFamily.LOG_TALLY, "LT3", 1, "LCK", true);
    f.version(DetailFamily.LOG_TALLY, "LT3", 2, "PND", false);
    f.txn(DetailFamily.LOG_TALLY, 51, "LT3", 1, 300, 1, 2, "CAN", "CRT", "CR");

    assertThatThrownBy(() -> f.reset.reset(ret, USER))
        .isInstanceOf(WorkflowValidationException.class)
        .hasMessageStartingWith("Document can not have summarization failures cleared.");
  }

  @Test
  void aDetailThatIsNotLockedRefusesTheDiscard() {
    SummaryReturn ret = generated(SummaryReturnType.WEIGHT);
    f.document("WS2", DetailFamily.WEIGH_SLIP, "OCM");
    f.version(DetailFamily.WEIGH_SLIP, "WS2", 1, "ISS", true);
    f.txn(DetailFamily.WEIGH_SLIP, 61, "WS2", 1, 300, 1, 1, "ORG", "ISS", "DR");

    assertThatThrownBy(() -> f.reset.reset(ret, USER))
        .isInstanceOf(WorkflowValidationException.class)
        .hasMessage("Detail scale document WS2 version status should be locked not issued (ISS) active");
    assertThat(f.s.api.calls).isEmpty();
  }

  @Test
  void noDetailsRefusesTheDiscard() {
    SummaryReturn ret = generated(SummaryReturnType.PIECE);
    assertThatThrownBy(() -> f.reset.reset(ret, USER))
        .isInstanceOf(WorkflowValidationException.class)
        .hasMessage("Detail documents missing for generated summary 300");
  }

  @Test
  void aLockedDocumentInAnInvoicedStateCannotBeCleared() {
    SummaryReturn ret = generated(SummaryReturnType.WEIGHT);
    f.document("WS3", DetailFamily.WEIGH_SLIP, "OCM");
    f.version(DetailFamily.WEIGH_SLIP, "WS3", 1, "LCK", true);
    f.txn(DetailFamily.WEIGH_SLIP, 71, "WS3", 1, 300, 1, 1, "ORG", "CRT", "DR");

    assertThatThrownBy(() -> f.reset.reset(ret, USER))
        .isInstanceOf(WorkflowValidationException.class)
        .hasMessageStartingWith("Document can not have summarization failures cleared.");
  }

  /**
   * WorkflowManagerBean.reset: each document is reset, then its replacement-pending version is
   * processed by the detail-return workflow (formerly refused here).
   */
  @Test
  void aReplacementPendingVersionIsProcessedByTheDetailWorkflowAfterTheReset() {
    SummaryReturn ret = generated(SummaryReturnType.PIECE);
    f.document("LT4", DetailFamily.LOG_TALLY, "OIP");
    f.version(DetailFamily.LOG_TALLY, "LT4", 1, "LCK", true);
    f.version(DetailFamily.LOG_TALLY, "LT4", 2, "PNV", false);
    f.txn(DetailFamily.LOG_TALLY, 81, "LT4", 1, 300, 1, 1, "ORG", "CRT", "DR");
    f.document("LT5", DetailFamily.LOG_TALLY, "OIP");
    f.version(DetailFamily.LOG_TALLY, "LT5", 1, "LCK", true);
    f.txn(DetailFamily.LOG_TALLY, 82, "LT5", 1, 300, 1, 1, "ORG", "CRT", "DR");

    f.reset.reset(ret, USER);

    assertThat(calls("HBS_STORE_LOG_TALLY")).extracting(c -> c.arg("i_scl_rtn_version_state_code"))
        .containsExactly("RDY", "RDY");
    assertThat(f.afterReset).containsExactly("LT4/1", "LT5/1");
  }

  @Test
  void aDetailWorkflowFailureAfterTheResetIsALegacyWorkflowMessage() {
    SummaryReturn ret = generated(SummaryReturnType.PIECE);
    f.document("LT6", DetailFamily.LOG_TALLY, "OIP");
    f.version(DetailFamily.LOG_TALLY, "LT6", 1, "LCK", true);
    f.txn(DetailFamily.LOG_TALLY, 83, "LT6", 1, 300, 1, 1, "ORG", "CRT", "DR");
    DetailDocumentReset failing = new DetailDocumentReset(f.repo, f.writer, (ddn, v, u) -> {
      throw new IllegalStateException("More than one PNV for ddn " + ddn);
    });
    assertThatThrownBy(() -> failing.reset(ret, USER)).isInstanceOf(WorkflowValidationException.class)
        .hasMessage("More than one PNV for ddn LT6");
  }

  @Test
  void aVersionWithTwoTxnsOnTheSummaryIsResetOnce() {
    SummaryReturn ret = generated(SummaryReturnType.WEIGHT);
    f.document("WS4", DetailFamily.WEIGH_SLIP, "CWR");
    f.version(DetailFamily.WEIGH_SLIP, "WS4", 1, "LCK", true);
    f.txn(DetailFamily.WEIGH_SLIP, 91, "WS4", 1, 300, 1, 2, "CAN", "CRT", "CR");
    f.txn(DetailFamily.WEIGH_SLIP, 92, "WS4", 1, 300, 1, 3, "RWN", "CRT", "DR");

    f.reset.reset(ret, USER);

    assertThat(calls("HBS_STORE_WEIGH_SLIP")).hasSize(1);
    assertThat(calls("HBS_STORE_WEIGH_SLIP_TXN")).singleElement()
        .satisfies(c -> assertThat(c.arg("i_dtl_txn_id")).isEqualTo(new java.math.BigDecimal(92)));
  }

  @Test
  void aVersionWithoutTxnsIsStillReset() {
    f.document("WS5", DetailFamily.WEIGH_SLIP, "OIP");
    f.version(DetailFamily.WEIGH_SLIP, "WS5", 1, "LCK", true);
    DetailDocument d = f.repo.load(DetailFamily.WEIGH_SLIP, new DetailDocumentRepository.Key("WS5", 1));

    f.reset.reset(d, USER);

    assertThat(f.s.api.names()).doesNotContain("HBS_STORE_WEIGH_SLIP_TXN");
    assertThat(calls("HBS_STORE_WEIGH_SLIP").get(0).arg("i_scl_rtn_version_state_code")).isEqualTo("RDY");
  }
}
