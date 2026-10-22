package ca.bc.gov.nrs.hbs.api.scalecontrol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.hbs.api.legacyrules.FixedAudit;
import ca.bc.gov.nrs.hbs.api.legacyrules.LegacyMessages;
import ca.bc.gov.nrs.hbs.api.workflow.RecordingTableApi;
import ca.bc.gov.nrs.hbs.api.workflow.StaleUpdateException;
import ca.bc.gov.nrs.hbs.api.workflow.WorkflowValidationException;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** P954 clear anomaly and P908/P909 assessment windows. */
class ScaleControlWorkflowsTest {

  private static final Timestamp NOW = Timestamp.valueOf("2026-10-21 10:15:00");

  private final ScaleControlReads reads = ca.bc.gov.nrs.hbs.api.legacyrules.NullMocks.of(ScaleControlReads.class);
  private final RecordingTableApi api = new RecordingTableApi();
  private final FixedAudit audit = new FixedAudit(NOW.toLocalDateTime());

  private static Map<String, Object> body(Object... kv) {
    Map<String, Object> m = new HashMap<>();
    for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
    return m;
  }

  private static void assertProblem(Runnable r, String message) {
    assertThatThrownBy(r::run).isInstanceOf(WorkflowValidationException.class)
        .satisfies(e -> assertThat(((WorkflowValidationException) e).problems())
            .extracting(WorkflowValidationException.Problem::message).contains(message));
  }

  // ---- P954 ----------------------------------------------------------------------------------

  private static Map<String, Object> anomaly(String status) {
    Map<String, Object> row = new HashMap<>();
    for (String col : new String[] {"USER_ID", "CLEARED_DATETIME", "WS_DTL_DOC_NO", "WS_VERSION",
        "SMPL_TLY_DTL_DOC_NO", "SMPL_TLY_VERSION", "SFP_TLY_DTL_DOC_NO", "SFP_TLY_VERSION", "ARRVL_LDGR_DTL_DOC_NO",
        "ARRVL_LDGR_VERSION", "DEPRT_LDGR_DTL_DOC_NO", "DEPRT_LDGR_VERSION", "USER_CLEARED_COMMENT"}) {
      row.put(col, null);
    }
    row.put("ANOMALY_ID", BigDecimal.valueOf(55));
    row.put("SCALE_ANOMALY_STATUS_CODE", status);
    row.put("SCALE_ANOMALY_TYPE_CODE", "G");
    row.put("SCALE_CONTROL_TYPE_CODE", "S");
    row.put("ANOMALY_BATCH_ID", BigDecimal.valueOf(900));
    row.put("ORIGINAL_ANOMALY_BATCH_ID", BigDecimal.valueOf(800));
    row.put("DOCUMENT_EVENT_DATE", Timestamp.valueOf("2026-09-01 00:00:00"));
    row.put("ANOMALY_GAP_COUNT", BigDecimal.valueOf(2));
    row.put("LOG_TLY_DTL_DOC_NO", "D123");
    row.put("LOG_TLY_VERSION", BigDecimal.ONE);
    return row;
  }

  private ClearAnomalyCommand clear(String status, String controlStatus, boolean inDistrict) {
    when(reads.anomaly(55)).thenReturn(anomaly(status));
    when(reads.assessmentControl("S")).thenReturn(Map.of("ANOMALY_ASSESSMENT_STATUS_CODE", controlStatus));
    when(reads.anomalyScaleSite(55)).thenReturn("A12");
    when(reads.siteInUserDistricts("A12", FixedAudit.USER)).thenReturn(inDistrict);
    return new ClearAnomalyCommand(reads, api, audit);
  }

  @Test
  void clear_storesUserClearedKeepingTheLinks() {
    clear("O", "RCM", true).execute(body("anomalyId", "55", "userClearedComment", "checked with site"));
    var call = api.callsTo("HBS_STORE_SCALE_ANOMALY_LOG").get(0);
    assertThat(call.arg("i_scale_anomaly_status_code")).isEqualTo("U");
    assertThat(call.arg("i_user_id")).isEqualTo(FixedAudit.USER);
    assertThat(call.arg("i_cleared_datetime")).isEqualTo(NOW);
    assertThat(call.arg("i_user_cleared_comment")).isEqualTo("checked with site");
    assertThat(call.arg("i_log_tly_dtl_doc_no")).isEqualTo("D123");
    assertThat(call.arg("i_anomaly_batch_id")).isEqualTo(BigDecimal.valueOf(900));
    assertThat(call.arg("i_org_batch_id")).isEqualTo(BigDecimal.valueOf(800));
  }

  @Test
  void clear_preconditions() {
    assertThatThrownBy(() -> clear("U", "RCM", true).execute(body("anomalyId", "55")))
        .isInstanceOf(WorkflowValidationException.class);
    assertProblem(() -> clear("O", "AIP", true).execute(body("anomalyId", "55")), LegacyMessages.STATUS_CHANGED);
    assertProblem(() -> clear("O", "WCM", false).execute(body("anomalyId", "55")), LegacyMessages.ANOMALY_USER_ACCESS);
    assertProblem(() -> clear("O", "WCM", true).execute(body("anomalyId", "55", "userClearedComment", "x".repeat(241))),
        LegacyMessages.maxLength("Comment", 240));
    assertThat(api.calls).isEmpty();
  }

  // ---- P908 / P909 ---------------------------------------------------------------------------

