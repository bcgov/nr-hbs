package ca.bc.gov.nrs.hbs.api.summaryreturns;

import static ca.bc.gov.nrs.hbs.api.security.HbsRoles.BILL_ADMIN;
import static ca.bc.gov.nrs.hbs.api.security.HbsRoles.CLI_CRUISE_ADMIN;
import static ca.bc.gov.nrs.hbs.api.security.HbsRoles.CLI_SCALER;
import static ca.bc.gov.nrs.hbs.api.security.HbsRoles.INV_CORR_APP;
import static ca.bc.gov.nrs.hbs.api.security.HbsRoles.SUMM_DATA_CORR;
import static ca.bc.gov.nrs.hbs.api.security.HbsRoles.SUMM_DATA_CTL;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturnType.PIECE;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturnType.SAMPLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.bc.gov.nrs.hbs.api.workflow.StaleUpdateException;
import ca.bc.gov.nrs.hbs.api.workflow.WorkflowValidationException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

/** Button rules (SummaryReturnFieldsTag), the command preconditions and the preview. */
class SummaryStateCommandsTest {

  private final SummaryFixtures f = new SummaryFixtures();
  private final SummaryStateCommands.Deps deps = new SummaryStateCommands.Deps(f.repo, f.writer, f.access, f.audit,
      f.workflows, f.buttons, SummaryFixtures.provider(f.details));

  private SummaryCaller caller = SummaryFixtures.caller(SUMM_DATA_CORR);

  private <T extends SummaryReturnCommand> Map<String, Object> run(SummaryReturnCommand cmd, Map<String, Object> body) {
    return cmd.execute(body);
  }

  /** Commands with a fixed caller. */
  private SummaryStateCommands.StateCommand cmd(String button) {
    SummaryStateCommands.StateCommand c = switch (button) {
      case "hold" -> new SummaryStateCommands.Hold(deps) { @Override protected SummaryCaller caller() { return caller; } };
      case "release" -> new SummaryStateCommands.Release(deps) { @Override protected SummaryCaller caller() { return caller; } };
      case "discard" -> new SummaryStateCommands.Discard(deps) { @Override protected SummaryCaller caller() { return caller; } };
      case "cancel" -> new SummaryStateCommands.Cancel(deps) { @Override protected SummaryCaller caller() { return caller; } };
      case "approveRateCorrection" -> new SummaryStateCommands.ApproveRateCorrection(deps) {
        @Override protected SummaryCaller caller() { return caller; } };
      default -> throw new IllegalArgumentException(button);
    };
    return c;
  }

  private Map<String, Object> body(long dcn, long version) {
    Map<String, Object> b = new HashMap<>();
    b.put("documentControlNumber", dcn);
    b.put("version", version);
    b.put("updateTimestamp", "2026-10-01T09:30:00");
    return b;
  }

  private SummaryReturn piece(String doc, String ver) {
    f.header(100, PIECE, doc, false);
    f.version(PIECE, 100, 1, ver, true);
    return f.repo.loadActive(100);
  }

  // ── Buttons ──────────────────────────────────────────────────────────────

  @Test
  void dataCorrectionSeesTheLegacyButtons() {
    SummaryCaller corr = SummaryFixtures.caller(SUMM_DATA_CORR);
    assertThat(f.buttons.available(corr, piece("OIP", "INC"))).containsExactlyInAnyOrder("update", "release", "discard");
    assertThat(f.buttons.available(corr, piece("OIP", "RDY"))).containsExactlyInAnyOrder("hold", "discard");
    assertThat(f.buttons.available(corr, piece("OCM", "ISS"))).containsExactlyInAnyOrder(
        "cancel", "cancelReplaceWithChanges", "cancelReplaceWithoutChanges");
    assertThat(f.buttons.available(corr, piece("CCM", "CAN"))).containsExactlyInAnyOrder(
        "replaceWithChanges", "replaceWithoutChanges");
    assertThat(f.buttons.available(corr, piece("OIP", "HLD"))).containsExactlyInAnyOrder("update", "release", "discard");
  }

  @Test
  void dataControlMayOnlyEditIncomplete() {
    SummaryCaller ctl = SummaryFixtures.caller(SUMM_DATA_CTL);
    assertThat(f.buttons.available(ctl, piece("OIP", "INC"))).containsExactlyInAnyOrder("update", "release", "discard");
    assertThat(f.buttons.available(ctl, piece("OIP", "HLD"))).isEmpty();
    assertThat(f.buttons.available(ctl, piece("OCM", "ISS"))).isEmpty();
  }

