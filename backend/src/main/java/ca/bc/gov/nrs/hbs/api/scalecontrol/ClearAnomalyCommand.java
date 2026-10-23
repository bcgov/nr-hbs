package ca.bc.gov.nrs.hbs.api.scalecontrol;

import ca.bc.gov.nrs.hbs.api.legacyrules.Inputs;
import ca.bc.gov.nrs.hbs.api.legacyrules.LegacyMessages;
import ca.bc.gov.nrs.hbs.api.query.Capability;
import ca.bc.gov.nrs.hbs.api.workflow.Audit;
import ca.bc.gov.nrs.hbs.api.workflow.ProcArgs;
import ca.bc.gov.nrs.hbs.api.workflow.TableApi;
import ca.bc.gov.nrs.hbs.api.workflow.WorkflowCommand;
import ca.bc.gov.nrs.hbs.api.workflow.WorkflowValidationException;
import java.sql.Timestamp;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * P954 "Confirm Anomaly Clear" → Yes ({@code P911Action.performActionClearAnomalies}
 * → {@code AnomalyManagerBean.updateScaleAnomalyLog}).
 *
 * <p>Preconditions — legacy showed the Clear button only when all held
 * (P912.jsp … P953.jsp), and P911Action re-checked the role:
 * <ul>
 *   <li>the caller holds HBS_SCALE_ADMIN (capability SCALE_CONTROL_ADMIN);</li>
 *   <li>the anomaly is Outstanding ({@code SCALE_ANOMALY_STATUS_CODE = 'O'});</li>
 *   <li>its control type's ANOMALY_ASSESSMENT_CONTROL status is RCM or WCM
 *       ({@code ScaleAnomalyLog.getClearFlag}, BT10652) — no assessment batch
 *       is working on it;</li>
 *   <li>the document's scale site is in one of the caller's districts
 *       ({@code hbs3:isScaleSiteAssociatedWithOrgUnit}, HBS_USER_DIST_DATA_DOMAIN),
 *       else errors.scaleanomaly-user-access.</li>
 * </ul>
 * Write: HBS_STORE_SCALE_ANOMALY_LOG with the row re-read in this
 * transaction, except status {@code U} (user cleared), USER_ID = the caller,
 * CLEARED_DATETIME = now and USER_CLEARED_COMMENT (optional, 240 max).
 * SCALE_ANOMALY_LOG has no UPDATE_TIMESTAMP; the Outstanding precondition is
 * the concurrency check (the nightly audit B19xx only re-opens or stale-dates).
 *
 * <p>Body: {@code anomalyId}, {@code userClearedComment}.
 */
@Component
public class ClearAnomalyCommand implements WorkflowCommand {

  static final String PROC = "HBS_STORE_SCALE_ANOMALY_LOG";
  static final String OUTSTANDING = "O";
  static final String USER_CLEARED = "U";
  /** DacConstants.AnomalyAssessmentStatusCode RESOLUTION_COMPLETE / ADJUSTMENT_COMPLETE. */
  static final Set<String> CLEARABLE_CONTROL_STATUSES = Set.of("RCM", "WCM");

  private final ScaleControlReads reads;
  private final TableApi tableApi;
  private final Audit audit;

  public ClearAnomalyCommand(ScaleControlReads reads, TableApi tableApi, Audit audit) {
    this.reads = reads;
    this.tableApi = tableApi;
    this.audit = audit;
  }

  @Override
  public String id() {
    return "scaleControl.anomalies.clear";
  }

  @Override
  public String legacy() {
    return "P954 (P911Action.performActionClearAnomalies, AnomalyManagerBean.updateScaleAnomalyLog)";
  }

  @Override
  public Capability capability() {
    return Capability.SCALE_CONTROL_ADMIN;
  }

  @Override
  public Map<String, Object> execute(Map<String, Object> body) {
    long anomalyId = Inputs.requiredLong(body, "anomalyId", "Anomaly");
    Object rawComment = body.get("userClearedComment");
    String comment = rawComment == null || rawComment.toString().isBlank() ? null : rawComment.toString();
    if (comment != null && comment.length() > 240) {
      throw new WorkflowValidationException("userClearedComment", LegacyMessages.maxLength("Comment", 240));
    }

    Map<String, Object> row = reads.anomaly(anomalyId);
    if (row == null) {
      throw new WorkflowValidationException("anomalyId", "Unable to find Scale Anomaly Log, Anomaly ID:  " + anomalyId);
    }
    if (!OUTSTANDING.equals(Inputs.colText(row, "SCALE_ANOMALY_STATUS_CODE"))) {
      throw new WorkflowValidationException("anomalyId", "Only an outstanding anomaly can be cleared.");
    }
    String controlType = Inputs.colText(row, "SCALE_CONTROL_TYPE_CODE");
    Map<String, Object> control = reads.assessmentControl(controlType);
    if (control == null
        || !CLEARABLE_CONTROL_STATUSES.contains(Inputs.colText(control, "ANOMALY_ASSESSMENT_STATUS_CODE"))) {
      throw new WorkflowValidationException("anomalyId", LegacyMessages.STATUS_CHANGED);
    }
    String user = audit.user();
    String site = reads.anomalyScaleSite(anomalyId);
    if (site == null || !reads.siteInUserDistricts(site, user)) {
      throw new WorkflowValidationException("scaleSite", LegacyMessages.ANOMALY_USER_ACCESS);
    }

    Timestamp now = audit.now();
    ProcArgs args = tableApi.argsFromRow(PROC, row)
        .put("i_scale_anomaly_status_code", USER_CLEARED)
        .put("i_user_id", user)
        .put("i_cleared_datetime", now)
        .put("i_user_cleared_comment", comment)
        .put("i_org_batch_id", Inputs.col(row, "ORIGINAL_ANOMALY_BATCH_ID"));
    tableApi.call(PROC, args);
    return Map.of("anomalyId", anomalyId, "scaleAnomalyStatusCode", USER_CLEARED);
  }
}