  private static Map<String, Object> control(String status) {
    Map<String, Object> row = new HashMap<>();
    row.put("SCALE_CONTROL_TYPE_CODE", "W");
    row.put("PRESENTATION_SEQUENCE", BigDecimal.valueOf(2));
    row.put("ASSESSMENT_BEGIN", BigDecimal.valueOf(30));
    row.put("ASSESSMENT_END", BigDecimal.valueOf(2));
    row.put("ANOMALY_ASSESSMENT_STATUS_CODE", status);
    row.put("PREVIOUS_ANOMALY_BATCH_ID", BigDecimal.valueOf(40));
    row.put("CURRENT_ANOMALY_BATCH_ID", BigDecimal.valueOf(41));
    row.put("UPDATE_TIMESTAMP", Timestamp.valueOf("2026-10-01 03:00:00"));
    row.put("UPDATE_USERID", "HBSBATCH");
    return row;
  }

  @Test
  void window_shrinkIsOneStore() {
    when(reads.assessmentControl("W")).thenReturn(control("RCM"));
    Map<String, Object> out = new UpdateAssessmentWindowCommand(reads, api, audit).execute(body(
        "scaleControlTypeCode", "W", "assessmentBegin", "20", "assessmentEnd", "3",
        "anomalyAssessmentStatusCode", "RCM", "updateTimestamp", "2026-10-01T03:00:00"));
    assertThat(api.names()).containsExactly("HBS_STORE_ANMLY_ASSESS_CONTROL");
    var call = api.calls.get(0);
    assertThat(call.arg("i_assessment_begin")).isEqualTo(BigDecimal.valueOf(20));
    assertThat(call.arg("i_assessment_end")).isEqualTo(BigDecimal.valueOf(3));
    assertThat(call.arg("i_anmly_assess_status_code")).isEqualTo("RCM");
    assertThat(call.arg("i_current_anomaly_batch_id")).isEqualTo(BigDecimal.valueOf(41));
    assertThat(out).containsEntry("proposedBatchRunDate", "2026-10-22")
        .containsEntry("proposedFromScaleDate", "2026-10-02")
        .containsEntry("proposedToScaleDate", "2026-10-19");
  }

  @Test
  void window_expandReopensStaleAnomaliesBetweenWipAndWcm() {
    when(reads.assessmentControl("W")).thenReturn(control("WCM"));
    new UpdateAssessmentWindowCommand(reads, api, audit).execute(body(
        "scaleControlTypeCode", "W", "assessmentBegin", "45", "assessmentEnd", "2"));
    assertThat(api.names()).containsExactly("HBS_STORE_ANMLY_ASSESS_CONTROL", "HBS_STORE_ANMLY_ASSESS_CONTROL",
        "HBS_UPDATE_STALE_ANOMALIES", "HBS_STORE_ANMLY_ASSESS_CONTROL");
    assertThat(api.calls.get(1).arg("i_anmly_assess_status_code")).isEqualTo("WIP");
    var stale = api.calls.get(2);
    assertThat(stale.arg("i_documenteventdate")).isEqualTo("2026-09-06");
    assertThat(stale.arg("i_scalecontroltype")).isEqualTo("W");
    assertThat(stale.arg("i_anomalybatchid")).isEqualTo(BigDecimal.valueOf(41));
    assertThat(api.calls.get(3).arg("i_anmly_assess_status_code")).isEqualTo("WCM");
  }

  @Test
  void window_statusAndLock() {
    when(reads.assessmentControl("W")).thenReturn(control("AIP"));
    var cmd = new UpdateAssessmentWindowCommand(reads, api, audit);
    assertProblem(() -> cmd.execute(body("scaleControlTypeCode", "W", "assessmentBegin", "45", "assessmentEnd", "2")),
        LegacyMessages.STATUS_CHANGED);
    when(reads.assessmentControl("W")).thenReturn(control("RCM"));
    assertProblem(() -> cmd.execute(body("scaleControlTypeCode", "W", "assessmentBegin", "45", "assessmentEnd", "2",
        "anomalyAssessmentStatusCode", "WCM")), LegacyMessages.STATUS_CHANGED);
    assertThatThrownBy(() -> cmd.execute(body("scaleControlTypeCode", "W", "assessmentBegin", "45",
        "assessmentEnd", "2", "updateTimestamp", "2026-10-02T03:00:00"))).isInstanceOf(StaleUpdateException.class);
    assertThat(api.calls).isEmpty();
  }

  @ParameterizedTest(name = "from {0} to {1} (old 30..2) -> {2}")
  @CsvSource(nullValues = "-", value = {
      "40, 2, -",
      "10, 5, -",
      "-, 2, From Days is required.",
      "4a, 2, From Days must be numeric.",
      "5, 5, From Days must be greater than To Days.",
      "2, 1, The new value for From Days must be greater than the old value for To Days.",
      "40, 30, The new value for To Days must be less than the old value for From Days.",
      "30, 2, No values have been changed and no updates made.",
  })
  void window_validation(String from, String to, String expected) {
    if (expected == null) {
      assertThat(UpdateAssessmentWindowCommand.validate(from, to, 30, 2)).containsExactly(
          Integer.parseInt(from), Integer.parseInt(to));
    } else {
      assertProblem(() -> UpdateAssessmentWindowCommand.validate(from, to, 30, 2), expected);
    }
  }
}
