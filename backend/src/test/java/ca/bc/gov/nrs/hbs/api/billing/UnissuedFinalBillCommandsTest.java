package ca.bc.gov.nrs.hbs.api.billing;

import static ca.bc.gov.nrs.hbs.api.security.HbsRoles.BILL_ADMIN;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturnType.PIECE;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturnType.WEIGHT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryCaller;
import ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryFixtures;
import ca.bc.gov.nrs.hbs.api.workflow.WorkflowValidationException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.transaction.PlatformTransactionManager;

/** P832 Hold Selected / Release Selected (P831Action ACTION_HOLD / ACTION_RELEASE). */
class UnissuedFinalBillCommandsTest {

  private final SummaryFixtures f = new SummaryFixtures();
  private final UnissuedFinalBillCommands.Deps deps = new UnissuedFinalBillCommands.Deps(f.repo, f.writer, f.access,
      f.audit, f.workflows, new StaticListableBeanFactory().getBeanProvider(PlatformTransactionManager.class));
  private final SummaryCaller caller = SummaryFixtures.caller(BILL_ADMIN);

  private final UnissuedFinalBillCommands.Hold hold = new UnissuedFinalBillCommands.Hold(deps) {
    @Override
    protected SummaryCaller caller() {
      return caller;
    }
  };
  private final UnissuedFinalBillCommands.Release release = new UnissuedFinalBillCommands.Release(deps) {
    @Override
    protected SummaryCaller caller() {
      return caller;
    }
  };

  private void finalBill(long dcn, String docState, String status) {
    f.header(dcn, WEIGHT, docState, false);
    f.version(WEIGHT, dcn, 1, status, true).set("scl_rtn_category_code", "FB");
  }

  private static Map<String, Object> row(long dcn, long version) {
    Map<String, Object> r = new HashMap<>();
    r.put("documentControlNumber", dcn);
    r.put("version", version);
    r.put("updateTimestamp", "2026-10-01T09:30:00");
    return r;
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> results(Map<String, Object> out) {
    return (List<Map<String, Object>>) out.get("results");
  }

  @Test
  void holdSelectedHoldsReadyRowsAndSkipsTheOthers() {
    finalBill(500, "OIP", "RDY");
    finalBill(501, "OIP", "HLD");
    f.header(502, PIECE, "OIP", false);
    f.version(PIECE, 502, 1, "RDY", true);
    List<Map<String, Object>> out = results(hold.execute(Map.of("rows", List.of(row(500, 1), row(501, 1),
        row(502, 1)))));
    assertThat(out).extracting(r -> r.get("status")).containsExactly("PROCESSED", "NOTPROCESSED", "NOTPROCESSED");
    assertThat(out.get(2).get("message")).isEqualTo("Not a weight scale final bill.");
    // BT16641 hold: the ready version is superseded and a new held version is created.
    assertThat(f.api.callsTo("HBS_STORE_WEIGHT_SCL_SUMMARY")).singleElement()
        .satisfies(c -> assertThat(c.arg("i_scl_rtn_version_state_code")).isEqualTo("SPR"));
    assertThat(f.api.callsTo("HBS_CREATE_WEIGHT_SCL_SUMMARY")).singleElement()
        .satisfies(c -> assertThat(c.arg("i_scl_rtn_version_state_code")).isEqualTo("HLD"));
    assertThat(out.get(0).get("message")).isEqualTo("Held (version 2).");
  }

  @Test
  void anErrorRowIsHeldToo() {
    finalBill(500, "OIP", "ERR"); // ERR is a ready state (ScaleReturnReadyState), so it is holdable
    List<Map<String, Object>> out = results(hold.execute(Map.of("rows", List.of(row(500, 1)))));
    assertThat(out.get(0)).containsEntry("status", "PROCESSED");
  }

  @Test
  void aStateTheWorkflowRefusesIsReportedAsAnError() {
    finalBill(500, "CWR", "RDY"); // listed RDY, but hold is only allowed under OIP / RWC
    List<Map<String, Object>> out = results(hold.execute(Map.of("rows", List.of(row(500, 1)))));
    assertThat(out.get(0)).containsEntry("status", "ERROR");
    assertThat(out.get(0).get("message").toString()).contains("Hold is not available");
  }

  @Test
  void staleRowsAreErrorsAndTheOthersStillRun() {
    finalBill(500, "OIP", "RDY");
    finalBill(501, "OIP", "RDY");
    Map<String, Object> stale = row(500, 1);
    stale.put("updateTimestamp", "2026-10-01T09:31:00");
    List<Map<String, Object>> out = results(hold.execute(Map.of("rows", List.of(stale, row(501, 1)))));
    assertThat(out).extracting(r -> r.get("status")).containsExactly("ERROR", "PROCESSED");
  }

  @Test
  void releaseSelectedReleasesHeldRowsOnly() {
    finalBill(500, "OIP", "HLD");
    finalBill(501, "OIP", "RDY");
    List<Map<String, Object>> out = results(release.execute(Map.of("rows", List.of(row(500, 1), row(501, 1)))));
    assertThat(out).extracting(r -> r.get("status")).containsExactly("PROCESSED", "NOTPROCESSED");
    assertThat(out.get(0).get("message").toString()).startsWith("Released: status RDY.");
    assertThat(f.api.callsTo("HBS_STORE_WEIGHT_SCL_SUMMARY")).singleElement()
        .satisfies(c -> assertThat(c.arg("i_scl_rtn_version_state_code")).isEqualTo("RDY"));
  }

  @Test
  void nothingSelectedIsRefused() {
    assertThatThrownBy(() -> hold.execute(Map.of("rows", List.of())))
        .isInstanceOf(WorkflowValidationException.class);
  }

  @Test
  void idsAndCapability() {
    assertThat(hold.id()).isEqualTo("billing.unissuedFinalBills.hold");
    assertThat(release.id()).isEqualTo("billing.unissuedFinalBills.release");
    assertThat(hold.capability().name()).isEqualTo("FINAL_BILL_ADMIN");
  }
}