  @Test
  void wasteBatchReturnsOfferCancelOnly() {
    SummaryReturn r = piece("OCM", "ISS");
    r.version().set("entry_userid", "HBSB013");
    assertThat(f.buttons.available(SummaryFixtures.caller(SUMM_DATA_CORR), r)).containsExactly("cancel");
  }

  @Test
  void sampleReturnsHaveNoReplaceWithoutChanges() {
    f.header(400, SAMPLE, "OCM", false);
    f.version(SAMPLE, 400, 1, "ISS", true);
    assertThat(f.buttons.available(SummaryFixtures.caller(SUMM_DATA_CORR), f.repo.loadActive(400)))
        .containsExactlyInAnyOrder("cancel", "cancelReplaceWithChanges");
  }

  @Test
  void inactiveVersionsHaveNoButtons() {
    f.header(100, PIECE, "OCM", false);
    f.version(PIECE, 100, 1, "ISS", false);
    SummaryReturn r = f.repo.load(100, 1);
    assertThat(f.buttons.available(SummaryFixtures.caller(SUMM_DATA_CORR), r)).isEmpty();
  }

  @Test
  void generatedReturnsRateCorrectionButtonsNeedTheDistrict() {
    f.header(300, PIECE, "OCM", true);
    f.version(PIECE, 300, 1, "ISS", true);
    SummaryReturn r = f.repo.loadActive(300);
    assertThat(f.buttons.available(SummaryFixtures.caller(BILL_ADMIN), r)).isEmpty();
    f.access.districtMarks = Set.of("ABC123");
    assertThat(f.buttons.available(SummaryFixtures.caller(BILL_ADMIN), r)).containsExactly("requestRateCorrection");
    r.version().set("scl_rtn_version_state_code", "AWP");
    r.header().set("scale_return_state_code", "CWR");
    assertThat(f.buttons.available(SummaryFixtures.caller(INV_CORR_APP), r))
        .containsExactlyInAnyOrder("approveRateCorrection", "rejectRateCorrection");
  }

  @Test
  void cruiseBasedAdminActsOnItsAreaBasedReturnsOnly() {
    SummaryReturn r = piece("OIP", "RDY");
    r.version().set("pssmry_type", "AREAEST");
    SummaryCaller cba = SummaryFixtures.caller(CLI_CRUISE_ADMIN);
    assertThat(f.buttons.available(cba, r)).containsExactlyInAnyOrder("hold", "discard");
    f.access.markHolder = "00077777";
    assertThat(f.buttons.available(cba, r)).isEmpty();
  }

  @Test
  void volumeEstimateCustodianMayHoldAndDiscard() {
    SummaryReturn r = piece("OIP", "RDY");
    r.version().set("pssmry_type", "FLDSCLEST");
    SummaryCaller scaler = SummaryFixtures.caller(CLI_SCALER);
    assertThat(f.buttons.available(scaler, r)).isEmpty();
    f.access.licences = Set.of("K123");
    assertThat(f.buttons.available(scaler, r)).containsExactlyInAnyOrder("hold", "discard");
  }

  // ── Commands ─────────────────────────────────────────────────────────────

  @Test
  void holdCommandReturnsTheNewActiveVersion() {
    piece("OIP", "RDY");
    Map<String, Object> out = run(cmd("hold"), body(100, 1));
    assertThat(out).containsEntry("version", 2L).containsEntry("status", "HLD").containsEntry("returnStateCode", "OIP");
  }

  @Test
  void commandRefusesAButtonTheUserWouldNotSee() {
    piece("OIP", "INC");
    assertThatThrownBy(() -> run(cmd("cancel"), body(100, 1)))
        .isInstanceOf(WorkflowValidationException.class).hasMessageContaining("Cancel is not available");
    assertThat(f.api.calls).isEmpty();
  }

  @Test
  void staleTimestampIsRefused() {
    piece("OIP", "RDY");
    Map<String, Object> b = body(100, 1);
    b.put("updateTimestamp", "2026-10-01T09:31:00");
    assertThatThrownBy(() -> run(cmd("hold"), b)).isInstanceOf(StaleUpdateException.class);
    assertThat(f.api.calls).isEmpty();
  }

  @Test
  void aVersionThatIsNoLongerActiveIsStale() {
    piece("OIP", "RDY");
    assertThatThrownBy(() -> run(cmd("hold"), body(100, 3))).isInstanceOf(StaleUpdateException.class);
  }

