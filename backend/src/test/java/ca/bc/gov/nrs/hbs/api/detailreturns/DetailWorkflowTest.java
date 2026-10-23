package ca.bc.gov.nrs.hbs.api.detailreturns;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.bc.gov.nrs.hbs.api.detailreturns.model.DetailDocument;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.DetailVersion;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.DocType;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.LogLine;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.Row;
import ca.bc.gov.nrs.hbs.api.detailreturns.state.DocumentState;
import ca.bc.gov.nrs.hbs.api.detailreturns.state.VersionState;
import ca.bc.gov.nrs.hbs.api.workflow.StaleUpdateException;
import java.math.BigDecimal;
import java.sql.Timestamp;
import org.junit.jupiter.api.Test;

/** Proc sequences and resulting states of the ported workflows (legacy control/workflow/*). */
class DetailWorkflowTest {

  private final WorkflowFixture f = new WorkflowFixture();

  private DetailDocument load(String ddn, long version) {
    return f.store.find(ddn, version).orElseThrow();
  }

  @Test
  void addCreatesDocumentThenOriginalIncompleteVersionAndLines() {
    Row v = DetailPayload.blankVersion(DocType.LOG_TALLY).set("version", 1L).set("detail_document_number", "D1")
        .set("scale_event_type_code", "PS").set("scale_date", Timestamp.valueOf("2026-10-01 00:00:00"))
        .setYes("valid_signature_ind", true).set("dtl_doc_seq", 9L).set("entry_userid", "U")
        .set("entry_timestamp", Timestamp.valueOf("2026-10-21 10:00:00"));
    VersionState vs = VersionState.initialOnline();
    DetailVersion ver = new DetailVersion(v, vs, false);
    ver.logs().add(new LogLine(new Row().set("log_number", 1).set("length", new BigDecimal("5.3")).set("top", 20)
        .set("butt", 30).set("net_volume", new BigDecimal("1.082")).set("nmv_ind", "N").set("deduction_count", 0)));
    DetailDocument doc = new DetailDocument(DocType.LOG_TALLY,
        new Row().set("detail_document_number", "D1").set("entry_userid", "U")
            .set("entry_timestamp", Timestamp.valueOf("2026-10-21 10:00:00")),
        new DocumentState("OIP", vs), ver, false);

    f.workflow.add(doc, "IDIR\\TEST");

    assertThat(f.api.names()).containsSubsequence("HBS_CREATE_DTL_SCL_DOCUMENT", "HBS_CREATE_AUDIT",
        "HBS_CREATE_LOG_TALLY", "HBS_CREATE_LOG_TALLY_DETAIL");
    assertThat(f.last("HBS_CREATE_DTL_SCL_DOCUMENT").arg("i_scale_return_state_code")).isEqualTo("OIP");
    var created = f.last("HBS_CREATE_LOG_TALLY");
    assertThat(created.arg("i_scl_rtn_version_state_code")).isEqualTo("INC");
    assertThat(created.arg("i_active_version_ind")).isEqualTo("Y");
    assertThat(created.arg("i_original_version_ind")).isEqualTo("Y");
    assertThat(f.last("HBS_CREATE_LOG_TALLY_DETAIL").arg("i_detail_document_number")).isEqualTo("D1");
    assertThat(f.last("HBS_CREATE_AUDIT").arg("i_action_type_code")).isEqualTo("INS");
    assertThat(f.last("HBS_CREATE_AUDIT").arg("i_object_new_id")).isEqualTo("D1,1");
  }

  @Test
  void saveRemovesOldErrorsStoresVersionAndSavesNewErrors() {
    f.logTally("D1", "OIP", 1, "INC", true);
    f.rows.versionErrors.add(new Row().set("error_id", 77).set("detail_document_number", "D1").set("version", 1));
    f.errorCodes.add("TM01");
    DetailDocument doc = load("D1", 1);

    f.workflow.save(doc, "IDIR\\TEST");

    assertThat(f.api.names()).containsSubsequence("HBS_REMOVE_LOG_TALLY_ERROR", "HBS_CREATE_AUDIT",
        "HBS_STORE_LOG_TALLY", "HBS_CREATE_LOG_TALLY_ERROR");
    assertThat(f.last("HBS_REMOVE_LOG_TALLY_ERROR").arg("i_error_id")).isEqualTo(BigDecimal.valueOf(77));
    assertThat(f.last("HBS_STORE_LOG_TALLY").arg("i_scl_rtn_version_state_code")).isEqualTo("INC");
    assertThat(f.last("HBS_STORE_LOG_TALLY").arg("i_net_volume")).isEqualTo(new BigDecimal("0.0"));
    assertThat(f.last("HBS_CREATE_LOG_TALLY_ERROR").arg("i_hbs_edit_err_message_code")).isEqualTo("TM01");
    assertThat(f.calls("HBS_STORE_DTL_SCL_DOCUMENT")).isEmpty(); // legacy Save does not write the document
  }

