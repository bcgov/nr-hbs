package ca.bc.gov.nrs.hbs.api.scalecontrol;

import ca.bc.gov.nrs.hbs.api.legacyrules.Inputs;
import ca.bc.gov.nrs.hbs.api.legacyrules.LegacyMessages;
import ca.bc.gov.nrs.hbs.api.query.Capability;
import ca.bc.gov.nrs.hbs.api.workflow.Audit;
import ca.bc.gov.nrs.hbs.api.workflow.ProcArgs;
import ca.bc.gov.nrs.hbs.api.workflow.RowLock;
import ca.bc.gov.nrs.hbs.api.workflow.TableApi;
import ca.bc.gov.nrs.hbs.api.workflow.WorkflowCommand;
import ca.bc.gov.nrs.hbs.api.workflow.WorkflowValidationException;
import ca.bc.gov.nrs.hbs.api.workflow.WorkflowValidationException.Problem;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * P908 "Update Window Spans" → P909 "Confirm New Assessment Windows" for one
 * scale control type ({@code P908Action}, {@code HBSValidator.validateAnomalyAssessmentWindow},
 * {@code AnomalyManagerBean.processAssessmentWindow} /
 * {@code processAssessmentWindowExpand}).
 *
 * <ol>
 *   <li>Re-read ANOMALY_ASSESSMENT_CONTROL, check UPDATE_TIMESTAMP; the status
 *       must still be the one loaded and RCM / WCM (only those rows were
 *       editable, {@code updateFlag}) — else error.status.changed
 *       ({@code P908Action.validateStatus}).</li>
 *   <li>From/To Days required and numeric; From &gt; To; new From &gt; old To;
 *       new To &lt; old From; something changed (error.values.unchanged).</li>
 *   <li>HBS_STORE_ANMLY_ASSESS_CONTROL with the new span.</li>
 *   <li>Window expanded (To unchanged, From increased): status WIP,
 *       HBS_UPDATE_STALE_ANOMALIES(today − new From, type,
 *       CURRENT_ANOMALY_BATCH_ID) re-opens stale-dated anomalies inside the
 *       wider window, then status WCM. One transaction, so the WIP state the
 *       legacy batch's {@code canProcessAssessment} would skip is never
 *       committed; the end state is legacy's.</li>
 * </ol>
 * Returns the P909 preview: proposed next run (tomorrow) and its From / To
 * scale dates.
 *
 * <p>Body: {@code scaleControlTypeCode}, {@code assessmentBegin} (From Days),
 * {@code assessmentEnd} (To Days), {@code anomalyAssessmentStatusCode} (as
 * loaded), {@code updateTimestamp}.
 */
@Component
public class UpdateAssessmentWindowCommand implements WorkflowCommand {

  static final String STORE = "HBS_STORE_ANMLY_ASSESS_CONTROL";
  static final String STALE = "HBS_UPDATE_STALE_ANOMALIES";
  static final Set<String> UPDATABLE = Set.of("RCM", "WCM");
  static final String ADJUSTMENT_IN_PROGRESS = "WIP";
  static final String ADJUSTMENT_COMPLETE = "WCM";
  static final String FROM_DAYS = "From Days";
  static final String TO_DAYS = "To Days";

  private final ScaleControlReads reads;
  private final TableApi tableApi;
  private final Audit audit;

  public UpdateAssessmentWindowCommand(ScaleControlReads reads, TableApi tableApi, Audit audit) {
    this.reads = reads;
    this.tableApi = tableApi;
    this.audit = audit;
  }

  @Override
  public String id() {
    return "scaleControl.anomalyWindows.update";
  }

  @Override
  public String legacy() {
    return "P908/P909 (P908Action, HBSValidator.validateAnomalyAssessmentWindow, "
        + "AnomalyManagerBean.processAssessmentWindow)";
  }

  @Override
  public Capability capability() {
    return Capability.PRODUCTION_CONTROL;
  }