  @Test
  void industryUsersOnlyActOnTheirClientsReturns() {
    piece("OIP", "RDY").version();
    f.repo.versions.get("P:100:1").set("pssmry_type", "AREAEST").set("client_number_paid_by", null);
    f.access.markHolder = "00077777";
    caller = SummaryFixtures.caller(CLI_CRUISE_ADMIN);
    assertThatThrownBy(() -> run(cmd("hold"), body(100, 1))).isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void releaseCommandReportsEditErrors() {
    piece("OIP", "INC");
    f.edits.errors = List.of(EditError.version("LKP", "TM01"));
    Map<String, Object> out = run(cmd("release"), body(100, 1));
    assertThat(out).containsEntry("status", "ERR");
    assertThat(out.get("editErrors")).isEqualTo(List.of("LKP TM01"));
  }

  @Test
  void approvalNeedsDetailsStillCorrectable() {
    f.header(300, PIECE, "CWR", true);
    f.version(PIECE, 300, 1, "ISS", false);
    f.version(PIECE, 300, 2, "AWP", true);
    f.access.districtMarks = Set.of("ABC123");
    caller = SummaryFixtures.caller(INV_CORR_APP);
    f.details.eligible = false;
    assertThatThrownBy(() -> run(cmd("approveRateCorrection"), body(300, 2)))
        .hasMessage(SummaryReturnMessages.RATE_CORRECTION_CONFIRM_NO_DETAILS);
  }

  // ── Preview ──────────────────────────────────────────────────────────────

  private SummaryPreviewCommand preview() {
    return new SummaryPreviewCommand(f.repo, f.writer, f.access, f.audit, f.buttons, SummaryFixtures.provider(f.details)) {
      @Override
      protected SummaryCaller caller() {
        return caller;
      }
    };
  }

  @SuppressWarnings("unchecked")
  @Test
  void previewOfCancelReplaceShowsTheCancelAndReplaceTransactions() {
    piece("OCM", "ISS");
    f.repo.latestTxns.put("P:100:1", new Row().set("event_sequence", 1).set("debit_credit_type", "DR")
        .set("hbs_txn_type_code", "ORG").set("invoice_number", "Q123456").set("volume", new BigDecimal("12.500"))
        .set("value", new BigDecimal("345.67")).set("weight", null));
    Map<String, Object> b = body(100, 1);
    b.put("action", "cancelReplaceWithChanges");
    Map<String, Object> out = preview().execute(b);
    assertThat((List<String>) out.get("availableActions")).contains("cancelReplaceWithChanges");
    List<Map<String, Object>> returns = (List<Map<String, Object>>) out.get("returns");
    List<Map<String, Object>> txns = (List<Map<String, Object>>) returns.get(0).get("transactions");
    assertThat(txns).extracting(t -> t.get("txnTypeDesc")).containsExactly("Original", "Cancel", "Replace");
    assertThat(txns).extracting(t -> t.get("eventSequence")).containsExactly(1L, 2L, 3L);
    assertThat(txns.get(1)).containsEntry("drCr", "CR").containsEntry("volume", new BigDecimal("12.500"))
        .containsEntry("invoiceNumber", "Q123456");
    assertThat(f.api.calls).isEmpty();
  }

  @SuppressWarnings("unchecked")
  @Test
  void previewOfReleasingCancelReplaceWithChangesShowsBothVersions() {
    f.header(100, PIECE, "CWC", false);
    f.version(PIECE, 100, 1, "ISS", false);
    f.version(PIECE, 100, 2, "INC", true);
    f.repo.latestTxns.put("P:100:1", new Row().set("event_sequence", 1).set("debit_credit_type", "DR")
        .set("hbs_txn_type_code", "ORG").set("invoice_number", "Q1").set("volume", BigDecimal.ONE)
        .set("value", BigDecimal.TEN).set("weight", null));
    Map<String, Object> b = body(100, 2);
    b.put("action", "release");
    List<Map<String, Object>> returns = (List<Map<String, Object>>) preview().execute(b).get("returns");
    assertThat(returns).extracting(r -> r.get("version")).containsExactly(2L, 1L);
    List<Map<String, Object>> prevTxns = (List<Map<String, Object>>) returns.get(1).get("transactions");
    assertThat(prevTxns).extracting(t -> t.get("txnTypeDesc")).containsExactly("Original", "Cancel");
    List<Map<String, Object>> newTxns = (List<Map<String, Object>>) returns.get(0).get("transactions");
    assertThat(newTxns).extracting(t -> t.get("eventSequence")).containsExactly(3L);
  }
}