  @Test
  void releaseCleanOriginalGoesReady() {
    f.logTally("D1", "OIP", 1, "INC", true);
    DetailDocument doc = load("D1", 1);
    f.workflow.release(doc, "U");
    assertThat(doc.version().state().value()).isEqualTo("RDY");
    assertThat(f.calls("HBS_STORE_LOG_TALLY")).allMatch(c -> "RDY".equals(c.arg("i_scl_rtn_version_state_code")));
    assertThat(f.calls("HBS_STORE_DTL_SCL_DOCUMENT")).hasSize(1);
    assertThat(f.calls("HBS_CREATE_SCL_RTN_AUDIT")).anyMatch(c -> "Version_Status".equals(c.arg("i_field_name"))
        && "INC".equals(c.arg("i_old_value")) && "RDY".equals(c.arg("i_new_value")));
  }

  @Test
  void releaseWithEditErrorsGoesError() {
    f.logTally("D1", "OIP", 1, "INC", true);
    f.errorCodes.add("SS01");
    DetailDocument doc = load("D1", 1);
    f.workflow.release(doc, "U");
    assertThat(doc.version().state().value()).isEqualTo("ERR");
    assertThat(f.last("HBS_CREATE_LOG_TALLY_ERROR").arg("i_hbs_edit_err_message_code")).isEqualTo("SS01");
  }

  @Test
  void releaseOfNonBillableReturnIsNotToBeIssued() {
    f.logTally("D1", "OIP", 1, "INC", true);
    f.billable = false;
    DetailDocument doc = load("D1", 1);
    f.workflow.release(doc, "U");
    assertThat(doc.version().state().value()).isEqualTo("NTI");
  }

  @Test
  void releaseOfIncompleteCorrectionGoesAwaitingApproval() {
    f.logTally("D1", "CWC", 2, "INC", true);
    DetailDocument doc = load("D1", 2);
    f.workflow.release(doc, "U");
    assertThat(doc.version().state().value()).isEqualTo("AWP");
    assertThat(f.validations).isZero(); // state.Release, no edits
  }

  @Test
  void releaseRefusesSignatureFailureAndClearSignatureFailureReleasesIt() {
    f.logTally("D1", "OIP", 1, "DSF", true);
    assertThatThrownBy(() -> f.workflow.release(load("D1", 1), "U"))
        .hasMessageContaining("digital signature failure");
    DetailDocument doc = load("D1", 1);
    f.workflow.clearSignatureFailure(doc, "U");
    assertThat(doc.version().state().value()).isEqualTo("RDY");
    f.logTally("D2", "OIP", 1, "RDY", true);
    assertThatThrownBy(() -> f.workflow.clearSignatureFailure(load("D2", 1), "U"))
        .hasMessageContaining("Use release() instead");
  }

  @Test
  void releaseOfAnIssuedVersionIsRefusedWithLegacyMessage() {
    f.logTally("D1", "OCM", 1, "ISS", true);
    assertThatThrownBy(() -> f.workflow.release(load("D1", 1), "U"))
        .hasMessageStartingWith("Cannot release document with state original complete (OCM)");
  }

  @Test
  void holdSupersedesAndCopiesIntoNewHeldVersion() {
    Row v1 = f.logTally("D1", "OIP", 1, "RDY", true);
    f.rows.lines.add(new Row().set("logdtl_id", 5).set("detail_document_number", "D1").set("version", 1)
        .set("log_number", 1).set("net_volume", new BigDecimal("1.0")).set("nmv_ind", "N").set("deduction_count", 0));
    DetailDocument doc = load("D1", 1);

    DetailDocument held = f.workflow.hold(doc, "U");

    assertThat(doc.version().state().value()).isEqualTo("SPR");
    assertThat(doc.version().state().isActive()).isFalse();
    assertThat(held.version().number()).isEqualTo(2L);
    assertThat(held.version().state().value()).isEqualTo("HLD");
    var created = f.last("HBS_CREATE_LOG_TALLY");
    assertThat(created.arg("i_version")).isEqualTo(BigDecimal.valueOf(2));
    assertThat(created.arg("i_scl_rtn_version_state_code")).isEqualTo("HLD");
    assertThat(created.arg("i_original_version_ind")).isEqualTo("N");
    assertThat(f.last("HBS_CREATE_LOG_TALLY_DETAIL").arg("i_version")).isEqualTo(BigDecimal.valueOf(2));
    assertThat(f.api.names()).containsSubsequence("HBS_STORE_LOG_TALLY", "HBS_STORE_DTL_SCL_DOCUMENT",
        "HBS_CREATE_LOG_TALLY");
    assertThat(v1).isNotNull();
  }