  @Override
  public Map<String, Object> execute(Map<String, Object> body) {
    String type = Inputs.upper(body, "scaleControlTypeCode");
    if (type == null) {
      throw new WorkflowValidationException("scaleControlTypeCode", LegacyMessages.required("Scale Control Type"));
    }
    Map<String, Object> row = reads.assessmentControl(type);
    if (row == null) {
      throw new WorkflowValidationException("scaleControlTypeCode", LegacyMessages.invalid("Scale Control Type"));
    }
    RowLock.check(Inputs.col(row, "UPDATE_TIMESTAMP"), body.get("updateTimestamp"));
    String dbStatus = Inputs.colText(row, "ANOMALY_ASSESSMENT_STATUS_CODE");
    String loadedStatus = Inputs.upper(body, "anomalyAssessmentStatusCode");
    if (!UPDATABLE.contains(dbStatus) || (loadedStatus != null && !loadedStatus.equalsIgnoreCase(dbStatus))) {
      throw new WorkflowValidationException("anomalyAssessmentStatusCode", LegacyMessages.STATUS_CHANGED);
    }

    int oldFrom = ((Number) Inputs.col(row, "ASSESSMENT_BEGIN")).intValue();
    int oldTo = ((Number) Inputs.col(row, "ASSESSMENT_END")).intValue();
    int[] span = validate(Inputs.text(body, "assessmentBegin"), Inputs.text(body, "assessmentEnd"), oldFrom, oldTo);
    int newFrom = span[0];
    int newTo = span[1];

    String user = audit.user();
    Timestamp now = audit.now();
    store(row, newFrom, newTo, dbStatus, user, now);
    boolean expanded = newTo == oldTo && newFrom > oldFrom;
    if (expanded) {
      store(row, newFrom, newTo, ADJUSTMENT_IN_PROGRESS, user, now);
      LocalDate assessmentBegin = now.toLocalDateTime().toLocalDate().minusDays(newFrom);
      tableApi.call(STALE, ProcArgs.of()
          .put("i_documenteventdate", assessmentBegin.toString())
          .put("i_scalecontroltype", type)
          .put("i_anomalybatchid", Inputs.col(row, "CURRENT_ANOMALY_BATCH_ID")));
      store(row, newFrom, newTo, ADJUSTMENT_COMPLETE, user, now);
    }

    // P909 preview (ScaleAnomalyHelper.populateAssessmentControlParamList).
    LocalDate run = now.toLocalDateTime().toLocalDate().plusDays(1);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("scaleControlTypeCode", type);
    result.put("proposedBatchRunDate", run.toString());
    result.put("proposedFromScaleDate", run.minusDays(newFrom).toString());
    result.put("proposedToScaleDate", run.minusDays(newTo).toString());
    result.put("staleAnomaliesReopened", expanded);
    return result;
  }

  /** HBSValidator.validateAnomalyAssessmentWindow for one row; returns {newFrom, newTo}. */
  static int[] validate(String from, String to, int oldFrom, int oldTo) {
    List<Problem> errors = new ArrayList<>();
    if (from == null) errors.add(new Problem("assessmentBegin", LegacyMessages.required(FROM_DAYS)));
    if (to == null) errors.add(new Problem("assessmentEnd", LegacyMessages.required(TO_DAYS)));
    if (from != null && !numeric(from)) errors.add(new Problem("assessmentBegin", LegacyMessages.notNumeric(FROM_DAYS)));
    if (to != null && !numeric(to)) errors.add(new Problem("assessmentEnd", LegacyMessages.notNumeric(TO_DAYS)));
    if (!errors.isEmpty()) {
      throw new WorkflowValidationException(errors);
    }
    int newFrom = Integer.parseInt(from);
    int newTo = Integer.parseInt(to);
    if (newFrom <= newTo) {
      throw new WorkflowValidationException("assessmentBegin", LegacyMessages.FROM_DAYS_GREATER);
    }
    if (newFrom <= oldTo) {
      throw new WorkflowValidationException("assessmentBegin", LegacyMessages.NEW_FROM_OLD_TO);
    }
    if (newTo >= oldFrom) {
      throw new WorkflowValidationException("assessmentEnd", LegacyMessages.NEW_TO_OLD_FROM);
    }
    if (newFrom == oldFrom && newTo == oldTo) {
      throw new WorkflowValidationException("assessmentBegin", LegacyMessages.VALUES_UNCHANGED);
    }
    return new int[] {newFrom, newTo};
  }

  /** StringUtil.isNumeric: digits only (maxlength 4 on the form). */
  private static boolean numeric(String s) {
    return s.length() <= 9 && s.chars().allMatch(Character::isDigit);
  }

  private void store(Map<String, Object> row, int from, int to, String status, String user, Timestamp now) {
    tableApi.call(STORE, tableApi.argsFromRow(STORE, row)
        .put("i_assessment_begin", from)
        .put("i_assessment_end", to)
        .put("i_anmly_assess_status_code", status)
        .put("i_update_timestamp", now)
        .put("i_update_userid", user));
  }
}