  @Test
  void holdUnderReplaceWithNoChangesCancelsTheReadyVersion() {
    f.logTally("D1", "RNP", 2, "RDY", true);
    DetailDocument doc = load("D1", 2);
    f.workflow.hold(doc, "U");
    assertThat(doc.version().state().value()).isEqualTo("CAN");
    assertThat(doc.state().value()).isEqualTo("RCP");
  }

  @Test
  void discardOriginalKeepsDiscardedVersionActive() {
    f.logTally("D1", "OIP", 1, "INC", true);
    DetailDocument doc = load("D1", 1);
    f.workflow.discard(doc, "U");
    assertThat(doc.version().state().value()).isEqualTo("DEL");
    assertThat(doc.version().state().isActive()).isTrue();
    assertThat(f.last("HBS_STORE_LOG_TALLY").arg("i_active_version_ind")).isEqualTo("Y");
    assertThat(f.last("HBS_CREATE_AUDIT").arg("i_action_type_code")).isEqualTo("DEL");
  }

  @Test
  void discardOfCorrectionReactivatesIssuedVersion() {
    f.logTally("D1", "CWC", 1, "ISS", false);
    f.rows.addVersion("D1", 2, "INC", true);
    DetailDocument doc = load("D1", 2);
    f.workflow.discard(doc, "U");
    assertThat(f.calls("HBS_STORE_LOG_TALLY")).anyMatch(c -> BigDecimal.ONE.equals(c.arg("i_version"))
        && "ISS".equals(c.arg("i_scl_rtn_version_state_code")) && "Y".equals(c.arg("i_active_version_ind")));
    assertThat(f.calls("HBS_STORE_DTL_SCL_DOCUMENT")).anyMatch(c -> "OCM".equals(c.arg("i_scale_return_state_code")));
  }

  @Test
  void cancelPutsIssuedVersionAwaitingApprovalUnderCancelInProgress() {
    f.logTally("D1", "OCM", 1, "ISS", true);
    DetailDocument doc = load("D1", 1);
    f.workflow.cancel(doc, "U");
    assertThat(doc.version().state().value()).isEqualTo("AWP");
    assertThat(f.last("HBS_STORE_DTL_SCL_DOCUMENT").arg("i_scale_return_state_code")).isEqualTo("CIP");
    assertThat(f.last("HBS_CREATE_AUDIT").arg("i_action_type_code")).isEqualTo("CAN");
  }

  @Test
  void cancelAndReplaceWithChangesDeactivatesIssuedAndCreatesIncompleteCopy() {
    f.logTally("D1", "OCM", 1, "ISS", true);
    DetailDocument doc = load("D1", 1);
    DetailDocument next = f.workflow.replaceWithChanges(doc, "U");
    assertThat(doc.version().state().value()).isEqualTo("ISS");
    assertThat(doc.version().state().isActive()).isFalse();
    assertThat(doc.state().value()).isEqualTo("CWC");
    assertThat(next.version().state().value()).isEqualTo("INC");
    assertThat(next.version().number()).isEqualTo(2L);
    assertThat(f.last("HBS_CREATE_LOG_TALLY").arg("i_scl_rtn_version_state_code")).isEqualTo("INC");
  }

  @Test
  void replaceWithNoChangesOnCancelledVersion() {
    f.logTally("D1", "CCM", 1, "CAN", true);
    DetailDocument doc = load("D1", 1);
    f.workflow.replaceWithNoChanges(doc, "U");
    assertThat(doc.version().state().value()).isEqualTo("AWP");
    assertThat(doc.state().value()).isEqualTo("RNP");
  }

  @Test
  void approveCancelMakesVersionReady() {
    f.logTally("D1", "CIP", 1, "AWP", true);
    DetailDocument doc = load("D1", 1);
    f.workflow.approve(doc, "U");
    assertThat(doc.version().state().value()).isEqualTo("RDY");
    assertThat(f.last("HBS_CREATE_AUDIT").arg("i_action_type_code")).isEqualTo("APP");
  }

  @Test
  void approveCorrectionMakesItPendingAndIssuedVersionReady() {
    f.logTally("D1", "CWC", 1, "ISS", false);
    f.rows.addVersion("D1", 2, "AWP", true);
    DetailDocument doc = load("D1", 2);
    f.workflow.approve(doc, "U");
    assertThat(doc.version().state().value()).isEqualTo("PND");
    assertThat(doc.version().state().isActive()).isFalse();
    assertThat(f.calls("HBS_STORE_LOG_TALLY")).anyMatch(c -> BigDecimal.ONE.equals(c.arg("i_version"))
        && "RDY".equals(c.arg("i_scl_rtn_version_state_code")) && "Y".equals(c.arg("i_active_version_ind")));
  }

  @Test
  void rejectCancelRestoresIssuedVersionAndOriginalComplete() {
    f.logTally("D1", "CIP", 1, "AWP", true);
    DetailDocument doc = load("D1", 1);
    f.workflow.reject(doc, "U");
    assertThat(doc.version().state().value()).isEqualTo("ISS");
    assertThat(doc.state().value()).isEqualTo("OCM");
  }

  @Test
  void rejectCorrectionDiscardsItAndReactivatesIssuedVersion() {
    f.logTally("D1", "CWC", 1, "ISS", false);
    f.rows.addVersion("D1", 2, "AWP", true);
    DetailDocument doc = load("D1", 2);
    f.workflow.reject(doc, "U");
    assertThat(doc.version().state().value()).isEqualTo("DEL");
    assertThat(doc.state().value()).isEqualTo("OCM");
    // reloaded after the first write: OCM + ISS inactive → re-activated
    assertThat(f.calls("HBS_STORE_LOG_TALLY")).anyMatch(c -> BigDecimal.ONE.equals(c.arg("i_version"))
        && "ISS".equals(c.arg("i_scl_rtn_version_state_code")) && "Y".equals(c.arg("i_active_version_ind")));
  }

  @Test
  void staleDocumentStateIsRefusedWithLegacyMessage() {
    f.logTally("D1", "OCM", 1, "ISS", true);
    DetailDocument doc = load("D1", 1);
    f.setDocumentState("D1", "CIP"); // another process got there first
    assertThatThrownBy(() -> f.workflow.cancel(doc, "U")).isInstanceOf(StaleUpdateException.class)
        .hasMessage("Your changes were not saved because the scale return has been updated by another process.");
  }

  @Test
  void staleVersionTimestampIsRefused() {
    Row v = f.logTally("D1", "OIP", 1, "INC", true);
    DetailDocument doc = load("D1", 1);
    v.set("update_timestamp", Timestamp.valueOf("2026-10-21 11:11:11"));
    assertThatThrownBy(() -> f.workflow.save(doc, "U")).isInstanceOf(StaleUpdateException.class);
  }

  /**
   * WorkflowManagerBean.reset → processReplacementVersionPending(ddn, document, user): after the
   * summary-return discard reset a locked version back to RDY, a replacement-pending version
   * supersedes it and goes through the full release.
   */
  @Test
  void afterAResetThePendingReplacementSupersedesAndIsReleased() {
    f.logTally("D1", "OIP", 1, "RDY", true);
    f.rows.addVersion("D1", 2, "PNV", false).set("scale_event_type_code", "PS")
        .set("scale_date", Timestamp.valueOf("2026-10-01 00:00:00")).set("scale_site_id_nmbr", "S001")
        .set("timber_mark", "ABC123").set("primary_license_number", "1234").set("return_number", 56)
        .set("log_count", 1);

    f.workflow.processReplacementVersionPendingAfterReset("D1", 1, "U");

    assertThat(f.calls("HBS_STORE_LOG_TALLY")).anyMatch(c -> BigDecimal.ONE.equals(c.arg("i_version"))
        && "SPR".equals(c.arg("i_scl_rtn_version_state_code")) && "N".equals(c.arg("i_active_version_ind")));
    assertThat(f.calls("HBS_STORE_LOG_TALLY")).anyMatch(c -> BigDecimal.valueOf(2).equals(c.arg("i_version"))
        && "RDY".equals(c.arg("i_scl_rtn_version_state_code")) && "Y".equals(c.arg("i_active_version_ind")));
  }

  @Test
  void afterAResetWithoutPendingReplacementNothingHappens() {
    f.logTally("D1", "OIP", 1, "RDY", true);
    f.workflow.processReplacementVersionPendingAfterReset("D1", 1, "U");
    assertThat(f.api.calls).isEmpty();
  }
}
